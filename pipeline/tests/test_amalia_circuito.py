"""O circuito completo do conteúdo gerado: gerar, rever, entrar na DB.

O que interessa provar aqui é a regra 10.8 do plano — *toda a saída de LLM é
marcada como gerada, na base de dados e na interface, sem exceções* — e o seu
corolário menos óbvio: o que **não** foi aprovado por um humano não entra.
"""

from __future__ import annotations

from palavrame.build import open_readonly
from palavrame.cli import main
from palavrame.generate.runner import Candidate, save_candidates
from palavrame.generate.source import approved_entries


def _candidatos():
    return [
        Candidate(
            lemma="ensonado", pos="adjetivo", sense_ord=1,
            definition="Que tem sono.",
            sentence="O miúdo ensonado arrastou-se até à cozinha.",
            model="amalia", attempts=1, status="aprovado",
        ),
        Candidate(
            lemma="ensonado", pos="adjetivo", sense_ord=1,
            definition="Que tem sono.",
            sentence="Frase que passou a validação mas ninguém viu ainda.",
            model="amalia", attempts=1, status="pendente",
        ),
        Candidate(
            lemma="ensonado", pos="adjetivo", sense_ord=1,
            definition="Que tem sono.",
            sentence="O ensonado sonhou longamente ao almoço.",
            model="amalia", attempts=3, status="rejeitado",
        ),
    ]


def test_so_os_aprovados_saem_da_revisao(paths):
    caminho = paths.work / "candidatos-amalia.jsonl"
    save_candidates(_candidatos(), caminho)

    entradas = approved_entries(caminho)
    assert len(entradas) == 1
    exemplos = entradas[0].examples
    assert len(exemplos) == 1                 # pendente e rejeitado ficam fora
    assert exemplos[0].generated is True
    assert exemplos[0].source == "amalia"


def test_aprovados_chegam_a_db_marcados(paths):
    save_candidates(_candidatos(), paths.work / "candidatos-amalia.jsonl")
    assert main(["--offline", "f0", "--db-version", "teste"]) == 0

    conn = open_readonly(paths.out / "dicionario-teste.db")
    linhas = conn.execute(
        "SELECT e.sentence, e.generated, s.name FROM examples e"
        " JOIN sources s ON s.id = e.source_id"
        " JOIN lemmas l ON l.id = e.lemma_id"
        " WHERE l.lemma = 'ensonado'"
    ).fetchall()
    assert len(linhas) == 1
    assert linhas[0]["generated"] == 1
    assert "AMALIA" in linhas[0]["name"]


def test_sem_revisao_nada_gerado_entra(paths):
    """Sem ficheiro de candidatos, a DB não tem uma única frase gerada."""
    assert main(["--offline", "f0", "--db-version", "teste"]) == 0
    conn = open_readonly(paths.out / "dicionario-teste.db")
    total = conn.execute(
        "SELECT COUNT(*) c FROM examples WHERE generated = 1"
    ).fetchone()["c"]
    assert total == 0


def test_folha_de_revisao_assinala_a_frase_gerada(paths):
    save_candidates(_candidatos(), paths.work / "candidatos-amalia.jsonl")
    main(["--offline", "f0", "--db-version", "teste"])
    folha = (paths.out / "revisao-teste.md").read_text(encoding="utf-8")
    assert "🤖 O miúdo ensonado arrastou-se" in folha
