"""VOC — Vocabulário Ortográfico Comum da CPLP.

Papel no pipeline: **autoridade ortográfica**. O VOC decide duas coisas e mais
nenhuma (plano 5.2):

* que sequências de letras são palavras do português;
* como se escrevem sob o AO90.

Não traz definições. É a espinha dorsal da tabela `lemmas`; as aceções vêm de
outras fontes e são-lhe penduradas.

Aquisição
---------
O VOC é consultável em voc.cplp.org mas não publica um dump estável e
documentado. Por isso esta fonte é **manual por omissão**: obtém-se o ficheiro
uma vez, coloca-se em `pipeline/cache/voc_cplp/` e o pipeline regista-o no
lockfile como qualquer outro ficheiro descarregado.

Formato esperado (CSV ou TSV, com ou sem cabeçalho)::

    lema,classe
    abacate,substantivo
    caber,verbo

Uma coluna só também serve — a classe fica `desconhecido` e é preenchida
depois pelas outras fontes.

Se e quando se confirmar um URL de dump oficial, basta preenchê-lo em
`endpoints["dump"]` e `fetch()` passa a automático.
"""

from __future__ import annotations

import csv
import io
from typing import Iterable, Iterator

from ..schema import SourceEntry, canonical_pos
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="voc_cplp",
    name="Vocabulário Ortográfico Comum da CPLP",
    url="https://voc.cplp.org/",
    license=License(
        name="POR VERIFICAR",
        url="https://voc.cplp.org/",
        attribution="Vocabulário Ortográfico Comum da Língua Portuguesa (CPLP/IILP)",
        redistributable=None,
        verified=False,
        notes=(
            "Verificar em F0 se a lista de lemas é redistribuível. Nota: uma "
            "lista de palavras de uma língua tem pouca originalidade e em "
            "vários ordenamentos não é protegível, mas isso é opinião, não "
            "verificação. Confirmar antes de publicar."
        ),
    ),
    provides=("lemmas",),
    primary="voc.csv",
    endpoints={},   # sem dump automático confirmado
    manual=(
        "Obter a lista de lemas em https://voc.cplp.org/ e guardar como "
        "pipeline/cache/voc_cplp/voc.csv (colunas: lema[,classe]). "
        "Depois: palavrame fetch --source voc_cplp"
    ),
)

CACHE_NAME = "voc.csv"


class VocCplp(Source):
    info = INFO

    def fetch(self) -> None:
        dump_url = self.info.endpoints.get("dump")
        if dump_url:
            self.cache.fetch(dump_url, self.slug, CACHE_NAME)
            return
        path = self.cache.paths.cache / self.slug / CACHE_NAME
        if not path.exists():
            raise SourceUnavailable(self.info.manual)
        # Já lá está: só regista no lockfile para a build ser verificável.
        self.cache.local(self.slug, CACHE_NAME, path)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        path = self.cache.paths.cache / self.slug / CACHE_NAME
        if not path.exists():
            raise SourceUnavailable(self.info.manual)

        text = path.read_text(encoding="utf-8", errors="replace")
        for lemma, pos in _rows(text):
            entry = SourceEntry(lemma=lemma, source=self.slug, pos=canonical_pos(pos))
            if wanted is None or entry.normalized in wanted:
                yield entry


def _rows(text: str) -> Iterator[tuple[str, str | None]]:
    """Lê CSV/TSV tolerante: deteta o delimitador e ignora o cabeçalho."""
    sample = text[:4096]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",;\t|")
        delimiter = dialect.delimiter
    except csv.Error:
        delimiter = "\t" if "\t" in sample else ","

    reader = csv.reader(io.StringIO(text), delimiter=delimiter)
    for i, row in enumerate(reader):
        if not row or not row[0].strip():
            continue
        lemma = row[0].strip()
        if i == 0 and lemma.lower() in {"lema", "palavra", "forma", "termo"}:
            continue          # cabeçalho
        if lemma.startswith("#"):
            continue
        pos = row[1].strip() if len(row) > 1 and row[1].strip() else None
        yield lemma, pos
