"""Curadoria manual — as definições escritas à mão.

A regra que o Jorge pôs ao aprovar esta fonte, e que estes testes protegem:
**só entra o que não está disponível em mais nenhuma fonte aberta.** Daí a
prioridade mais baixa na fusão e o aviso do validador.
"""

from __future__ import annotations

import sqlite3

from palavrame.build import build_database
from palavrame.config import SENSE_SOURCE_PRIORITY
from palavrame.merge.merger import merge_entries
from palavrame.schema import Sense, SourceEntry
from palavrame.sources import build as build_source
from palavrame.sources.curadoria import INFO as CURADORIA_INFO
from palavrame.validate import validate_database


def _escrever(paths, texto: str) -> None:
    (paths.seeds / "curadoria.csv").write_text(texto, encoding="utf-8")


def test_le_o_csv(cache, paths):
    _escrever(paths, (
        "lema,classe,definicao,nota\n"
        'ensonado,adjetivo,"Que tem sono; sonolento.",\n'
        'fanico,substantivo,"Desmaio; perda dos sentidos.",coloquial\n'
    ))
    entries = {e.lemma: e for e in build_source("curadoria", cache).parse()}

    assert entries["ensonado"].pos == "adjetivo"
    assert entries["ensonado"].senses[0].definition == "Que tem sono; sonolento."
    assert entries["ensonado"].senses[0].source == "curadoria"
    assert entries["fanico"].pos == "substantivo"


def test_varias_linhas_do_mesmo_lema_dao_varias_acecoes(cache, paths):
    _escrever(paths, (
        "lema,classe,definicao,nota\n"
        'manga,substantivo,"Parte do vestuário que cobre o braço.",\n'
        'manga,substantivo,"Fruto da mangueira.",\n'
    ))
    entries = {e.lemma: e for e in build_source("curadoria", cache).parse()}
    assert [s.definition for s in entries["manga"].senses] == [
        "Parte do vestuário que cobre o braço.",
        "Fruto da mangueira.",
    ]
    assert [s.ord for s in entries["manga"].senses] == [1, 2]


def test_linhas_incompletas_ou_comentadas_ignoram_se(cache, paths):
    """O ficheiro escreve-se à mão. Há-de ter linhas por acabar."""
    _escrever(paths, (
        "lema,classe,definicao,nota\n"
        "palavra-por-definir,adjetivo,,ainda não sei\n"
        ",substantivo,Uma definição sem lema.,\n"
        '#comentado,adjetivo,"Não deve entrar.",\n'
        'ensonado,adjetivo,"Que tem sono.",\n'
    ))
    entries = {e.lemma for e in build_source("curadoria", cache).parse()}
    assert entries == {"ensonado"}


def test_sem_ficheiro_a_fonte_cala_se(cache, paths):
    """Não ter curadoria é o estado normal de quem clona o repositório."""
    (paths.seeds / "curadoria.csv").unlink(missing_ok=True)
    assert list(build_source("curadoria", cache).parse()) == []


def test_curadoria_vem_depois_das_fontes_publicadas():
    """A ordem é o mecanismo. Sem ela, uma definição minha passava à frente
    do Wikcionário, que é exatamente o que não se quer.

    Só o wordnet vem depois, e vem porque as suas glosas são tradução
    automática — ver `test_glosas_wordnet.py`.
    """
    for publicada in ("wikcionario", "dicionario_aberto"):
        assert SENSE_SOURCE_PRIORITY.index(publicada) < \
            SENSE_SOURCE_PRIORITY.index("curadoria")


def test_definicao_aberta_aparece_antes_da_curada():
    aberta = SourceEntry(
        lemma="ensonado", source="wikcionario", pos="adjetivo",
        senses=[Sense("Que tem sono.", "wikcionario", ord=1)],
    )
    curada = SourceEntry(
        lemma="ensonado", source="curadoria", pos="adjetivo",
        senses=[Sense("Sonolento.", "curadoria", ord=1)],
    )
    resultado = merge_entries([curada, aberta], strict_backbone=False)
    entrada = resultado.entries[0]
    assert [s.source for s in entrada.senses] == ["wikcionario", "curadoria"]


def test_curadoria_nao_inventa_palavras_ja_existentes():
    """Curar `ensonado` não pode criar um segundo `ensonado`."""
    do_hunspell = SourceEntry(lemma="ensonado", source="hunspell_natura",
                              pos="adjetivo")
    curada = SourceEntry(
        lemma="ensonado", source="curadoria", pos="adjetivo",
        senses=[Sense("Que tem sono.", "curadoria", ord=1)],
    )
    resultado = merge_entries([do_hunspell, curada], strict_backbone=False)
    assert [e.lemma for e in resultado.entries] == ["ensonado"]
    assert len(resultado.entries[0].senses) == 1


def test_validador_avisa_quando_a_curadoria_ficou_redundante(tmp_path):
    """Se o Wikcionário crescer, a linha curada deixa de ser precisa.

    Aviso, nunca erro: a definição continua correta, só deixou de ser
    necessária. Quem corre a build decide se apaga a linha.
    """
    from palavrame.schema import MergedEntry
    from palavrame.sources.base import License, SourceInfo

    wikcionario = SourceInfo(
        slug="wikcionario", name="Wikcionário", url="https://pt.wiktionary.org/",
        license=License(name="CC BY-SA 4.0", attribution="Wikcionário.",
                        redistributable=True, verified=True),
    )
    entrada = MergedEntry(
        lemma="ensonado", pos="adjetivo",
        senses=[
            Sense("Que tem sono.", "wikcionario", ord=1),
            Sense("Sonolento.", "curadoria", ord=2),
        ],
        contributors=["wikcionario", "curadoria"],
    )
    caminho = tmp_path / "d.db"
    build_database(caminho, [entrada], [wikcionario, CURADORIA_INFO],
                   db_version="t")

    relatorio = validate_database(caminho, [wikcionario, CURADORIA_INFO])
    avisos = {c.name for c in relatorio.warnings}
    assert "curadoria redundante" in avisos
    assert relatorio.ok            # avisa, não bloqueia


def test_sem_redundancia_nao_ha_aviso(tmp_path):
    from palavrame.schema import MergedEntry

    entrada = MergedEntry(
        lemma="ensonado", pos="adjetivo",
        senses=[Sense("Que tem sono.", "curadoria", ord=1)],
        contributors=["curadoria"],
    )
    caminho = tmp_path / "d.db"
    build_database(caminho, [entrada], [CURADORIA_INFO], db_version="t")

    relatorio = validate_database(caminho, [CURADORIA_INFO])
    assert "curadoria redundante" not in {c.name for c in relatorio.checks}
    assert "curadoria" in {c.name for c in relatorio.checks}


def test_conteudo_curado_declara_licenca_e_atribuicao():
    """É conteúdo do projeto, mas entra na DB como qualquer outra fonte:
    com licença, atribuição, e visível no ecrã 'Fontes e licenças'."""
    licenca = CURADORIA_INFO.license
    assert licenca.redistributable is True
    assert licenca.verified
    assert licenca.attribution.strip()
    assert "BY-SA" in licenca.name.upper()   # acompanha a base derivada


def test_ficheiro_do_repositorio_e_valido(cache):
    """O `seeds/curadoria.csv` que vai no repositório tem de ler-se.

    Um erro de vírgula aqui só aparecia a meio de uma F1 de horas.
    """
    entries = list(build_source("curadoria", cache).parse())
    for entry in entries:
        assert entry.lemma and entry.senses
        for sense in entry.senses:
            assert sense.definition.strip()
            assert sense.definition[0].isupper()
            assert sense.definition.rstrip().endswith(".")
