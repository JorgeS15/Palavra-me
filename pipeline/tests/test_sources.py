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
    """Ficheiros manuais têm precedência sobre o download — e ficam no lockfile."""
    fonte = build_source("hunspell_natura", cache)
    fonte.fetch()
    registados = {k for k in cache.lock_entries() if k.startswith("hunspell_natura/")}
    assert registados == {"hunspell_natura/pt_PT.aff", "hunspell_natura/pt_PT.dic"}


def test_hunspell_fetch_sem_nada_tenta_o_download_e_explica(cache, paths):
    """Sem ficheiros manuais, o fetch vai ao endpoint do Natura.

    Nos testes o cache está em modo offline, portanto o que se afirma é o
    OfflineError — com a mensagem a dizer exatamente que URL faltou. Numa
    máquina com rede, o mesmo caminho descarrega o tarball e extrai os
    .aff/.dic (ver `_extract_tarballs`).
    """
    import shutil

    from palavrame.cache import OfflineError

    shutil.rmtree(paths.cache / "hunspell_natura")
    with pytest.raises(OfflineError, match="hunspell"):
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


# --- dump SQL do Dicionário Aberto (F1) -------------------------------------


def _dump_sql(tmp_path):
    """Um mysqldump sintético, no formato real das tabelas word/revision."""
    xml_v1 = "<entry id=\\'casa\\'><form><orth>Casa</orth></form><sense><def>Definição velha.</def></sense></entry>"
    xml_v2 = "<entry id=\\'casa\\'><form><orth>Casa</orth></form><sense><gramGrp>f.</gramGrp><def>Edifício d\\'habitação.</def></sense></entry>"
    xml_apagada = "<entry id=\\'morta\\'><form><orth>Morta</orth></form><sense><def>Não devia aparecer.</def></sense></entry>"
    sql = (
        "-- MySQL dump sintético (fixture)\n"
        # O dump real usa "INSERT  IGNORE INTO" (dois espaços); a fixture
        # cobre as duas variantes de propósito.
        "INSERT  IGNORE INTO `revision` VALUES "
        f"(1,1,'u','2010-01-01 00:00:00','{xml_v1}',0,NULL,NULL),"
        f"(2,1,'u','2011-01-01 00:00:00','{xml_v2}',0,NULL,NULL),"
        f"(1,2,'u','2010-01-01 00:00:00','{xml_apagada}',0,NULL,NULL);\n"
        "INSERT INTO `word` VALUES "
        "(1,'Casa',0,2,0,'u',NULL,'casa',NULL),"
        "(2,'Morta',0,1,1,'u','mod','morta',NULL);\n"
    )
    path = tmp_path / "da-dump.sql"
    path.write_text(sql, encoding="utf-8")
    return path


def test_da_dump_sql_le_a_revisao_em_vigor(tmp_path):
    from palavrame.sources.dicionario_aberto import _parse_sql_dump

    entries = list(_parse_sql_dump(_dump_sql(tmp_path), None, "dicionario_aberto"))
    # Só a palavra viva, só a revisão em vigor (a 2), e sem a capitalização
    # tipográfica de 1913.
    assert [e.lemma for e in entries] == ["casa"]
    assert [s.definition for s in entries[0].senses] == ["Edifício d'habitação."]


def test_da_dump_sql_respeita_o_filtro_de_lemas(tmp_path):
    from palavrame.sources.dicionario_aberto import _parse_sql_dump

    assert list(_parse_sql_dump(_dump_sql(tmp_path), {"outra"}, "x")) == []


def test_da_parse_nao_repete_o_que_a_api_ja_deu(paths, cache):
    """Com dump E respostas da API no cache, a API (mais recente) ganha."""
    base = paths.cache / "dicionario_aberto"
    xml = (
        "<entry id=\\'janela\\'><form><orth>Janela</orth></form>"
        "<sense><def>Definição desatualizada do dump.</def></sense></entry>"
    )
    (base / "da-dump.sql").write_text(
        "INSERT INTO `revision` VALUES "
        f"(1,9,'u','2010-01-01 00:00:00','{xml}',0,NULL,NULL);\n"
        "INSERT INTO `word` VALUES "
        "(9,'Janela',0,1,0,'u',NULL,'janela',NULL);\n",
        encoding="utf-8",
    )
    entries = list(build_source("dicionario_aberto", cache).parse(["janela"]))
    janela = [e for e in entries if e.normalized == "janela"]
    assert len(janela) == 1
    # A definição é a da API (fixture word/janela.json), não a do dump.
    assert janela[0].senses[0].definition.startswith("Abertura na parede")


def test_sql_values_aguenta_escapes():
    from palavrame.mysqldump import sql_values

    linha = r"INSERT INTO `t` VALUES (1,'d\'água, (sim)\\',NULL,0);"
    rows = list(sql_values(linha))
    assert rows == [["1", "d'água, (sim)\\", None, "0"]]


def test_insert_re_aceita_ignore_e_espacos_a_mais():
    """O mysqldump real escreve `INSERT  IGNORE INTO` — as duas formas contam."""
    from palavrame.mysqldump import insert_re

    padrao = insert_re("word", "revision")
    assert padrao.match("INSERT INTO `word` VALUES (1);")
    assert padrao.match("INSERT  IGNORE INTO `revision` VALUES (1);")
    assert not padrao.match("INSERT INTO `outra` VALUES (1);")


def test_wikcionario_converte_form_of_em_flexao(cache):
    """Páginas de flexão ('cantada, particípio de cantar') não são lemas."""
    entries = list(build_source("wikcionario", cache).parse())
    lemas = {e.lemma for e in entries}
    # A página "cantada" (form-of) não cria lema próprio...
    assert not any(e.lemma == "cantada" and e.senses for e in entries)
    # ...cria uma flexão pendurada no lema verdadeiro.
    cantar = [e for e in entries if e.lemma == "cantar"]
    assert cantar and "cantada" in {f.form for f in cantar[0].forms}


# --- PULO (dump SQL de wordnet.pt) ------------------------------------------


def _dump_pulo(path):
    """Amostra no esquema real do PULO: variant + relation."""
    path.write_text(
        # cão/cachorro no mesmo synset (sinónimos legítimos);
        # o synset 'balde de tradução' tem 6 membros e deve ser ignorado.
        "INSERT  IGNORE INTO `wei_por-30_variant` VALUES "
        "('cão',1,'por-30-02084071','n','',50,NULL,'------',''),"
        "('cachorro',1,'por-30-02084071','n','',50,NULL,'------',''),"
        "('spitz',1,'por-30-02085998','n','',50,NULL,'------',''),"
        "('janela',1,'por-30-03526198','n','',50,NULL,'------',''),"
        "('covil',1,'por-30-03526198','n','',50,NULL,'------',''),"
        "('deficit',1,'por-30-03526198','n','',50,NULL,'------',''),"
        "('divida',1,'por-30-03526198','n','',50,NULL,'------',''),"
        "('cava',1,'por-30-03526198','n','',50,NULL,'------',''),"
        "('buraco',1,'por-30-03526198','n','',50,NULL,'------','');\n"
        # 12 = hiponimo: cão (geral) -> spitz (específico); 33 = antonimo
        "INSERT  IGNORE INTO `wei_por-30_relation` VALUES "
        "(12,'por-30-02084071','n','por-30-02085998','n',0,'aa','1','pwn'),"
        "(33,'por-30-02084071','n','por-30-03526198','n',0,'aa','1','pwn');\n",
        encoding="utf-8",
    )
    return path


def test_pulo_deriva_sinonimos_e_hierarquia(paths, cache):
    _dump_pulo(paths.cache / "wordnet_pt" / "pulo.sql")
    entries = _by_lemma(build_source("wordnet_pt", cache).parse())

    cao = {(r.target, r.relation) for r in entries["cão"].relations}
    assert ("cachorro", "sinonimo") in cao
    assert ("spitz", "hiponimo") in cao
    # A relação inversa é gerada, para a app não ter de a procurar ao contrário.
    spitz = {(r.target, r.relation) for r in entries["spitz"].relations}
    assert ("cão", "hiperonimo") in spitz


def test_pulo_ignora_synsets_balde_de_traducao(paths, cache):
    """'janela' e 'deficit' partilham o synset inglês 'hole' — não são sinónimos."""
    _dump_pulo(paths.cache / "wordnet_pt" / "pulo.sql")
    entries = _by_lemma(build_source("wordnet_pt", cache).parse())
    janela = {r.target for r in entries.get("janela", _Vazio()).relations}
    assert "deficit" not in janela and "covil" not in janela


class _Vazio:
    relations: list = []


def test_wikcionario_pagina_de_flexao_com_tabela_nao_vira_lema(paths, cache):
    """'couberam' remete para 'caber' e não é palavra, mesmo trazendo formas."""
    (paths.cache / "wikcionario" / "wikcionario.jsonl").write_text(
        '{"word":"couberam","lang_code":"pt","pos":"verb",'
        '"forms":[{"form":"couberam","tags":["third-person","plural"]}],'
        '"senses":[{"form_of":[{"word":"caber"}],"tags":["form-of"],'
        '"glosses":["terceira pessoa do plural do pretérito de caber"]}]}\n',
        encoding="utf-8",
    )
    entries = list(build_source("wikcionario", cache).parse())
    assert [e.lemma for e in entries] == ["caber"]
    assert "couberam" in {f.form for f in entries[0].forms}


def test_da_def_com_varias_linhas_da_varias_acecoes():
    """1913 escreve as aceções em linhas seguidas dentro do mesmo <def>."""
    from palavrame.sources.dicionario_aberto import _parse_tei_string

    xml = ("<entry id='macilento'><form><orth>Macilento</orth></form>"
           "<sense><gramGrp>adj.</gramGrp>"
           "<def>\nMagro.\nPálido.\nAmortecido.\n</def></sense></entry>")
    entry = _parse_tei_string(xml, "dicionario_aberto")[0]
    assert entry.pos == "adjetivo"       # vem de <gramGrp>, não de <pos>
    assert [s.definition for s in entry.senses] == ["Magro.", "Pálido.", "Amortecido."]


def test_split_domain_nao_come_a_definicao():
    """A regressão que deixou 'macilento' sem definição nenhuma."""
    from palavrame.sources.dicionario_aberto import _split_domain

    # Uma palavra capitalizada com ponto NÃO é um domínio.
    assert _split_domain("Magro.") == ("Magro.", [])
    assert _split_domain("Amortecido.") == ("Amortecido.", [])
    # Entre parênteses, ou abreviatura conhecida, é.
    assert _split_domain("(Náut.) Cabo de amarração.") == ("Cabo de amarração.", ["Náut."])
    assert _split_domain("Fig. Coisa vaga.") == ("Coisa vaga.", ["Fig."])
    # Uma marca sozinha, sem definição a seguir, fica como definição.
    assert _split_domain("Fig.") == ("Fig.", [])
