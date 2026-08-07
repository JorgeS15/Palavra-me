"""Leitura de dumps do mysqldump — partilhada por mais de uma fonte.

Vive fora de `sources/` porque duas fontes precisam dela (o Dicionário
Aberto e o PULO publicam ambos mysqldump) e o contrato do projeto diz
que as fontes não se importam umas às outras.
"""

from __future__ import annotations

import re
from typing import Iterator

# O dump real escreve `INSERT  IGNORE INTO` (com IGNORE e dois espaços);
# aceitar só `INSERT INTO` foi o erro que deixou a primeira F1 sem o dump.
def insert_re(*tabelas: str) -> "re.Pattern[str]":
    """Compila o reconhecedor de INSERT para as tabelas dadas."""
    nomes = "|".join(re.escape(t) for t in tabelas)
    return re.compile(rf"^INSERT\s+(?:IGNORE\s+)?INTO\s+`({nomes})`\s+VALUES")


_MYSQL_ESCAPES = {
    "0": "\0", "n": "\n", "r": "\r", "t": "\t", "Z": "\x1a",
    "'": "'", '"': '"', "\\": "\\", "%": "%", "_": "_",
}


def sql_values(line: str) -> Iterator[list]:
    """Itera as linhas (tuplos) de um `INSERT ... VALUES (...),(...);`.

    Um tokenizador pequeno e explícito em vez de regex: os campos de texto
    do mysqldump contêm aspas, parênteses e vírgulas escapados com barra
    invertida, e uma regex que sobreviva a isso é ilegível.
    """
    idx = line.find("VALUES")
    if idx < 0:
        return
    i, n = idx + len("VALUES"), len(line)
    row: list = []
    field: list[str] = []
    state = "fora"          # fora | tuplo | texto
    while i < n:
        ch = line[i]
        if state == "fora":
            if ch == "(":
                row, state = [], "tuplo"
        elif state == "tuplo":
            if ch == "'":
                field, state = [], "texto"
            elif ch == ",":
                pass
            elif ch == ")":
                yield row
                state = "fora"
            elif not ch.isspace():
                # valor não-textual (número, NULL): lê até , ou )
                j = i
                while j < n and line[j] not in ",)":
                    j += 1
                token = line[i:j].strip()
                row.append(None if token.upper() == "NULL" else token)
                i = j
                continue
        elif state == "texto":
            if ch == "\\" and i + 1 < n:
                field.append(_MYSQL_ESCAPES.get(line[i + 1], line[i + 1]))
                i += 2
                continue
            if ch == "'":
                row.append("".join(field))
                state = "tuplo"
            else:
                field.append(ch)
        i += 1


def open_dump(path) -> "object":
    import lzma

    if path.suffix == ".xz":
        return lzma.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


