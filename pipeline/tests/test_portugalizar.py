"""Testes da primitiva de grafia BR->PT.

O módulo só propõe a gémea aguda; quem decide se a troca é legítima é o merge
(ver test_merge.py). Aqui verifica-se apenas a transformação de letras.
"""

from __future__ import annotations

from palavrame.portugalizar import para_grafia_aguda, tem_circunflexo


def test_troca_circunflexo_por_agudo():
    assert para_grafia_aguda("gênero") == "género"
    assert para_grafia_aguda("bebê") == "bebé"
    assert para_grafia_aguda("fenômeno") == "fenómeno"
    assert para_grafia_aguda("abdômen") == "abdómen"


def test_preserva_maiuscula():
    assert para_grafia_aguda("Antônio") == "António"
    assert para_grafia_aguda("Gênero") == "Género"


def test_sem_circunflexo_devolve_none():
    # Nada a portugalizar: a decisão de não mexer é do módulo, não do merge.
    assert para_grafia_aguda("janela") is None
    assert para_grafia_aguda("português") == "portugués"  # troca, mas...
    # ...'portugués' não existe; é o merge que rejeita por não estar atestada.
    assert para_grafia_aguda("três") == "trés"


def test_tem_circunflexo():
    assert tem_circunflexo("bebê")
    assert not tem_circunflexo("bebé")
    assert not tem_circunflexo("casa")
