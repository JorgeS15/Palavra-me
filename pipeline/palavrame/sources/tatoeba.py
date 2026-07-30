"""Tatoeba — primeira escolha para frases de exemplo (plano 4.3).

Frases curtas, escritas por humanos com intenção didática. É exatamente o
registo que serve numa app de leitura.

Formato do export por língua (`por_sentences.tsv`), estável há anos::

    <id>\\t<iso639-3>\\t<texto>

Licença CC BY 2.0 FR: **atribuição obrigatória**, por frase. Por isso cada
exemplo guarda o `source_ref` com o id da frase — sem isso não se cumpre a
licença e o ecrã de fontes fica a mentir.
"""

from __future__ import annotations

import bz2
import gzip
import io
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Iterator

from ..config import EXAMPLE_MAX_CHARS, EXAMPLE_MIN_CHARS
from ..text import tokens
from ..schema import Example, SourceEntry
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="tatoeba",
    name="Tatoeba",
    url="https://tatoeba.org/",
    license=License(
        name="CC BY 2.0 FR",
        url="https://creativecommons.org/licenses/by/2.0/fr/",
        attribution="Frases de tatoeba.org, CC BY 2.0 FR.",
        redistributable=True,
        verified=False,
        notes=(
            "Confirmar em F0 a licença em vigor na página de downloads e a "
            "forma de atribuição pedida (por frase ou por corpus)."
        ),
    ),
    provides=("examples",),
    endpoints={
        "por_sentences": (
            "https://downloads.tatoeba.org/exports/per_language/por/"
            "por_sentences.tsv.bz2"
        ),
    },
)

CACHE_NAME = "por_sentences.tsv.bz2"

# Marcadores de PT-BR úteis para o filtro heurístico de variante (plano 9).
# Não é perfeito e não se pretende que seja: serve para despriorizar, não
# para excluir.
_BR_MARKERS = {
    "voce", "voces", "onibus", "trem", "geladeira", "bunda", "legal",
    "bacana", "celular", "grama", "banheiro", "cafe da manha", "time",
    "esporte", "acougue", "torcida", "carteira de motorista", "aluguel",
    "xicara", "terno", "meia", "bonde", "caminhao", "sorvete", "bala",
}
_PT_MARKERS = {
    "autocarro", "comboio", "frigorifico", "telemovel", "casa de banho",
    "pequeno almoco", "relvado", "sande", "boleia", "eletrodomestico",
    "rapariga", "gajo", "fixe", "estoril", "talho", "peao", "fato",
    "sumo", "gelado", "rebucado", "camiao", "chavena",
}


class Tatoeba(Source):
    info = INFO

    def fetch(self) -> None:
        self.cache.fetch(self.info.endpoints["por_sentences"], self.slug, CACHE_NAME)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        """Indexa as frases por palavra e devolve uma entrada por lema.

        Nota: aqui o índice é por **forma normalizada tal como aparece na
        frase**. A ligação forma -> lema faz-se no `merge`, que já tem a
        tabela `forms`. Isto evita ter de lematizar durante o parsing.
        """
        wanted = self._wanted(lemmas)
        if wanted is None:
            raise ValueError(
                "O Tatoeba tem centenas de milhares de frases: indexar tudo "
                "sem lista de lemas não é útil. Passa `lemmas`."
            )

        by_word: dict[str, list[Example]] = defaultdict(list)
        for sid, sentence in self._sentences():
            if not (EXAMPLE_MIN_CHARS <= len(sentence) <= EXAMPLE_MAX_CHARS):
                continue
            words = set(tokens(sentence))
            hits = words & wanted
            if not hits:
                continue
            variant = _guess_variant(words)
            for word in hits:
                by_word[word].append(
                    Example(
                        sentence=sentence,
                        source=self.slug,
                        source_ref=str(sid),
                        variant=variant,
                    )
                )

        for word, examples in by_word.items():
            # pt-PT primeiro, depois desconhecido, depois pt-BR.
            examples.sort(key=lambda e: {"pt-PT": 0, "unknown": 1}.get(e.variant, 2))
            yield SourceEntry(lemma=word, source=self.slug, examples=examples)

    # --- leitura ----------------------------------------------------------

    def _sentences(self) -> Iterator[tuple[str, str]]:
        # Aceita o export tal como vem (.bz2) e também já descomprimido — é
        # frequente descomprimir para inspecionar, e nesse caso o pipeline
        # deve continuar a encontrá-lo em vez de ficar calado.
        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return
        for path in sorted(base.glob("*sentences.tsv*")):
            for line in _open_text(path):
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                sid, lang, text = parts[0], parts[1], parts[2].strip()
                if lang and lang != "por":
                    continue
                if text:
                    yield sid, text


def _open_text(path: Path) -> Iterator[str]:
    """Abre .bz2, .gz ou texto simples, sempre em UTF-8."""
    if path.suffix == ".bz2":
        with bz2.open(path, "rt", encoding="utf-8", errors="replace") as fh:
            yield from fh
    elif path.suffix == ".gz":
        with gzip.open(path, "rt", encoding="utf-8", errors="replace") as fh:
            yield from fh
    else:
        with open(path, encoding="utf-8", errors="replace") as fh:
            yield from fh


def _guess_variant(words: set[str]) -> str:
    """Heurística deliberadamente simples de PT-PT vs PT-BR.

    Só olha para léxico marcado. Devolve 'unknown' sempre que não há sinal,
    que é a maioria dos casos — e é a resposta honesta.
    """
    br = len(words & _BR_MARKERS)
    pt = len(words & _PT_MARKERS)
    if pt > br:
        return "pt-PT"
    if br > pt:
        return "pt-BR"
    return "unknown"


__all__ = ["INFO", "Tatoeba", "_open_text", "_guess_variant"]
