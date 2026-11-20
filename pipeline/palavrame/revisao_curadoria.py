"""Revisão interativa das definições curadas (as marcadas "rascunho — rever").

À imagem de `review.py` (que revê os exemplos gerados), mas para a curadoria:
mostra uma palavra de cada vez e a pessoa aceita, edita, corrige a classe,
apaga ou salta. Grava a cada decisão, num ficheiro temporário que só depois
substitui o original — para se poder parar a meio (ou fechar o terminal) sem
perder o que já foi revisto nem arriscar corromper o CSV.

Aceitar limita-se a tirar a marca "rascunho — rever" da nota: a definição
passa a estar aprovada, e um `grep "rascunho — rever"` diz sempre quantas
faltam.
"""

from __future__ import annotations

import csv
import sys
from pathlib import Path

MARCA = "rascunho — rever"
CAMPOS = ["lema", "classe", "definicao", "nota"]

_AJUDA = """
  Enter / a   aceitar (tira a marca de rascunho)
  e           editar a definição (e aprovar)
  c           corrigir a classe gramatical
  x           apagar a palavra da curadoria
  s           saltar (deixa por rever)
  q           guardar e sair          ?  esta ajuda
"""


def carregar(caminho: Path):
    with caminho.open(encoding="utf-8", newline="") as f:
        leitor = csv.DictReader(f)
        campos = list(leitor.fieldnames or CAMPOS)
        linhas = [dict(linha) for linha in leitor]
    return campos, linhas


def guardar(caminho: Path, campos, linhas) -> None:
    """Escreve tudo de volta, saltando as linhas marcadas para apagar.

    Grava primeiro num ficheiro ao lado e só depois o substitui: se algo falhar
    a meio, o `curadoria.csv` que já lá estava fica intacto.
    """
    vivas = [linha for linha in linhas if not linha.get("_apagar")]
    temporario = caminho.with_name(caminho.name + ".a-gravar")
    with temporario.open("w", encoding="utf-8", newline="") as f:
        escritor = csv.DictWriter(f, fieldnames=campos, extrasaction="ignore")
        escritor.writeheader()
        for linha in vivas:
            escritor.writerow({c: linha.get(c, "") for c in campos})
    temporario.replace(caminho)


def por_rever(linhas):
    return [linha for linha in linhas if MARCA in (linha.get("nota") or "")]


def aceitar(linha) -> None:
    linha["nota"] = (linha.get("nota") or "").replace(MARCA, "").strip(" —;,")


def _ler(prompt: str, entrada) -> str:
    if entrada is sys.stdin:
        return input(prompt)
    linha = entrada.readline()
    if linha == "":
        raise EOFError
    return linha.rstrip("\n")


def rever(caminho: Path, entrada=None) -> int:
    """O ciclo de revisão. Devolve quantas decisões foram tomadas."""
    entrada = entrada or sys.stdin
    campos, linhas = carregar(caminho)
    pendentes = por_rever(linhas)
    if not pendentes:
        print("  Nada por rever — todas as definições estão aprovadas.")
        return 0

    print(f"\n  {len(pendentes)} definições por rever.{_AJUDA}")
    feitas = 0
    for i, linha in enumerate(pendentes, start=1):
        print(f"\n  [{i}/{len(pendentes)}]  {linha.get('lema', '')}  "
              f"({linha.get('classe', '')})")
        print(f"      {linha.get('definicao', '')}")

        while True:
            try:
                escolha = _ler("  > ", entrada).strip().lower()
            except EOFError:
                guardar(caminho, campos, linhas)
                print("\n  Fim da entrada. Guardado.")
                return feitas

            if escolha in ("", "a"):
                aceitar(linha)
                feitas += 1
                guardar(caminho, campos, linhas)
                break
            if escolha == "e":
                try:
                    nova = _ler("  nova definição: ", entrada).strip()
                except EOFError:
                    nova = ""
                if nova:
                    linha["definicao"] = nova
                    aceitar(linha)
                    feitas += 1
                    guardar(caminho, campos, linhas)
                break
            if escolha == "c":
                try:
                    nova = _ler("  nova classe: ", entrada).strip()
                except EOFError:
                    nova = ""
                if nova:
                    linha["classe"] = nova
                    guardar(caminho, campos, linhas)
                continue   # corrigir a classe não decide; volta a pedir ação
            if escolha == "x":
                linha["_apagar"] = True
                feitas += 1
                guardar(caminho, campos, linhas)
                break
            if escolha == "s":
                break
            if escolha == "q":
                guardar(caminho, campos, linhas)
                print(f"\n  Guardado. {feitas} decididas nesta sessão; "
                      f"faltam {len(pendentes) - i + 1} a partir desta.")
                return feitas
            print(_AJUDA)

    guardar(caminho, campos, linhas)
    print(f"\n  Fim. {feitas} decididas. Já não há rascunhos por rever.")
    return feitas
