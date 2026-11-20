"""O relatório de lemas sem definição (a lista de trabalho da curadoria)."""

from __future__ import annotations

import sqlite3

from palavrame.lacunas import lemas_sem_definicao


def _base() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.executescript(
        """
        CREATE TABLE lemmas (id INTEGER PRIMARY KEY, lemma TEXT, pos TEXT,
                             frequency_rank INTEGER);
        CREATE TABLE senses (id INTEGER PRIMARY KEY, lemma_id INTEGER);
        CREATE TABLE forms (form TEXT, lemma_id INTEGER);
        """
    )
    # com definição
    conn.execute("INSERT INTO lemmas VALUES (1,'janela','substantivo',10)")
    conn.execute("INSERT INTO senses VALUES (1,1)")
    # sem definição, com frequência
    conn.execute("INSERT INTO lemmas VALUES (2,'acutilância','substantivo',500)")
    # sem definição, sem frequência
    conn.execute("INSERT INTO lemmas VALUES (3,'sintónico','adjetivo',NULL)")
    # sem definição mas nome próprio: não conta
    conn.execute("INSERT INTO lemmas VALUES (4,'Serralves','nome proprio',NULL)")
    # sem definição mas é FLEXÃO de outro lema (esteja, de estar): lixo, não conta
    conn.execute("INSERT INTO lemmas VALUES (5,'estar','verbo',5)")
    conn.execute("INSERT INTO senses VALUES (2,5)")          # estar tem definição
    conn.execute("INSERT INTO lemmas VALUES (6,'esteja','desconhecido',20)")
    conn.execute("INSERT INTO forms VALUES ('esteja',5)")   # esteja -> estar
    conn.commit()
    return conn


def test_lista_so_os_sem_definicao_fora_nomes_proprios_e_flexoes():
    conn = _base()
    lemas = [l for l, _, _ in lemas_sem_definicao(conn)]
    # janela tem definição; Serralves é próprio; 'esteja' é flexão de 'estar'.
    assert lemas == ["acutilância", "sintónico"]


def test_ordena_por_frequencia_e_depois_os_sem_frequencia():
    conn = _base()
    lemas = [l for l, _, _ in lemas_sem_definicao(conn)]
    # a de frequência conhecida vem antes da que não tem.
    assert lemas.index("acutilância") < lemas.index("sintónico")
