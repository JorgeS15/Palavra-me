"""Portugalização da grafia: da variante brasileira para a europeia.

O português do Brasil escreve com **circunflexo** (ê, ô) uma série de palavras
paroxítonas que o português europeu escreve com **agudo** (é, ó): `gênero`/
`género`, `bebê`/`bebé`, `abdômen`/`abdómen`, `fenômeno`/`fenómeno`. É a
divergência ortográfica mais visível entre as duas normas, e a que enche a app
de circunflexos que não são de cá.

Há também vocabulário próprio do Brasil (`ônibus`, `trem`, `nenê`) — mas isso
não é grafia, é outra palavra, e não se toca aqui.

Este módulo faz **uma** coisa e de propósito só uma: dada uma forma com
circunflexo, propõe a sua gémea com agudo. Não decide sozinho se a troca é
legítima. Quem decide é o `merge`, que só a aceita quando a gémea aguda é uma
palavra portuguesa **atestada** (Hunspell pt-PT) e a circunflexa **não** é.
Essa condição, e não o padrão, é o que impede os falsos positivos:

* `avô` → `avó`? Ambas são portuguesas (Hunspell atesta as duas) — não se une.
* `português` → `portugués`? A gémea não existe em lado nenhum — não se une.
* `bebê` → `bebé`? `bebé` é atestada, `bebê` não — **une-se**.

A troca é sempre no sentido circunflexo→agudo (BR→PT). O contrário — inventar
uma grafia com circunflexo a partir de uma aguda — nunca se faz, porque é aí
que `avó` viraria `avô`.
"""

from __future__ import annotations

_CIRCUNFLEXO = "êôÊÔ"
_CIRC_PARA_AGUDO = str.maketrans("êôÊÔ", "éóÉÓ")


def tem_circunflexo(palavra: str) -> bool:
    return any(c in _CIRCUNFLEXO for c in palavra)


def para_grafia_aguda(palavra: str) -> str | None:
    """A gémea com agudo de uma forma com circunflexo.

    `gênero` -> `género`, `Antônio` -> `António`. Devolve `None` se a palavra
    não tem nenhum ê/ô — não há nada a portugalizar.
    """
    if not tem_circunflexo(palavra):
        return None
    return palavra.translate(_CIRC_PARA_AGUDO)
