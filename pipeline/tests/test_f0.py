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
    """A rede de segurança das licenças continua armada.

    Desde 2026-07-30 as fontes da F0 estão verificadas (docs/fontes.md),
    portanto a DB de fixtures passa o modo distribuição — e isso também se
    afirma aqui. Mas a recusa tem de continuar a funcionar para uma fonte
    por verificar, e isso testa-se dando ao validador um SourceInfo com
    `verified=False` para uma fonte usada na DB.
    """
    import dataclasses

    from palavrame.sources import all_infos
    from palavrame.validate.checks import validate_database

    main(["--offline", "f0", "--db-version", "teste"])
    db = paths.out / "dicionario-teste.db"
    assert main(["validar", "--db", str(db)]) == 0
    # Fontes verificadas: publicar passa.
    assert main(["validar", "--db", str(db), "--distribuicao"]) == 0

    # A mesma DB com uma licença regredida para não-verificada: publicar falha.
    infos = []
    for info in all_infos():
        if info.slug == "dicionario_aberto":
            info = dataclasses.replace(
                info, license=dataclasses.replace(info.license, verified=False)
            )
        infos.append(info)
    report = validate_database(db, infos, distribution=True)
    assert any(c.blocking for c in report.checks), (
        "uma fonte por verificar tem de bloquear a distribuição"
    )


def test_comando_fontes_lista_o_que_falta_verificar(capsys):
    assert main(["fontes"]) == 0
    saida = capsys.readouterr().out
    assert "POR VERIFICAR" in saida
    assert "docs/fontes.md" in saida


def test_f1_constroi_sobre_as_fixtures_sem_seeds(paths, capsys):
    """O `f1` fecha o circuito completo sem lista de lemas.

    Sobre as fixtures o universo é pequeno, mas o caminho é o real: fontes
    de lemas primeiro, exemplos indexados sobre o universo, fusão, DB.
    """
    codigo = main(["--offline", "f1", "--db-version", "t1"])
    saida = capsys.readouterr().out
    assert codigo == 0, saida
    assert "universo:" in saida
    db = paths.out / "dicionario-t1.db"
    assert db.exists()

    conn = open_readonly(db)
    lemmas = {r[0] for r in conn.execute("SELECT lemma FROM lemmas")}
    # Palavras que só as fixtures de fontes de lemas conhecem.
    assert "janela" in lemmas and "caber" in lemmas
    # A sonda de flexão continua a funcionar no caminho F1.
    row = conn.execute(
        "SELECT l.lemma FROM forms f JOIN lemmas l ON l.id = f.lemma_id"
        " WHERE f.normalized = 'couberam'"
    ).fetchone()
    assert row and row[0] == "caber"
