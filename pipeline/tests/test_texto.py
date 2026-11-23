"""Testes das funções de superfície: nomes próprios e tokenização."""

from __future__ import annotations

from palavrame.text import nomes_proprios, surface_words


def test_nome_proprio_a_meio_da_frase():
    # O caso real: o exemplo de 'nené' era sobre o jogador do Benfica.
    p = surface_words("Mas foi com Jimmy Hagan que Nené se afirmou no Benfica.")
    np = nomes_proprios(p)
    assert "nené" in np
    assert "jimmy" in np and "hagan" in np and "benfica" in np
    # Palavras comuns não são apanhadas.
    assert "mas" not in np and "foi" not in np and "que" not in np


def test_palavra_comum_em_minuscula_nao_e_nome_proprio():
    p = surface_words("A filha amamentou o nené com muito carinho.")
    assert "nené" not in nomes_proprios(p)


def test_maiuscula_no_inicio_da_frase_nao_conta():
    # No início toda a gente escreve maiúscula; não diz nada sobre ser próprio.
    p = surface_words("Nené é uma forma de tratar uma criança pequena.")
    assert "nené" not in nomes_proprios(p)


def test_sigla_toda_em_maiusculas_nao_e_nome_proprio():
    p = surface_words("O teste ao ADN foi conclusivo.")
    assert "adn" not in nomes_proprios(p)


def test_surface_words_preserva_caixa_e_acentos():
    assert surface_words("O Bebé dorme.") == ["O", "Bebé", "dorme"]


def test_apostrofo_tipografico_e_o_simples():
    # Os ebooks escrevem `wouldn’t` com o ’ (U+2019); tem de dar o mesmo que
    # o apóstrofo simples, e a app (`Texto.kt`) faz igual.
    from palavrame.text import normalize

    assert normalize("Wouldn’t") == "wouldn't"
    assert normalize("d’água") == "d'agua"
    assert normalize("d'água") == "d'agua"
