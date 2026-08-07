"""Dicionário Aberto — esqueleto principal das aceções.

Cândido de Figueiredo, *Novo Diccionário da Língua Portuguesa* (1913), em
domínio público. Boa cobertura de PT-PT, vocabulário e fraseado datados
(plano 4.4). É a fonte de maior volume e a de menor risco legal.

Dois caminhos de aquisição
--------------------------
* **API por palavra** (`api.dicionario-aberto.net/word/<lema>`) — é o caminho
  certo para a F0: 100 pedidos, sem descarregar um dump inteiro.
* **Dump TEI** — para a F1, quando se quer o dicionário todo. O URL do dump
  está por confirmar; preenche-se `endpoints["dump"]` e `fetch()` trata dele.

A resposta da API não tem um esquema publicado e estável, por isso o parser é
deliberadamente tolerante: aceita JSON com várias formas conhecidas e cai para
o TEI embutido quando o encontra. Confirmar o formato real é literalmente o
passo 1 da F0 — se a forma mudar, é aqui que se ajusta.
"""

from __future__ import annotations

import json
import re
from typing import Any, Iterable, Iterator
from xml.etree import ElementTree as ET

from ..mysqldump import insert_re, open_dump, sql_values
from ..text import clean_definition
from ..schema import Sense, SourceEntry, canonical_pos
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="dicionario_aberto",
    name="Dicionário Aberto",
    url="https://dicionario-aberto.net/",
    license=License(
        name="CC BY-SA 2.5 PT",
        url="https://creativecommons.org/licenses/by-sa/2.5/pt/",
        attribution=(
            "Dicionário Aberto (dicionario-aberto.net), a partir do Novo "
            "Diccionário da Língua Portuguesa de Cândido de Figueiredo "
            "(1913), CC BY-SA 2.5 PT."
        ),
        redistributable=True,
        verified=True,   # lido em 2026-07-30 em dicionario-aberto.net/about
        notes=(
            "A obra de 1913 está em domínio público, mas o site declara a "
            "edição digital como CC BY-SA 2.5 PT ('This work is licensed "
            "under a Creative Commons Attribution-Share Alike 2.5 Portugal "
            "License'). Tratar como copyleft, tal como o Wikcionário — não "
            "muda nada na prática, a DB derivada já ia ser CC BY-SA."
        ),
    ),
    provides=("lemmas", "senses"),
    endpoints={
        "word": "https://api.dicionario-aberto.net/word/{lemma}",
        "wordlist": "https://api.dicionario-aberto.net/wordlist",
        # O dicionário inteiro (F1). É o dump oficial do repositório do
        # projeto — mysqldump de 2015-12-13, o único publicado. A API por
        # palavra continua a valer para o que for mais recente.
        "dump": (
            "https://github.com/ambs/Dicionario-Aberto/raw/master/SQL/"
            "data-20151213.sql.xz"
        ),
    },
)

_TEI_NS = {"tei": "http://www.tei-c.org/ns/1.0"}


def _decap(lemma: str) -> str:
    """Tira a capitalização tipográfica dos verbetes de 1913.

    O dicionário original capitaliza TODOS os verbetes ('Casa', 'Caber',
    'Água') — é tipografia, não grafia, e sem isto cada verbete criava na
    fusão uma entrada duplicada ao lado da do Wikcionário ('Caber' vazio ao
    lado de 'caber'). Rebaixa-se apenas o padrão de verbete (inicial
    maiúscula + resto minúsculo); siglas e afins ficam como estão.
    O custo — nomes próprios do DA perderem a caixa — é aceitável num
    dicionário de língua e fica registado aqui.
    """
    if len(lemma) > 1 and lemma[0].isupper() and lemma[1:].islower():
        return lemma[0].lower() + lemma[1:]
    return lemma


# Marcas de domínio no fraseado de 1913: "(Náut.)", "Fig.", "[Bot.]".
_DOMAIN = re.compile(r"^\s*[\(\[]?\s*([A-ZÁÉÍÓÚÂÊÔÃÕÇ][\wçãõáéíóúâêô]{1,14}\.)\s*[\)\]]?\s*")

# Abreviaturas que o dicionário de 1913 usa como marca de domínio ou de uso.
# Fora dos parênteses, só estas contam — ver `_split_domain`.
_ABREVIATURAS = frozenset({
    "Fig.", "Fam.", "Ant.", "Prov.", "Pop.", "Poét.", "Bras.", "Neol.",
    "Anat.", "Arch.", "Archit.", "Astron.", "Bot.", "Chim.", "Cir.",
    "Com.", "Ecles.", "Geol.", "Geom.", "Gram.", "Hist.", "Jur.", "Liturg.",
    "Med.", "Mil.", "Min.", "Mús.", "Náut.", "Pharm.", "Phil.", "Phys.",
    "Theol.", "Typ.", "Vet.", "Zool.", "Bibl.", "Agric.", "Alg.", "Arith.",
    "Bibliogr.", "Dram.", "Eccles.", "Electr.", "Escol.", "Filos.", "Fís.",
    "Gír.", "Ictiol.", "Jogo.", "Lóg.", "Mat.", "Mec.", "Metal.", "Meteor.",
    "Mit.", "Numism.", "Ópt.", "Ornit.", "Pint.", "Psic.", "Quím.", "Ret.",
    "Teol.", "Term.", "Zoot.",
})


class DicionarioAberto(Source):
    info = INFO

    def fetch(self, lemmas: Iterable[str] | None = None) -> None:
        """Descarrega o dump, ou uma palavra por ficheiro se `lemmas` for dado."""
        dump_url = self.info.endpoints.get("dump")
        if lemmas is None and dump_url:
            if ".sql" in dump_url:
                name = "da-dump.sql" + (".xz" if dump_url.endswith(".xz") else "")
            else:
                name = "dump-tei.xml"
            self.cache.fetch(dump_url, self.slug, name)
            return
        if lemmas is None:
            raise ValueError(
                "Sem URL de dump confirmado, o Dicionário Aberto só se busca "
                "por lema. Passa a lista de lemas (ex.: seeds/lemas-f0.txt)."
            )
        template = self.info.endpoints["word"]
        for lemma in lemmas:
            url = template.format(lemma=_quote(lemma))
            self.cache.fetch(url, self.slug, f"word/{_safe_name(lemma)}.json")

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        base = self.cache.paths.cache / self.slug

        # A API por palavra primeiro: é mais recente do que qualquer dump
        # (o dump SQL publicado é de 2015). O que a API já cobriu não se
        # repete a partir do dump.
        seen: set[str] = set()
        word_dir = base / "word"
        if word_dir.is_dir():
            for path in sorted(word_dir.glob("*.json")):
                raw = path.read_text(encoding="utf-8", errors="replace").strip()
                if not raw:
                    continue
                for entry in _parse_word_payload(raw, self.slug):
                    if wanted is None or entry.normalized in wanted:
                        seen.add(entry.normalized)
                        yield entry

        dump = base / "dump-tei.xml"
        if dump.exists():
            for entry in _parse_tei_dump(dump, wanted, self.slug):
                if entry.normalized not in seen:
                    yield entry

        for sql in sorted(list(base.glob("*.sql")) + list(base.glob("*.sql.xz"))):
            for entry in _parse_sql_dump(sql, wanted, self.slug):
                if entry.normalized not in seen:
                    yield entry


# --- API por palavra -------------------------------------------------------


def _parse_word_payload(raw: str, slug: str) -> list[SourceEntry]:
    """A API devolve JSON; algumas respostas embrulham TEI numa string."""
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        # Resposta já em XML.
        return _parse_tei_string(raw, slug)

    entries: list[SourceEntry] = []
    for item in _as_list(data):
        if isinstance(item, str):
            entries.extend(_parse_tei_string(item, slug))
        elif isinstance(item, dict):
            if "xml" in item and isinstance(item["xml"], str):
                entries.extend(_parse_tei_string(item["xml"], slug))
            else:
                entry = _entry_from_dict(item, slug)
                if entry:
                    entries.append(entry)
    return entries


def _as_list(data: Any) -> list[Any]:
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("entries", "results", "data"):
            if isinstance(data.get(key), list):
                return data[key]
    return [data]


def _entry_from_dict(item: dict, slug: str) -> SourceEntry | None:
    lemma = _first_str(item, ("lemma", "word", "orth", "headword", "form"))
    if not lemma:
        return None
    entry = SourceEntry(
        lemma=_decap(lemma),
        source=slug,
        pos=canonical_pos(_first_str(item, ("pos", "gram", "class", "gramGrp"))),
        syllables=_first_str(item, ("syllables", "syll", "hyphenation")),
    )
    raw_senses = item.get("senses") or item.get("definitions") or item.get("defs")
    for ord_, sense in enumerate(raw_senses or [], start=1):
        text = sense if isinstance(sense, str) else _first_str(
            sense, ("definition", "def", "text", "sense")
        )
        if not text:
            continue
        definition, domains = _split_domain(clean_definition(text))
        if definition:
            entry.senses.append(
                Sense(definition=definition, source=slug, ord=ord_, domains=domains)
            )
    return entry if entry.senses or entry.lemma else None


def _first_str(item: dict, keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


# --- TEI -------------------------------------------------------------------


def _parse_tei_dump(path, wanted: set[str] | None, slug: str) -> Iterator[SourceEntry]:
    """Percorre o dump em streaming — tem centenas de MB."""
    for _, elem in ET.iterparse(str(path), events=("end",)):
        if _localname(elem.tag) != "entry":
            continue
        entry = _entry_from_tei(elem, slug)
        if entry and (wanted is None or entry.normalized in wanted):
            yield entry
        elem.clear()


def _parse_tei_string(xml: str, slug: str) -> list[SourceEntry]:
    xml = xml.strip()
    if not xml.startswith("<"):
        return []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        try:
            root = ET.fromstring(f"<root>{xml}</root>")
        except ET.ParseError:
            return []
    nodes = [root] if _localname(root.tag) == "entry" else list(
        _iter_local(root, "entry")
    )
    out = []
    for node in nodes:
        entry = _entry_from_tei(node, slug)
        if entry:
            out.append(entry)
    return out


def _entry_from_tei(node: ET.Element, slug: str) -> SourceEntry | None:
    orth = next(_iter_local(node, "orth"), None)
    lemma = _text(orth)
    if not lemma:
        return None

    # A classe gramatical vem em <gramGrp> ('adj.', 's. m.'), não em <pos>.
    # Procurar a etiqueta errada deixava o dicionário inteiro de 1913 com
    # pos='desconhecido'.
    # `is not None` e não `or`: um Element sem filhos é falso em
    # ElementTree, e um `<gramGrp>adj.</gramGrp>` não tem filhos nenhuns —
    # com `or` era descartado e a classe gramatical perdia-se toda.
    # Duas formas no mesmo dicionário: `<gramGrp>adj.</gramGrp>` e
    # `<gramGrp><pos>s. m.</pos></gramGrp>`. Tenta-se o texto de uma e,
    # se vier vazio, o da outra — testar só a presença do elemento deixava
    # de fora todas as entradas da segunda forma.
    pos_texto = ""
    for nome in ("gramGrp", "pos"):
        no = next(_iter_local(node, nome), None)
        pos_texto = _text(no) if no is not None else ""
        if pos_texto.strip():
            break
    entry = SourceEntry(
        lemma=_decap(lemma), source=slug, pos=canonical_pos(pos_texto)
    )

    syll = next(_iter_local(node, "syll"), None)
    if syll is not None:
        entry.syllables = _text(syll)

    ord_ = 0
    for sense_node in _iter_local(node, "sense"):
        # No TEI o domínio está marcado em <usg>; não se adivinha a partir
        # do texto. Ver `_split_domain` para o porquê de não o fazer aqui.
        usg = [_text(u) for u in _iter_local(sense_node, "usg") if _text(u)]
        for def_node in _iter_local(sense_node, "def"):
            for texto in _acecoes_do_def(_text(def_node) or ""):
                # A marca pode vir em <usg> ou entre parênteses no texto,
                # conforme a entrada; aceitam-se as duas.
                definicao, dominios = _split_domain(texto)
                # Segunda limpeza, depois de extrair o domínio: é ela que
                # apanha o parêntese que ficou órfão quando a marca saiu.
                # `(Gír. Or. ind.)` deixava a aceção `ind.)` no `adicar`.
                definicao = clean_definition(definicao)
                if not definicao:
                    continue
                ord_ += 1
                entry.senses.append(
                    Sense(
                        definition=definicao,
                        source=slug,
                        ord=ord_,
                        domains=sorted({*dominios, *usg}),
                    )
                )
    return entry


def _acecoes_do_def(bruto: str) -> list[str]:
    """Parte um <def> nas suas aceções.

    O dicionário de 1913 escreve as várias aceções de uma palavra em linhas
    seguidas dentro do mesmo <def>::

        <def>
        Magro.
        Pálido.
        Amortecido.
        </def>

    São três aceções, não uma. Tratá-las como um bloco só dava entradas
    longas e ilegíveis; parti-las dá o que se espera de um dicionário.

    **Mas não se parte dentro de parênteses.** As etimologias e remissões de
    1913 vêm entre parênteses e podem atravessar linhas::

        Tremeluzir; brilhar froixamente.
        (Cp. cast. grujulear)

    Partir isto às cegas produzia uma aceção de mentira — `cast. grujulear)`,
    com o parêntese órfão à vista — e havia **5 105 dessas na base**, 1,4% do
    total. `adicar` tinha como segundo significado `ind.)`. A regra é a de
    qualquer parser: só se corta onde os parênteses estão fechados.
    """
    acecoes: list[str] = []
    pendente = ""
    for linha in bruto.splitlines():
        pendente = f"{pendente} {linha}".strip() if pendente else linha
        if pendente.count("(") > pendente.count(")"):
            continue          # parêntese por fechar: a aceção ainda não acabou
        if pendente.count(")") > pendente.count("("):
            # Fecha um parêntese que nunca abriu: é cauda de outra coisa, não
            # é uma aceção. Verifica-se aqui, no texto em bruto, porque a
            # limpeza tira o parêntese órfão e apagaria a pista.
            pendente = ""
            continue
        if (limpa := clean_definition(pendente)):
            acecoes.append(limpa)
        pendente = ""
    if pendente and (limpa := clean_definition(pendente)):
        acecoes.append(limpa)
    return acecoes


def _iter_local(node: ET.Element, name: str) -> Iterator[ET.Element]:
    """`iter()` ignorando o namespace — o TEI vem ora com ora sem."""
    for child in node.iter():
        if child is not node and _localname(child.tag) == name:
            yield child


def _localname(tag: Any) -> str:
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def _text(node: ET.Element | None) -> str | None:
    if node is None:
        return None
    text = "".join(node.itertext()).strip()
    return text or None


def _split_domain(text: str) -> tuple[str, list[str]]:
    """Separa a marca de domínio do início da definição.

    '(Náut.) Cabo de amarração.' -> ('Cabo de amarração.', ['Náut.'])

    Só reconhece marcas entre parênteses/parênteses retos, ou abreviaturas
    da lista conhecida. A versão anterior aceitava qualquer palavra
    capitalizada terminada em ponto, e isso comeu definições inteiras: a
    entrada de *macilento* é `Magro. Pálido. Amortecido.` e ficou vazia,
    com as três palavras registadas como domínios. Uma definição nunca é
    apagada por uma heurística — se houver dúvida, o texto fica.
    """
    domains: list[str] = []
    while True:
        match = _DOMAIN.match(text)
        if not match:
            break
        marca = match.group(1)
        entre_parenteses = match.group(0).lstrip()[:1] in "(["
        if not entre_parenteses and marca not in _ABREVIATURAS:
            break
        resto = text[match.end():]
        if not resto.strip():
            break        # era a definição inteira, não um domínio
        domains.append(marca)
        text = resto
    return clean_definition(text), domains


def _quote(lemma: str) -> str:
    from urllib.parse import quote

    return quote(lemma, safe="")


def _safe_name(lemma: str) -> str:
    from ..text import normalize

    return normalize(lemma).replace(" ", "_") or "sem_nome"


# --- dump SQL (github.com/ambs/Dicionario-Aberto, SQL/data-*.sql.xz) --------
#
# O dicionário inteiro só está publicado como dump mysqldump: a tabela `word`
# diz qual é a revisão em vigor de cada palavra (`last_revision`, `deleted`)
# e a tabela `revision` traz o TEI de cada revisão na coluna `xml`. Duas
# passagens sobre o ficheiro — `revision` aparece antes de `word` no dump,
# e guardar todos os XML em memória seria o dicionário inteiro.

_INSERT = insert_re("word", "revision")


def _parse_sql_dump(path, wanted: set[str] | None, slug: str) -> Iterator[SourceEntry]:
    # 1ª passagem: que revisão vale para cada palavra.
    # word: (word_id, word, sense, last_revision, deleted, creator, deletor,
    #        normalized, derived_from)
    em_vigor: dict[str, str] = {}   # word_id -> last_revision (como texto)
    with open_dump(path) as fh:
        for line in fh:
            m = _INSERT.match(line)
            if not m or m.group(1) != "word":
                continue
            for row in sql_values(line):
                if len(row) < 5:
                    continue
                word_id, _, _, last_rev, deleted = row[0], row[1], row[2], row[3], row[4]
                if str(deleted) == "0":
                    em_vigor[str(word_id)] = str(last_rev)

    # 2ª passagem: o TEI das revisões em vigor.
    # revision: (revision_id, word_id, creator, timestamp, xml, deleted, ...)
    with open_dump(path) as fh:
        for line in fh:
            m = _INSERT.match(line)
            if not m or m.group(1) != "revision":
                continue
            for row in sql_values(line):
                if len(row) < 6:
                    continue
                rev_id, word_id, xml, deleted = row[0], row[1], row[4], row[5]
                if str(deleted) != "0" or not isinstance(xml, str):
                    continue
                if em_vigor.get(str(word_id)) != str(rev_id):
                    continue
                for entry in _parse_tei_string(xml, slug):
                    if wanted is None or entry.normalized in wanted:
                        yield entry
