"""PAPEL — Palavras Associadas Porto Editora / Linguateca.

Relações léxico-semânticas extraídas automaticamente **das definições do
Dicionário da Língua Portuguesa da Porto Editora**, ao abrigo de um protocolo
de colaboração com o departamento de dicionários da editora. Construído na
Linguateca por Hugo Gonçalo Oliveira.

Porque é que isto interessa a esta app
--------------------------------------
Não traz definições — a equipa decidiu explicitamente não aproveitar a divisão
de sentidos do dicionário. Traz **83 mil relações de sinonímia** e 49 mil de
hiperonímia, de um dicionário contemporâneo.

Para quem encontra `ensonado` a meio de um romance e vê "sem definição em
nenhuma fonte", um *"o mesmo que sonolento"* resolve o problema tão bem como
uma definição — e resolve-o com português de agora, em vez do de 1913 que é
o esqueleto da base.

A avaliação manual publicada dá 99-100% de precisão à sinonímia e à
hiperonímia, que são precisamente as duas que a app mostra.

Formato
-------
Ficheiros `relacoes_final_GRUPO.txt`, um triplo por linha::

    palavra1 RELACAO palavra2
    palavra1 RELACAO palavra2 :: registo;domínio;variante

Os três campos depois do `::` vêm do dicionário sem qualquer modificação, e
podem estar vazios (`fam.;;` tem registo mas não domínio).
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Iterable, Iterator, Optional

from ..schema import Relation, SourceEntry
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="papel",
    name="PAPEL (Porto Editora / Linguateca)",
    url="https://www.linguateca.pt/PAPEL/",
    license=License(
        name="Público e gratuito (Linguateca / Porto Editora)",
        url="https://www.linguateca.pt/PAPEL/papel.html",
        attribution=(
            "PAPEL — Palavras Associadas Porto Editora, Linguateca, extraído "
            "do Dicionário da Língua Portuguesa da Porto Editora. "
            "https://www.linguateca.pt/PAPEL"
        ),
        redistributable=True,
        verified=True,   # 2026-08-06: confirmação por email, obtida pelo Jorge
        notes=(
            "O sítio declara o recurso 'público, grátis e utilizável por "
            "TODOS os actores de processamento da língua que o quiserem "
            "usar', mas não publica texto de licença e o conteúdo deriva do "
            "dicionário da Porto Editora ao abrigo de um protocolo. O Jorge "
            "obteve confirmação por email a 2026-08-06 de que a "
            "redistribuição está coberta. É a ÚNICA fonte da base sem "
            "licença escrita: o email é o que sustenta a decisão e deve ser "
            "guardado — ver docs/fontes.md."
        ),
    ),
    provides=("relations",),
    primary="PAPEL.v.3.5_utf8.zip",
    endpoints={
        "PAPEL.v.3.5_utf8.zip":
            "https://www.linguateca.pt/PAPEL/PAPEL.v.3.5_utf8.zip",
    },
    manual=(
        "Se o URL mudar: https://www.linguateca.pt/PAPEL/papel.html tem a "
        "ligação para o zip mais recente. Depois: "
        "palavrame fetch --source papel --url <URL>"
    ),
)

# Do conjunto do PAPEL — mais de trinta tipos — só se aproveitam os três que a
# app mostra. Os restantes (FINALIDADE_DE, MATERIAL_DE, CONTIDO_EM...) são
# interessantes para processamento de língua e ruído para quem lê um romance.
#
# O sufixo indica a classe gramatical dos argumentos: SINONIMO_N_DE para
# nomes, SINONIMO_V_DE para verbos, e por aí fora.
_RELACOES = {
    "SINONIMO": "sinonimo",
    "ANTONIMO": "antonimo",
    "HIPERONIMO": "hiperonimo",
}

# `palavra1 RELACAO palavra2` e, opcionalmente, ` :: registo;domínio;variante`.
_TRIPLO = re.compile(
    r"^(?P<esquerda>\S+)\s+(?P<relacao>[A-Z_]+)\s+(?P<direita>\S+)"
    r"(?:\s*::\s*(?P<marcas>.*))?$"
)


def _canonica(relacao: str) -> Optional[str]:
    """`SINONIMO_N_DE` -> `sinonimo`. Devolve nulo para o que não interessa."""
    raiz = relacao.split("_", 1)[0]
    return _RELACOES.get(raiz)


class Papel(Source):
    info = INFO

    def fetch(self) -> None:
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, name)

    def parse(self, lemmas: Optional[Iterable[str]] = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        por_palavra: "dict[str, SourceEntry]" = {}

        for nome, linhas in self._ficheiros():
            for linha in linhas:
                triplo = _TRIPLO.match(linha.strip())
                if not triplo:
                    continue
                relacao = _canonica(triplo.group("relacao"))
                if not relacao:
                    continue

                esquerda = _termo(triplo.group("esquerda"))
                direita = _termo(triplo.group("direita"))
                if not esquerda or not direita or esquerda == direita:
                    continue

                for origem, alvo, tipo in _ambos_os_sentidos(
                    esquerda, direita, relacao
                ):
                    entry = por_palavra.get(origem)
                    if entry is None:
                        entry = SourceEntry(lemma=origem, source=self.slug)
                        if wanted is not None and entry.normalized not in wanted:
                            continue
                        por_palavra[origem] = entry
                    entry.relations.append(
                        Relation(target=alvo, relation=tipo, source=self.slug)
                    )

        for entry in por_palavra.values():
            if entry.relations:
                yield entry

    # --- auxiliares -------------------------------------------------------

    def _ficheiros(self) -> "Iterator[tuple[str, Iterable[str]]]":
        """Lê os `relacoes_final_*.txt` de dentro do zip, ou soltos no cache."""
        import zipfile

        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            raise SourceUnavailable(
                f"Falta o pacote do PAPEL em {base}. {self.info.manual}"
            )

        encontrou = False
        for arquivo in sorted(base.glob("*.zip")):
            with zipfile.ZipFile(arquivo) as zf:
                for membro in _escolher(zf.namelist()):
                    encontrou = True
                    with zf.open(membro) as fh:
                        yield membro, (
                            linha.decode("utf-8", "replace") for linha in fh
                        )

        for solto in _escolher([p.name for p in base.glob("*.txt")]):
            encontrou = True
            with (base / solto).open(encoding="utf-8", errors="replace") as fh:
                yield solto, fh

        if not encontrou:
            raise SourceUnavailable(
                f"O pacote do PAPEL em {base} não tem ficheiros "
                f"`relacoes_final_*.txt`. {self.info.manual}"
            )


def _escolher(nomes: "Iterable[str]") -> "list[str]":
    """Os ficheiros a ler, sem os ler duas vezes.

    O pacote traz `relacoes_final.txt` — a união de tudo — **e** um ficheiro
    por grupo (`relacoes_final_SINONIMIA.txt`, `..._HIPERONIMIA.txt`, ...).
    Ler ambos duplicava cada triplo. Prefere-se a união; os por-grupo só
    entram se ela faltar.

    Ignora-se também o `__MACOSX/`, que o zip traz e que tem cópias com o
    mesmo nome atrás de um prefixo.
    """
    uteis = [
        n for n in nomes
        if not n.startswith("__MACOSX")
        and n.rsplit("/", 1)[-1].startswith("relacoes_final")
        and n.endswith(".txt")
    ]
    uniao = [n for n in uteis if n.rsplit("/", 1)[-1] == "relacoes_final.txt"]
    return sorted(uniao) if uniao else sorted(uteis)


def _termo(bruto: str) -> str:
    """`abrir_o_apetite` -> `abrir o apetite`.

    O PAPEL junta com sublinhado as expressões que trata como um item lexical
    só — verbos com objeto direto, nomes de locais compostos.
    """
    return bruto.replace("_", " ").strip()


def _ambos_os_sentidos(
    esquerda: str, direita: str, relacao: str
) -> "list[tuple[str, str, str]]":
    """A relação vista dos dois lados.

    A sinonímia e a antonímia são simétricas: se A é sinónimo de B, B é
    sinónimo de A, e a app precisa de a encontrar a partir de qualquer das
    duas. A hiperonímia não é — inverte-se para hiponímia, que é o que
    permite mostrar o termo mais específico na entrada do mais geral.
    """
    if relacao in ("sinonimo", "antonimo"):
        return [
            (esquerda, direita, relacao),
            (direita, esquerda, relacao),
        ]
    return [
        (esquerda, direita, "hiponimo"),     # esquerda é o geral
        (direita, esquerda, "hiperonimo"),
    ]
