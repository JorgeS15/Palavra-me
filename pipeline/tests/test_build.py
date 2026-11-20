"""Testes da escrita e validação do `dicionario.db`."""

from __future__ import annotations

import sqlite3

import pytest

from palavrame.build import build_database, open_readonly
from palavrame.schema import Example, Form, MergedEntry, Relation, Sense
from palavrame.sources.base import License, SourceInfo
from palavrame.text import normalize
from palavrame.validate import validate_database

INFO_LIVRE = SourceInfo(
    slug="fonte_livre", name="Fonte Livre", url="https://exemplo.pt/",
    license=License(name="Domínio público", attribution="Fonte Livre.",
                    redistributable=True, verified=True),
)
INFO_POR_VERIFICAR = SourceInfo(
    slug="fonte_duvidosa", name="Fonte Duvidosa", url="https://exemplo.pt/",
    license=License(name="POR VERIFICAR", attribution="Fonte Duvidosa.",
                    redistributable=None, verified=False),
)
INFO_PROIBIDA = SourceInfo(
    slug="fonte_proibida", name="Fonte Proibida", url="https://exemplo.pt/",
    license=License(name="CC BY-NC", attribution="Fonte Proibida.",
                    redistributable=False, verified=True),
)


def entrada_caber():
    return MergedEntry(
        lemma="caber", pos="verbo",
        senses=[Sense("Poder conter-se em certo espaço.", "fonte_livre", ord=1)],
        forms=[Form("couberam", "pretérito"), Form("cabemos")],
        examples=[Example("Não couberam todos no carro.", "fonte_livre", "1",
                          variant="pt-PT", sense_ord=1)],
        relations=[Relation("janela", "sinonimo")],
        frequency_rank=120, contributors=["fonte_livre"],
    )


def entrada_janela():
    return MergedEntry(
        lemma="janela", pos="substantivo",
        senses=[Sense("Abertura na parede.", "fonte_livre", ord=1)],
        forms=[Form("janelas", "plural")],
        contributors=["fonte_livre"],
    )


@pytest.fixture
def db(tmp_path):
    path = tmp_path / "dicionario-teste.db"
    stats = build_database(
        path, [entrada_caber(), entrada_janela()], [INFO_LIVRE],
        db_version="teste",
    )
    return path, stats


def test_esquema_tem_todas_as_tabelas_do_plano(db):
    path, _ = db
    conn = open_readonly(path)
    tabelas = {
        row["name"]
        for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")
    }
    assert {"lemmas", "senses", "forms", "examples", "synonyms",
            "sources", "meta"} <= tabelas


def test_db_abre_em_so_leitura(db):
    path, _ = db
    conn = open_readonly(path)
    with pytest.raises(sqlite3.OperationalError):
        conn.execute("INSERT INTO lemmas (lemma, normalized) VALUES ('x','x')")


def test_o_lema_esta_sempre_na_tabela_forms(db):
    """A app procura só em `forms`. Se o lema não lá estiver, não se encontra."""
    path, _ = db
    conn = open_readonly(path)
    for lemma in ("caber", "janela"):
        row = conn.execute(
            "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
            " WHERE f.normalized = ?", (normalize(lemma),)
        ).fetchone()
        assert row and row["lemma"] == lemma


def test_pesquisa_por_flexao(db):
    path, _ = db
    conn = open_readonly(path)
    row = conn.execute(
        "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
        " WHERE f.normalized = ?", ("couberam",)
    ).fetchone()
    assert row["lemma"] == "caber"


def test_fts_encontra_sem_acentos(db):
    path, _ = db
    conn = open_readonly(path)
    rows = conn.execute(
        "SELECT lemma FROM lemmas_fts WHERE lemmas_fts MATCH ?", ("janela",)
    ).fetchall()
    assert [r["lemma"] for r in rows] == ["janela"]


def test_meta_tem_versao_e_data(db):
    path, stats = db
    conn = open_readonly(path)
    meta = {r["key"]: r["value"] for r in conn.execute("SELECT key, value FROM meta")}
    assert meta["db_version"] == "teste"
    assert meta["schema_version"] == "1"
    assert meta["built_at"].endswith("Z")
    assert meta["count_lemmas"] == "2"


def test_checksum_gravado_ao_lado(db):
    path, stats = db
    sidecar = path.with_suffix(path.suffix + ".sha256")
    assert sidecar.exists()
    assert stats["sha256"] in sidecar.read_text(encoding="utf-8")


def test_relacao_para_lema_ausente_e_ignorada(tmp_path):
    entrada = entrada_caber()
    entrada.relations = [Relation("inexistente", "sinonimo")]
    path = tmp_path / "d.db"
    build_database(path, [entrada], [INFO_LIVRE], db_version="t")
    conn = open_readonly(path)
    assert conn.execute("SELECT COUNT(*) c FROM synonyms").fetchone()["c"] == 0


def test_exemplo_gerado_fica_marcado(tmp_path):
    entrada = entrada_caber()
    entrada.examples = [
        Example("Frase inventada pelo modelo.", "fonte_livre", "amalia",
                generated=True, sense_ord=1)
    ]
    path = tmp_path / "d.db"
    stats = build_database(path, [entrada], [INFO_LIVRE], db_version="t")
    assert stats["generated_examples"] == 1
    conn = open_readonly(path)
    assert conn.execute("SELECT generated g FROM examples").fetchone()["g"] == 1


# --- validação -------------------------------------------------------------


def test_validacao_aprova_db_saudavel(db):
    path, _ = db
    report = validate_database(path, [INFO_LIVRE],
                               probes=[("couberam", "caber")])
    assert report.ok, report.render()


def test_validacao_apanha_sonda_falhada(db):
    path, _ = db
    report = validate_database(path, [INFO_LIVRE],
                               probes=[("pusesse", "pôr")])
    assert not report.ok
    assert any("pesquisa por flexão" in c.name for c in report.errors)


def test_licenca_por_verificar_bloqueia_distribuicao(tmp_path):
    entrada = entrada_caber()
    entrada.senses = [Sense("Definição qualquer.", "fonte_duvidosa", ord=1)]
    entrada.examples = []
    path = tmp_path / "d.db"
    build_database(path, [entrada], [INFO_POR_VERIFICAR], db_version="t")

    local = validate_database(path, [INFO_POR_VERIFICAR], distribution=False)
    assert local.ok                     # uso local: passa com aviso
    assert local.warnings

    publica = validate_database(path, [INFO_POR_VERIFICAR], distribution=True)
    assert not publica.ok               # publicar: bloqueia
    assert any("licenças por verificar" in c.name for c in publica.errors)


def test_fonte_nao_redistribuivel_bloqueia_sempre(tmp_path):
    entrada = entrada_caber()
    entrada.senses = [Sense("Definição qualquer.", "fonte_proibida", ord=1)]
    entrada.examples = []
    path = tmp_path / "d.db"
    build_database(path, [entrada], [INFO_PROIBIDA], db_version="t")
    report = validate_database(path, [INFO_PROIBIDA], distribution=False)
    assert not report.ok
    assert any("não redistribuíveis" in c.message for c in report.errors)


def test_a_rede_de_seguranca_nao_aparece_na_tabela_de_fontes(tmp_path):
    """"Fonte não declarada" não pode chegar ao ecrã do utilizador.

    O builder cria essa linha para servir de destino a conteúdo que chegue sem
    fonte registada — é o que evita uma violação de chave estrangeira a meio
    de uma build de horas. Mas ficava na tabela mesmo sem nada apontar para
    ela, e o ecrã "Fontes e licenças" mostrava ao leitor uma fonte com licença
    "DESCONHECIDA" que não contribuiu com uma única palavra.
    """
    path = tmp_path / "d.db"
    build_database(path, [entrada_janela()], [INFO_LIVRE], db_version="t")
    conn = open_readonly(path)
    fontes = [r["name"] for r in conn.execute("SELECT name FROM sources")]
    assert "Fonte não declarada" not in fontes
    assert fontes == [INFO_LIVRE.name]


def test_a_rede_de_seguranca_fica_quando_e_precisa(tmp_path):
    """Se for usada, tem de ficar — e o validador tem de reprovar a base."""
    entrada = entrada_janela()
    entrada.senses = [Sense("Sem fonte nenhuma.", "fonte_que_nao_existe", ord=1)]
    path = tmp_path / "d.db"
    build_database(path, [entrada], [INFO_LIVRE], db_version="t")

    conn = open_readonly(path)
    assert "Fonte não declarada" in [
        r["name"] for r in conn.execute("SELECT name FROM sources")
    ]
    report = validate_database(path, [INFO_LIVRE])
    assert not report.ok
    assert any("proveniência" in c.name for c in report.errors)


def test_forms_vazia_e_erro(tmp_path):
    """Sem `forms`, a app não encontra nada a partir do texto do livro."""
    entrada = entrada_janela()
    entrada.forms = []
    path = tmp_path / "d.db"
    build_database(path, [entrada], [INFO_LIVRE], db_version="t")
    # O builder acrescenta sempre o lema; a proporção fica em 1.0 -> aviso.
    report = validate_database(path, [INFO_LIVRE])
    assert any(c.name == "forms" for c in report.warnings)


def test_copia_para_a_app_tira_o_que_a_app_nao_usa(tmp_path):
    """O `empacotar` emagrece a cópia que vai no APK: sem `forms.tag`, sem o
    índice de lema para formas, sem o FTS, e `forms` sem rowid nem repetidas. A pesquisa da app (formas por
    `normalized`) tem de continuar a funcionar, e a base original não muda."""
    from palavrame.cli import _emagrecer

    original = tmp_path / "d.db"
    build_database(original, [entrada_caber(), entrada_janela()], [INFO_LIVRE], db_version="t")
    tamanho_original = original.stat().st_size

    copia = _emagrecer(original)
    try:
        conn = sqlite3.connect(copia)
        colunas = [r[1] for r in conn.execute("PRAGMA table_info(forms)")]
        assert colunas == ["normalized", "lemma_id", "form"]
        # WITHOUT ROWID: a chave é o índice da pesquisa, sem outro ao lado.
        indices = {r[1] for r in conn.execute("PRAGMA index_list(forms)")}
        assert len(indices) == 1 and all(i.startswith("sqlite_autoindex") for i in indices)
        plano = " ".join(r[3] for r in conn.execute(
            "EXPLAIN QUERY PLAN SELECT lemma_id FROM forms WHERE normalized = ?", ("x",)))
        assert "PRIMARY KEY" in plano, plano
        # A forma só fica escrita quando difere da chave normalizada; a app
        # usa COALESCE.
        formas = dict(conn.execute(
            "SELECT normalized, COALESCE(form, normalized) FROM forms"
            " WHERE normalized IN ('couberam', 'caber')"))
        assert formas == {"couberam": "couberam", "caber": "caber"}
        assert conn.execute(
            "SELECT COUNT(*) FROM forms WHERE form = normalized").fetchone()[0] == 0
        total = conn.execute("SELECT COUNT(*) FROM forms").fetchone()[0]
        meta = conn.execute("SELECT value FROM meta WHERE key='count_forms'").fetchone()[0]
        assert int(meta) == total
        tabelas = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        assert "lemmas_fts" not in tabelas
        lemas = [r[0] for r in conn.execute(
            "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
            " WHERE f.normalized = ?", ("couberam",))]
        assert lemas == ["caber"]
        assert conn.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        conn.close()
    finally:
        copia.unlink(missing_ok=True)

    assert original.stat().st_size == tamanho_original   # a original fica intacta


def test_a_classe_de_cada_acecao_vai_para_a_base_e_lemma_tem_indice(tmp_path):
    """A coluna `senses.pos` (1.21) e o índice em `lemmas(lemma)`, que a app
    usa para procurar pela grafia exata sem percorrer a tabela."""
    e = MergedEntry(
        lemma="computador", pos="substantivo",
        senses=[Sense("Que computa.", "fonte_livre", ord=1, pos="adjetivo"),
                Sense("Máquina de calcular.", "fonte_livre", ord=2, pos="substantivo")],
        contributors=["fonte_livre"],
    )
    caminho = tmp_path / "d.db"
    build_database(caminho, [e], [INFO_LIVRE], db_version="t")
    with sqlite3.connect(caminho) as conn:
        classes = [r[0] for r in conn.execute("SELECT pos FROM senses ORDER BY ord")]
        assert classes == ["adjetivo", "substantivo"]
        plano = " ".join(r[3] for r in conn.execute(
            "EXPLAIN QUERY PLAN SELECT id FROM lemmas WHERE lemma = ?", ("computador",)))
        assert "SCAN" not in plano, plano
