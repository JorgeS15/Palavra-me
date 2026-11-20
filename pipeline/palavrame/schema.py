"""Esquema canónico intermédio.

Cada fonte é normalizada para estes tipos antes de qualquer fusão. É o
contrato entre `sources/` e `merge/`: uma fonte nova só precisa de produzir
`SourceEntry`s para entrar no pipeline.

Estes tipos não são o esquema SQL — esse está em `build/sqlite.py` e segue a
secção 6.1 do plano. A separação é intencional: o intermédio pode carregar
proveniência e diagnóstico que não vão para a DB final.
"""

from __future__ import annotations

import json
import unicodedata
from dataclasses import asdict, dataclass, field
from typing import Any, Iterable

from .text import normalize, sem_trema


def _nfc(s: str) -> str:
    """Forma canónica da grafia à entrada do pipeline: NFC e sem trema.

    Duas normalizações que têm de acontecer cedo, senão a fusão (que agrupa por
    grafia exata) fica com duplicados:

    * **NFC.** As fontes misturam `é` num só código (U+00E9) com `e`+acento em
      códigos separados (U+0065 U+0301). No ecrã são idênticos, mas como
      cadeias diferem, e `bebé` aparecia a dobrar. Canonicaliza também a grafia
      que a portugalização produz (`é` composto), para bater certo com a fonte.
    * **Trema.** Abolido no português: `lângüido` é só grafia velha de
      `lânguido`, e sem isto ficavam duas entradas, uma delas só com "Ver
      lânguido". O trema, ao contrário do acento, nunca distingue palavras.
    """
    return sem_trema(unicodedata.normalize("NFC", s))

# Classes gramaticais canónicas. Cada fonte traz a sua nomenclatura; o
# normalizador mapeia para esta lista fechada.
POS = (
    "substantivo",
    # Serralves, Aachen, HTTP. Separado de `substantivo` de propósito: não são
    # palavras de dicionário e um dia pode querer-se descê-los na ordenação
    # dos candidatos. Enquanto isso não acontece, ao menos deixam de dizer
    # "desconhecido" a quem os procura.
    "nome proprio",
    "adjetivo",
    "verbo",
    "adverbio",
    "pronome",
    "preposicao",
    "conjuncao",
    "interjeicao",
    "numeral",
    "artigo",
    "locucao",
    "prefixo",
    "sufixo",
    "desconhecido",
)

# Mapeamento das etiquetas mais comuns nas fontes para as canónicas acima.
POS_ALIASES = {
    "n": "substantivo", "noun": "substantivo", "s": "substantivo",
    "s.m.": "substantivo", "s.f.": "substantivo", "sm": "substantivo",
    "sf": "substantivo", "substantivo masculino": "substantivo",
    "substantivo feminino": "substantivo", "nome": "substantivo",
    "adj": "adjetivo", "adj.": "adjetivo", "a": "adjetivo",
    "adjective": "adjetivo", "adjetivo": "adjetivo",
    "v": "verbo", "v.": "verbo", "verb": "verbo", "vt": "verbo",
    "vi": "verbo", "vp": "verbo", "v.t.": "verbo", "v.i.": "verbo",
    "adv": "adverbio", "adv.": "adverbio", "adverb": "adverbio",
    "r": "adverbio",
    "pron": "pronome", "pron.": "pronome", "pronoun": "pronome",
    "prep": "preposicao", "prep.": "preposicao", "preposition": "preposicao",
    "conj": "conjuncao", "conj.": "conjuncao", "conjunction": "conjuncao",
    "interj": "interjeicao", "interj.": "interjeicao",
    "num": "numeral", "art": "artigo",
    "loc": "locucao", "phrase": "locucao",
    # O Dicionário Aberto (1913) etiqueta os substantivos só pelo género —
    # `<gramGrp>m.</gramGrp>`, `<gramGrp>f.</gramGrp>` — que juntos são 150
    # mil das suas entradas. Sem isto, o dicionário inteiro de 1913 ficava
    # sem classe gramatical.
    "m": "substantivo", "m.": "substantivo",
    "f": "substantivo", "f.": "substantivo",
    "m. pl.": "substantivo", "f. pl.": "substantivo",
    "pl": "substantivo", "pl.": "substantivo",
    "pref": "prefixo", "pref.": "prefixo",
    "suf": "sufixo", "suf.": "sufixo",
}


def canonical_pos(raw: str | None) -> str:
    """Reduz uma etiqueta de classe gramatical arbitrária às canónicas."""
    if not raw:
        return "desconhecido"
    key = raw.strip().lower().rstrip(".")
    if key in POS:
        return key
    if key in POS_ALIASES:
        return POS_ALIASES[key]
    if f"{key}." in POS_ALIASES:
        return POS_ALIASES[f"{key}."]

    # Etiquetas compostas, dos dois feitios que as fontes usam:
    # "substantivo masculino plural" e "s. f." — em ambos os casos o primeiro
    # elemento decide, com e sem o ponto da abreviatura.
    parts = key.split()
    if parts:
        first = parts[0]
        for candidate in (first, first.rstrip("."), f"{first.rstrip('.')}."):
            if candidate in POS_ALIASES:
                return POS_ALIASES[candidate]
    return "desconhecido"


@dataclass
class Sense:
    """Uma aceção. Nunca se fundem aceções de fontes diferentes (plano 5.2)."""

    definition: str
    source: str                      # slug da fonte, ex. "dicionario_aberto"
    ord: int = 0
    domains: list[str] = field(default_factory=list)   # ["Figurado"], ["Náutica"]
    modernized: bool = False         # fraseado adaptado por LLM
    original_definition: str | None = None  # preenchido quando modernized
    # A classe gramatical DESTA aceção. Uma palavra pode ter aceções de
    # classes diferentes ("computador": adjetivo e substantivo); a do lema é
    # só a principal. Nula se a fonte não a der.
    pos: str | None = None
    # Se a aceção remete para outra palavra ("Vide ação", "O mesmo que
    # e-mail"), o lema de destino, confirmado no dicionário. A app torna-o
    # tocável e mostra a definição do destino. Nulo se não remete.
    remete_para: str | None = None

    def __post_init__(self) -> None:
        self.definition = _nfc(self.definition)


@dataclass
class Example:
    """Uma frase de exemplo, sempre com proveniência (plano 4.3)."""

    sentence: str
    source: str
    source_ref: str | None = None    # id Tatoeba, URL, linha do corpus
    variant: str = "unknown"         # 'pt-PT' | 'pt-BR' | 'unknown'
    generated: bool = False
    sense_ord: int | None = None     # a que aceção pertence, se conhecida

    def __post_init__(self) -> None:
        self.sentence = _nfc(self.sentence)


@dataclass
class Form:
    """Forma flexionada -> lema. O coração da pesquisa (plano 4.2)."""

    form: str
    tag: str | None = None           # morfologia, quando a fonte a dá

    def __post_init__(self) -> None:
        self.form = _nfc(self.form)

    @property
    def normalized(self) -> str:
        return normalize(self.form)


@dataclass
class Relation:
    """Relação semântica entre lemas, por texto (os ids só existem no fim)."""

    target: str
    relation: str                    # 'sinonimo' | 'antonimo' | 'hiperonimo'
    source: str = "wordnet_pt"


@dataclass
class SourceEntry:
    """O que uma fonte produz para um lema.

    Uma fonte pode preencher só uma parte: o VOC só dá lema + pos, o Tatoeba
    só dá exemplos, o Hunspell só dá flexões.
    """

    lemma: str
    source: str
    pos: str = "desconhecido"
    senses: list[Sense] = field(default_factory=list)
    examples: list[Example] = field(default_factory=list)
    forms: list[Form] = field(default_factory=list)
    relations: list[Relation] = field(default_factory=list)
    syllables: str | None = None
    frequency_rank: int | None = None
    notes: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.lemma = _nfc(self.lemma)

    @property
    def normalized(self) -> str:
        return normalize(self.lemma)


@dataclass
class MergedEntry:
    """Um lema depois da fusão, pronto a escrever em SQLite."""

    lemma: str
    pos: str
    senses: list[Sense] = field(default_factory=list)
    examples: list[Example] = field(default_factory=list)
    forms: list[Form] = field(default_factory=list)
    relations: list[Relation] = field(default_factory=list)
    syllables: str | None = None
    frequency_rank: int | None = None
    # Fontes que contribuíram, para o relatório de cobertura.
    contributors: list[str] = field(default_factory=list)

    @property
    def normalized(self) -> str:
        return normalize(self.lemma)


# --- Serialização dos intermédios ------------------------------------------
# Os intermédios ficam em JSONL para que qualquer passo do pipeline possa ser
# inspecionado à mão sem correr código.


def dump_jsonl(items: Iterable[Any], path) -> int:
    n = 0
    with open(path, "w", encoding="utf-8") as fh:
        for item in items:
            fh.write(json.dumps(asdict(item), ensure_ascii=False) + "\n")
            n += 1
    return n


def load_jsonl(path, cls) -> list[Any]:
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                out.append(_rebuild(cls, json.loads(line)))
    return out


_NESTED = {"senses": Sense, "examples": Example, "forms": Form, "relations": Relation}


def _rebuild(cls, data: dict) -> Any:
    kwargs = dict(data)
    for key, nested_cls in _NESTED.items():
        if key in kwargs and kwargs[key] is not None:
            kwargs[key] = [nested_cls(**d) for d in kwargs[key]]
    return cls(**kwargs)
