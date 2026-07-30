"""Revisão humana dos exemplos gerados (plano 5.3: "um script simples chega").

Nada de gerado entra na DB sem passar por aqui. A validação automática só tira
o lixo óbvio; a pergunta que fica — *isto ensina alguma coisa a quem está a
ler?* — não se automatiza.
"""

from __future__ import annotations

import sys
from typing import Sequence

from .generate.runner import Candidate

_HELP = """
  a  aprovar        r  rejeitar        e  editar
  s  saltar         q  guardar e sair  ?  esta ajuda
"""


def review_loop(candidates: Sequence[Candidate], stream=None) -> int:
    """Percorre os candidatos pendentes. Devolve quantas decisões foram tomadas."""
    stream = stream or sys.stdin
    pending = [c for c in candidates if c.status == "pendente" and c.sentence]
    if not pending:
        print("  Nada por rever.")
        return 0

    print(f"\n  {len(pending)} exemplos por rever.{_HELP}")
    decisions = 0

    for i, candidate in enumerate(pending, start=1):
        print(f"\n  [{i}/{len(pending)}] {candidate.lemma} ({candidate.pos}) "
              f"— aceção {candidate.sense_ord}")
        print(f"      definição: {candidate.definition}")
        print(f"      frase:     {candidate.sentence}")
        if candidate.attempts > 1:
            print(f"      (à {candidate.attempts}ª tentativa)")

        while True:
            try:
                choice = input("  > ").strip().lower()
            except EOFError:
                print("\n  Fim da entrada. A guardar.")
                return decisions

            if choice in ("a", "s", ""):
                if choice == "a":
                    candidate.status = "aprovado"
                    decisions += 1
                break
            if choice == "r":
                candidate.status = "rejeitado"
                candidate.rejection_reasons.append("rejeitado na revisão humana")
                decisions += 1
                break
            if choice == "e":
                try:
                    edited = input("  nova frase: ").strip()
                except EOFError:
                    break
                if edited:
                    candidate.sentence = edited
                    candidate.status = "aprovado"
                    candidate.rejection_reasons.append("editado na revisão humana")
                    decisions += 1
                break
            if choice == "q":
                return decisions
            print(_HELP)

    return decisions
