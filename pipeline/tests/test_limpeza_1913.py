"""Limpeza do ruído editorial do Dicionário Aberto.

Todos estes defeitos foram encontrados da mesma maneira: a jogar o modo jogo
com a coleção real do Jorge. Uma pergunta de escolha múltipla põe três
definições lado a lado no ecrã, e o que numa entrada isolada passa
despercebido salta à vista quando está ao lado de outras duas.

O dicionário de 1913 é uma obra de erudição e escreve como tal — remete para
os autores onde foi buscar o abono, marca a origem entre parênteses, usa a
ortografia do seu tempo. Nada disso ajuda quem está a ler um romance e quer
saber o que a palavra quer dizer.
"""

from __future__ import annotations

from palavrame.sources.dicionario_aberto import _acecoes_do_def
from palavrame.text import clean_definition


# --- remissões e abonos ----------------------------------------------------

def test_tira_a_remissao_bibliografica():
    """`Cf.` remete para a obra onde o autor foi buscar o exemplo.

    Eram 8 543 aceções — 2,4% da base — com a referência colada ao texto.
    """
    assert clean_definition("Irritação, agastamento. Cf. Filinto, XIII, 86.") \
        == "Irritação, agastamento."
    assert clean_definition("À grande; ostentosamente. Cf. Filinto, D. Manuel, III, 212.") \
        == "À grande; ostentosamente."


def test_tira_a_remissao_comparativa():
    """`Cp.` manda comparar com outra palavra. Também não é definição."""
    assert clean_definition("Rombo. Cp. chamorro.") == "Rombo."


def test_tira_o_local_de_recolha():
    assert clean_definition("Matar. (Colhido em Vila-Real)") == "Matar."
    assert clean_definition("O mesmo que bestunto. (Colhido em Vila-Real)") \
        == "O mesmo que bestunto."


def test_acecao_que_e_so_uma_citacao_desaparece():
    """`Cf. Cancion. Ger., I, 146.` não define coisa nenhuma."""
    assert clean_definition("Cf. Cancion. Ger., I, 146.") == ""


def test_remissao_dentro_do_parentese_nao_deixa_o_parentese_aberto():
    limpa = clean_definition("(Talvez por afagulhado, de fagulha. Cp. fagulha)")
    assert limpa.count("(") == limpa.count(")")
    assert "Cp." not in limpa


# --- ortografia ------------------------------------------------------------

def test_tira_o_trema():
    """Abolido em Portugal em 1945. `reünir` numa app de 2026 é estranho."""
    assert clean_definition("Juntar; reünir em colecção.") == "Juntar; reunir em colecção."
    assert clean_definition("Freqüente.") == "Frequente."
    assert clean_definition("Retribuïção do agente.") == "Retribuição do agente."


def test_maiuscula_inicial():
    """O Wikcionário escreve em minúscula, o Dicionário Aberto em maiúscula.

    Lado a lado — e sobretudo nas três opções do jogo — a mistura parecia
    descuido. Eram 158 mil aceções em minúscula.
    """
    assert clean_definition("que não dá resultado; infecundo") \
        == "Que não dá resultado; infecundo"
    assert clean_definition("À grande.") == "À grande."      # já acentuada, não mexe


def test_nao_estraga_o_que_ja_estava_bem():
    assert clean_definition("Cárcere térreo ou subterrâneo, escuro e húmido.") \
        == "Cárcere térreo ou subterrâneo, escuro e húmido."


# --- parênteses partidos ao meio -------------------------------------------

def test_nao_parte_acecao_dentro_de_parenteses():
    """A etimologia de 1913 atravessa linhas e não pode ser cortada ao meio.

    Era o defeito mais numeroso: **5 105 aceções**, 1,4% da base. O
    `bruxulear` tinha como segundo significado `cast. grujulear)`, com o
    parêntese órfão à vista.
    """
    bruto = "Tremeluzir; brilhar froixamente.\n(Cp. cast. grujulear)"
    acecoes = _acecoes_do_def(bruto)
    assert acecoes == ["Tremeluzir; brilhar froixamente."]


def test_continua_a_partir_acecoes_a_serio():
    """A correção não pode desfazer o que a divisão por linhas faz bem."""
    assert _acecoes_do_def("Magro.\nPálido.\nAmortecido.") \
        == ["Magro.", "Pálido.", "Amortecido."]


def test_fragmento_com_parentese_orfao_nao_e_acecao():
    assert _acecoes_do_def("Ver.\nind.)") == ["Ver."]


def test_o_que_sobra_e_pouco_e_esta_medido():
    """Honestidade sobre o que estas regras *não* apanham.

    Quando a anotação inteira é um parêntese numa linha só — `(Gír. Or.
    ind.)` no `adicar` — a marca de domínio sai pelo `_split_domain` e o
    resto (`Ind`) fica como aceção. São cerca de vinte casos em 209 mil, e
    resolvê-los exigiria decidir que uma aceção inteira é anotação, o que dá
    mais falsos positivos do que os vinte que corrige. Fica registado em vez
    de mal resolvido.
    """
    assert _acecoes_do_def("(Gír. Or. ind.)") == ["(Gír. Or. ind.)"]


def test_parentese_por_fechar_no_fim_nao_engole_a_acecao():
    """Fonte truncada: mais vale a aceção com o parêntese reposto do que nada."""
    acecoes = _acecoes_do_def("Coisa qualquer.\n(Do lat. aliquid")
    assert acecoes[0] == "Coisa qualquer."
    assert len(acecoes) == 2 and acecoes[1].endswith(")")
