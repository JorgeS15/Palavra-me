"""Contrato comum a todas as fontes.

Cada módulo em `sources/` faz duas coisas e mais nenhuma:

1. `fetch()` — traz os ficheiros brutos para o cache. Único sítio com rede.
2. `parse()` — lê do cache e devolve `SourceEntry`s. Sem rede, determinístico.

A separação existe para que `parse()` seja testável com fixtures e para que a
build inteira possa correr offline depois do primeiro `fetch`.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable, Iterator

from ..cache import Cache
from ..schema import SourceEntry


@dataclass(frozen=True)
class License:
    """Estado de licenciamento de uma fonte.

    `redistributable` é a única pergunta que importa para publicar a app, e é
    deliberadamente um tri-estado: `None` significa "ainda não verificado",
    não "provavelmente sim". O `validate` recusa construir uma DB de
    distribuição com fontes por verificar.
    """

    name: str                       # "CC BY 2.0 FR", "Domínio público"
    url: str | None = None
    attribution: str = ""           # texto exato exigido pela fonte
    redistributable: bool | None = None
    verified: bool = False          # licença lida e confirmada por um humano
    notes: str = ""


@dataclass(frozen=True)
class SourceInfo:
    """Identidade de uma fonte. Alimenta `docs/fontes.md` e a tabela `sources`."""

    slug: str
    name: str
    url: str
    license: License
    provides: tuple[str, ...] = ()   # 'lemmas'|'senses'|'forms'|'examples'|'relations'
    # URLs de aquisição por verificar contra o site real. Enquanto
    # `license.verified` for falso, tratar como hipótese.
    endpoints: dict[str, str] = field(default_factory=dict)
    # Passos manuais quando a fonte não permite download automático.
    manual: str = ""


class Source:
    """Classe base. Subclasses declaram `info` e implementam `fetch`/`parse`."""

    info: SourceInfo

    def __init__(self, cache: Cache):
        self.cache = cache

    @property
    def slug(self) -> str:
        return self.info.slug

    def fetch(self) -> None:
        """Traz os ficheiros brutos para o cache."""
        raise NotImplementedError

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        """Lê do cache e produz entradas canónicas.

        `lemmas`, quando dado, restringe o resultado a esse conjunto — é o que
        torna o protótipo de 100 lemas da F0 rápido sobre dumps de milhões de
        linhas.
        """
        raise NotImplementedError

    # --- auxiliares -------------------------------------------------------

    @staticmethod
    def _wanted(lemmas: Iterable[str] | None) -> set[str] | None:
        from ..text import normalize

        if lemmas is None:
            return None
        return {normalize(l) for l in lemmas}


class SourceUnavailable(RuntimeError):
    """A fonte precisa de um passo manual que ainda não foi feito."""
