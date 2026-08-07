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

import gzip
import json
from typing import Iterable, Iterator

from ..text import clean_definition
from ..schema import Example, Form, Sense, SourceEntry, canonical_pos
from .base import License, Source, SourceInfo, SourceUnavailable

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
        verified=True,   # confirmado em 2026-07-30: texto Wikimedia é CC BY-SA 4.0
        notes=(
            "COPYLEFT. O texto dos projetos Wikimedia está sob CC BY-SA 4.0 "
            "desde a atualização dos Terms of Use de 2023 (dupla licença com "
            "GFDL; a página local 'Direitos de autor' do ptwiktionary ainda "
            "menciona a GFDL, mas o rodapé de cada entrada diz CC BY-SA 4.0). "
            "A DB derivada que inclua estas aceções tem de ser distribuída "
            "sob CC BY-SA. Atribuição: link para a entrada é suficiente "
            "segundo os ToU — o source_ref por aceção trata disso."
        ),
    ),
    provides=("lemmas", "senses", "forms", "examples"),
    primary="wikcionario.jsonl",
    endpoints={},   # ver CANDIDATOS: o kaikki reorganiza os caminhos
    manual=(
        "O kaikki.org muda os caminhos dos dumps. Abre https://kaikki.org/, "
        "escolhe a edição portuguesa do Wiktionary, copia o link do ficheiro "
        ".jsonl e corre: "
        "palavrame fetch --source wikcionario --url <URL>"
    ),
)

CACHE_NAME = "wikcionario.jsonl"

# Caminhos do kaikki.org, por ordem de preferência. O primeiro foi confirmado
# em 2026-07-30 na página de raw data (kaikki.org/dictionary/rawdata.html):
# é o extrato da edição PORTUGUESA do Wiktionary (pt-extract, ~330 MB, .gz
# ~34 MB), que é o que queremos — glosas em português. Os dois seguintes são
# o dump da edição inglesa filtrado para português (glosas em inglês), que só
# serve de recurso.
CANDIDATOS = (
    "https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl.gz",
    "https://kaikki.org/dictionary/downloads/pt/pt-extract.jsonl",
    "https://kaikki.org/dictionary/Portuguese/kaikki.org-dictionary-Portuguese.jsonl",
)


class Wikcionario(Source):
    info = INFO

    def fetch(self) -> None:
        urls = list(self.info.endpoints.values()) or list(CANDIDATOS)
        falhas = []
        for url in urls:
            try:
                self.cache.fetch(url, self.slug, CACHE_NAME)
                print(f"      encontrado em {url}")
                return
            except Exception as exc:
                falhas.append(f"{url} -> {exc}")

        raise SourceUnavailable(
            "nenhum dos caminhos conhecidos do kaikki.org respondeu.\n      "
            + "\n      ".join(falhas)
            + "\n\n      "
            + self.info.manual
        )

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        path = self.cache.paths.cache / self.slug / CACHE_NAME
        if not path.exists():
            return
        # O caminho preferido do kaikki é um .gz; aceita-o tal como veio, em
        # vez de obrigar quem corre o pipeline a descomprimir à mão. A deteção
        # é pelos bytes mágicos, não pela extensão — o ficheiro no cache
        # chama-se sempre `wikcionario.jsonl`, comprimido ou não.
        with open(path, "rb") as probe:
            is_gzip = probe.read(2) == b"\x1f\x8b"
        opener = (
            (lambda: gzip.open(path, "rt", encoding="utf-8", errors="replace"))
            if is_gzip
            else (lambda: open(path, encoding="utf-8", errors="replace"))
        )
        with opener() as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except json.JSONDecodeError:
                    continue
                for entry in _entries_from_wiktextract(data, self.slug):
                    if wanted is None or entry.normalized in wanted:
                        yield entry


def _entries_from_wiktextract(data: dict, slug: str) -> list[SourceEntry]:
    """Uma linha do wiktextract pode valer uma entrada — ou só uma flexão.

    Mais de metade das entradas portuguesas do Wikcionário são páginas de
    formas flexionadas ('cantada — feminino do particípio de cantar'), que o
    wiktextract marca com `form_of` na aceção. Tratá-las como lemas foi o que
    inchou a primeira F1 real para 396 mil lemas. Aqui convertem-se no que
    são: linhas da tabela `forms`, penduradas no lema verdadeiro. Só as
    aceções que definem alguma coisa criam entrada própria.
    """
    lemma = (data.get("word") or "").strip()
    if not lemma:
        return []
    # O wiktextract cobre muitas línguas no mesmo ficheiro.
    lang = (data.get("lang_code") or data.get("lang") or "").lower()
    if lang and lang not in {"pt", "português", "portuguese"}:
        return []

    entry = SourceEntry(lemma=lemma, source=slug, pos=canonical_pos(data.get("pos")))
    alvo_de_flexao: dict[str, str] = {}   # lema alvo -> etiqueta (a glosa)

    ord_ = 0
    for sense in data.get("senses") or []:
        alvos = [
            (t.get("word") or "").strip()
            for t in (sense.get("form_of") or sense.get("alt_of") or [])
            if isinstance(t, dict)
        ]
        alvos = [a for a in alvos if a and a != lemma]
        if alvos or "form-of" in (sense.get("tags") or []):
            glosa = ((sense.get("glosses") or [""])[0] or "").strip()
            for alvo in alvos:
                alvo_de_flexao.setdefault(alvo, glosa[:80] or None)
            continue   # não é uma aceção: é uma remissão
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

    out: list[SourceEntry] = []
    # Uma página que só remete para outra palavra ('couberam — pretérito de
    # caber') não é um lema, mesmo quando traz uma tabela de conjugação a
    # reboque. Sem esta condição, 'couberam' e 'pusesse' apareciam na app
    # como palavras a par de 'caber' e 'pôr' — que é precisamente a confusão
    # que a tabela `forms` existe para evitar.
    so_remissao = bool(alvo_de_flexao) and not entry.senses
    if not so_remissao and (entry.senses or entry.forms or entry.examples):
        out.append(entry)
    # As remissões viram flexões do lema alvo: pesquisar "cantada" tem de
    # chegar a "cantar" mesmo sem o Hunspell conhecer a forma.
    for alvo, etiqueta in alvo_de_flexao.items():
        out.append(
            SourceEntry(
                lemma=alvo,
                source=slug,
                forms=[Form(form=lemma, tag=etiqueta)],
            )
        )
    return out
