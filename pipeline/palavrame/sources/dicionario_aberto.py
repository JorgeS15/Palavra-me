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

from ..text import clean_definition
from ..schema import Sense, SourceEntry, canonical_pos
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="dicionario_aberto",
    name="Dicionário Aberto",
    url="https://dicionario-aberto.net/",
    license=License(
        name="Domínio público",
        url="https://dicionario-aberto.net/",
        attribution=(
            "Dicionário Aberto, a partir do Novo Diccionário da Língua "
            "Portuguesa de Cândido de Figueiredo (1913), domínio público."
        ),
        redistributable=True,
        verified=False,   # confirmar em F0 os termos do site, não só da obra
        notes=(
            "A obra de 1913 está em domínio público. Confirmar em F0 que a "
            "digitalização/edição do Dicionário Aberto não acrescenta termos "
            "próprios, e registar a atribuição que o projeto pede."
        ),
    ),
    provides=("lemmas", "senses"),
    endpoints={
        "word": "https://api.dicionario-aberto.net/word/{lemma}",
        "wordlist": "https://api.dicionario-aberto.net/wordlist",
        # "dump": preencher quando o URL do dump TEI estiver confirmado.
    },
)

_TEI_NS = {"tei": "http://www.tei-c.org/ns/1.0"}
# Marcas de domínio no fraseado de 1913: "(Náut.)", "Fig.", "[Bot.]".
_DOMAIN = re.compile(r"^\s*[\(\[]?\s*([A-ZÁÉÍÓÚÂÊÔÃÕÇ][\wçãõáéíóúâêô]{1,14}\.)\s*[\)\]]?\s*")


class DicionarioAberto(Source):
    info = INFO

    def fetch(self, lemmas: Iterable[str] | None = None) -> None:
        """Descarrega o dump, ou uma palavra por ficheiro se `lemmas` for dado."""
        dump_url = self.info.endpoints.get("dump")
        if lemmas is None and dump_url:
            self.cache.fetch(dump_url, self.slug, "dump-tei.xml")
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

        dump = base / "dump-tei.xml"
        if dump.exists():
            yield from _parse_tei_dump(dump, wanted, self.slug)

        word_dir = base / "word"
        if word_dir.is_dir():
            for path in sorted(word_dir.glob("*.json")):
                raw = path.read_text(encoding="utf-8", errors="replace").strip()
                if not raw:
                    continue
                for entry in _parse_word_payload(raw, self.slug):
                    if wanted is None or entry.normalized in wanted:
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
        lemma=lemma,
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

    pos_node = next(_iter_local(node, "pos"), None)
    entry = SourceEntry(lemma=lemma, source=slug, pos=canonical_pos(_text(pos_node)))

    syll = next(_iter_local(node, "syll"), None)
    if syll is not None:
        entry.syllables = _text(syll)

    ord_ = 0
    for sense_node in _iter_local(node, "sense"):
        for def_node in _iter_local(sense_node, "def"):
            text = clean_definition(_text(def_node) or "")
            if not text:
                continue
            ord_ += 1
            definition, domains = _split_domain(text)
            usg = [_text(u) for u in _iter_local(sense_node, "usg") if _text(u)]
            entry.senses.append(
                Sense(
                    definition=definition,
                    source=slug,
                    ord=ord_,
                    domains=sorted({*domains, *usg}),
                )
            )
    return entry


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
    """
    domains: list[str] = []
    match = _DOMAIN.match(text)
    while match:
        domains.append(match.group(1))
        text = text[match.end():]
        match = _DOMAIN.match(text)
    return clean_definition(text), domains


def _quote(lemma: str) -> str:
    from urllib.parse import quote

    return quote(lemma, safe="")


def _safe_name(lemma: str) -> str:
    from ..text import normalize

    return normalize(lemma).replace(" ", "_") or "sem_nome"
