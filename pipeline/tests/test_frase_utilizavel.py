"""O filtro que separa exemplos de uso de tudo o resto.

O Wikcionário aceita como exemplo qualquer coisa que um editor lá ponha, e
nem tudo é uma frase. O caso que originou isto: `dardejar` mostrava, como
primeiro exemplo, *"26 poetas hoje"* — um verso cortado a meio, sem sentido
fora do poema de onde saiu.
"""

from __future__ import annotations

from palavrame.text import frase_utilizavel


def test_o_verso_cortado_que_deu_origem_a_isto():
    assert not frase_utilizavel("26 poetas hoje")


def test_frases_a_serio_passam():
    assert frase_utilizavel("Os livros na estante estão arrumados segundo o assunto.")
    assert frase_utilizavel("O grupo atemorizava todos os moradores.")
    # Sem ponto final, mas é uma frase — não se exige pontuação.
    assert frase_utilizavel("Os exames indicavam a sanidade do atleta")


def test_fragmentos_e_termos_soltos_nao():
    assert not frase_utilizavel("amar")
    assert not frase_utilizavel("cebolada, papelada")
    assert not frase_utilizavel("Acervo histórico")


def test_formulas_e_derivacoes_nao():
    assert not frase_utilizavel("reciclar + -abilidade = reciclabilidade")


def test_listas_de_numeros_nao():
    assert not frase_utilizavel("1, 2, 3, ..., 99, 100")


def test_titulos_em_maiusculas_nao():
    assert not frase_utilizavel("COMO GANHAR DINHEIRO JOGANDO GAMES")


def test_comprimento():
    assert not frase_utilizavel("É curta.")                       # < 20
    assert not frase_utilizavel("A " + "palavra comprida " * 12)  # > 140
