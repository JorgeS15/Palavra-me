"""Portabilidade — o pipeline corre em Windows tanto como em Linux.

O desenvolvimento faz-se em Windows. Estes testes existem porque os erros de
portabilidade não se manifestam na máquina de quem escreve o código: aparecem
na máquina de quem o corre, e aparecem tarde.
"""

from __future__ import annotations

import sqlite3

import pytest

from palavrame.build import build_database, open_readonly
from palavrame.build.sqlite import readonly_uri
from palavrame.schema import Form, MergedEntry, Sense
from palavrame.sources.base import License, SourceInfo

INFO = SourceInfo(
    slug="teste", name="Teste", url="https://exemplo.pt/",
    license=License(name="Domínio público", attribution="Teste.",
                    redistributable=True, verified=True),
)


def _entrada():
    return MergedEntry(
        lemma="janela", pos="substantivo",
        senses=[Sense("Abertura na parede.", "teste", ord=1)],
        forms=[Form("janelas", "plural")],
        contributors=["teste"],
    )


def test_uri_de_so_leitura_e_absoluto_e_com_barras(tmp_path):
    uri = readonly_uri(tmp_path / "d.db")
    assert uri.startswith("file:///")
    assert "\\" not in uri            # nunca barras invertidas, nem no Windows
    assert uri.endswith("?mode=ro")


def test_abre_db_em_pasta_com_espacos(tmp_path):
    """`C:\\Users\\Jorge Silva\\...` é um caminho perfeitamente normal.

    A interpolação ingénua do caminho para dentro do URI partia aqui: o espaço
    tem de ser percent-encoded, senão a query string `?mode=ro` deixa de ser
    interpretável.
    """
    pasta = tmp_path / "Jorge Silva" / "Os meus documentos"
    pasta.mkdir(parents=True)
    caminho = pasta / "dicionario.db"
    build_database(caminho, [_entrada()], [INFO], db_version="t")

    conn = open_readonly(caminho)
    assert conn.execute("SELECT lemma FROM lemmas").fetchone()["lemma"] == "janela"
    assert "%20" in readonly_uri(caminho)


def test_abre_db_com_acentos_no_caminho(tmp_path):
    pasta = tmp_path / "Área de Trabalho" / "dicionário"
    pasta.mkdir(parents=True)
    caminho = pasta / "dicionário.db"
    build_database(caminho, [_entrada()], [INFO], db_version="t")
    assert open_readonly(caminho).execute("SELECT COUNT(*) c FROM lemmas").fetchone()["c"] == 1


def test_db_continua_a_ser_so_leitura(tmp_path):
    caminho = tmp_path / "d.db"
    build_database(caminho, [_entrada()], [INFO], db_version="t")
    with pytest.raises(sqlite3.OperationalError):
        open_readonly(caminho).execute("INSERT INTO lemmas (lemma, normalized) VALUES ('x','x')")


def test_ficheiros_de_texto_sao_lidos_como_utf8(paths):
    """No Windows a codificação por omissão é cp1252, não UTF-8.

    Qualquer `open()` sem `encoding=` explícito leria as fontes na codificação
    do sistema e estragaria todos os acentos. Este teste percorre o código a
    garantir que isso não acontece em lado nenhum.
    """
    import ast
    from pathlib import Path

    # Abridores de arquivo: devolvem bytes e a descodificação é explícita a
    # seguir, por isso não levam `encoding` aqui.
    #
    # `zf.open` é o método de um `ZipFile` já aberto — `with zipfile.ZipFile(x)
    # as zf` é a convenção do projeto. Devolve um fluxo binário, tal como
    # `zipfile.open`, e quem o usa decodifica à mão. Sem esta entrada, todas
    # as fontes que leem de dentro de um zip apareciam como falso positivo.
    ARQUIVOS = {"tarfile.open", "zipfile.open", "zf.open", "tf.open"}

    def nome_completo(func) -> str:
        if isinstance(func, ast.Attribute):
            base = getattr(func.value, "id", None)
            return f"{base}.{func.attr}" if base else func.attr
        return getattr(func, "id", "")

    pacote = Path(__file__).resolve().parent.parent / "palavrame"
    faltas = []
    for ficheiro in sorted(pacote.rglob("*.py")):
        arvore = ast.parse(ficheiro.read_text(encoding="utf-8"))
        for no in ast.walk(arvore):
            if not isinstance(no, ast.Call):
                continue
            completo = nome_completo(no.func)
            if completo in ARQUIVOS:
                continue
            nome = completo.rsplit(".", 1)[-1]
            if nome not in {"open", "read_text", "write_text"}:
                continue
            # Modo binário não leva encoding, e com razão.
            modo = next(
                (a.value for a in no.args
                 if isinstance(a, ast.Constant) and isinstance(a.value, str)
                 and set(a.value) <= set("rwaxbt+")),
                "",
            )
            if "b" in modo:
                continue
            if not any(k.arg == "encoding" for k in no.keywords):
                faltas.append(f"{ficheiro.name}:{no.lineno}")
    assert not faltas, (
        "chamadas de ficheiro sem encoding explícito (partem em Windows): "
        + ", ".join(faltas)
    )


def test_ficheiros_com_terminadores_windows(tmp_path, monkeypatch):
    """Um .dic guardado com CRLF tem de dar as mesmas flexões que com LF."""
    from palavrame.affix import expand, read_aff, read_dic

    aff = "SET UTF-8\r\nSFX S Y 1\r\nSFX S 0 s [aeiou]\r\n"
    dic = "1\r\njanela/S\r\n"
    # write_bytes e não write_text(newline=""): preserva o CRLF tal e qual,
    # e o parâmetro `newline` de write_text só existe desde o Python 3.10 —
    # este teste tem de correr no 3.9 (ver requires-python no pyproject).
    (tmp_path / "pt.aff").write_bytes(aff.encode("utf-8"))
    (tmp_path / "pt.dic").write_bytes(dic.encode("utf-8"))

    tabela = read_aff(tmp_path / "pt.aff")
    palavras = list(read_dic(tmp_path / "pt.dic", tabela))
    assert palavras[0][0] == "janela"      # sem \r agarrado ao lema
    formas = {g.form for g in expand(*palavras[0][:2], tabela)}
    assert formas == {"janela", "janelas"}


def test_saida_aguenta_simbolos_fora_do_cp1252(capsys, monkeypatch):
    """Simula a consola do Windows: cp1252 com erros estritos.

    Sem a reconfiguração, imprimir `∅` ou `→` neste cenário rebentava e levava
    o relatório inteiro com ele.
    """
    import io
    import sys

    from palavrame.cli import _forcar_utf8_na_saida

    cp1252 = io.TextIOWrapper(io.BytesIO(), encoding="cp1252", errors="strict")
    monkeypatch.setattr(sys, "stdout", cp1252)

    with pytest.raises(UnicodeEncodeError):
        print("∅ → 🤖")
        sys.stdout.flush()

    _forcar_utf8_na_saida()
    print("∅ → 🤖")               # agora passa
    sys.stdout.flush()


def test_limpeza_tira_o_italico_de_1913():
    """O Dicionário Aberto marca itálicos com sublinhados.

    São marcação da fonte, não texto, e chegavam intactos ao ecrã da app em
    48 mil aceções — 13% do total. `De _autor_.` aparecia assim mesmo.
    """
    from palavrame.text import clean_definition

    assert clean_definition("De _autor_.") == "De autor."
    assert clean_definition("O mesmo que _hipogínio_.") == "O mesmo que hipogínio."
    assert clean_definition("Flexão feminina do pronome _o_.") == \
        "Flexão feminina do pronome o."
    # Um sublinhado sozinho, sem par, também sai.
    assert "_" not in clean_definition("Coisa _estranha")
