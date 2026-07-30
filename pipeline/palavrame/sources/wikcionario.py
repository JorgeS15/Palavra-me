"""Wikcionário PT — camada moderna por cima do Dicionário Aberto.

O plano é explícito: **não parsear wikitext à mão** (secção 4.1). Consome-se a
saída do `wiktextract`, em JSONL, um objeto por entrada. Duas origens
possíveis:

* dumps prontos em kaikki.org (não é preciso correr o wiktextract);
* correr o `wiktextract` sobre um dump do ptwiktionary (extra `[wiktextract]`).

Em ambos os casos o formato é o mesmo, que é a razão de ser desta escolha.

Licença: CC BY-SA. É **copyleft** — a base de dados derivada que inclua isto
tem de ser publicada sob CC BY-SA (plano secção 8). Não contamina o código da
app, contamina os dados.
"""

from __future__ import annotations

import json
from typing import Iterable, Iterator

from ..text import clean_definition
from ..schema import Example, Form, Sense, SourceEntry, canonical_pos
from .base import License, Source, SourceInfo

INFO = SourceInfo(
    slug="wikcionario",
    name="Wikcionário (Wiktionary em português)",
    url="https://pt.wiktionary.org/",
    license=License(
        name="CC BY-SA 4.0",
        url="https://creativecommons.org/licenses/by-sa/4.0/",
        attribution=(
            "Wikcionário (pt.wiktionary.org), colaboradores do Wikcionário, "
            "CC BY-SA 4.0."
        ),
        redistributable=True,
        verified=False,
        notes=(
            "COPYLEFT. Confirmar em F0 a versão exata da licença em vigor no "
            "ptwiktionary e a forma de atribuição pedida. A DB derivada que "
            "inclua estas aceções tem de ser distribuída sob CC BY-SA."
        ),
    ),
    provides=("lemmas", "senses", "forms", "examples"),
    endpoints={
        # URLs por confirmar em F0 — kaikki reorganiza os caminhos.
        "kaikki_pt": (
            "https://kaikki.org/ptwiktionary/"
            "kaikki.org-dictionary-Portuguese.jsonl"
        ),
    },
)

CACHE_NAME = "wikcionario.jsonl"


class Wikcionario(Source):
    info = INFO

    def fetch(self) -> None:
        self.cache.fetch(self.info.endpoints["kaikki_pt"], self.slug, CACHE_NAME)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        path = self.cache.paths.cache / self.slug / CACHE_NAME
        if not path.exists():
            return
        with open(path, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except json.JSONDecodeError:
                    continue
                entry = _entry_from_wiktextract(data, self.slug)
                if entry and (wanted is None or entry.normalized in wanted):
                    yield entry


def _entry_from_wiktextract(data: dict, slug: str) -> SourceEntry | None:
    lemma = (data.get("word") or "").strip()
    if not lemma:
        return None
    # O wiktextract cobre muitas línguas no mesmo ficheiro.
    lang = (data.get("lang_code") or data.get("lang") or "").lower()
    if lang and lang not in {"pt", "português", "portuguese"}:
        return None

    entry = SourceEntry(lemma=lemma, source=slug, pos=canonical_pos(data.get("pos")))

    ord_ = 0
    for sense in data.get("senses") or []:
        glosses = sense.get("glosses") or sense.get("raw_glosses") or []
        for gloss in glosses:
            text = clean_definition(gloss or "")
            if not text:
                continue
            ord_ += 1
            entry.senses.append(
                Sense(
                    definition=text,
                    source=slug,
                    ord=ord_,
                    domains=[t for t in (sense.get("topics") or []) if t],
                )
            )
        # Exemplos ligados a esta aceção.
        for ex in sense.get("examples") or []:
            sentence = (ex.get("text") if isinstance(ex, dict) else ex) or ""
            sentence = sentence.strip()
            if sentence:
                entry.examples.append(
                    Example(
                        sentence=sentence,
                        source=slug,
                        source_ref=f"pt.wiktionary.org/wiki/{lemma}",
                        variant="unknown",
                        sense_ord=ord_ or None,
                    )
                )

    # Flexões declaradas na entrada — complementam o Hunspell.
    for form in data.get("forms") or []:
        text = (form.get("form") or "").strip()
        if text and text not in {"-", lemma}:
            tags = form.get("tags") or []
            entry.forms.append(Form(form=text, tag=" ".join(tags) or None))

    if not (entry.senses or entry.forms or entry.examples):
        return None
    return entry
