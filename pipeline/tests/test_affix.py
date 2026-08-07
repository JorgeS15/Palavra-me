"""Testes da expansão de afixos.

É o componente que decide se a app encontra a palavra que está no livro. Os
casos abaixo são os que o plano cita pelo nome: *couberam*, *pusesse*,
*ensonados*.
"""

from __future__ import annotations

import pytest

from palavrame.affix import expand, parse_condition, read_aff, read_dic

from conftest import FIXTURES


@pytest.fixture
def table():
    return read_aff(FIXTURES / "hunspell_natura" / "pt_PT.aff")


@pytest.fixture
def forms(table):
    """lema -> conjunto de formas geradas."""
    out = {}
    for word, flags, _ in read_dic(FIXTURES / "hunspell_natura" / "pt_PT.dic", table):
        out[word] = {g.form for g in expand(word, flags, table)}
    return out


def test_condicao_ponto_aceita_tudo():
    assert parse_condition(".") == []
    assert parse_condition("") == []


def test_condicao_classe_de_caracteres():
    atoms = parse_condition("[^aeiou]y")
    assert len(atoms) == 2
    negated, chars = atoms[0]
    assert negated is True
    assert chars == frozenset("aeiou")


def test_lema_entra_como_forma(forms):
    assert "caber" in forms["caber"]
    assert "janela" in forms["janela"]


def test_verbo_irregular_caber(forms):
    # O caso do plano: quem lê "couberam" tem de chegar a "caber".
    assert "couberam" in forms["caber"]
    assert "coubesse" in forms["caber"]
    assert "cabemos" in forms["caber"]


def test_strip_do_lema_inteiro(forms):
    # "pôr" -> "pusesse" exige remover o lema todo. É legal e é preciso.
    assert "pusesse" in forms["pôr"]
    assert "puseram" in forms["pôr"]
    assert "ponho" in forms["pôr"]


def test_adjetivo_genero_e_numero(forms):
    assert {"ensonado", "ensonados", "ensonada", "ensonadas"} <= forms["ensonado"]


def test_plural_em_l(forms):
    assert "telemóveis" in forms["telemóvel"]
    # E não o plural ingénuo.
    assert "telemóvels" not in forms["telemóvel"]


def test_condicao_bloqueia_regra_errada(forms):
    # A regra do plural em -l não se aplica a "janela": não acaba em l.
    assert "janelis" not in forms["janela"]
    assert "janelas" in forms["janela"]


def test_produto_cruzado_prefixo_e_sufixo(forms):
    # fazer/FR: o prefixo "re-" cruza com as formas sufixadas.
    assert "refazer" in forms["fazer"]
    assert "fizeram" in forms["fazer"]
    assert "refizeram" in forms["fazer"]


def test_flag_desconhecida_nao_rebenta(table):
    formas = {g.form for g in expand("teste", frozenset("ZZZ"), table)}
    assert formas == {"teste"}


def test_needaffix_exclui_o_lema(table):
    table.needaffix.add("!")
    formas = {g.form for g in expand("caber", frozenset({"V", "!"}), table)}
    assert "caber" not in formas          # a raiz não é palavra
    assert "couberam" in formas           # mas as flexões são
