"""Hunspell pt-PT do projeto Natura (Universidade do Minho) — flexões.

Fonte da tabela `forms`, que é o que faz a pesquisa funcionar a partir do que
está escrito no livro (plano 4.2). A expansão está em `palavrame.affix`; aqui
só se localizam os ficheiros e se envolve o resultado no esquema canónico.

Aquisição: o Natura publica os dicionários em natura.di.uminho.pt e em
espelhos no GitHub. Confirmar em F0 o URL e a licença exata — o projeto usa
licenças livres, mas há mais do que uma em jogo consoante o pacote.
"""

from __future__ import annotations

from pathlib import Path
from typing import Iterable, Iterator

from ..affix import AffixTable, expand, read_aff, read_dic
from ..schema import Form, SourceEntry
from .base import License, Source, SourceInfo, SourceUnavailable

INFO = SourceInfo(
    slug="hunspell_natura",
    name="Dicionário Hunspell pt-PT (projeto Natura)",
    url="https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main",
    license=License(
        name="POR VERIFICAR (GPL/LGPL/MPL, conforme o pacote)",
        url="https://natura.di.uminho.pt/wiki/doku.php?id=dicionarios:main",
        attribution="Dicionário ortográfico pt-PT, projeto Natura, Universidade do Minho.",
        redistributable=None,
        verified=False,
        notes=(
            "Confirmar em F0 a licença exata do pacote pt-PT. Nota: o que se "
            "embarca na app é a tabela `forms` derivada, não os ficheiros "
            ".aff/.dic — mas uma licença copyleft pode alcançar o derivado. "
            "Ler os termos antes de decidir."
        ),
    ),
    provides=("forms",),
    endpoints={},   # preencher em F0 com o URL confirmado
)


class HunspellNatura(Source):
    info = INFO

    def fetch(self) -> None:
        for name, url in self.info.endpoints.items():
            self.cache.fetch(url, self.slug, name)
        if self.info.endpoints:
            return

        # Sem URL confirmado, aceita os ficheiros postos à mão — e regista-os
        # no lockfile, para que a build continue a ser verificável. É o mesmo
        # que o VOC faz; um `fetch` não deve reclamar do que já lá está.
        aff, dic = self._locate()
        for path in (aff, dic):
            self.cache.local(self.slug, path.name, path)

    def parse(self, lemmas: Iterable[str] | None = None) -> Iterator[SourceEntry]:
        wanted = self._wanted(lemmas)
        aff_path, dic_path = self._locate()
        table = read_aff(aff_path)

        for word, flags, morph in read_dic(dic_path, table):
            entry = SourceEntry(lemma=word, source=self.slug)
            if wanted is not None and entry.normalized not in wanted:
                continue
            for generated in expand(word, flags, table):
                entry.forms.append(
                    Form(form=generated.form, tag=generated.tag or morph)
                )
            if entry.forms:
                yield entry

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
