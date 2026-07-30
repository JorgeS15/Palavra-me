from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

FIXTURES = Path(__file__).resolve().parent / "fixtures"


@pytest.fixture
def paths(tmp_path, monkeypatch):
    """Uma raiz de pipeline descartável com as fixtures já no cache.

    Copiar as fixtures para o cache é exatamente o que um `fetch` faria, o que
    significa que os testes exercitam o mesmo caminho de leitura que uma build
    a sério.
    """
    from palavrame.config import Paths

    root = tmp_path / "pipeline"
    root.mkdir()
    cache = root / "cache"
    for source_dir in FIXTURES.iterdir():
        if source_dir.is_dir():
            shutil.copytree(source_dir, cache / source_dir.name)

    shutil.copytree(
        Path(__file__).resolve().parent.parent / "seeds", root / "seeds"
    )
    monkeypatch.setenv("PALAVRAME_HOME", str(root))
    return Paths(root).ensure()


@pytest.fixture
def cache(paths):
    from palavrame.cache import Cache

    # offline=True: os testes nunca podem tocar na rede, mesmo por engano.
    return Cache(paths, offline=True)


@pytest.fixture
def seed_lemmas(paths):
    from palavrame.cli import _read_seeds

    return _read_seeds(paths, "lemas-f0.txt")
