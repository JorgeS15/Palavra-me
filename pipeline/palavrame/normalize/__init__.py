"""Vocabulário canónico partilhado pelas fontes.

O plano previa um passo `normalize/` entre `sources/` e `merge/`. Na prática
ficou melhor cada fonte normalizar dentro do seu próprio `parse()`: a
conversão do formato bruto para o esquema canónico e a normalização são o
mesmo trabalho, e separá-las obrigaria a inventar um formato intermédio por
fonte só para o desfazer a seguir.

O que este pacote mantém é a **definição** de canónico — as funções que todas
as fontes têm de usar para o resultado ser comparável. É aqui que se olha para
saber o que "normalizado" quer dizer neste projeto.
"""

from ..schema import POS, POS_ALIASES, canonical_pos
from ..text import clean_definition, normalize, strip_accents, tokens

__all__ = [
    "POS",
    "POS_ALIASES",
    "canonical_pos",
    "clean_definition",
    "normalize",
    "strip_accents",
    "tokens",
]
