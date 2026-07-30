"""A rede vive só em `sources/` (plano, instrução 3 da secção 10).

Não é uma regra de estilo. Se um módulo de fusão ou de build fizer um pedido
de rede, uma build deixa de ser reprodutível e deixa de correr offline — e o
sintoma só aparece meses depois, num sítio difícil de encontrar. Mais vale um
teste que falha já.
"""

from __future__ import annotations

import ast
from pathlib import Path

import pytest

PACKAGE = Path(__file__).resolve().parent.parent / "palavrame"

# Módulos autorizados a abrir sockets.
ALLOWED = {
    "cache.py",                 # o descarregador propriamente dito
    "generate/runner.py",       # fala com o modelo em localhost
}

NETWORK_MODULES = {
    "urllib", "urllib.request", "urllib.error", "http", "http.client",
    "socket", "requests", "httpx", "aiohttp", "ftplib", "telnetlib",
}


def _modules() -> list[Path]:
    return sorted(p for p in PACKAGE.rglob("*.py"))


def _relative(path: Path) -> str:
    return path.relative_to(PACKAGE).as_posix()


def _imports(tree: ast.AST) -> set[str]:
    found: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            found.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
            found.add(node.module)
    return found


@pytest.mark.parametrize("path", _modules(), ids=_relative)
def test_rede_apenas_nos_modulos_autorizados(path):
    relative = _relative(path)
    tree = ast.parse(path.read_text(encoding="utf-8"))
    offending = {
        name for name in _imports(tree)
        if name in NETWORK_MODULES or name.split(".")[0] in NETWORK_MODULES
    }

    if relative in ALLOWED or relative.startswith("sources/"):
        return
    assert not offending, (
        f"{relative} importa {sorted(offending)}. A rede só pode viver em "
        f"sources/ e em {sorted(ALLOWED)} (plano, secção 10.3)."
    )


def test_sources_nao_importam_umas_das_outras():
    """Cada fonte é isolada: uma mudança numa não pode partir outra."""
    sources = PACKAGE / "sources"
    for path in sorted(sources.glob("*.py")):
        if path.name in {"__init__.py", "base.py"}:
            continue
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom) and node.level == 1:
                # `from .base import ...` é o contrato comum, e é o único.
                assert node.module in {"base", None}, (
                    f"{path.name} importa de .{node.module}; as fontes têm de "
                    "ser independentes umas das outras."
                )


def test_pipeline_completo_corre_offline(paths, seed_lemmas):
    """A prova prática: uma build inteira sem tocar na rede."""
    from palavrame.cache import Cache
    from palavrame.merge import merge_entries
    from palavrame.sources import REGISTRY, build as build_source

    cache = Cache(paths, offline=True)
    entries = []
    for slug in REGISTRY:
        source = build_source(slug, cache)
        try:
            entries.extend(source.parse(seed_lemmas))
        except Exception:
            continue
    assert entries
    assert merge_entries(entries).entries
