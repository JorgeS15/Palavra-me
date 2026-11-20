"""Curadoria de origem — marcar à mão a variante ou registo de uma palavra.

O Wikcionário só etiqueta ~1,4% das aceções, e no Brasil o vocabulário é o
padrão NÃO marcado: `time` (equipa) e `nené` chegam sem etiqueta, e nenhuma
heurística os distingue com segurança de português europeu. Aqui uma pessoa
diz, palavra a palavra, que aquilo é brasileiro (ou estrangeirismo, ou o que
for), e o rótulo passa a aparecer em todas as aceções do lema — no mesmo sítio
onde a app já mostra "Figurado" ou "Náutica".

É irmã da `curadoria`: vive em `seeds/origem.csv`, versionada com o projeto, e
lê-se no build. A diferença é que a curadoria acrescenta aceções e esta apenas
as rotula. Formato, UTF-8 com cabeçalho::

    lema,origem,nota
    time,Brasil,
    nené,Brasil,

`origem` é o rótulo tal como aparece no ecrã. Uma pessoa decidiu — por isso
vale mais do que qualquer etiqueta em falta na fonte.
"""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Iterable

from .text import normalize

FICHEIRO = "origem.csv"


def carregar(seeds_dir: Path) -> dict[str, str]:
    """Lê `seeds/origem.csv` para um mapa {forma normalizada do lema: rótulo}."""
    caminho = seeds_dir / FICHEIRO
    mapa: dict[str, str] = {}
    if not caminho.exists():
        return mapa
    with caminho.open(encoding="utf-8", newline="") as f:
        for linha in csv.DictReader(f):
            lema = (linha.get("lema") or "").strip()
            origem = (linha.get("origem") or "").strip()
            if not lema or not origem or lema.startswith("#"):
                continue
            mapa[normalize(lema)] = origem
    return mapa


def aplicar(entries: Iterable, mapa: dict[str, str]) -> int:
    """Põe o rótulo de origem à frente dos domínios de cada aceção do lema.

    Devolve o número de aceções marcadas. Idempotente: correr duas vezes não
    duplica o rótulo.
    """
    if not mapa:
        return 0
    marcadas = 0
    for entry in entries:
        rotulo = mapa.get(entry.normalized)
        if not rotulo:
            continue
        for sense in entry.senses:
            if rotulo not in sense.domains:
                sense.domains.insert(0, rotulo)
                marcadas += 1
    return marcadas
