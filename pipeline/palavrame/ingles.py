"""Fusão do dicionário de inglês.

O `Merger` português existe por causa de problemas que o inglês não tem: o
VOC como autoridade de lemas, a grafia de 1913, o brasileiro a unir na grafia
portuguesa. Aqui há uma fonte de conteúdo (o Wiktionary inglês) e uma de
frequência (o Leipzig), e a fusão é proporcionalmente simples:

1. as entradas da mesma palavra (uma por classe e etimologia: `lead` metal,
   `lead` guiar) juntam-se num lema. A de mais aceções vai à frente e dá a
   classe principal: a página do Wiktionary ordena por etimologia, e punha o
   chumbo antes de "guiar";
2. as flexões (`ran` -> `run`) penduram-se no lema de destino — e caem se ele
   não existir;
3. as remissões (`colour` -> `color`) só ficam tocáveis se o destino tiver
   definição;
4. a frequência de um lema é a melhor entre ele e as suas formas: `run` vale
   pelo `ran` e pelo `running`, e passa à frente do `ran` náutico.

O resultado são `MergedEntry`s — o mesmo contrato da build portuguesa, que
`build_database` escreve sem saber a língua.
"""

from __future__ import annotations

from typing import Iterable

from .schema import MergedEntry, SourceEntry
from .text import normalize


def fundir(
    entradas: Iterable[SourceEntry],
    ranks: dict[str, int] | None = None,
) -> list[MergedEntry]:
    ranks = ranks or {}
    lemas: dict[str, MergedEntry] = {}
    aceções_por_classe: dict[str, dict[str, int]] = {}
    formas_soltas: dict[str, list] = {}

    por_lema: dict[str, list[SourceEntry]] = {}
    for e in entradas:
        if not e.senses:
            if e.forms:
                formas_soltas.setdefault(e.lemma, []).extend(e.forms)
            continue
        por_lema.setdefault(e.lemma, []).append(e)

    for grupo in por_lema.values():
        grupo.sort(key=lambda x: -len(x.senses))   # estável: empates na ordem da página
        for e in grupo:
            alvo = lemas.get(e.lemma)
            if alvo is None:
                alvo = lemas[e.lemma] = MergedEntry(lemma=e.lemma, pos=e.pos)
                aceções_por_classe[e.lemma] = {}
            desvio = len(alvo.senses)
            for s in e.senses:
                s.ord += desvio
                alvo.senses.append(s)
            for ex in e.examples:
                if ex.sense_ord is not None:
                    ex.sense_ord += desvio
                alvo.examples.append(ex)
            _juntar_formas(alvo, e.forms)
            vistos = {r.target for r in alvo.relations}
            for r in e.relations:
                if r.target not in vistos:
                    vistos.add(r.target)
                    alvo.relations.append(r)
            if alvo.syllables is None and e.syllables:
                alvo.syllables = e.syllables
            if e.source not in alvo.contributors:
                alvo.contributors.append(e.source)
            contagem = aceções_por_classe[e.lemma]
            contagem[e.pos] = contagem.get(e.pos, 0) + len(e.senses)
    del por_lema

    for lema, formas in formas_soltas.items():
        alvo = lemas.get(lema)
        if alvo is not None:
            _juntar_formas(alvo, formas)
    del formas_soltas

    for lema, entrada in lemas.items():
        contagem = aceções_por_classe[lema]
        # Em empate fica a primeira classe da página, que é a ordem do kaikki.
        entrada.pos = max(contagem, key=lambda p: contagem[p])

        for s in entrada.senses:
            if s.remete_para is None:
                continue
            destino = lemas.get(s.remete_para)
            if destino is None or destino is entrada or not _tem_definicao_propria(destino):
                s.remete_para = None

        candidatos = [ranks.get(entrada.normalized)]
        candidatos += [ranks.get(f.normalized) for f in entrada.forms]
        validos = [r for r in candidatos if r is not None]
        entrada.frequency_rank = min(validos) if validos else None

        if ranks and "leipzig_en" not in entrada.contributors and validos:
            entrada.contributors.append("leipzig_en")

    return list(lemas.values())


def _juntar_formas(alvo: MergedEntry, formas) -> None:
    existentes = {f.form for f in alvo.forms}
    for f in formas:
        if f.form != alvo.lemma and f.form not in existentes:
            existentes.add(f.form)
            alvo.forms.append(f)


def _tem_definicao_propria(entrada: MergedEntry) -> bool:
    """Um destino de remissão que só remete outra vez não ajuda ninguém."""
    return any(s.remete_para is None for s in entrada.senses)


def ranks_de(entradas: Iterable[SourceEntry]) -> dict[str, int]:
    """Do Leipzig: forma normalizada -> melhor rank."""
    out: dict[str, int] = {}
    for e in entradas:
        if e.frequency_rank is None:
            continue
        chave = normalize(e.lemma)
        if chave not in out or e.frequency_rank < out[chave]:
            out[chave] = e.frequency_rank
    return out
