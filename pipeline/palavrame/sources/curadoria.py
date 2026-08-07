"""Curadoria manual — as definições que nenhuma fonte aberta tem.

Porque é que isto existe
------------------------
Há palavras portuguesas correntes que não estão definidas em fonte aberta
nenhuma. O caso que deu origem a este módulo é `ensonado`: existe no
vocabulário do Natura, o Leipzig tem frases reais com ela, mas não está no
Dicionário Aberto (1913, é posterior) nem nas 624 mil entradas do Wikcionário.
São cerca de 4 mil palavras assim. Ou se escrevem à mão, ou ficam vazias para
sempre.

É o ponto 3 da secção 4.4 do plano — *curadoria incremental* — e não é preciso
preencher as 4 mil: preenchem-se as que tropeçam na leitura, e ao fim de um ano
as que lá estão são exatamente as que interessaram a alguém.

As regras
---------
1. **Só preenche lacunas.** Esta fonte é a última na prioridade das aceções.
   Se uma palavra já tem definição de uma fonte publicada, a curada entra a
   seguir, nunca à frente — e o `validar` avisa, para que se possa apagar a
   linha.
2. **Nunca inventa palavras.** Como o Hunspell, uma entrada de curadoria só
   abre lema novo se mais ninguém reclamou aquela grafia.
3. **Fica marcada.** A fonte aparece na DB e no ecrã como "Curadoria
   Palavra-me", ao lado do "Dicionário Aberto" e do "Wikcionário". Quem lê sabe
   que aquela definição não veio de um dicionário publicado.
4. **Não é conteúdo de LLM.** Se algum dia se gerarem definições com o AMALIA,
   isso é outra fonte, com a marca `generated` — nunca esta.

O formato
---------
`pipeline/seeds/curadoria.csv`, UTF-8, com cabeçalho::

    lema,classe,definicao,nota
    ensonado,adjetivo,"Que tem sono; sonolento.",
    fanico,substantivo,"Desmaio; perda momentânea dos sentidos.",coloquial

`classe` aceita as etiquetas canónicas (`substantivo`, `adjetivo`, `verbo`, …)
ou as abreviaturas correntes; passa por `canonical_pos` como qualquer outra
fonte. `nota` é livre e serve para quem revê — não entra na DB.

Várias linhas com o mesmo lema dão várias aceções, pela ordem do ficheiro.
"""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Iterable, Iterator, Optional

from ..schema import Sense, SourceEntry, canonical_pos
from .base import License, Source, SourceInfo

FICHEIRO = "curadoria.csv"

INFO = SourceInfo(
    slug="curadoria",
    # Aparece assim no ecrã "Fontes e licenças" e por baixo de cada aceção,
    # ao lado de "Dicionário Aberto" e "Wikcionário": é uma biblioteca do
    # projeto, não um dicionário de terceiros. A atribuição, logo abaixo,
    # é que diz que foi escrita à mão — a proveniência não se perde.
    name="Palavra-me",
    url="https://github.com/jorges15/palavra-me",
    license=License(
        name="CC BY-SA 4.0",
        url="https://creativecommons.org/licenses/by-sa/4.0/",
        attribution=(
            "Definições escritas à mão para o Palavra-me, onde nenhuma fonte "
            "aberta define a palavra. Disponíveis sob CC BY-SA 4.0."
        ),
        redistributable=True,
        verified=True,   # é conteúdo próprio: a licença é uma escolha, não uma leitura
        notes=(
            "Conteúdo original do projeto. CC BY-SA para acompanhar a licença "
            "da base derivada (que já é copyleft por causa do Wikcionário) e "
            "para que uma correção de terceiros volte para o mesmo sítio."
        ),
    ),
    provides=("senses",),
    primary=FICHEIRO,
)


class Curadoria(Source):
    info = INFO

    def fetch(self) -> None:
        """Não há nada a descarregar: o ficheiro é escrito à mão."""
        origem = self._ficheiro_de_seeds()
        if origem.exists():
            self.cache.local(self.slug, FICHEIRO, origem)

    def parse(self, lemmas: Optional[Iterable[str]] = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        caminho = self._localizar()
        if caminho is None:
            return

        por_lema: "dict[str, SourceEntry]" = {}
        with caminho.open(encoding="utf-8", newline="") as f:
            for linha in csv.DictReader(f):
                lema = (linha.get("lema") or "").strip()
                definicao = (linha.get("definicao") or "").strip()
                # Linha em branco, por preencher, ou comentada: ignora-se sem
                # ruído. Este ficheiro escreve-se à mão e há-de ter as duas
                # coisas.
                if not lema or not definicao or lema.startswith("#"):
                    continue

                entry = por_lema.get(lema)
                if entry is None:
                    entry = SourceEntry(
                        lemma=lema,
                        source=self.slug,
                        pos=canonical_pos(linha.get("classe")),
                    )
                    if wanted is not None and entry.normalized not in wanted:
                        continue
                    por_lema[lema] = entry

                entry.senses.append(
                    Sense(
                        definition=definicao,
                        source=self.slug,
                        ord=len(entry.senses) + 1,
                    )
                )

        for entry in por_lema.values():
            if entry.senses:
                yield entry

    # --- auxiliares -------------------------------------------------------

    def _ficheiro_de_seeds(self) -> Path:
        return self.cache.paths.seeds / FICHEIRO

    def _localizar(self) -> Optional[Path]:
        """O ficheiro em `seeds/` manda; o do cache é a cópia de trabalho.

        Ao contrário das outras fontes, esta vive no repositório e não no
        cache — é código-fonte do projeto, versionado com ele. Ler primeiro de
        `seeds/` significa que basta gravar o ficheiro e correr o `f1`, sem
        `fetch` pelo meio.
        """
        origem = self._ficheiro_de_seeds()
        if origem.exists():
            return origem
        copia = self.cache.paths.cache / self.slug / FICHEIRO
        return copia if copia.exists() else None
