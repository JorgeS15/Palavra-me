"""O AMALIA como fonte de exemplos, depois da revisão humana.

Fecha o circuito da geração: `gerar` produz candidatos, `rever` aprova alguns,
e é aqui que os aprovados voltam a entrar no pipeline como qualquer outra
fonte de exemplos — último degrau da cascata, abaixo do Tatoeba e do Leipzig.

Não é um `Source` normal e não está no `REGISTRY`: não tem nada para
descarregar. Lê de `work/`, onde a revisão deixou o resultado.

Só entram candidatos com `status == "aprovado"`. Um candidato que passou a
validação automática mas ainda não foi visto por um humano fica de fora — o
plano não abre exceção nisto (secção 10.8).
"""

from __future__ import annotations

from pathlib import Path

from ..schema import SourceEntry
from ..sources.base import License, SourceInfo

INFO = SourceInfo(
    slug="amalia",
    name="AMALIA (exemplos gerados)",
    url="https://huggingface.co/amalia-llm",
    license=License(
        name="Apache 2.0 (modelo)",
        url="https://www.apache.org/licenses/LICENSE-2.0",
        attribution=(
            "Frases de exemplo geradas pelo modelo AMALIA (Apache 2.0) e "
            "revistas manualmente."
        ),
        redistributable=True,
        verified=True,
        notes=(
            "As frases geradas não herdam a licença do modelo — o Apache 2.0 "
            "cobre o modelo, não a saída. Verificado no texto da licença. "
            "Marcadas como geradas na DB e na UI."
        ),
    ),
    provides=("examples",),
)


def approved_entries(path: Path) -> list[SourceEntry]:
    """Lê os candidatos revistos e devolve os aprovados, agrupados por lema."""
    if not path.exists():
        return []

    from .runner import load_candidates

    by_lemma: dict[str, SourceEntry] = {}
    for candidate in load_candidates(path):
        if candidate.status != "aprovado" or not candidate.sentence:
            continue
        entry = by_lemma.setdefault(
            candidate.lemma, SourceEntry(lemma=candidate.lemma, source=INFO.slug)
        )
        entry.examples.append(candidate.to_example())
    return list(by_lemma.values())
