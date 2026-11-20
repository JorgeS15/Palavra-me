"""Rótulos de variante/registo nas aceções do Wikcionário.

Decisão do Jorge: não deitar fora o vocabulário brasileiro nem os
estrangeirismos, mas avisar o leitor. O rótulo entra em `domains`, que a app
mostra por cima da definição.

Mede-se contra o dump real que só ~1,4% das aceções têm etiqueta de região —
no Wikcionário o brasileiro é o padrão não marcado — por isso o rótulo é
parcial de propósito: marca o que está etiquetado, não o resto.
"""

from __future__ import annotations

from palavrame.sources.wikcionario import _entries_from_wiktextract


def _linha(word, senses, **extra):
    return {"word": word, "lang_code": "pt", "senses": senses, **extra}


def test_acecao_brasileira_fica_e_ganha_rotulo():
    d = _linha("ônibus", [
        {"glosses": ["veículo de transporte coletivo"], "tags": ["Brazil"]},
    ])
    entrada = next(e for e in _entries_from_wiktextract(d, "wikcionario")
                   if e.lemma == "ônibus")
    assert len(entrada.senses) == 1
    assert "Brasil" in entrada.senses[0].domains


def test_estrangeirismo_fica_e_ganha_rotulo():
    d = _linha("timer", [
        {"glosses": ["temporizador"], "raw_tags": ["estrangeirismo"]},
    ])
    entrada = next(e for e in _entries_from_wiktextract(d, "wikcionario")
                   if e.lemma == "timer")
    assert "Estrangeirismo" in entrada.senses[0].domains


def test_rotulo_junta_se_ao_dominio_tematico():
    d = _linha("chope", [
        {"glosses": ["cerveja de pressão"], "tags": ["Brazil"],
         "topics": ["Bebidas"]},
    ])
    entrada = next(e for e in _entries_from_wiktextract(d, "wikcionario")
                   if e.lemma == "chope")
    assert entrada.senses[0].domains == ["Brasil", "Bebidas"]


def test_acecao_portuguesa_normal_nao_ganha_rotulo():
    d = _linha("janela", [
        {"glosses": ["abertura na parede"]},
        {"glosses": ["área retangular no ecrã"], "raw_tags": ["Informática"]},
    ])
    entrada = next(e for e in _entries_from_wiktextract(d, "wikcionario")
                   if e.lemma == "janela")
    assert entrada.senses[0].domains == []
    assert "Brasil" not in entrada.senses[1].domains


def test_palavra_brasileira_sem_etiqueta_fica_sem_rotulo():
    # Limitação medida: 'nené' não tem etiqueta, portanto não leva rótulo.
    # Fica na base como qualquer outra — não temos como saber que é brasileira.
    d = _linha("nené", [{"glosses": ["criança pequenina"]}])
    entrada = next(e for e in _entries_from_wiktextract(d, "wikcionario")
                   if e.lemma == "nené")
    assert entrada.senses[0].domains == []
