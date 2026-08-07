"""Glosas do wordnet como aceções de último recurso.

O PULO traz 117 mil glosas em português, e o pipeline ignorava-as. São
tradução automática das glosas inglesas da WordNet de Princeton, com tudo o
que isso implica: umas são boas — *"uma sala onde um prisioneiro é mantido"* —
e outras são literais ao ponto de estarem erradas, como *"tosquiar a lã de"*
para `espoliar`, que é o inglês *fleece* vertido à letra.

Daí as duas travas que estes testes protegem:

1. **Ortografia.** Glosas em português do Brasil não entram. Uma app de
   leitura de literatura portuguesa não pode dizer *oxigênio*.
2. **Último recurso.** Uma glosa só entra se a palavra não tiver definição
   nenhuma. Encostada a uma entrada do Dicionário Aberto, estragava-a.
"""

from __future__ import annotations

from palavrame.config import FILL_ONLY_SOURCES, SENSE_SOURCE_PRIORITY
from palavrame.merge.merger import merge_entries
from palavrame.schema import Sense, SourceEntry
from palavrame.text import limpar_glosa, parece_do_brasil


# --- deteção de ortografia brasileira --------------------------------------

def test_apanha_o_e_circunflexo_antes_de_nasal():
    """No Brasil escreve-se ô/ê antes de nasal; em Portugal ó/é."""
    assert parece_do_brasil("levando oxigênio aos tecidos")
    assert parece_do_brasil("um dicionário de sinônimos")
    assert parece_do_brasil("relativo ao gênero")
    assert parece_do_brasil("crescimento econômico")


def test_apanha_a_grafia_pre_reforma_e_o_voce():
    assert parece_do_brasil("na forma de uma idéia")
    assert parece_do_brasil("preocupado com idéias")
    assert parece_do_brasil("o desespero que você sente")


def test_nao_rejeita_portugues_europeu():
    """O custo de um falso positivo é perder uma glosa boa — mas o custo de
    um falso negativo é a app dizer 'oxigênio'. Ainda assim, a regra tem de
    deixar passar o português normal."""
    assert not parece_do_brasil("a região do corpo entre o tórax e a pelve")
    assert not parece_do_brasil("um composto convertido no seu isómero")
    assert not parece_do_brasil("dar ênfase a alguma coisa")
    assert not parece_do_brasil("Que tem sono; sonolento.")
    assert not parece_do_brasil("o cargo de presidente")


# --- limpeza ---------------------------------------------------------------

def test_glosa_fica_com_aspeto_de_definicao():
    """Ao lado das aceções de 1913, não se pode distinguir pela pontuação."""
    assert limpar_glosa("o cargo de presidente") == "O cargo de presidente."
    assert limpar_glosa("aparecer novamente") == "Aparecer novamente."


def test_tira_os_exemplos_entre_aspas_de_princeton():
    """Princeton escreve `definição; "frase de exemplo"`."""
    limpa = limpar_glosa('mover-se depressa; "ele correu para casa"')
    assert limpa == "Mover-se depressa."
    assert '"' not in limpa


def test_glosa_vazia_nao_da_definicao():
    assert limpar_glosa("") == ""
    assert limpar_glosa('   "só um exemplo"  ') == ""


# --- comportamento na fusão ------------------------------------------------

def _wordnet(lemma, definicao):
    return SourceEntry(
        lemma=lemma, source="wordnet_pt",
        senses=[Sense(definicao, "wordnet_pt", ord=1)],
    )


def test_glosa_nao_se_encosta_a_uma_definicao_a_serio():
    """A regressão que interessa.

    `espoliar` tem definição do Dicionário Aberto. A glosa do wordnet diz
    "tosquiar a lã de", que está errada. Não pode aparecer ao lado.
    """
    boa = SourceEntry(
        lemma="espoliar", source="dicionario_aberto", pos="verbo",
        senses=[Sense("Despojar pela violência ou fraude.",
                      "dicionario_aberto", ord=1)],
    )
    resultado = merge_entries(
        [boa, _wordnet("espoliar", "Tosquiar a lã de.")], strict_backbone=False
    )
    fontes = [s.source for s in resultado.entries[0].senses]
    assert fontes == ["dicionario_aberto"]


def test_glosa_entra_quando_nao_ha_mais_nada():
    """Uma definição imperfeita e identificada é melhor do que um ecrã que
    diz 'sem definição em nenhuma fonte'."""
    vazio = SourceEntry(lemma="pança", source="hunspell_natura",
                        pos="substantivo")
    glosa = "A região do corpo de um vertebrado entre o tórax e a pelve."
    resultado = merge_entries(
        [vazio, _wordnet("pança", glosa)], strict_backbone=False
    )
    entrada = resultado.entries[0]
    assert [s.source for s in entrada.senses] == ["wordnet_pt"]
    assert entrada.senses[0].definition == glosa


def test_wordnet_continua_a_nao_criar_lemas():
    """Está em SENSE_SOURCE_PRIORITY *e* em NON_LEMMA_SOURCES.

    As duas listas respondem a perguntas diferentes — quem preenche e quem
    decide o que é uma palavra — e a segunda tem de ganhar. As entradas do
    wordnet vêm alinhadas com a WordNet inglesa; deixá-las abrir lemas
    encheria a base de palavras que nenhum dicionário português reconhece.
    """
    resultado = merge_entries(
        [_wordnet("kluge", "Uma solução desajeitada.")], strict_backbone=False
    )
    assert resultado.entries == []


def test_wordnet_e_o_ultimo_de_todos():
    """A ordem é o mecanismo todo, e tem de estar declarada num sítio só."""
    assert SENSE_SOURCE_PRIORITY[-1] == "wordnet_pt"
    assert SENSE_SOURCE_PRIORITY.index("curadoria") < \
        SENSE_SOURCE_PRIORITY.index("wordnet_pt")
    assert "wordnet_pt" in FILL_ONLY_SOURCES
    assert "curadoria" not in FILL_ONLY_SOURCES


def test_escrever_a_definicao_a_mao_expulsa_a_glosa():
    """Se te deste ao trabalho de a escrever, a máquina cala-se.

    Não é só uma questão de ordem de apresentação: sendo o wordnet fonte de
    preenchimento, uma linha no `curadoria.csv` faz a glosa traduzida
    desaparecer da entrada por completo.
    """
    vazio = SourceEntry(lemma="pança", source="hunspell_natura")
    curada = SourceEntry(
        lemma="pança", source="curadoria",
        senses=[Sense("Barriga grande e proeminente.", "curadoria", ord=1)],
    )
    resultado = merge_entries(
        [vazio, curada,
         _wordnet("pança", "A região do corpo entre o tórax e a pelve.")],
        strict_backbone=False,
    )
    fontes = [s.source for s in resultado.entries[0].senses]
    assert fontes == ["curadoria"]
