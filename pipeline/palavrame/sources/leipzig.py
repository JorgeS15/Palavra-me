"""Leipzig Corpora Collection — frequências e frases autênticas.

Dá duas coisas ao pipeline:

* **`frequency_rank`** — a coluna que ordena resultados ambíguos. Quando o
  utilizador escreve *"cantada"* e há vários candidatos, é isto que decide o
  que aparece primeiro (plano 9). É provavelmente o contributo mais valioso
  desta fonte.
* **frases** — segundo degrau da cascata de exemplos, abaixo do Tatoeba.
  Mistura PT-PT e PT-BR: os corpora `por_pt_*` são de Portugal, os `por_br_*`
  do Brasil, e os `por_*` sem marca são mistos.

Formato dos arquivos (estável): dentro do `.tar.gz` há
`*-words.txt`  -> `rank \\t palavra \\t frequência`
`*-sentences.txt` -> `número \\t frase`

Licença por verificar: o Leipzig distribui sob CC BY-NC por omissão em vários
corpora. **NC impede a redistribuição numa app publicada** se houver qualquer
componente comercial, e é discutível mesmo sem ela. Enquanto não estiver
verificado, o `validate` não deixa esta fonte entrar numa DB de distribuição.
"""

from __future__ import annotations

import tarfile
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Iterator

from ..config import EXAMPLE_MAX_CHARS, EXAMPLE_MIN_CHARS
from ..text import normalize, tokens
from ..schema import Example, SourceEntry
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="leipzig",
    name="Leipzig Corpora Collection (português)",
    url="https://wortschatz.uni-leipzig.de/en/download/Portuguese",
    license=License(
        name="POR VERIFICAR (frequentemente CC BY-NC)",
        url="https://wortschatz.uni-leipzig.de/en/download",
        attribution=(
            "Leipzig Corpora Collection, Universität Leipzig. D. Goldhahn, "
            "T. Eckart, U. Quasthoff: Building Large Monolingual Dictionaries "
            "at the Leipzig Corpora Collection, LREC 2012."
        ),
        redistributable=None,
        verified=False,
        notes=(
            "CRÍTICO: se for NC, as frases não podem ir numa app publicada. "
            "As FREQUÊNCIAS são factos sobre a língua e o risco é muito menor "
            "do que o das frases — mas confirmar as duas coisas em separado "
            "em F0, porque a decisão não é a mesma."
        ),
    ),
    provides=("examples", "frequency"),
    endpoints={
        # Preencher em F0 com os ficheiros escolhidos. Preferir `por_pt_*`.
        # Exemplo do padrão de nomes usado pelo projeto:
        #   https://downloads.wortschatz-leipzig.de/corpora/por_pt_2019_1M.tar.gz
    },
)


class Leipzig(Source):
    info = INFO

    def fetch(self) -> None:
        if not self.info.endpoints:
            raise RuntimeError(
                "Nenhum corpus Leipzig escolhido. Preenche `endpoints` em "
                "sources/leipzig.py depois de decidir em F0 quais os corpora "
                "(preferir por_pt_*) e de confirmar a licença."
            )
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, f"{name}.tar.gz")

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return

        ranks = self._ranks(base)
        by_word: dict[str, list[Example]] = defaultdict(list)

        for corpus, num, sentence in self._sentences(base):
            if not (EXAMPLE_MIN_CHARS <= len(sentence) <= EXAMPLE_MAX_CHARS):
                continue
            words = set(tokens(sentence))
            hits = words & wanted if wanted is not None else words
            for word in hits:
                if len(by_word[word]) >= 20:      # teto por palavra
                    continue
                by_word[word].append(
                    Example(
                        sentence=sentence,
                        source=self.slug,
                        source_ref=f"{corpus}:{num}",
                        variant=_variant_of(corpus),
                    )
                )

        seen = set()
        for word, examples in by_word.items():
            seen.add(word)
            yield SourceEntry(
                lemma=word,
                source=self.slug,
                examples=examples,
                frequency_rank=ranks.get(word),
            )
        # Palavras com frequência mas sem frase aproveitável continuam a valer:
        # o `frequency_rank` sozinho já ordena desambiguações.
        for word, rank in ranks.items():
            if word not in seen and (wanted is None or word in wanted):
                yield SourceEntry(lemma=word, source=self.slug, frequency_rank=rank)

    # --- leitura ----------------------------------------------------------

    def _ranks(self, base: Path) -> dict[str, int]:
        """Menor rank = mais frequente. Fica o melhor rank entre corpora."""
        ranks: dict[str, int] = {}
        for corpus, name, lines in self._members(base, "-words.txt"):
            for line in lines:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                try:
                    rank = int(parts[0])
                except ValueError:
                    continue
                word = normalize(parts[1])
                if word and (word not in ranks or rank < ranks[word]):
                    ranks[word] = rank
        return ranks

    def _sentences(self, base: Path) -> Iterator[tuple[str, str, str]]:
        for corpus, name, lines in self._members(base, "-sentences.txt"):
            for line in lines:
                parts = line.rstrip("\n").split("\t", 1)
                if len(parts) == 2 and parts[1].strip():
                    yield corpus, parts[0], parts[1].strip()

    def _members(self, base: Path, suffix: str) -> Iterator[tuple[str, str, Iterator[str]]]:
        """Percorre os `.tar.gz` sem os extrair, e também ficheiros soltos."""
        for archive in sorted(base.glob("*.tar.gz")):
            corpus = archive.name[: -len(".tar.gz")]
            with tarfile.open(archive, "r:gz") as tar:
                for member in tar.getmembers():
                    if not member.isfile() or not member.name.endswith(suffix):
                        continue
                    fh = tar.extractfile(member)
                    if fh is None:
                        continue
                    text = fh.read().decode("utf-8", errors="replace")
                    yield corpus, member.name, iter(text.splitlines(keepends=True))
        for plain in sorted(base.glob(f"*{suffix}")):
            corpus = plain.name[: -len(suffix)]
            with open(plain, encoding="utf-8", errors="replace") as fh:
                yield corpus, plain.name, iter(fh.readlines())


def _variant_of(corpus: str) -> str:
    """A variante lê-se no nome do corpus, que é mais fiável que heurística."""
    name = corpus.lower()
    if "por_pt" in name or "por-pt" in name:
        return "pt-PT"
    if "por_br" in name or "por-br" in name:
        return "pt-BR"
    return "unknown"
