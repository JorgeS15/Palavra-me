"""Localizações e definições globais do pipeline.

Tudo o que é caminho de ficheiro passa por aqui, para que uma build seja
descritível por inteiro a partir de um objeto `Paths`.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

# pipeline/palavrame/config.py -> pipeline/
PIPELINE_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = PIPELINE_ROOT.parent


@dataclass(frozen=True)
class Paths:
    """Todos os diretórios que o pipeline usa."""

    root: Path

    @property
    def cache(self) -> Path:
        """Ficheiros brutos descarregados, endereçados por hash."""
        return self.root / "cache"

    @property
    def cache_lock(self) -> Path:
        """Registo url -> sha256 que torna a build reprodutível."""
        return self.root / "cache" / "sources.lock.json"

    @property
    def work(self) -> Path:
        """Intermediários: normalizado, fundido, gerado."""
        return self.root / "work"

    @property
    def out(self) -> Path:
        """Bases de dados e relatórios finais."""
        return self.root / "out"

    @property
    def seeds(self) -> Path:
        return self.root / "seeds"

    def ensure(self) -> "Paths":
        for d in (self.cache, self.work, self.out):
            d.mkdir(parents=True, exist_ok=True)
        return self


def default_paths() -> Paths:
    """Raiz do pipeline, sobreponível por `PALAVRAME_HOME` (útil nos testes)."""
    override = os.environ.get("PALAVRAME_HOME")
    return Paths(Path(override).resolve() if override else PIPELINE_ROOT)


# --- Parâmetros de conteúdo ------------------------------------------------

# Comprimento aceitável de uma frase de exemplo, em caracteres. Fora disto a
# frase é rejeitada: demasiado curta não demonstra nada, demasiado longa não
# se lê num ecrã de telemóvel.
EXAMPLE_MIN_CHARS = 20
EXAMPLE_MAX_CHARS = 140

# Quantos exemplos guardar por aceção. Mais do que isto só engorda a DB.
MAX_EXAMPLES_PER_SENSE = 3

# Ordem de preferência das fontes de exemplos (secção 4.3 do plano).
EXAMPLE_SOURCE_PRIORITY = ("tatoeba", "leipzig", "amalia")

# Ordem de preferência das fontes de aceções (secção 5.2 do plano):
# o Wikcionário é moderno, o Dicionário Aberto é 1913 mas cobre mais.
SENSE_SOURCE_PRIORITY = ("wikcionario", "dicionario_aberto")
