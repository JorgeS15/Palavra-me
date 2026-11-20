"""O revisor interativo da curadoria — testado com entrada scriptada."""

from __future__ import annotations

import io

from palavrame.revisao_curadoria import carregar, rever


def _ficheiro(tmp_path):
    p = tmp_path / "curadoria.csv"
    p.write_text(
        "lema,classe,definicao,nota\n"
        "abc,substantivo,\"Uma coisa qualquer.\",rascunho — rever\n"
        "xyz,verbo,\"Fazer algo.\",rascunho — rever\n"
        "def,adjetivo,\"Já aprovada.\",\n",
        encoding="utf-8",
    )
    return p


def _por_lema(caminho):
    _, linhas = carregar(caminho)
    return {l["lema"]: l for l in linhas}


def test_aceitar_tira_a_marca_de_rascunho(tmp_path):
    p = _ficheiro(tmp_path)
    rever(p, io.StringIO("a\nq\n"))          # aceita 'abc', depois sai
    d = _por_lema(p)
    assert d["abc"]["nota"] == ""            # aprovada
    assert d["xyz"]["nota"] == "rascunho — rever"   # ficou por rever


def test_editar_muda_a_definicao_e_aprova(tmp_path):
    p = _ficheiro(tmp_path)
    rever(p, io.StringIO("e\nOutra definição melhor.\nq\n"))
    d = _por_lema(p)
    assert d["abc"]["definicao"] == "Outra definição melhor."
    assert d["abc"]["nota"] == ""


def test_apagar_remove_a_linha(tmp_path):
    p = _ficheiro(tmp_path)
    rever(p, io.StringIO("x\nq\n"))
    d = _por_lema(p)
    assert "abc" not in d
    assert "xyz" in d and "def" in d         # as outras ficam


def test_corrigir_classe_nao_decide_sozinho(tmp_path):
    p = _ficheiro(tmp_path)
    # corrige a classe e a seguir aceita a mesma palavra
    rever(p, io.StringIO("c\nnome\na\nq\n"))
    d = _por_lema(p)
    assert d["abc"]["classe"] == "nome"
    assert d["abc"]["nota"] == ""


def test_saltar_deixa_por_rever(tmp_path):
    p = _ficheiro(tmp_path)
    rever(p, io.StringIO("s\nq\n"))
    d = _por_lema(p)
    assert d["abc"]["nota"] == "rascunho — rever"


def test_as_linhas_ja_aprovadas_nao_entram_na_revisao(tmp_path):
    p = _ficheiro(tmp_path)
    # aceitar tudo até ao fim; 'def' (sem marca) nunca deve ser tocada
    rever(p, io.StringIO("a\na\n"))
    d = _por_lema(p)
    assert d["def"]["definicao"] == "Já aprovada."
