"""Wordnet do português — sinónimos, antónimos, hiperónimos.

Cobre PULO (wordnet.pt, Universidade do Minho) e OpenWordNet-PT, que publicam
formatos diferentes mas dizem a mesma espécie de coisa: palavras agrupadas em
*synsets*, com relações entre synsets.

O pipeline aceita dois formatos, pela ordem em que os encontra no cache:

* **N-Triples** (`.nt`) — formato do OpenWordNet-PT. Lêem-se as formas lexicais
  por synset e as relações entre synsets.
* **TSV** (`.tsv`) — formato simples, útil para PULO ou para uma extração
  manual::

      <synset_id>\\t<palavra>[\\t<pos>]
      # relações:
      <synset_id>\\t<relacao>\\t<synset_id>

Duas palavras no mesmo synset são sinónimos uma da outra: é assim que se
derivam as relações `sinonimo` sem precisar de as ter explícitas.
"""

from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Iterator

from ..mysqldump import open_dump, sql_values
from ..schema import Relation, Sense, SourceEntry
from ..text import limpar_glosa, parece_do_brasil
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="wordnet_pt",
    name="Wordnet do português (PULO / OpenWordNet-PT)",
    url="http://wordnet.pt/",
    license=License(
        name="CC BY-SA 2.5 PT (PULO); CC BY 4.0 (OpenWordNet-PT)",
        url="https://creativecommons.org/licenses/by-sa/2.5/pt/",
        attribution=(
            "PULO — Portuguese Unified Lexical Ontology (wordnet.pt), "
            "Universidade do Minho, CC BY-SA 2.5 PT."
        ),
        redistributable=True,
        verified=True,   # 2026-08-01: resposta do autor por email
        notes=(
            "RESOLVIDO em 2026-08-01. Alberto Simões respondeu ao email: "
            "'Pode usar os dados do PULO, com a mesma licença do dicionário "
            "aberto' — ou seja CC BY-SA 2.5 PT, o mesmo copyleft que a DB já "
            "herda do Dicionário Aberto e do Wikcionário. O PULO entra, com "
            "atribuição. O OpenWordNet-PT (CC BY 4.0, verificado 2026-07-30) "
            "continua compatível e pode ser fundido, mas o URL do dump no "
            "GitHub mudou e dá 404; o PULO publica um SQL estável, que é o "
            "caminho preferido."
        ),
    ),
    provides=("relations", "senses"),
    primary="pulo.sql.xz",
    endpoints={},   # ver CANDIDATOS; se mudarem, fetch --url resolve
    manual=(
        "PULO: http://wordnet.pt/download → copia o link do .sql.xz mais "
        "recente. OpenWordNet-PT: https://github.com/own-pt/openWordnet-PT/"
        "tree/master/dump → link raw do dump. Depois: "
        "palavrame fetch --source wordnet_pt --url <URL>"
    ),
)

# Relações que interessam à app. Uma wordnet tem dezenas; a UI mostra três.
_RELATION_MAP = {
    "hypernym": "hiperonimo",
    "hyponym": "hiponimo",
    "antonym": "antonimo",
    "hiperonimo": "hiperonimo",
    "hiponimo": "hiponimo",
    "antonimo": "antonimo",
    "sinonimo": "sinonimo",
}

_NT_TRIPLE = re.compile(
    r'^\s*<([^>]+)>\s+<([^>]+)>\s+(?:<([^>]+)>|"((?:[^"\\]|\\.)*)")'
)


# Caminhos prováveis do dump do OpenWordNet-PT no GitHub, por ordem. O
# repositório reorganiza-se de vez em quando; um 404 aqui é esperado, não é
# avaria — o INFO.manual explica como obter o URL certo.
CANDIDATOS = (
    # PULO primeiro: URL estável e licença agora confirmada por email.
    "http://wordnet.pt/sql/pulo.20160508.sql.xz",
    "https://raw.githubusercontent.com/own-pt/openWordnet-PT/master/dump/own-pt.nt.gz",
    "https://raw.githubusercontent.com/own-pt/openWordnet-PT/master/dump/own-pt.nt",
)

CACHE_NAME = "pulo.sql.xz"

# Acima de quantos membros um synset deixa de servir para derivar sinónimos.
# Ver a explicação em `parse`. Cinco é conservador e mantém os synsets que
# são mesmo conjuntos de sinónimos ('cão', 'cachorro', 'Canis familiaris').
MAX_SINONIMOS_POR_SYNSET = 5

# Comprimento mínimo de uma glosa para valer como definição. Abaixo disto são
# fragmentos da tradução automática, não definições.
MIN_GLOSA = 15


class WordnetPt(Source):
    info = INFO

    def fetch(self) -> None:
        urls = list(self.info.endpoints.values()) or list(CANDIDATOS)
        falhas = []
        for url in urls:
            try:
                self.cache.fetch(url, self.slug, CACHE_NAME)
                print(f"      encontrado em {url}")
                return
            except Exception as exc:
                falhas.append(f"{url} -> {exc}")
        raise SourceUnavailable(
            "nenhum dos caminhos conhecidos do OpenWordNet-PT respondeu.\n      "
            + "\n      ".join(falhas)
            + "\n\n      "
            + self.info.manual
        )

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return

        members: dict[str, list[str]] = defaultdict(list)   # synset -> palavras
        links: list[tuple[str, str, str]] = []              # synset, rel, synset
        glosses: dict[str, str] = {}                        # synset -> definição

        for path in sorted(list(base.glob("*.nt")) + list(base.glob("*.nt.gz"))):
            _read_nt(path, members, links)
        for path in sorted(base.glob("*.tsv")):
            _read_tsv(path, members, links)
        for path in sorted(list(base.glob("*.sql")) + list(base.glob("*.sql.xz"))):
            _read_pulo_sql(path, members, links, glosses)

        # Relações entre synsets expandidas para relações entre palavras.
        cross: dict[str, list[Relation]] = defaultdict(list)
        for left, relation, right in links:
            for a in members.get(left, ()):
                for b in members.get(right, ()):
                    if a != b:
                        cross[a].append(Relation(target=b, relation=relation))

        by_word: dict[str, list[Relation]] = defaultdict(list)
        for words in members.values():
            # Synsets muito povoados não são conjuntos de sinónimos: são
            # baldes de tradução. O PULO foi construído alinhando português
            # com a WordNet de Princeton, e o synset inglês 'hole' recolhe
            # janela, buraco, covil, défice e dívida — traduções do mesmo
            # conceito inglês, que em português não são sinónimas. Mostrar
            # isso a quem lê seria pior do que não mostrar nada, por isso
            # acima deste tamanho só se aproveitam as relações explícitas
            # (hiperónimo, antónimo), que vêm entre synsets e não sofrem
            # do mesmo problema.
            if len(words) > MAX_SINONIMOS_POR_SYNSET:
                continue
            for word in words:
                for other in words:
                    if other != word:
                        by_word[word].append(
                            Relation(target=other, relation="sinonimo")
                        )
        for word, relations in cross.items():
            by_word[word].extend(relations)

        senses = _glosas_por_palavra(members, glosses)

        for word in sorted(set(by_word) | set(senses)):
            entry = SourceEntry(
                lemma=word,
                source=self.slug,
                relations=_dedupe(by_word.get(word, [])),
                senses=[
                    Sense(definition=texto, source=self.slug, ord=i)
                    for i, texto in enumerate(senses.get(word, ()), start=1)
                ],
            )
            if wanted is None or entry.normalized in wanted:
                yield entry


def _glosas_por_palavra(
    members: dict, glosses: dict
) -> "dict[str, list[str]]":
    """Glosa de cada synset atribuída às palavras portuguesas que o compõem.

    As glosas do PULO são tradução automática das da WordNet de Princeton, e
    isso vê-se: umas são boas (*"a região do corpo de um vertebrado entre o
    tórax e a pelve"*), outras são inglês mal vertido (*"de ou relacionado
    com..."*). Entram na base porque **12 941 palavras não têm melhor** — e
    entram em último lugar entre as fontes abertas, para que só apareçam
    onde o Wikcionário e o Dicionário Aberto se calaram.

    O que não passa: glosas em ortografia brasileira (ver `parece_do_brasil`)
    e glosas curtas demais para dizerem alguma coisa.
    """
    por_palavra: "dict[str, list[str]]" = defaultdict(list)
    for synset, bruta in glosses.items():
        palavras = members.get(synset)
        if not palavras:
            continue                       # synset sem português: não serve
        if parece_do_brasil(bruta):
            continue
        texto = limpar_glosa(bruta)
        if len(texto) < MIN_GLOSA:
            continue
        for palavra in palavras:
            if texto not in por_palavra[palavra]:
                por_palavra[palavra].append(texto)
    return por_palavra


def _dedupe(relations: list[Relation]) -> list[Relation]:
    seen: set[tuple[str, str]] = set()
    out: list[Relation] = []
    for rel in relations:
        key = (rel.target, rel.relation)
        if key not in seen:
            seen.add(key)
            out.append(rel)
    return out


def _read_nt(
    path: Path,
    members: dict[str, list[str]],
    links: list[tuple[str, str, str]],
) -> None:
    """Lê N-Triples do OpenWordNet-PT.

    Interessam duas formas de triplo: as que ligam um synset a uma forma
    lexical em português, e as que ligam dois synsets por uma relação.
    """
    # O dump do GitHub vem em .gz; aceita-se tal como veio, por deteção dos
    # bytes mágicos (o nome no cache pode não refletir o conteúdo).
    import gzip

    with open(path, "rb") as probe:
        is_gzip = probe.read(2) == b"\x1f\x8b"
    opener = (
        (lambda: gzip.open(path, "rt", encoding="utf-8", errors="replace"))
        if is_gzip
        else (lambda: open(path, encoding="utf-8", errors="replace"))
    )
    with opener() as fh:
        for line in fh:
            match = _NT_TRIPLE.match(line)
            if not match:
                continue
            subject, predicate, obj_uri, literal = match.groups()
            pred = predicate.rsplit("/", 1)[-1].rsplit("#", 1)[-1].lower()

            if literal is not None:
                if pred in {"lexicalform", "label", "word", "writtenform"}:
                    # Só o português: o OWN-PT traz também as formas inglesas.
                    if '@pt' in line or '@en' not in line:
                        word = _unescape(literal).strip()
                        if word:
                            members[subject].append(word)
            elif obj_uri is not None:
                relation = _RELATION_MAP.get(pred)
                if relation:
                    links.append((subject, relation, obj_uri))


def _read_tsv(
    path: Path,
    members: dict[str, list[str]],
    links: list[tuple[str, str, str]],
) -> None:
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split("\t")]
            if len(parts) < 2:
                continue
            if len(parts) >= 3 and parts[1].lower() in _RELATION_MAP:
                links.append((parts[0], _RELATION_MAP[parts[1].lower()], parts[2]))
            elif parts[1]:
                members[parts[0]].append(parts[1])


def _unescape(literal: str) -> str:
    return (
        literal.replace('\\"', '"')
        .replace("\\n", " ")
        .replace("\\t", " ")
        .replace("\\\\", "\\")
    )


# --- PULO (wordnet.pt), dump MySQL ------------------------------------------
#
# Esquema real do `pulo.<data>.sql.xz`, confirmado no ficheiro (2026-08-01):
#
#   wei_por-30_variant   word, sense, offset, pos, ...   palavra -> synset
#   wei_por-30_relation  relation, sourceSynset, sourcePos,
#                        targetSynset, targetPos, ...    synset -> synset
#   wei_por-30_synset    offset, pos, ..., gloss, ...    glosas (em português)
#
# `relation` é um código numérico herdado do EuroWordNet/MCR, sem tabela de
# nomes no dump. Os códigos abaixo foram identificados por dupla via: pares
# de exemplo legíveis ('capaz' -> 'incapaz') e contagens que batem certo com
# as estatísticas publicadas do OpenWordNet-PT (similarTo 21386,
# memberHolonym 12293, partHolonym 9097, hypernym ~89k). Só se mapeiam os
# códigos de que há certeza; o resto ignora-se em silêncio, que é melhor do
# que inventar uma relação errada.
_PULO_RELACOES = {
    "12": "hiponimo",   # source é o geral, target o específico
    "33": "antonimo",
    "34": "sinonimo",   # similar_to entre adjetivos: perto de sinónimo
}

_PULO_VARIANT = "wei_por-30_variant"
_PULO_RELATION = "wei_por-30_relation"
_PULO_SYNSET = "wei_por-30_synset"


def _read_pulo_sql(path, members: dict, links: list, glosses: dict = None) -> None:
    inverso = {"hiponimo": "hiperonimo", "hiperonimo": "hiponimo"}
    if glosses is None:
        glosses = {}

    with open_dump(path) as fh:
        for line in fh:
            stripped = line.lstrip()
            if not stripped.startswith("INSERT"):
                continue

            if _PULO_VARIANT in stripped[:80]:
                for row in sql_values(line):
                    if len(row) < 4:
                        continue
                    word, _, offset, pos = row[0], row[1], row[2], row[3]
                    if not isinstance(word, str) or not word:
                        continue
                    # As expressões vêm com underscore: 'fazer_dormir'.
                    members[f"{offset}-{pos}"].append(word.replace("_", " "))

            elif _PULO_RELATION in stripped[:80]:
                for row in sql_values(line):
                    if len(row) < 5:
                        continue
                    relacao = _PULO_RELACOES.get(str(row[0]))
                    if not relacao:
                        continue
                    esquerda = f"{row[1]}-{row[2]}"
                    direita = f"{row[3]}-{row[4]}"
                    links.append((esquerda, relacao, direita))
                    # O PULO guarda só um sentido da relação hierárquica;
                    # a app precisa dos dois para mostrar o hiperónimo de
                    # uma palavra sem ter de percorrer a tabela ao contrário.
                    if relacao in inverso:
                        links.append((direita, inverso[relacao], esquerda))

            elif _PULO_SYNSET in stripped[:80]:
                # offset, pos, sons, status, lexical, instance, gloss, ...
                for row in sql_values(line):
                    if len(row) < 7:
                        continue
                    glosa = row[6]
                    if isinstance(glosa, str) and glosa.strip():
                        glosses[f"{row[0]}-{row[1]}"] = glosa.strip()
