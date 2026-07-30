"""Normalização de texto português.

`normalize()` é a função mais importante do pipeline: define a chave sob a
qual tudo é indexado e pesquisado. A app faz exatamente a mesma normalização
sobre o que o utilizador escreve, por isso qualquer alteração aqui é uma
alteração de formato da base de dados.
"""

from __future__ import annotations

import re
import unicodedata

# Mantém-se o hífen (couve-flor, dar-se-á) e o apóstrofo (d'água); tudo o
# resto que não seja letra ou dígito vira espaço e depois desaparece.
_LIMPEZA = re.compile(r"[^\w\-']+", re.UNICODE)
_ESPACOS = re.compile(r"\s+")


def strip_accents(s: str) -> str:
    """Remove diacríticos, preservando o resto.

    'ção' -> 'cao', 'pôr' -> 'por'. O ç decompõe-se em c + cedilha em NFD,
    por isso não precisa de tratamento especial.
    """
    decomposed = unicodedata.normalize("NFD", s)
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def normalize(s: str) -> str:
    """Forma canónica de pesquisa: minúsculas, sem acentos, sem pontuação.

    É o que vai para as colunas `normalized` de `lemmas` e `forms`.
    """
    s = strip_accents(s.strip().lower())
    s = _LIMPEZA.sub(" ", s)
    return _ESPACOS.sub(" ", s).strip()


def clean_definition(s: str) -> str:
    """Limpa uma definição vinda de uma fonte.

    Colapsa espaços e tira pontuação órfã nas pontas. Não reescreve nada — a
    modernização de fraseado é um passo explícito e marcado (`modernized`),
    nunca um efeito secundário da limpeza.
    """
    s = _ESPACOS.sub(" ", s.replace("\n", " ")).strip()
    s = s.strip(" ;,:")
    # As entradas do Dicionário Aberto acabam frequentemente em ponto solto
    # após remoção de marcação; normaliza para um único ponto final.
    s = re.sub(r"\s+\.$", ".", s)
    return s


def tokens(s: str) -> list[str]:
    """Palavras normalizadas de uma frase, para validação de exemplos."""
    return [t for t in normalize(s).replace("-", " ").split(" ") if t]
