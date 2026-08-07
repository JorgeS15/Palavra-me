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
from .wikcionario import Wikcionario
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


def all_infos() -> list[SourceInfo]:
    return [cls.info for cls in SOURCE_CLASSES]


def build(slug: str, cache: Cache) -> Source:
    if slug not in REGISTRY:
        known = ", ".join(REGISTRY)
        raise KeyError(f"Fonte desconhecida: {slug}. Conhecidas: {known}")
    return REGISTRY[slug](cache)


__all__ = [
    "REGISTRY",
    "SOURCE_CLASSES",
    "License",
    "Source",
    "SourceInfo",
    "SourceUnavailable",
    "all_infos",
    "build",
]
