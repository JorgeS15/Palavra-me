"""Prompts para o AMALIA.

Dois trabalhos distintos, com riscos distintos:

* **exemplos** — inventar uma frase. Risco baixo: se sair mal, rejeita-se.
* **modernização de fraseado** — reescrever uma definição de 1913 em português
  contemporâneo. Risco alto: o modelo pode mudar o sentido sem que isso seja
  óbvio. Por isso o prompt é restritivo ao ponto de ser aborrecido, e o
  resultado passa **sempre** por revisão humana (plano 4.4).

Os prompts recebem a **aceção específica**, não só o lema — sem isso o modelo
escolhe o sentido mais comum e o exemplo não serve à aceção a que está preso.
"""

from __future__ import annotations

from ..config import EXAMPLE_MAX_CHARS, EXAMPLE_MIN_CHARS

# Descrição da classe gramatical em linguagem que ajuda o modelo, com uma
# instrução do que NÃO fazer. A do adjetivo existe por causa do erro
# documentado no plano 5.3 ("O ensonado sonhou...").
_POS_GUIDANCE = {
    "adjetivo": (
        "A palavra tem de aparecer como ADJETIVO, a qualificar um nome "
        "explícito na frase (por exemplo: «o miúdo ensonado»). NÃO a uses "
        "como nome — não escrevas «o ensonado» a fazer de sujeito."
    ),
    "substantivo": (
        "A palavra tem de aparecer como NOME, com determinante quando for "
        "natural («a bonança», «um armistício»)."
    ),
    "verbo": (
        "A palavra tem de aparecer CONJUGADA, não no infinitivo isolado nem "
        "numa definição. Escolhe um tempo comum na fala."
    ),
    "adverbio": (
        "A palavra tem de aparecer como ADVÉRBIO, a modificar um verbo, um "
        "adjetivo ou outro advérbio."
    ),
}

_EXAMPLE_TEMPLATE = """Escreve UMA frase de exemplo em português europeu.

Palavra: {lemma}
Classe gramatical: {pos}
Significado a ilustrar: {definition}

Regras:
- A frase tem de usar a palavra «{lemma}» (podes flexioná-la).
- {pos_guidance}
- A frase tem de ilustrar o significado indicado acima, e não outro sentido \
da palavra.
- Português europeu, registo do dia a dia. Nada de brasileirismos.
- Entre {min_chars} e {max_chars} caracteres.
- NÃO expliques a palavra nem repitas a definição dentro da frase.
- Responde apenas com a frase, sem aspas, sem numeração, sem comentários.

Frase:"""

_MODERNIZE_TEMPLATE = """Reescreve esta definição de dicionário em português \
europeu contemporâneo.

Palavra: {lemma}
Classe gramatical: {pos}
Definição original (1913): {definition}

Regras absolutas:
- NÃO acrescentes sentidos que a definição original não tenha.
- NÃO removas sentidos que a definição original tenha.
- NÃO dês exemplos, NÃO dês etimologia, NÃO comentes.
- Muda apenas o que for preciso para um leitor de hoje perceber: vocabulário \
antiquado, sintaxe pesada, abreviaturas.
- Se a definição já for clara em português de hoje, devolve-a tal e qual.
- Mantém o registo de dicionário: sem sujeito, sem «significa», sem «é quando».

Responde apenas com a definição reescrita.

Definição reescrita:"""


def build_example_prompt(lemma: str, pos: str, definition: str) -> str:
    return _EXAMPLE_TEMPLATE.format(
        lemma=lemma,
        pos=pos,
        definition=definition.strip(),
        pos_guidance=_POS_GUIDANCE.get(
            pos, "A palavra tem de aparecer com a classe gramatical indicada."
        ),
        min_chars=EXAMPLE_MIN_CHARS,
        max_chars=EXAMPLE_MAX_CHARS,
    )


def build_modernize_prompt(lemma: str, pos: str, definition: str) -> str:
    return _MODERNIZE_TEMPLATE.format(
        lemma=lemma, pos=pos, definition=definition.strip()
    )
