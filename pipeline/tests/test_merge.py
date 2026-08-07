"""Testes das regras de fusão da secção 5.2 do plano."""

from __future__ import annotations

from palavrame.merge import merge_entries
from palavrame.schema import Example, Form, Relation, Sense, SourceEntry


def entry(lemma, source, **kwargs):
    return SourceEntry(lemma=lemma, source=source, **kwargs)


def test_voc_decide_a_lista_de_lemas():
    entries = [
        entry("janela", "voc_cplp", pos="substantivo"),
        entry("janella", "dicionario_aberto",
              senses=[Sense("Grafia antiga.", "dicionario_aberto")]),
    ]
    result = merge_entries(entries)
    assert result.backbone == "voc_cplp"
    assert [e.lemma for e in result.entries] == ["janela"]
    # A grafia rejeitada não desaparece em silêncio.
    assert any(c["lemma"] == "janella" for c in result.conflicts.rejected_lemmas)


def test_sem_voc_o_modo_permissivo_aceita_tudo():
    entries = [entry("janella", "dicionario_aberto",
                     senses=[Sense("Abertura.", "dicionario_aberto")])]
    result = merge_entries(entries, strict_backbone=False)
    assert result.backbone is None
    assert [e.lemma for e in result.entries] == ["janella"]


def test_wikcionario_vem_antes_do_dicionario_aberto():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "dicionario_aberto",
              senses=[Sense("Abertura na parede de um edifício.", "dicionario_aberto")]),
        entry("janela", "wikcionario",
              senses=[Sense("Área retangular no ecrã.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    fontes = [s.source for s in result.entries[0].senses]
    assert fontes == ["wikcionario", "dicionario_aberto"]


def test_acecoes_de_fontes_diferentes_nunca_se_fundem():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wikcionario", senses=[Sense("Sentido A.", "wikcionario")]),
        entry("janela", "dicionario_aberto",
              senses=[Sense("Sentido B.", "dicionario_aberto")]),
    ]
    senses = merge_entries(entries).entries[0].senses
    assert len(senses) == 2
    assert {s.source for s in senses} == {"wikcionario", "dicionario_aberto"}
    assert [s.ord for s in senses] == [1, 2]


def test_definicao_repetida_entre_fontes_regista_conflito():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wikcionario", senses=[Sense("Abertura.", "wikcionario")]),
        entry("janela", "dicionario_aberto",
              senses=[Sense("abertura", "dicionario_aberto")]),
    ]
    result = merge_entries(entries)
    assert len(result.entries[0].senses) == 1
    assert result.conflicts.duplicate_senses


def test_divergencia_de_classe_gramatical_fica_registada():
    entries = [
        entry("solta", "voc_cplp", pos="adjetivo"),
        entry("solta", "wikcionario", pos="substantivo",
              senses=[Sense("Ato de soltar.", "wikcionario")]),
    ]
    result = merge_entries(entries)
    assert result.conflicts.pos_disagreements
    # Não se escolhe automaticamente entre as duas: fica a primeira.
    assert result.entries[0].pos == "adjetivo"


def test_relatorio_de_conflitos_guarda_amostra_e_conta_tudo():
    """O relatório é para ler, e ninguém lê a décima milésima discordância.

    Guardava-se tudo, e o relatório da F1 chegou a 3 MB só de classes
    gramaticais em desacordo — a crescer com cada fonte nova que traga `pos`.
    A contagem tem de continuar exata; a amostra é que fica com teto.
    """
    from palavrame.merge.merger import MAX_CONFLITOS_REGISTADOS

    excesso = MAX_CONFLITOS_REGISTADOS + 25
    entries = []
    for i in range(excesso):
        entries.append(entry(f"palavra{i}", "voc_cplp", pos="adjetivo"))
        entries.append(
            entry(f"palavra{i}", "wikcionario", pos="substantivo",
                  senses=[Sense(f"Definição {i}.", "wikcionario")])
        )
    result = merge_entries(entries)

    assert len(result.conflicts.pos_disagreements) == MAX_CONFLITOS_REGISTADOS
    assert result.conflicts.counts["pos_disagreements"] == excesso
    assert result.conflicts.total() == excesso
    assert result.conflicts.as_dict()["pos_disagreements_omitidos"] == 25


def test_exemplo_liga_se_ao_lema_pela_flexao():
    """O caminho crítico: a frase diz "couberam", o exemplo vai para "caber"."""
    entries = [
        entry("caber", "voc_cplp", pos="verbo"),
        entry("caber", "hunspell_natura", forms=[Form("couberam"), Form("cabemos")]),
        entry("couberam", "tatoeba", examples=[
            Example("Não couberam todos no carro pequeno.", "tatoeba", "1"),
        ]),
    ]
    result = merge_entries(entries)
    caber = result.entries[0]
    assert caber.lemma == "caber"
    assert len(caber.examples) == 1
    assert "couberam" in caber.examples[0].sentence


def test_exemplo_sem_lema_conhecido_fica_orfao():
    entries = [
        entry("janela", "voc_cplp"),
        entry("xpto", "tatoeba", examples=[Example("Frase com xpto lá dentro.", "tatoeba")]),
    ]
    result = merge_entries(entries)
    assert result.conflicts.orphan_examples
    assert result.entries[0].examples == []


def test_cascata_de_exemplos_prefere_tatoeba():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "leipzig", examples=[Example("Frase do Leipzig aqui.", "leipzig")]),
        entry("janela", "tatoeba", examples=[Example("Frase do Tatoeba aqui.", "tatoeba")]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert exemplos[0].source == "tatoeba"


def test_pt_pt_antes_de_pt_br():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "tatoeba", examples=[
            Example("Frase brasileira sobre janela.", "tatoeba", "1", variant="pt-BR"),
            Example("Frase portuguesa sobre janela.", "tatoeba", "2", variant="pt-PT"),
        ]),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert exemplos[0].variant == "pt-PT"


def test_teto_de_exemplos_por_acecao():
    from palavrame.config import MAX_EXAMPLES_PER_SENSE

    muitos = [
        Example(f"Frase número {i} sobre a janela da sala.", "tatoeba", str(i), sense_ord=1)
        for i in range(10)
    ]
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "hunspell_natura", forms=[Form("janela")]),
        entry("janela", "wikcionario", senses=[Sense("Abertura.", "wikcionario")],
              examples=muitos),
    ]
    exemplos = merge_entries(entries).entries[0].examples
    assert len(exemplos) == MAX_EXAMPLES_PER_SENSE


def test_frequencia_mais_baixa_ganha():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "leipzig", frequency_rank=900),
        entry("janela", "hunspell_natura", forms=[Form("janela")], frequency_rank=42),
    ]
    assert merge_entries(entries).entries[0].frequency_rank == 42


def test_relacao_para_si_proprio_e_descartada():
    entries = [
        entry("janela", "voc_cplp"),
        entry("janela", "wordnet_pt", relations=[Relation("janela", "sinonimo")]),
    ]
    assert merge_entries(entries).entries[0].relations == []


def test_frequencia_do_leipzig_chega_aos_lemas():
    """Regressão: a fusão em streaming deixava cair o `frequency_rank`.

    O Leipzig entra pelo caminho das fontes de exemplos, que passou a ser
    separado — e nesse caminho só se aplicavam exemplos. Resultado: os
    186 mil lemas da F1 ficaram todos sem frequência, e a ordenação dos
    candidatos por frequência que o plano pede (secção 9) não fazia nada.
    """
    from palavrame.merge.merger import merge_entries
    from palavrame.schema import Sense, SourceEntry

    wiki = SourceEntry(lemma="janela", source="wikcionario")
    wiki.senses.append(Sense(definition="Abertura na parede.", source="wikcionario"))
    leipzig = SourceEntry(lemma="janela", source="leipzig", frequency_rank=42)

    resultado = merge_entries([wiki, leipzig], strict_backbone=False)
    janela = next(e for e in resultado.entries if e.lemma == "janela")
    assert janela.frequency_rank == 42
