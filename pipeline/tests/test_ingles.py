"""O dicionário de inglês: o parser do Wiktionary inglês e a fusão.

As fixtures (`fixtures/wiktionary_en`, `fixtures/leipzig_en`) imitam o formato
do kaikki e do Leipzig; o conteúdo foi escrito para o teste.
"""

from __future__ import annotations

from palavrame.ingles import fundir, ranks_de
from palavrame.sources import REGISTRY, REGISTRY_EN, all_infos, build as build_source
from palavrame.sources.wiktionary_en import rotulos


def _fundido(cache):
    ranks = ranks_de(build_source("leipzig_en", cache).parse())
    entradas = fundir(build_source("wiktionary_en", cache).parse(), ranks)
    return {e.lemma: e for e in entradas}


def test_fontes_inglesas_nao_entram_na_build_portuguesa():
    assert not set(REGISTRY) & set(REGISTRY_EN)
    assert {i.slug for i in all_infos("en")} == {"wiktionary_en", "leipzig_en"}


def test_so_ingles_e_sem_nomes_proprios(cache):
    lemas = _fundido(cache)
    assert "janela" not in lemas
    assert "Thames" not in lemas


def test_flexoes_viram_formas_do_lema(cache):
    lemas = _fundido(cache)
    formas = {f.form for f in lemas["run"].forms}
    assert {"ran", "runs", "running"} <= formas
    # O marcador vazio da tabela e a grafia alternativa não são flexões.
    assert "-" not in formas and "rin" not in formas
    assert "dogs" in {f.form for f in lemas["dog"].forms}
    # `dogs` não é palavra de dicionário: é só forma.
    assert "dogs" not in lemas


def test_flexao_de_lema_inexistente_cai(cache):
    # `cats` aponta para `cat`, que não está no dicionário.
    assert "cat" not in _fundido(cache)


def test_palavra_que_e_flexao_e_lema_fica_nos_dois(cache):
    lemas = _fundido(cache)
    assert lemas["ran"].senses[0].definition == "Yarn coiled on a winch."
    # A frequência do `run` conta com as formas; passa à frente do náutico.
    assert lemas["run"].frequency_rank == 40
    assert lemas["ran"].frequency_rank == 55


def test_rotulos_de_uso_sem_os_gramaticais(cache):
    lemas = _fundido(cache)
    assert lemas["run"].senses[0].domains == []                 # (intransitive)
    assert lemas["run"].senses[1].domains == ["figuratively"]   # (transitive, figuratively)
    assert lemas["ran"].senses[0].domains == ["nautical"]
    assert rotulos("(archaic, slang) Foo.") == ["archaic", "slang"]
    assert rotulos("(with of) Foo.") == []


def test_grafia_alternativa_remete_para_o_destino(cache):
    assert _fundido(cache)["colour"].senses[0].remete_para == "color"


def test_exemplos_feitos_de_proposito_primeiro(cache):
    lemas = _fundido(cache)
    assert [e.sentence for e in lemas["wistful"].examples] == [
        "His eyes grew wistful as he recalled his university days."
    ]
    # Citações em verso cortado não servem de exemplo.
    assert lemas["dog"].examples == []


def test_erros_ortograficos_e_formas_compostas_ficam_de_fora(cache):
    wistful = _fundido(cache)["wistful"]
    assert len(wistful.senses) == 2
    assert wistful.forms == []          # "more wistful" não é forma pesquisável
    assert wistful.syllables == "wist-ful"


def test_classe_com_mais_acecoes_vai_a_frente(cache):
    lead = _fundido(cache)["lead"]
    assert lead.pos == "verbo"
    assert lead.senses[0].definition == "To guide."
    assert [s.ord for s in lead.senses] == [0, 1, 2, 3]


def test_sinonimos_da_acecao_antes_do_tesauro(cache):
    dog = _fundido(cache)["dog"]
    assert [r.target for r in dog.relations] == ["hound", "cur"]
