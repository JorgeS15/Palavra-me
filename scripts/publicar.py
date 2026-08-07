"""Publica uma versão no GitHub: etiqueta, notas e APK.

Porque é que isto é local e não uma GitHub Action
-------------------------------------------------
A Action não consegue compilar a app: precisaria do dicionário de 60 MB, que
não é versionado, e da chave de assinatura, que nunca deve estar num
repositório. Compilar fica na máquina de quem publica. O que se automatiza é
o resto — e é o resto que se faz mal quando é manual: esquecer o APK,
esquecer a etiqueta, colar as notas da versão errada.

Uso:

    python scripts/publicar.py            # mostra o que faria
    python scripts/publicar.py --a-serio  # publica

Precisa do GitHub CLI (`gh`) autenticado: https://cli.github.com
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
GRADLE = RAIZ / "android" / "app" / "build.gradle.kts"
CHANGELOG = RAIZ / "CHANGELOG.md"
# Dois sítios, porque as duas formas de compilar escrevem em sítios
# diferentes: o `gradlew assembleRelease` em build/outputs, e o assistente
# "Generate Signed APK" do Android Studio em app/release. Procura-se o mais
# recente dos que existirem.
LOCAIS_DO_APK = (
    RAIZ / "android" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk",
    RAIZ / "android" / "app" / "release" / "app-release.apk",
)


def versao_da_app() -> tuple[str, int]:
    texto = GRADLE.read_text(encoding="utf-8")
    nome = re.search(r'versionName\s*=\s*"([^"]+)"', texto)
    codigo = re.search(r"versionCode\s*=\s*(\d+)", texto)
    if not nome or not codigo:
        sair("Não encontrei versionName/versionCode em android/app/build.gradle.kts.")
    return nome.group(1), int(codigo.group(1))


def notas(versao: str) -> str:
    """A secção do CHANGELOG que menciona esta versão da app.

    Procura pelo marcador `— app vX.Y.Z` no cabeçalho. Se não existir, é
    porque o CHANGELOG não foi escrito para esta versão — e publicar sem
    notas é publicar às cegas, portanto pára.
    """
    texto = CHANGELOG.read_text(encoding="utf-8")
    seccoes = re.split(r"\n(?=## )", texto)
    for seccao in seccoes:
        cabecalho = seccao.splitlines()[0]
        if cabecalho.startswith("## ") and f"app v{versao}" in cabecalho:
            corpo = seccao.split("\n", 1)[1].strip()
            return corpo
    sair(
        f"O CHANGELOG.md não tem nenhuma secção marcada com 'app v{versao}'.\n"
        f"  Escreve a entrada antes de publicar — o cabeçalho deve terminar\n"
        f"  em '— app v{versao}'."
    )
    return ""


def encontrar_apk() -> Path:
    existentes = [p for p in LOCAIS_DO_APK if p.exists()]
    if not existentes:
        sair(
            "Não encontrei o APK assinado. Procurei em:\n    "
            + "\n    ".join(str(p) for p in LOCAIS_DO_APK)
            + "\n\n  Compila primeiro:  cd android && .\\gradlew assembleRelease"
        )
    return max(existentes, key=lambda p: p.stat().st_mtime)


def conferir_apk() -> Path:
    apk = encontrar_apk()
    with zipfile.ZipFile(apk) as z:
        assets = [n for n in z.namelist() if n.startswith("assets/")]
        tem = any(n in ("assets/dicionario.db", "assets/dicionario.db.gz") for n in assets)
    if not tem:
        sair(
            "O APK não traz o dicionário. Assets encontrados:\n    "
            + "\n    ".join(assets or ["(nenhum)"])
        )
    print(f"  APK: {apk} ({apk.stat().st_size / 1048576:.0f} MB), com dicionário")
    return apk


def git(*args: str) -> str:
    r = subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True)
    if r.returncode != 0:
        sair(f"git {' '.join(args)} falhou:\n{r.stderr.strip()}")
    return r.stdout.strip()


def sair(mensagem: str) -> None:
    print(f"\n  {mensagem}\n", file=sys.stderr)
    raise SystemExit(2)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--a-serio", action="store_true",
                   help="publica mesmo; sem isto só mostra o que faria")
    args = p.parse_args()

    versao, codigo = versao_da_app()
    etiqueta = f"v{versao}"
    print(f"\n  Versão: {etiqueta} (versionCode {codigo})")

    corpo = notas(versao)
    apk = conferir_apk()

    sujo = git("status", "--porcelain")
    if sujo:
        sair("Há alterações por commitar. Faz commit antes de publicar:\n    "
             + "\n    ".join(sujo.splitlines()[:10]))

    if git("tag", "-l", etiqueta):
        sair(f"A etiqueta {etiqueta} já existe. Sobe a versão antes de publicar.")

    print("\n  --- notas da versão ---")
    print("\n".join("  " + l for l in corpo.splitlines()[:12]))
    if len(corpo.splitlines()) > 12:
        print("  …")

    if not args.a_serio:
        print("\n  Ensaio. Para publicar mesmo: python scripts/publicar.py --a-serio\n")
        return 0

    git("tag", "-a", etiqueta, "-m", etiqueta)
    git("push", "origin", "HEAD", "--tags")

    r = subprocess.run(
        ["gh", "release", "create", etiqueta, str(apk),
         "--title", f"Palavra-me {etiqueta}", "--notes", corpo],
        cwd=RAIZ,
    )
    if r.returncode != 0:
        sair("O `gh release create` falhou. O `gh` está instalado e autenticado?")

    print(f"\n  Publicado: {etiqueta}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
