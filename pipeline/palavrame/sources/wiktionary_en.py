"""Wiktionary inglês — a espinha do dicionário de inglês.

Palavras inglesas com definições em inglês, como num dicionário para quem
aprende a língua. Consome-se o extrato do `wiktextract` publicado no kaikki.org
(um objeto JSON por palavra e classe), o mesmo formato que o Wikcionário
português já usa — nada de wikitexto à mão.

Do extrato aproveita-se:

* **as aceções**, com os rótulos de uso que o próprio Wiktionary põe entre
  parênteses à frente da glosa — `(archaic)`, `(slang)`, `(nautical)` — e que a
  app mostra em itálico por cima da definição;
* **as flexões.** O Wiktionary tem uma entrada para cada forma flexionada
  (`ran`, `dogs`), marcada `form-of`. Não são palavras do dicionário: viram
  formas do lema (`ran` → `run`), que é o que a pesquisa precisa;
* **as grafias alternativas** (`colour` → `color`, `UN` → `United Nations`),
  marcadas `alt-of`. Ficam como aceção com remissão, e a app mostra a
  definição do destino — o mesmo mecanismo do "Vide ação" do português;
* **exemplos** curtos e sinónimos.

Os nomes próprios (`Thames`, 200 mil entradas) ficam de fora: não é isso que
faz parar a leitura, e duplicavam o tamanho da base.

Licença: CC BY-SA 4.0, como o Wikcionário português — copyleft dos dados.
"""

from __future__ import annotations

import gzip
import json
import re
from typing import Iterable, Iterator

from ..schema import Example, Form, Relation, Sense, SourceEntry, canonical_pos
from ..text import clean_definition, frase_utilizavel
from .base import License, Source, SourceInfo

CACHE_NAME = "kaikki-English.jsonl.gz"

INFO = SourceInfo(
    slug="wiktionary_en",
    name="Wiktionary (English)",
    url="https://en.wiktionary.org/",
    license=License(
        name="CC BY-SA 4.0",
        url="https://creativecommons.org/licenses/by-sa/4.0/",
        attribution=(
            "Wiktionary (en.wiktionary.org), Wiktionary contributors, "
            "CC BY-SA 4.0. Extraído com wiktextract (kaikki.org)."
        ),
        redistributable=True,
        verified=True,   # mesmos Termos de Uso Wikimedia do Wikcionário PT
        notes=(
            "COPYLEFT, como o Wikcionário português: o texto dos projetos "
            "Wikimedia é CC BY-SA 4.0 desde os Terms of Use de 2023. A base "
            "inglesa derivada distribui-se sob CC BY-SA. O kaikki.org só "
            "reformata o dump (Tatu Ylonen, wiktextract)."
        ),
    ),
    provides=("lemmas", "senses", "forms", "examples", "relations"),
    primary=CACHE_NAME,
    endpoints={
        "english": (
            "https://kaikki.org/dictionary/English/"
            "kaikki.org-dictionary-English.jsonl.gz"
        ),
    },
    manual=(
        "Se o kaikki.org mudar o caminho: abre https://kaikki.org/dictionary/"
        "English/, copia o link do .jsonl.gz e corre: palavrame fetch "
        "--lingua en --source wiktionary_en --url <URL>"
    ),
)

# Classes que não são palavras de dicionário para quem lê.
_CLASSES_FORA = {"name", "character", "symbol", "punct", "infix", "interfix"}

# Etiquetas do Wiktionary sem equivalente nas canónicas (`schema.POS`).
_CLASSES_EN = {
    "noun": "substantivo", "verb": "verbo", "adj": "adjetivo",
    "adv": "adverbio", "pron": "pronome", "prep": "preposicao",
    "conj": "conjuncao", "intj": "interjeicao", "num": "numeral",
    "article": "artigo", "det": "pronome", "phrase": "locucao",
    "prep_phrase": "locucao", "proverb": "locucao", "contraction": "locucao",
    "prefix": "prefixo", "suffix": "sufixo", "postp": "preposicao",
}

# Rótulos gramaticais que o Wiktionary põe no mesmo parêntese dos de uso. São
# rigorosos mas não ajudam quem quer saber o que a palavra quer dizer, e
# enchiam o itálico por cima de metade das aceções.
_ROTULOS_GRAMATICAIS = {
    "transitive", "intransitive", "ambitransitive", "countable",
    "uncountable", "not comparable", "comparable", "usually", "with the",
    "plural only", "singular only", "in the plural", "usually plural",
    "usually in the plural", "chiefly", "often", "sometimes", "now",
    "reflexive", "ergative", "copulative", "auxiliary", "modal",
    "attributive", "predicative", "postpositive", "stative", "transitive or",
    "with of", "with to", "followed by", "intransitive with", "transitive with",
    "by extension", "also", "especially", "in combination", "used",
}

# Fragmentos de rótulo que denunciam o parêntese de gramática ("with of",
# "followed by to-infinitive", "+ object").
_GRAMATICA = re.compile(
    r"^(with|followed|takes|\+|used|in the|of a|of an|of the|construed|"
    r"usually|often|sometimes|chiefly|now)\b"
)

# "Commonwealth and Ireland standard spelling of color.", "Alternative form of
# grey." — remissões que o kaikki nem sempre marca com `alt_of`, só com a
# ligação em `links`. O destino é a ligação com que a glosa acaba.
_REMISSAO = re.compile(
    r"\b(spelling|form|variant|synonym|abbreviation|initialism|acronym) of\b", re.I
)

_PARENTESE_INICIAL = re.compile(r"^\(([^()]*)\)\s*")

MAX_ROTULOS = 3
MAX_EXEMPLOS = 3
MAX_SINONIMOS = 8
EXEMPLO_MAX = 160

# As flexões que interessam à pesquisa. "alternative" fica de fora: `color` é
# uma palavra própria, não uma forma de `colour`.
_TAGS_FLEXAO = {
    "plural", "past", "participle", "present", "third-person",
    "comparative", "superlative", "gerund",
}


def rotulos(raw_gloss: str | None) -> list[str]:
    """Os rótulos de uso do parêntese inicial: `(archaic, slang) …` -> […]."""
    if not raw_gloss:
        return []
    m = _PARENTESE_INICIAL.match(raw_gloss.strip())
    if not m:
        return []
    out: list[str] = []
    for parte in re.split(r",|;", m.group(1)):
        r = parte.strip()
        if not r or r.lower() in _ROTULOS_GRAMATICAIS or _GRAMATICA.match(r.lower()):
            continue
        # "of a person" e afins são restrições de sentido, não rótulos.
        if len(r) > 25:
            continue
        if r not in out:
            out.append(r)
        if len(out) == MAX_ROTULOS:
            break
    return out


def _exemplos(exemplos: list[dict]) -> list[str]:
    """Os exemplos a mostrar, os feitos de propósito primeiro.

    O Wiktionary tem dois tipos: os `example`, escritos para ensinar a palavra
    ("His eyes grew wistful as he recalled his university days."), e as
    `quotation`, citações de obras — muitas vezes verso cortado a meio, ou
    inglês de outro século. Uma citação só entra se a aceção não tiver
    exemplo, e se for uma frase inteira e curta.
    """
    feitos, citacoes = [], []
    for ex in exemplos:
        texto = " ".join((ex.get("text") or "").split())
        if not texto or "ſ" in texto or not frase_utilizavel(texto, 15, EXEMPLO_MAX):
            continue
        if ex.get("type") == "quotation":
            if " / " in texto or "[…]" in texto or "..." in texto or len(texto) > 120:
                continue
            citacoes.append(texto)
        elif ex.get("type") in ("example", None):
            feitos.append(texto)
    return feitos or citacoes


def _alvo_da_ligacao(glosa: str, links: list) -> list[str]:
    fim = glosa.rstrip(" .").lower()
    for ligacao in reversed(links):
        if not ligacao:
            continue
        texto = str(ligacao[0])
        if texto and fim.endswith(texto.lower()):
            return [texto]
    return []


def classe(pos: str | None) -> str:
    return _CLASSES_EN.get((pos or "").lower()) or canonical_pos(pos)


class WiktionaryEn(Source):
    info = INFO

    def fetch(self) -> None:
        self.cache.fetch(self.info.endpoints["english"], self.slug, CACHE_NAME)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        pasta = self.cache.paths.cache / self.slug
        # O dump vem comprimido; as fixtures dos testes vêm em texto.
        caminho = next((c for c in (pasta / CACHE_NAME, pasta / CACHE_NAME[:-3])
                        if c.exists()), None)
        if caminho is None:
            return
        from ..text import normalize

        abrir = gzip.open if caminho.suffix == ".gz" else open
        with abrir(caminho, "rt", encoding="utf-8") as fh:
            for linha in fh:
                linha = linha.strip()
                if not linha:
                    continue
                try:
                    dados = json.loads(linha)
                except json.JSONDecodeError:
                    continue
                for entrada in entradas_de(dados):
                    if wanted is None or normalize(entrada.lemma) in wanted:
                        yield entrada


def entradas_de(dados: dict) -> list[SourceEntry]:
    """Um objeto do kaikki -> as entradas que ele dá.

    Normalmente uma (a palavra, com aceções), mais uma por cada lema de que
    esta palavra é flexão: `ran` dá a forma `ran` à entrada de `run`. Uma
    palavra que é SÓ flexão (`dogs`) não dá entrada própria.
    """
    if (dados.get("lang_code") or "") != "en":
        return []
    palavra = (dados.get("word") or "").strip()
    pos = (dados.get("pos") or "").lower()
    if not palavra or pos in _CLASSES_FORA:
        return []

    pos_canonica = classe(pos)
    fontes = "wiktionary_en"
    principal = SourceEntry(lemma=palavra, source=fontes, pos=pos_canonica)
    flexoes: dict[str, SourceEntry] = {}
    sinonimos: list[str] = []

    for s in dados.get("senses") or []:
        tags = set(s.get("tags") or [])
        if "misspelling" in tags:
            continue
        glosas = s.get("glosses") or []
        if not glosas:
            continue
        # Subaceções trazem [glosa-mãe, glosa-filha]; a mãe já é aceção própria.
        glosa = glosas[-1]
        raw = (s.get("raw_glosses") or [None])[-1]

        alvos_flexao = [f.get("word") for f in s.get("form_of") or [] if f.get("word")]
        if "form-of" in tags and alvos_flexao:
            for alvo in alvos_flexao:
                if alvo == palavra or not any(ch.isalpha() for ch in palavra):
                    continue
                e = flexoes.setdefault(alvo, SourceEntry(lemma=alvo, source=fontes))
                tag = " ".join(sorted(tags & _TAGS_FLEXAO)) or "form-of"
                e.forms.append(Form(palavra, tag))
            continue

        definicao = clean_definition(glosa)
        if not definicao:
            continue
        alvos_alt = [a.get("word") for a in s.get("alt_of") or [] if a.get("word")]
        if not alvos_alt and _REMISSAO.search(glosa):
            alvos_alt = _alvo_da_ligacao(glosa, s.get("links") or [])
        sentido = Sense(
            definition=definicao,
            source=fontes,
            ord=len(principal.senses),
            domains=rotulos(raw),
            pos=pos_canonica,
            remete_para=alvos_alt[0] if alvos_alt and alvos_alt[0] != palavra else None,
        )
        principal.senses.append(sentido)

        n = 0
        for texto in _exemplos(s.get("examples") or []):
            principal.examples.append(
                Example(
                    sentence=texto, source=fontes,
                    source_ref=f"en.wiktionary.org/wiki/{palavra}",
                    variant="en", sense_ord=sentido.ord,
                )
            )
            n += 1
            if n == MAX_EXEMPLOS:
                break

        # Primeiro os sinónimos da própria aceção, depois os do tesauro.
        for syn in sorted(s.get("synonyms") or [], key=lambda d: "source" in d):
            w = (syn.get("word") or "").strip()
            if w and w != palavra and w not in sinonimos:
                sinonimos.append(w)

    for syn in dados.get("synonyms") or []:
        w = (syn.get("word") or "").strip()
        if w and w != palavra and w not in sinonimos:
            sinonimos.append(w)
    principal.relations = [
        Relation(w, "sinonimo", fontes) for w in sinonimos[:MAX_SINONIMOS]
    ]

    for f in dados.get("forms") or []:
        forma = (f.get("form") or "").strip()
        tags = set(f.get("tags") or [])
        # Há marcadores vazios na tabela de flexões ("-" para "não tem").
        if (not forma or forma == palavra or " " in forma
                or not any(ch.isalpha() for ch in forma)
                or "alternative" in tags or not tags & _TAGS_FLEXAO):
            continue
        principal.forms.append(Form(forma, " ".join(sorted(tags & _TAGS_FLEXAO))))

    hif = dados.get("hyphenations") or []
    if hif and isinstance(hif[0], dict) and len(hif[0].get("parts") or []) > 1:
        principal.syllables = "-".join(hif[0]["parts"])   # como no português: ja-ne-la

    saida = list(flexoes.values())
    if principal.senses:
        saida.insert(0, principal)
    elif principal.forms:
        # Sem aceções próprias, as formas que trazia continuam a servir se o
        # lema existir por outra classe (o `merge` deita-as fora se não).
        principal.relations = []
        saida.insert(0, principal)
    return saida
