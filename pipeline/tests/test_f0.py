"""O comando `f0` de ponta a ponta, sobre as fixtures.

Não testa a qualidade do dicionário — isso é o juízo humano da F0.3, e não se
automatiza. Testa que o circuito fecha: fontes, fusão, DB, validação,
relatórios, folha de revisão.
"""

from __future__ import annotations

from palavrame.build import open_readonly
from palavrame.cli import main


def test_f0_produz_db_e_relatorios(paths, capsys):
    codigo = main(["--offline", "f0", "--db-version", "teste"])
    saida = capsys.readouterr().out
    assert codigo == 0, saida

    db = paths.out / "dicionario-teste.db"
    assert db.exists()
    assert (db.with_suffix(".db.sha256")).exists()
    assert (paths.out / "relatorio-teste.md").exists()
    assert (paths.out / "relatorio-teste.json").exists()
    assert (paths.out / "revisao-teste.md").exists()
    assert (paths.work / "entries.jsonl").exists()


def test_f0_liga_flexao_a_lema_na_db(paths):
    main(["--offline", "f0", "--db-version", "teste"])
    conn = open_readonly(paths.out / "dicionario-teste.db")

    def procurar(escrito):
        return {
            row["lemma"]
            for row in conn.execute(
                "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
                " WHERE f.normalized = ?", (escrito,)
            )
        }

    # Os três casos que o plano nomeia.
    assert "caber" in procurar("couberam")
    assert "pôr" in procurar("pusesse")
    assert "ensonado" in procurar("ensonados")


def test_f0_junta_definicoes_de_varias_fontes(paths):
    main(["--offline", "f0", "--db-version", "teste"])
    conn = open_readonly(paths.out / "dicionario-teste.db")
    fontes = {
        row["name"]
        for row in conn.execute(
            "SELECT DISTINCT s.name FROM senses x JOIN sources s ON s.id = x.source_id"
            " JOIN lemmas l ON l.id = x.lemma_id WHERE l.lemma = 'janela'"
        )
    }
    assert len(fontes) >= 2       # Wikcionário moderno + Dicionário Aberto


def test_f0_atribui_exemplo_do_tatoeba(paths):
    main(["--offline", "f0", "--db-version", "teste"])
    conn = open_readonly(paths.out / "dicionario-teste.db")
    row = conn.execute(
        "SELECT e.sentence, e.source_ref FROM examples e"
        " JOIN lemmas l ON l.id = e.lemma_id"
        " JOIN sources s ON s.id = e.source_id"
        " WHERE l.lemma = 'janela' AND s.name LIKE 'Tatoeba%'"
    ).fetchone()
    assert row is not None
    assert row["source_ref"]       # sem referência não se cumpre a CC BY


def test_f0_regista_palavras_modernas_sem_definicao(paths, capsys):
    """O buraco de 1913 tem de ser visível, não escondido (plano 4.4)."""
    main(["--offline", "f0", "--db-version", "teste"])
    relatorio = (paths.out / "relatorio-teste.md").read_text(encoding="utf-8")
    assert "lemmas_without_sense" in relatorio


def test_folha_de_revisao_marca_o_que_e_gerado(paths):
    main(["--offline", "f0", "--db-version", "teste"])
    folha = (paths.out / "revisao-teste.md").read_text(encoding="utf-8")
    assert "útil para leitura" in folha
    assert "🤖" in folha           # a legenda do gerado está sempre lá
    assert "## 1." in folha


def test_validar_recusa_distribuicao_com_fontes_por_verificar(paths):
    main(["--offline", "f0", "--db-version", "teste"])
    db = paths.out / "dicionario-teste.db"
    assert main(["validar", "--db", str(db)]) == 0
    # Nenhuma fonte real está verificada ainda, portanto publicar tem de falhar.
    assert main(["validar", "--db", str(db), "--distribuicao"]) == 1


def test_comando_fontes_lista_o_que_falta_verificar(capsys):
    assert main(["fontes"]) == 0
    saida = capsys.readouterr().out
    assert "POR VERIFICAR" in saida
    assert "docs/fontes.md" in saida
