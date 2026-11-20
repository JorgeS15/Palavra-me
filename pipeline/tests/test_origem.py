"""Curadoria de origem: rótulos manuais de variante/registo por lema."""

from __future__ import annotations

from palavrame.origem import aplicar, carregar
from palavrame.schema import Sense, SourceEntry


def _entry(lemma, *defs):
    e = SourceEntry(lemma=lemma, source="teste")
    for i, d in enumerate(defs, 1):
        e.senses.append(Sense(definition=d, source="teste", ord=i))
    return e


def test_rotula_todas_as_acecoes_do_lema():
    entries = [_entry("time", "Grupo de pessoas.", "Conjunto desportivo.")]
    n = aplicar(entries, {"time": "Brasil"})
    assert n == 2
    assert all(s.domains[0] == "Brasil" for s in entries[0].senses)


def test_ignora_lemas_fora_do_mapa():
    entries = [_entry("janela", "Abertura na parede.")]
    assert aplicar(entries, {"time": "Brasil"}) == 0
    assert entries[0].senses[0].domains == []


def test_apanha_o_lema_por_forma_normalizada():
    # 'nené' no mapa, com acento; o entry chega igual — casa pela normalizada.
    entries = [_entry("nené", "Criança pequenina.")]
    assert aplicar(entries, {"nene": "Brasil"}) == 1
    assert entries[0].senses[0].domains == ["Brasil"]


def test_e_idempotente():
    entries = [_entry("time", "Grupo de pessoas.")]
    aplicar(entries, {"time": "Brasil"})
    aplicar(entries, {"time": "Brasil"})
    assert entries[0].senses[0].domains == ["Brasil"]


def test_o_rotulo_fica_a_frente_do_dominio_tematico():
    e = _entry("chope")
    e.senses.append(Sense(definition="Cerveja.", source="t", ord=1, domains=["Bebidas"]))
    aplicar([e], {"chope": "Brasil"})
    assert e.senses[-1].domains == ["Brasil", "Bebidas"]


def test_carregar_le_o_ficheiro_de_seeds(tmp_path):
    (tmp_path / "origem.csv").write_text(
        "lema,origem,nota\ntime,Brasil,\n# comentario,X,\nvazio,,\n",
        encoding="utf-8",
    )
    mapa = carregar(tmp_path)
    assert mapa == {"time": "Brasil"}
