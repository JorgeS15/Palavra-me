"""Cache de ficheiros brutos, endereçado por conteúdo.

Este módulo e os de `sources/` são os únicos que abrem sockets. Tudo o resto
do pipeline lê do cache, o que dá três coisas:

* builds reprodutíveis — o lockfile fixa o sha256 de cada ficheiro de origem;
* builds offline — depois do primeiro download, `--offline` chega;
* revisão honesta de licenças — sabe-se exatamente que bytes entraram.
"""

from __future__ import annotations

import hashlib
import json
import shutil
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from .config import Paths

USER_AGENT = (
    "palavra-me-pipeline/0.1 (+https://github.com/JorgeS15/Palavra-me) "
    "construção de dicionário offline; contacto pelo repositório"
)

# Downloads podem falhar por rede. Não se insiste em 403/404 — isso é
# política ou URL errado, e insistir só irrita o servidor da outra pessoa.
RETRIES = 4
BACKOFF_SECONDS = (2, 4, 8, 16)


class OfflineError(RuntimeError):
    """Pedido de download em modo offline sem o ficheiro em cache."""


class DownloadError(RuntimeError):
    """Falha de download depois de esgotadas as tentativas."""


@dataclass
class CachedFile:
    path: Path
    sha256: str
    url: str
    bytes: int
    fetched_at: str

    def read_bytes(self) -> bytes:
        return self.path.read_bytes()

    def read_text(self, encoding: str = "utf-8") -> str:
        return self.path.read_text(encoding=encoding, errors="replace")


class Cache:
    """Ficheiros brutos em `pipeline/cache/<slug>/<nome>`, com lockfile."""

    def __init__(self, paths: Paths, offline: bool = False):
        self.paths = paths
        self.offline = offline
        self.paths.cache.mkdir(parents=True, exist_ok=True)
        self._lock = self._load_lock()

    # --- lockfile ---------------------------------------------------------

    def _load_lock(self) -> dict:
        if self.paths.cache_lock.exists():
            return json.loads(self.paths.cache_lock.read_text(encoding="utf-8"))
        return {}

    def _save_lock(self) -> None:
        self.paths.cache_lock.write_text(
            json.dumps(self._lock, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def lock_entries(self) -> dict:
        return dict(self._lock)

    # --- aquisição --------------------------------------------------------

    def local(self, source: str, name: str, src: Path) -> CachedFile:
        """Regista um ficheiro já existente em disco.

        Necessário para fontes cuja obtenção não é automatizável (o VOC exige
        interação com o site, alguns corpora exigem formulário). O ficheiro
        entra no cache e no lockfile como qualquer outro, para que a build
        continue a ser verificável.
        """
        dest = self._dest(source, name)
        dest.parent.mkdir(parents=True, exist_ok=True)
        if src.resolve() != dest.resolve():
            shutil.copyfile(src, dest)
        return self._record(f"file://{src.resolve()}", source, name, dest)

    def fetch(self, url: str, source: str, name: str | None = None) -> CachedFile:
        """Devolve o ficheiro em cache, descarregando-o se necessário."""
        name = name or url.rstrip("/").split("/")[-1] or "download"
        dest = self._dest(source, name)
        key = self._key(source, name)

        if dest.exists():
            entry = self._lock.get(key)
            if entry and entry.get("sha256") == _sha256_file(dest):
                return CachedFile(
                    dest, entry["sha256"], entry["url"], entry["bytes"],
                    entry["fetched_at"],
                )
            # Ficheiro presente mas fora do lockfile (ou alterado): re-regista
            # em vez de voltar a descarregar. O aviso fica no relatório.
            return self._record(url, source, name, dest)

        if self.offline:
            raise OfflineError(
                f"{source}: falta '{name}' no cache e o pipeline está em modo "
                f"offline. Corre sem --offline (precisa de acesso a {url})."
            )

        dest.parent.mkdir(parents=True, exist_ok=True)
        self._download(url, dest)
        return self._record(url, source, name, dest)

    def _download(self, url: str, dest: Path) -> None:
        tmp = dest.with_suffix(dest.suffix + ".part")
        last: Exception | None = None
        for attempt in range(RETRIES):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=120) as resp, \
                        open(tmp, "wb") as out:
                    shutil.copyfileobj(resp, out)
                tmp.replace(dest)
                return
            except urllib.error.HTTPError as exc:
                # 4xx não melhora com insistência.
                tmp.unlink(missing_ok=True)
                raise DownloadError(
                    f"HTTP {exc.code} em {url}. Verifica o URL em docs/fontes.md."
                ) from exc
            except Exception as exc:  # rede, DNS, TLS, timeout
                last = exc
                tmp.unlink(missing_ok=True)
                if attempt < RETRIES - 1:
                    time.sleep(BACKOFF_SECONDS[attempt])
        raise DownloadError(f"Falha ao descarregar {url}: {last}")

    # --- interno ----------------------------------------------------------

    def _dest(self, source: str, name: str) -> Path:
        return self.paths.cache / source / name

    @staticmethod
    def _key(source: str, name: str) -> str:
        return f"{source}/{name}"

    def _record(self, url: str, source: str, name: str, dest: Path) -> CachedFile:
        digest = _sha256_file(dest)
        entry = {
            "url": url,
            "sha256": digest,
            "bytes": dest.stat().st_size,
            "fetched_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }
        self._lock[self._key(source, name)] = entry
        self._save_lock()
        return CachedFile(dest, digest, url, entry["bytes"], entry["fetched_at"])


def _sha256_file(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        while block := fh.read(chunk):
            h.update(block)
    return h.hexdigest()
