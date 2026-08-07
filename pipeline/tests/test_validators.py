"""Testes da validação automática de exemplos gerados (plano 5.3).

O caso central é o que o plano documenta: para `ensonado` (adjetivo), o
Priberam gerou *"O ensonado sonhou longamente ao almoço, debaixo do
cobertor"* — substantiva a palavra. "É gramaticalmente defensável e
pedagogicamente inútil. É exatamente o tipo de erro que a validação
automática apanha." Este ficheiro é a prova de que apanha.
"""

from __future__ import annotations

from palavrame.generate import validate_example
from palavrame.generate.runner import EchoBackend, generate_examples, _clean
from palavrame.schema import Form, MergedEntry, Sense

FORMAS_ENSONADO = ["ensonado", "ensonados", "ensonada", "ensonadas"]
DEF_ENSONADO = "Que tem sono; que está meio a dormir."


def test_aceita_adjetivo_bem_usado():
    result = validate_example(
        "O miúdo ensonado arrastou-se até à cozinha.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
    )
    assert result.ok, result.reasons


def test_rejeita_o_erro_do_priberam():
    result = validate_example(
        "O ensonado sonhou longamente ao almoço, debaixo do cobertor.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
    )
    assert not result.ok
    assert any("como nome" in r for r in result.reasons)


def test_rejeita_frase_sem_a_palavra():
    result = validate_example(
        "O rapaz estava com muito sono e foi deitar-se cedo.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
    )
    assert not result.ok
    assert any("não contém" in r for r in result.reasons)


def test_aceita_flexao_conhecida():
    result = validate_example(
        "Os miúdos ensonados nem deram pelo despertador.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
    )
    assert result.ok, result.reasons


def test_rejeita_copia_da_definicao():
    definicao = "Abertura na parede de um edifício, para dar luz e ar."
    result = validate_example(
        f"A janela é uma abertura na parede de um edifício para dar luz.",
        "janela", "substantivo", definicao, ["janela", "janelas"],
    )
    assert not result.ok
    assert any("repete a definição" in r for r in result.reasons)


def test_definicao_curta_nao_dispara_falso_positivo():
    # Definição de duas palavras não pode invalidar meia frase.
    result = validate_example(
        "A janela da sala dava para o pátio das traseiras.",
        "janela", "substantivo", "Abertura.", ["janela"],
    )
    assert result.ok, result.reasons


def test_rejeita_comprimento_fora_do_intervalo():
    curta = validate_example("A janela.", "janela", "substantivo", "Abertura.", ["janela"])
    assert not curta.ok
    assert any("comprimento" in r for r in curta.reasons)

    longa = validate_example(
        "A janela " + "muito grande e antiga " * 12,
        "janela", "substantivo", "Abertura.", ["janela"],
    )
    assert not longa.ok


def test_substantivo_com_determinante_nao_e_rejeitado():
    # A heurística de nominalização só se aplica a adjetivos.
    result = validate_example(
        "A janela abriu-se de repente com o vento da tarde.",
        "janela", "substantivo", "Abertura na parede.", ["janela"],
    )
    assert result.ok, result.reasons


def test_adjetivo_depois_do_nome_nao_e_rejeitado():
    # "o miúdo ensonado bocejou" — determinante, nome, adjetivo, verbo.
    # A palavra não vem logo a seguir ao determinante, portanto está bem usada.
    result = validate_example(
        "O miúdo ensonado bocejou durante a aula toda.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
    )
    assert result.ok, result.reasons


def test_pos_checker_externo_tem_precedencia():
    def sempre_errado(sentence, lemma, pos):
        return False

    result = validate_example(
        "O miúdo ensonado arrastou-se até à cozinha.",
        "ensonado", "adjetivo", DEF_ENSONADO, FORMAS_ENSONADO,
        pos_checker=sempre_errado,
    )
    assert not result.ok
    assert any("não usa a palavra como adjetivo" in r for r in result.reasons)


# --- limpeza da resposta do modelo -----------------------------------------


def test_limpeza_tira_prefixos_e_aspas():
    assert _clean('Frase: «O miúdo ensonado bocejou.»') == "O miúdo ensonado bocejou."
    assert _clean('- O gato dormiu.') == "O gato dormiu."


def test_limpeza_fica_pela_primeira_frase():
    assert _clean("A janela abriu. Depois fechou.") == "A janela abriu."


# --- circuito completo de geração ------------------------------------------


def _entrada():
    return MergedEntry(
        lemma="ensonado",
        pos="adjetivo",
        senses=[Sense(DEF_ENSONADO, "dicionario_aberto", ord=1)],
        forms=[Form(f) for f in FORMAS_ENSONADO],
    )


def test_gerador_repete_ate_passar():
    backend = EchoBackend([
        "O ensonado sonhou longamente ao almoço.",   # rejeitado: nominalizado
        "sem a palavra nenhuma aqui dentro nesta frase",  # rejeitado: ausente
        "O miúdo ensonado arrastou-se até à cozinha.",    # aceite
    ])
    candidatos = generate_examples([_entrada()], backend)
    assert len(candidatos) == 1
    assert candidatos[0].status == "pendente"
    assert candidatos[0].attempts == 3
    assert "arrastou-se" in candidatos[0].sentence


def test_gerador_desiste_e_marca_rejeitado():
    backend = EchoBackend(lambda prompt: "O ensonado sonhou longamente ao almoço.")
    candidatos = generate_examples([_entrada()], backend)
    assert candidatos[0].status == "rejeitado"
    assert candidatos[0].sentence == ""
    assert candidatos[0].rejection_reasons


def test_gerado_e_sempre_marcado():
    backend = EchoBackend(lambda p: "O miúdo ensonado arrastou-se até à cozinha.")
    candidato = generate_examples([_entrada()], backend)[0]
    exemplo = candidato.to_example()
    # Plano 10.8: toda a saída de LLM é marcada. Sem exceções.
    assert exemplo.generated is True
    assert exemplo.source == "amalia"


def test_nao_gera_onde_ja_ha_exemplo_real():
    from palavrame.schema import Example

    entrada = _entrada()
    entrada.examples = [Example("Frase real do Tatoeba.", "tatoeba", "1", sense_ord=1)]
    backend = EchoBackend(lambda p: "O miúdo ensonado arrastou-se até à cozinha.")
    assert generate_examples([entrada], backend) == []


def test_prompt_leva_a_acecao_especifica():
    from palavrame.generate import build_example_prompt

    prompt = build_example_prompt("ensonado", "adjetivo", DEF_ENSONADO)
    assert DEF_ENSONADO in prompt          # a aceção, não só o lema
    assert "ensonado" in prompt
    assert "ADJETIVO" in prompt            # exige a classe gramatical
    assert "NÃO a uses" in prompt          # e proíbe explicitamente o erro
