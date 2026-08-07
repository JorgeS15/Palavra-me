"""Validação automática de exemplos gerados (plano 5.3).

Quatro motivos de rejeição, todos do plano:

1. a frase não contém o lema nem nenhuma das suas flexões conhecidas;
2. a frase contém a definição literalmente — o modelo copiou em vez de
   exemplificar;
3. comprimento fora do intervalo;
4. classe gramatical errada.

O quarto é o interessante e o que apanha o erro documentado no plano: para
`ensonado` (adjetivo), *"O ensonado sonhou longamente ao almoço"* substantiva
a palavra. Sem lematizador, deteta-se pelo contexto sintático — um adjetivo
precedido de determinante e seguido de verbo está a ser usado como nome. A
heurística é conservadora: só rejeita padrões que dificilmente são outra
coisa, porque um falso positivo aqui deita fora uma frase boa.

Havendo spaCy instalado (extra `[spacy]`), `pos_checker` recebe um verificador
melhor e a heurística fica só como rede.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Callable, Iterable, Optional

from ..config import EXAMPLE_MAX_CHARS, EXAMPLE_MIN_CHARS
from ..text import normalize, tokens

DETERMINERS = {
    "o", "a", "os", "as", "um", "uma", "uns", "umas",
    "este", "esta", "estes", "estas", "esse", "essa", "esses", "essas",
    "aquele", "aquela", "aqueles", "aquelas", "meu", "minha", "seu", "sua",
    "nosso", "nossa", "do", "da", "dos", "das", "no", "na", "nos", "nas",
    "ao", "aos", "pelo", "pela", "meus", "minhas", "teu", "tua",
}

# Terminações verbais frequentes em PT. Serve para "a palavra seguinte parece
# um verbo", que é o sinal de que o adjetivo virou sujeito.
_VERBISH = re.compile(
    r"(ou|aram|eram|iram|ava|avam|ia|iam|ei|aste|este|iste|"
    r"amos|emos|imos|ara|era|ira|asse|esse|isse|ando|endo|indo|"
    r"ará|erá|irá|aria|eria|iria)$"
)

# Palavras que, a seguir a um determinante, tornam a leitura nominal legítima
# («o ensonado rapaz» é adjetivo, não nome).
_STOP_AFTER = {"e", "ou", "mas", "que", "de", "do", "da", "em", "com", "por"}


@dataclass
class ValidationResult:
    ok: bool
    reasons: list[str] = field(default_factory=list)

    def __bool__(self) -> bool:
        return self.ok


# `Optional` em vez de `bool | None`: isto é um alias avaliado em runtime
# (o `from __future__ import annotations` não o cobre), e a sintaxe `|` em
# runtime só existe a partir do Python 3.10. É a única concessão ao 3.9 no
# código todo — e é o que permite correr o pipeline no Python de sistema
# do Windows sem instalar outro.
PosChecker = Callable[[str, str, str], Optional[bool]]
"""(frase, lema, pos) -> True/False/None. None = não sei, não rejeita."""


def validate_example(
    sentence: str,
    lemma: str,
    pos: str,
    definition: str,
    known_forms: Iterable[str] = (),
    pos_checker: PosChecker | None = None,
) -> ValidationResult:
    reasons: list[str] = []
    sentence = sentence.strip()

    # 3. comprimento
    if not (EXAMPLE_MIN_CHARS <= len(sentence) <= EXAMPLE_MAX_CHARS):
        reasons.append(
            f"comprimento {len(sentence)} fora de "
            f"[{EXAMPLE_MIN_CHARS}, {EXAMPLE_MAX_CHARS}]"
        )

    words = tokens(sentence)
    if not words:
        return ValidationResult(False, reasons + ["frase vazia"])

    # 1. a palavra tem de lá estar
    candidates = {normalize(lemma)} | {normalize(f) for f in known_forms}
    candidates.discard("")
    matched = _find_match(words, candidates)
    if matched is None:
        reasons.append(f"não contém «{lemma}» nem nenhuma flexão conhecida")

    # 2. cópia da definição
    if _contains_definition(sentence, definition):
        reasons.append("repete a definição em vez de exemplificar")

    # 4. classe gramatical
    if matched is not None:
        verdict = pos_checker(sentence, lemma, pos) if pos_checker else None
        if verdict is False:
            reasons.append(f"não usa a palavra como {pos}")
        elif verdict is None and _looks_nominalized(words, matched, pos):
            reasons.append(
                f"usa a palavra como nome, mas a aceção é {pos} "
                "(padrão determinante + palavra + verbo)"
            )

    return ValidationResult(not reasons, reasons)


def _find_match(words: list[str], candidates: set[str]) -> int | None:
    for i, word in enumerate(words):
        if word in candidates:
            return i
    return None


def _contains_definition(sentence: str, definition: str) -> bool:
    """Rejeita cópia literal, não coincidência de vocabulário.

    Compara-se por n-gramas: a partir de cinco palavras seguidas iguais já não
    é coincidência. Definições curtas (menos de cinco palavras) exigem
    correspondência exata, senão «ave» rejeitaria meia frase.
    """
    definition_words = tokens(definition)
    sentence_norm = " ".join(tokens(sentence))
    if not definition_words:
        return False
    if len(definition_words) < 5:
        return " ".join(definition_words) in sentence_norm
    window = 5
    for i in range(len(definition_words) - window + 1):
        chunk = " ".join(definition_words[i:i + window])
        if chunk in sentence_norm:
            return True
    return False


def _looks_nominalized(words: list[str], index: int, pos: str) -> bool:
    """Deteta um adjetivo a ser usado como nome.

    Padrão: determinante, a palavra, e a seguir algo com cara de verbo.
    Exige as três condições justamente para não apanhar «o miúdo ensonado
    bocejou», onde a palavra vem depois do nome e não antes do verbo.
    """
    if pos != "adjetivo":
        return False
    if index == 0 or words[index - 1] not in DETERMINERS:
        return False
    if index + 1 >= len(words):
        # «Chegou o ensonado.» — determinante + palavra e mais nada.
        return True
    following = words[index + 1]
    if following in _STOP_AFTER or following in DETERMINERS:
        return False
    return bool(_VERBISH.search(following))


def spacy_pos_checker(model: str = "pt_core_news_sm") -> PosChecker:
    """Verificador de classe gramatical com spaCy (extra `[spacy]`).

    Devolve um checker que responde None se o modelo não estiver instalado —
    a validação continua a correr com a heurística.
    """
    tag_map = {
        "substantivo": {"NOUN", "PROPN"},
        "adjetivo": {"ADJ"},
        "verbo": {"VERB", "AUX"},
        "adverbio": {"ADV"},
        "pronome": {"PRON"},
        "preposicao": {"ADP"},
        "conjuncao": {"CCONJ", "SCONJ"},
        "numeral": {"NUM"},
        "artigo": {"DET"},
        "interjeicao": {"INTJ"},
    }

    try:
        import spacy

        nlp = spacy.load(model)
    except Exception:
        return lambda sentence, lemma, pos: None

    def check(sentence: str, lemma: str, pos: str) -> bool | None:
        wanted = tag_map.get(pos)
        if not wanted:
            return None
        target = normalize(lemma)
        doc = nlp(sentence)
        seen = False
        for token in doc:
            if normalize(token.lemma_) == target or normalize(token.text) == target:
                seen = True
                if token.pos_ in wanted:
                    return True
        return False if seen else None

    return check
