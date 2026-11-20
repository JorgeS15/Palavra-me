"""Lemas sem definição — a lista de trabalho da curadoria.

Algumas palavras entram na base porque uma fonte de lemas as reconhece (o
Hunspell, sobretudo), mas nenhuma fonte de aceções as define. Ficam com a
entrada vazia — *"Sem definição em nenhuma fonte"* no ecrã. São as candidatas a
curadoria (ver `seeds/curadoria.csv`).

Este módulo só faz a pergunta à DB já construída; o `palavrame lacunas` embrulha
a resposta num ficheiro pronto a preencher. Ordena-se por frequência para se
curar primeiro o que mais tropeça na leitura, não o que é raro.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path


def _e_palavra(lemma: str) -> bool:
    """Parece uma palavra (só letras, com quando muito hífen/apóstrofo)?"""
    nucleo = lemma.replace("-", "").replace("'", "")
    return bool(nucleo) and nucleo.isalpha()


def lemas_sem_definicao(conn: sqlite3.Connection) -> list[tuple[str, str | None, int | None]]:
    """(lema, classe, frequência) de cada lema sem nenhuma aceção.

    Duas exclusões, para a lista ser mesmo de trabalho e não de ruído:

    * **Nomes próprios** — um topónimo ou apelido sem definição é o esperado.
    * **Flexões de outra palavra** — sem autoridade de lemas, entram formas
      como lemas falsos (`esteja` de *estar*, `novos` de *novo*). Se a palavra
      também é forma de outro lema, não é uma lacuna a definir: é lixo a
      montante. Escreve-se-lhe uma definição seria um erro.

    Os mais frequentes vêm primeiro — é por aí que se cura.

    Feito em duas passagens em Python, não com um `NOT EXISTS` correlacionado:
    a coluna `forms.form` não tem índice, e cruzá-la com cada lema era O(n×m)
    sobre um milhão de formas. Assim é uma varredura só.
    """
    candidatos = conn.execute(
        """
        SELECT l.id, l.lemma, l.pos, l.frequency_rank
        FROM lemmas l
        LEFT JOIN senses s ON s.lemma_id = l.id
        WHERE s.id IS NULL
          AND (l.pos IS NULL OR l.pos NOT IN ('nome proprio', 'numeral'))
        """
    ).fetchall()
    # Fora o que nem sequer é uma palavra: numerais romanos (xix), datas
    # (1990), restos de citação (cap. XXIII). Uma palavra a curar é só letras
    # (e quando muito um hífen ou apóstrofo).
    por_lema = {
        lemma: (id_, pos, rank)
        for id_, lemma, pos, rank in candidatos
        if _e_palavra(lemma)
    }
    if not por_lema:
        return []

    # Uma passagem pelas formas: um candidato que seja flexão de OUTRO lema é
    # lema falso, não lacuna.
    e_flexao: set[str] = set()
    for form, lemma_id in conn.execute("SELECT form, lemma_id FROM forms"):
        alvo = por_lema.get(form)
        if alvo is not None and alvo[0] != lemma_id:
            e_flexao.add(form)

    resultado = [
        (lemma, pos, rank)
        for lemma, (id_, pos, rank) in por_lema.items()
        if lemma not in e_flexao
    ]
    resultado.sort(key=lambda t: (t[2] is None, t[2] if t[2] is not None else 0, t[0]))
    return resultado


def escrever_lista(db_path: Path, saida: Path) -> tuple[int, int, int]:
    """Escreve as lacunas para `saida`. Devolve (sem_def, total, com_frequência)."""
    conn = sqlite3.connect(db_path)
    try:
        linhas = lemas_sem_definicao(conn)
        total = conn.execute("SELECT COUNT(*) FROM lemmas").fetchone()[0]
    finally:
        conn.close()

    com_freq = sum(1 for _, _, rank in linhas if rank is not None)
    with saida.open("w", encoding="utf-8") as f:
        f.write(f"# {len(linhas)} lemas sem definição, de {total}.\n")
        f.write("# Ordenados por frequência: cura primeiro os de cima.\n")
        f.write("# Preenche a coluna da definição e cola no seeds/curadoria.csv.\n")
        f.write("lema,classe,definicao,nota\n")
        for lemma, pos, rank in linhas:
            nota = f"freq {rank}" if rank is not None else "sem frequência"
            f.write(f"{lemma},{pos or 'desconhecido'},,{nota}\n")
    return len(linhas), total, com_freq
