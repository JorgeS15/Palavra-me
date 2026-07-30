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

from ..schema import Relation, SourceEntry
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="wordnet_pt",
    name="Wordnet do português (PULO / OpenWordNet-PT)",
    url="http://wordnet.pt/",
    license=License(
        name="POR VERIFICAR",
        url="http://wordnet.pt/",
        attribution=(
            "PULO — Portuguese Unified Lexical Ontology, Universidade do "
            "Minho / OpenWordNet-PT."
        ),
        redistributable=None,
        verified=False,
        notes=(
            "PULO e OpenWordNet-PT têm termos diferentes. Verificar cada um "
            "separadamente em F0 e escolher, ou fundir só o que for "
            "redistribuível. O OpenWordNet-PT costuma ser o mais permissivo."
        ),
    ),
    provides=("relations",),
    endpoints={},   # preencher em F0 com o ficheiro escolhido
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


class WordnetPt(Source):
    info = INFO

    def fetch(self) -> None:
        if not self.info.endpoints:
            raise RuntimeError(
                "Nenhuma wordnet escolhida. Depois de decidir em F0 entre "
                "PULO e OpenWordNet-PT, preenche `endpoints` em "
                "sources/wordnet_pt.py."
            )
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, name)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return

        members: dict[str, list[str]] = defaultdict(list)   # synset -> palavras
        links: list[tuple[str, str, str]] = []              # synset, rel, synset

        for path in sorted(base.glob("*.nt")):
            _read_nt(path, members, links)
        for path in sorted(base.glob("*.tsv")):
            _read_tsv(path, members, links)

        # Relações entre synsets expandidas para relações entre palavras.
        cross: dict[str, list[Relation]] = defaultdict(list)
        for left, relation, right in links:
            for a in members.get(left, ()):
                for b in members.get(right, ()):
                    if a != b:
                        cross[a].append(Relation(target=b, relation=relation))

        by_word: dict[str, list[Relation]] = defaultdict(list)
        for words in members.values():
            for word in words:
                for other in words:
                    if other != word:
                        by_word[word].append(
                            Relation(target=other, relation="sinonimo")
                        )
        for word, relations in cross.items():
            by_word[word].extend(relations)

        for word, relations in by_word.items():
            entry = SourceEntry(
                lemma=word, source=self.slug, relations=_dedupe(relations)
            )
            if wanted is None or entry.normalized in wanted:
                yield entry


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
    with open(path, encoding="utf-8", errors="replace") as fh:
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
