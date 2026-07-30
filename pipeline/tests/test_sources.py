"""Testes dos parsers de cada fonte contra fixtures do formato respetivo."""

from __future__ import annotations

import pytest

from palavrame.sources import build as build_source


def _by_lemma(entries):
    return {e.lemma: e for e in entries}


def test_voc_da_lemas_e_classe(cache):
    entries = _by_lemma(build_source("voc_cplp", cache).parse())
    assert entries["caber"].pos == "verbo"
    assert entries["ensonado"].pos == "adjetivo"
    assert entries["janela"].pos == "substantivo"
    # O VOC não define nada — só diz o que é palavra.
    assert entries["caber"].senses == []


def test_voc_respeita_a_lista_de_lemas(cache):
    entries = list(build_source("voc_cplp", cache).parse(["janela"]))
    assert [e.lemma for e in entries] == ["janela"]


def test_dicionario_aberto_json(cache):
    entries = _by_lemma(build_source("dicionario_aberto", cache).parse())
    janela = entries["janela"]
    assert janela.pos == "substantivo"          # "s. f." -> canónico
    assert janela.syllables == "ja-ne-la"
    assert len(janela.senses) == 2
    assert janela.senses[0].definition.startswith("Abertura na parede")


def test_dicionario_aberto_extrai_dominio(cache):
    entries = _by_lemma(build_source("dicionario_aberto", cache).parse())
    figurado = entries["janela"].senses[1]
    # "(Fig.) Intervalo..." -> domínio separado da definição.
    assert figurado.domains == ["Fig."]
    assert not figurado.definition.startswith("(Fig.)")


def test_dicionario_aberto_tei_embutido(cache):
    entries = _by_lemma(build_source("dicionario_aberto", cache).parse())
    alfarrabio = entries["alfarrábio"]
    assert alfarrabio.syllables == "al-far-rá-bio"
    assert len(alfarrabio.senses) == 2
    assert alfarrabio.senses[0].domains == ["Bibl."]


def test_wikcionario_filtra_outras_linguas(cache):
    lemas = {e.lemma for e in build_source("wikcionario", cache).parse()}
    assert "telemóvel" in lemas
    assert "window" not in lemas          # entrada inglesa no mesmo ficheiro


def test_wikcionario_da_flexoes_e_exemplos(cache):
    entries = _by_lemma(build_source("wikcionario", cache).parse())
    telemovel = entries["telemóvel"]
    assert "telemóveis" in {f.form for f in telemovel.forms}
    assert telemovel.examples and telemovel.examples[0].source == "wikcionario"
    assert telemovel.senses[0].domains == ["telecomunicações"]


def test_hunspell_expande_flexoes(cache):
    entries = _by_lemma(build_source("hunspell_natura", cache).parse())
    assert "couberam" in {f.form for f in entries["caber"].forms}


def test_tatoeba_indexa_por_palavra_da_frase(cache):
    entries = _by_lemma(build_source("tatoeba", cache).parse(["janela", "silencio"]))
    assert "janela" in entries
    frases = [e.sentence for e in entries["janela"].examples]
    assert any("Abre a janela" in f for f in frases)
    assert all(e.source_ref for e in entries["janela"].examples)   # atribuição


def test_tatoeba_rejeita_frases_fora_do_comprimento(cache):
    entries = list(build_source("tatoeba", cache).parse(["curta"]))
    assert entries == []          # "curta" é curta demais para ser exemplo


def test_tatoeba_exige_lista_de_lemas(cache):
    with pytest.raises(ValueError):
        list(build_source("tatoeba", cache).parse())


def test_tatoeba_deteta_variante_pt_pt(cache):
    entries = _by_lemma(build_source("tatoeba", cache).parse(["rapariga"]))
    variantes = {e.variant for e in entries["rapariga"].examples}
    assert "pt-PT" in variantes


def test_leipzig_da_frequencias(cache):
    entries = _by_lemma(build_source("leipzig", cache).parse(["janela", "alfarrabio"]))
    assert entries["janela"].frequency_rank == 2
    # Palavra rara: rank alto, e sem frase no corpus — mas a frequência conta.
    assert entries["alfarrabio"].frequency_rank == 6


def test_wordnet_deriva_sinonimos_do_synset(cache):
    entries = _by_lemma(build_source("wordnet_pt", cache).parse())
    janela = entries["janela"]
    relacoes = {(r.target, r.relation) for r in janela.relations}
    assert ("postigo", "sinonimo") in relacoes
    assert ("abertura", "hiperonimo") in relacoes


def test_wordnet_nao_se_liga_a_si_proprio(cache):
    for entry in build_source("wordnet_pt", cache).parse():
        assert all(r.target != entry.lemma for r in entry.relations)
