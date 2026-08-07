"""Hunspell pt-PT do projeto Natura (Universidade do Minho) — flexões e classe.

Fonte da tabela `forms`, que é o que faz a pesquisa funcionar a partir do que
está escrito no livro (plano 4.2). A expansão está em `palavrame.affix`; aqui
só se localizam os ficheiros e se envolve o resultado no esquema canónico.

Dá também a **classe gramatical**, que está no campo morfológico de cada linha
e durante muito tempo se deitou fora — ver o bloco sobre a etiqueta do Natura
mais abaixo, que é onde está a parte interessante deste módulo.

Aquisição: o Natura publica os dicionários em natura.di.uminho.pt e em
espelhos no GitHub. Confirmar em F0 o URL e a licença exata — o projeto usa
licenças livres, mas há mais do que uma em jogo consoante o pacote.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Iterable, Iterator, Optional

from ..affix import AffixTable, expand, read_aff, read_dic
from ..schema import Form, SourceEntry
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="hunspell_natura",
    name="Dicionário Hunspell pt-PT (projeto Natura)",
    url="https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main",
    license=License(
        name="GPL/LGPL/MPL (tri-licença; usamos a MPL)",
        url="https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main",
        attribution=(
            "Dicionário ortográfico pt-PT, projeto Natura, Universidade do "
            "Minho — GPL/LGPL/MPL."
        ),
        redistributable=True,
        verified=True,   # 2026-07-30: cabeçalho do .aff do pacote 20251001
        notes=(
            "Confirmado pelo Jorge no cabeçalho do pt_PT.aff do pacote "
            "20251001: 'GPL/LGPL/MPL licenses, by this order' — tri-licença, "
            "como o wiki do Natura indicava. Ao abrigo da MPL, a tabela "
            "`forms` derivada pode ser redistribuída na app publicada. "
            "(O rodapé CC BY-NC-SA do wiki cobre o texto do wiki, não os "
            "dicionários.)"
        ),
    ),
    provides=("forms", "pos"),
    endpoints={
        # Último pacote publicado à data de 2026-07-30 (listagem confirmada).
        "hunspell-pt_PT.tar.gz": (
            "https://natura.di.uminho.pt/download/sources/Dictionaries/"
            "hunspell/hunspell-pt_PT-20251001.tar.gz"
        ),
    },
)


# --- a etiqueta morfológica do Natura --------------------------------------
#
# O `.dic` do Natura NÃO é uma lista de lemas, e tratá-lo como tal era o erro
# mais caro do pipeline. É uma lista de FORMAS, e o campo morfológico de cada
# linha diz o que a forma é:
#
#     ensonado    [CAT=adj,N=s,G=m]                      -> lema, adjetivo
#     tinham      [$ter$CAT=v,T=inf,TR=_$P=3,N=p,T=pi]   -> flexão de "ter"
#     Serralves   [CAT=np,SEM=p]                         -> nome próprio
#
# Sem ler `$lema$`, 4450 flexões (`tinham`, `ativeras`, `púnheis`, `corróis`)
# abriam entrada própria ao lado do verbo a que pertencem, todas sem definição
# nenhuma — palavras fantasma que a pesquisa devolvia como candidatas.
#
# Sem ler `CAT=`, 9611 lemas ficavam com classe gramatical "desconhecido"
# quando a fonte a sabia.

_LEMA = re.compile(r"\[\$([^$]+)\$")
_CAT = re.compile(r"\bCAT=([a-z]+)")

# O Natura usa o conjunto de etiquetas do jspell. `cp` (contrações: "ao",
# "comigo") e `punct` ficam deliberadamente de fora: não são classes
# gramaticais e o Wikcionário trata-as melhor.
CAT_PARA_POS = {
    "nc": "substantivo",
    "np": "nome proprio",
    "v": "verbo",
    "adj": "adjetivo",
    "a": "adjetivo",
    "adv": "adverbio",
    "card": "numeral",
    "nord": "numeral",
    "pind": "pronome",
    "pdem": "pronome",
    "ppos": "pronome",
    "prel": "pronome",
    "ppes": "pronome",
    "con": "conjuncao",
    "prep": "preposicao",
    "art": "artigo",
    "in": "interjeicao",
    "pref": "prefixo",
}


def lema_de(morph: Optional[str]) -> Optional[str]:
    """O lema a que esta linha do `.dic` pertence, se não for ela própria um.

    Devolve `None` para as entradas que já são lemas — que é o caso normal.
    """
    if not morph:
        return None
    encontrado = _LEMA.search(morph)
    if not encontrado:
        return None
    base = encontrado.group(1).strip()
    return base or None


def pos_de(morph: Optional[str]) -> str:
    """Classe gramatical a partir de `CAT=`, nas etiquetas canónicas."""
    if not morph:
        return "desconhecido"
    encontrado = _CAT.search(morph)
    if not encontrado:
        return "desconhecido"
    return CAT_PARA_POS.get(encontrado.group(1), "desconhecido")


class HunspellNatura(Source):
    info = INFO

    def fetch(self) -> None:
        # Ficheiros postos à mão têm precedência — regista-os no lockfile,
        # para que a build continue a ser verificável. Um `fetch` não deve
        # reclamar do que já lá está (é o mesmo que o VOC faz), nem obrigar
        # a rede quando não é precisa.
        try:
            aff, dic = self._locate()
        except SourceUnavailable:
            pass
        else:
            for path in (aff, dic):
                self.cache.local(self.slug, path.name, path)
            return

        if not self.info.endpoints:
            self._locate()   # relança o SourceUnavailable com a explicação
            return
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, name)
        self._extract_tarballs()

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        aff_path, dic_path = self._locate()
        table = read_aff(aff_path)

        for word, flags, morph in read_dic(dic_path, table):
            # A entrada do .dic pode ser uma FLEXÃO. Quando é, a etiqueta diz
            # de quem, e é a esse que as formas pertencem — ver `lema_de`.
            base = lema_de(morph) or word
            entry = SourceEntry(lemma=base, source=self.slug, pos=pos_de(morph))
            if wanted is not None and entry.normalized not in wanted:
                continue
            for generated in expand(word, flags, table):
                entry.forms.append(
                    Form(form=generated.form, tag=generated.tag or morph)
                )
            if entry.forms:
                yield entry

    def _extract_tarballs(self) -> None:
        """Tira os .aff/.dic de qualquer tarball no cache desta fonte.

        O Natura publica o pacote como .tar.gz; o parse quer os ficheiros
        soltos. Extração plana e só das extensões esperadas — sem confiar
        nos caminhos internos do arquivo.
        """
        import tarfile

        base = self.cache.paths.cache / self.slug
        if not base.is_dir():
            return
        for tarball in sorted(base.glob("*.tar.gz")):
            with tarfile.open(tarball, "r:gz") as tf:
                for member in tf.getmembers():
                    suffix = Path(member.name).suffix
                    if not member.isfile() or suffix not in (".aff", ".dic"):
                        continue
                    target = base / Path(member.name).name
                    extracted = tf.extractfile(member)
                    if extracted is None:
                        continue
                    target.write_bytes(extracted.read())

    def _locate(self) -> tuple[Path, Path]:
        base = self.cache.paths.cache / self.slug
        aff = sorted(base.glob("*.aff")) if base.is_dir() else []
        dic = sorted(base.glob("*.dic")) if base.is_dir() else []
        if not aff or not dic:
            raise SourceUnavailable(
                f"Faltam os ficheiros .aff/.dic em {base}. "
                "Ver INFO.license.notes antes de os obter."
            )
        return aff[0], dic[0]
