"""Registo das fontes.

Único ponto do pipeline com acesso à rede (ver `tests/test_no_network.py`).
"""

from __future__ import annotations

from ..cache import Cache
from .base import License, Source, SourceInfo, SourceUnavailable
from .curadoria import Curadoria
from .dicionario_aberto import DicionarioAberto
from .hunspell_natura import HunspellNatura
from .leipzig import Leipzig
from .ontopt import OntoPt
from .papel import Papel
from .tatoeba import Tatoeba
from .voc_cplp import VocCplp
from .leipzig_en import LeipzigEn
from .wikcionario import Wikcionario
from .wiktionary_en import WiktionaryEn
from .wordnet_pt import WordnetPt

# Ordem = ordem de execução do pipeline. O VOC vem primeiro porque define
# quais são as palavras; as outras fontes penduram-se nessa lista.
SOURCE_CLASSES: tuple[type[Source], ...] = (
    VocCplp,
    DicionarioAberto,
    Wikcionario,
    Curadoria,
    HunspellNatura,
    WordnetPt,
    OntoPt,
    Papel,
    Tatoeba,
    Leipzig,
)

REGISTRY: dict[str, type[Source]] = {cls.info.slug: cls for cls in SOURCE_CLASSES}

# O dicionário de inglês é outra base, com fontes próprias. Ficam num registo à
# parte para a build portuguesa (que percorre `REGISTRY`) nunca as ver.
SOURCE_CLASSES_EN: tuple[type[Source], ...] = (
    WiktionaryEn,
    LeipzigEn,
)

REGISTRY_EN: dict[str, type[Source]] = {cls.info.slug: cls for cls in SOURCE_CLASSES_EN}

REGISTOS = {"pt": REGISTRY, "en": REGISTRY_EN}
LINGUAS = tuple(REGISTOS)


def registo(lingua: str = "pt") -> dict[str, type[Source]]:
    if lingua not in REGISTOS:
        raise KeyError(f"Língua desconhecida: {lingua}. Conhecidas: {', '.join(LINGUAS)}")
    return REGISTOS[lingua]


def all_infos(lingua: str = "pt") -> list[SourceInfo]:
    return [cls.info for cls in registo(lingua).values()]


def build(slug: str, cache: Cache) -> Source:
    todas = {**REGISTRY, **REGISTRY_EN}
    if slug not in todas:
        known = ", ".join(todas)
        raise KeyError(f"Fonte desconhecida: {slug}. Conhecidas: {known}")
    return todas[slug](cache)


__all__ = [
    "LINGUAS",
    "REGISTRY",
    "REGISTRY_EN",
    "SOURCE_CLASSES",
    "License",
    "Source",
    "SourceInfo",
    "SourceUnavailable",
    "all_infos",
    "build",
    "registo",
]
