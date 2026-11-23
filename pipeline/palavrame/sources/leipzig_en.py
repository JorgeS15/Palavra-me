"""Leipzig Corpora Collection (inglês) — só a frequência.

Para o inglês o Leipzig dá uma coisa só: o `frequency_rank`, que ordena os
candidatos quando a mesma grafia chega a vários lemas (`left` é `leave` e é
`left`; `ran` é `run` e é um termo náutico). Os exemplos vêm do Wiktionary, que
os tem feitos de propósito e curtos; as frases de jornal do Leipzig não
acrescentavam nada que justificasse o tamanho.

Prefere-se um corpus britânico: quem lê em Portugal lê sobretudo edições
britânicas.

Formato (o mesmo dos corpora portugueses, ver `leipzig.py`): dentro do
`.tar.gz`, `*-words.txt` com `rank \\t palavra \\t frequência`. A leitura repete-se
aqui em vez de se importar de `leipzig.py`: as fontes não se conhecem umas às
outras (ver `tests/test_no_network.py`).
"""

from __future__ import annotations

import tarfile
from pathlib import Path
from typing import Iterable, Iterator

from ..schema import SourceEntry
from ..text import normalize
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="leipzig_en",
    name="Leipzig Corpora Collection (English)",
    url="https://wortschatz.uni-leipzig.de/en/download/English",
    license=License(
        name="CC BY (corpora descarregados)",
        url="https://wortschatz.uni-leipzig.de/en/usage",
        attribution=(
            "Leipzig Corpora Collection, Universität Leipzig, CC BY. "
            "D. Goldhahn, T. Eckart, U. Quasthoff: Building Large Monolingual "
            "Dictionaries at the Leipzig Corpora Collection, LREC 2012."
        ),
        redistributable=True,
        verified=True,   # os mesmos termos dos corpora portugueses (2026-07-30)
        notes="Mesmos termos do Leipzig português: os downloads são CC BY.",
    ),
    provides=("frequency",),
    endpoints={},
    manual=(
        "Se os caminhos conhecidos falharem: abre "
        "https://wortschatz.uni-leipzig.de/en/download/English, escolhe um "
        "corpus (de preferência eng-uk_*), copia o link do .tar.gz e corre: "
        "palavrame fetch --lingua en --source leipzig_en --url <URL>"
    ),
)

CANDIDATOS = (
    "https://downloads.wortschatz-leipzig.de/corpora/eng-uk_web_2002_1M.tar.gz",
    "https://downloads.wortschatz-leipzig.de/corpora/eng_news_2023_1M.tar.gz",
    "https://downloads.wortschatz-leipzig.de/corpora/eng_wikipedia_2016_1M.tar.gz",
)


class LeipzigEn(Source):
    info = INFO

    def fetch(self) -> None:
        falhas = []
        for url in CANDIDATOS:
            try:
                self.cache.fetch(url, self.slug, url.rsplit("/", 1)[-1])
                return
            except Exception as exc:
                falhas.append(f"{url} -> {exc}")
        raise SourceUnavailable(
            "nenhum corpus inglês do Leipzig respondeu.\n      "
            + "\n      ".join(falhas) + "\n\n      " + self.info.manual
        )

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return
        for palavra, rank in _ranks(base).items():
            if wanted is None or palavra in wanted:
                yield SourceEntry(lemma=palavra, source=self.slug, frequency_rank=rank)


def _ranks(base: Path) -> dict[str, int]:
    """Forma normalizada -> melhor rank (menor = mais frequente)."""
    ranks: dict[str, int] = {}
    for linhas in _ficheiros_de_palavras(base):
        for linha in linhas:
            partes = linha.rstrip("\n").split("\t")
            if len(partes) < 3:
                continue
            try:
                rank = int(partes[0])
            except ValueError:
                continue
            palavra = normalize(partes[1])
            if palavra and (palavra not in ranks or rank < ranks[palavra]):
                ranks[palavra] = rank
    return ranks


def _ficheiros_de_palavras(base: Path) -> Iterator[list[str]]:
    """Os `*-words.txt`, dentro dos `.tar.gz` (sem extrair) ou soltos."""
    for arquivo in sorted(base.glob("*.tar.gz")):
        with tarfile.open(arquivo, "r:gz") as tar:
            for membro in tar.getmembers():
                if membro.isfile() and membro.name.endswith("-words.txt"):
                    fh = tar.extractfile(membro)
                    if fh is not None:
                        yield fh.read().decode("utf-8", errors="replace").splitlines()
    for solto in sorted(base.glob("*-words.txt")):
        yield solto.read_text(encoding="utf-8", errors="replace").splitlines()
