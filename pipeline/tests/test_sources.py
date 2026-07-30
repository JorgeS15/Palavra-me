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


def test_hunspell_fetch_aceita_ficheiros_postos_a_mao(cache):
    """Sem URL confirmado, os ficheiros manuais bastam — e ficam no lockfile."""
    fonte = build_source("hunspell_natura", cache)
    fonte.fetch()
    registados = {k for k in cache.lock_entries() if k.startswith("hunspell_natura/")}
    assert registados == {"hunspell_natura/pt_PT.aff", "hunspell_natura/pt_PT.dic"}


def test_hunspell_fetch_avisa_quando_nao_ha_nada(cache, paths):
    import shutil

    from palavrame.sources.base import SourceUnavailable

    shutil.rmtree(paths.cache / "hunspell_natura")
    with pytest.raises(SourceUnavailable):
        build_source("hunspell_natura", cache).fetch()


def test_fetch_com_url_alternativo(paths, tmp_path, monkeypatch):
    """`fetch --url` alimenta uma fonte sem editar código.

    É o que salva a situação quando um URL muda — e mudam. O ficheiro entra no
    cache e no lockfile como qualquer outro, portanto a build continua a ser
    verificável.
    """
    from palavrame.cli import main

    origem = tmp_path / "descarregado-a-mao.jsonl"
    origem.write_text(
        '{"word":"bonança","lang_code":"pt","pos":"noun",'
        '"senses":[{"glosses":["Calmaria depois da tempestade."]}]}\n',
        encoding="utf-8",
    )

    assert main(["fetch", "--source", "wikcionario", "--ficheiro", str(origem)]) == 0

    # Guardado com o nome canónico da fonte, não com o nome do ficheiro.
    assert (paths.cache / "wikcionario" / "wikcionario.jsonl").exists()

    cache_offline = __import__(
        "palavrame.cache", fromlist=["Cache"]
    ).Cache(paths, offline=True)
    entradas = _by_lemma(build_source("wikcionario", cache_offline).parse())
    assert "bonança" in entradas


def test_url_sem_source_e_recusado(paths, capsys):
    from palavrame.cli import main

    assert main(["fetch", "--url", "https://exemplo.pt/x.jsonl"]) == 2
    assert "exigem exatamente um --source" in capsys.readouterr().err
