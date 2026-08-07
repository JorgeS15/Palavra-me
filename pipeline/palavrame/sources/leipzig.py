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
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="leipzig",
    name="Leipzig Corpora Collection (português)",
    url="https://wortschatz.uni-leipzig.de/en/download/Portuguese",
    license=License(
        name="CC BY (corpora descarregados)",
        url="https://wortschatz.uni-leipzig.de/en/usage",
        attribution=(
            "Leipzig Corpora Collection, Universität Leipzig, CC BY. "
            "D. Goldhahn, T. Eckart, U. Quasthoff: Building Large Monolingual "
            "Dictionaries at the Leipzig Corpora Collection, LREC 2012."
        ),
        redistributable=True,
        verified=True,   # lido em 2026-07-30 em wortschatz.uni-leipzig.de/en/usage
        notes=(
            "Melhor do que se temia. Os termos distinguem duas coisas: o "
            "portal web e as aplicações são CC BY-NC, mas 'The text corpora "
            "offered for download are made available under the Creative "
            "Commons licence CC BY' — os downloads, que é o que o pipeline "
            "usa, são CC BY sem NC. Frases E frequências entram, com "
            "atribuição e a citação do artigo LREC 2012 pedida nos termos. "
            "Preferir os corpora por_pt_* (Portugal)."
        ),
    ),
    provides=("examples", "frequency"),
    endpoints={},   # ver CANDIDATOS; se mudarem, fetch --url resolve
    manual=(
        "Se os caminhos conhecidos falharem: abre "
        "https://wortschatz.uni-leipzig.de/en/download/Portuguese, escolhe "
        "um corpus de Portugal (por-pt_*), copia o link do .tar.gz e corre: "
        "palavrame fetch --source leipzig --url <URL>"
    ),
)

# O padrão real de nomes é `<língua>[-variante]_<tipo>_<ano>_<tamanho>` —
# com HÍFEN na variante (por-pt), não underscore como o plano supunha. Os
# anos e tipos disponíveis mudam; um 404 aqui é esperado e o INFO.manual
# explica a alternativa. Preferir sempre por-pt (Portugal).
CANDIDATOS = (
    "https://downloads.wortschatz-leipzig.de/corpora/por-pt_web_2019_1M.tar.gz",
    "https://downloads.wortschatz-leipzig.de/corpora/por-pt_newscrawl_2019_1M.tar.gz",
    "https://downloads.wortschatz-leipzig.de/corpora/por-pt_web_2015_1M.tar.gz",
    "https://downloads.wortschatz-leipzig.de/corpora/por-pt_web_2019_300K.tar.gz",
)


class Leipzig(Source):
    info = INFO

    def fetch(self) -> None:
        if self.info.endpoints:
            for name, url in self.info.endpoints.items():
                self.cache.fetch(url, self.slug, f"{name}.tar.gz")
            return
        # Basta UM corpus para a F1 (frases + frequências vêm no mesmo
        # tarball); tenta os candidatos por ordem de preferência.
        falhas = []
        for url in CANDIDATOS:
            nome = url.rsplit("/", 1)[-1]
            try:
                self.cache.fetch(url, self.slug, nome)
                print(f"      encontrado em {url}")
                return
            except Exception as exc:
                falhas.append(f"{url} -> {exc}")
        raise SourceUnavailable(
            "nenhum dos corpora candidatos do Leipzig respondeu.\n      "
            + "\n      ".join(falhas)
            + "\n\n      "
            + self.info.manual
        )

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
