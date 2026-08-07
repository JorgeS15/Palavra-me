"""Normalização de texto português.

`normalize()` é a função mais importante do pipeline: define a chave sob a
qual tudo é indexado e pesquisado. A app faz exatamente a mesma normalização
sobre o que o utilizador escreve, por isso qualquer alteração aqui é uma
alteração de formato da base de dados.
"""

from __future__ import annotations

import re
import unicodedata

# Mantém-se o hífen (couve-flor, dar-se-á) e o apóstrofo (d'água); tudo o
# resto que não seja letra ou dígito vira espaço e depois desaparece.
_LIMPEZA = re.compile(r"[^\w\-']+", re.UNICODE)
_ESPACOS = re.compile(r"\s+")
# `_palavra_` — itálico do Dicionário Aberto.
_ITALICO = re.compile(r"_([^_]+)_")

# --- ruído editorial do Dicionário Aberto ----------------------------------
#
# O dicionário de 1913 é uma obra de erudição e escreve como tal: a definição
# vem seguida da fonte onde o autor foi buscar o abono. Isso é do dicionário,
# não da palavra, e chegava intacto ao ecrã da app em 8 821 aceções:
#
#     Irritação, agastamento. Cf. Filinto, XIII, 86.
#     Rombo. Cp. chamorro.
#     Matar. (Colhido em Vila-Real)
#
# `Cf.` remete para uma obra, `Cp.` manda comparar com outra palavra, e
# `(Colhido em X)` diz onde o termo foi recolhido. Nenhum dos três ajuda quem
# está a ler um romance e quer saber o que a palavra significa.
_CITACAO = re.compile(
    r"\s*\(?\s*(?:Cf\.|Cp\.|Colhido\s+em)\s[^)]*\)?\s*$",
    re.IGNORECASE,
)

# Trema, abolido em Portugal em 1945 e no Brasil em 1990: `reünir`,
# `Freqüente`, `agüenta`. São 54 aceções, mas destoam à vista.
_TREMA = str.maketrans("üïÜÏ", "uiUI")


def strip_accents(s: str) -> str:
    """Remove diacríticos, preservando o resto.

    'ção' -> 'cao', 'pôr' -> 'por'. O ç decompõe-se em c + cedilha em NFD,
    por isso não precisa de tratamento especial.
    """
    decomposed = unicodedata.normalize("NFD", s)
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def normalize(s: str) -> str:
    """Forma canónica de pesquisa: minúsculas, sem acentos, sem pontuação.

    É o que vai para as colunas `normalized` de `lemmas` e `forms`.
    """
    s = strip_accents(s.strip().lower())
    s = _LIMPEZA.sub(" ", s)
    return _ESPACOS.sub(" ", s).strip()


def clean_definition(s: str) -> str:
    """Limpa uma definição vinda de uma fonte.

    Colapsa espaços e tira pontuação órfã nas pontas. Não reescreve nada — a
    modernização de fraseado é um passo explícito e marcado (`modernized`),
    nunca um efeito secundário da limpeza.
    """
    s = _ESPACOS.sub(" ", s.replace("\n", " ")).strip()
    # O Dicionário Aberto marca itálicos com sublinhados — `_autor_` — que
    # são marcação da fonte, não texto. Chegavam intactos ao ecrã da app em
    # 48 mil aceções (13% do total). Tira-se o sublinhado e guarda-se a
    # palavra: `De _autor_.` passa a `De autor.`
    s = _ITALICO.sub(r"\1", s)
    s = s.replace("_", "")
    s = _ESPACOS.sub(" ", s).strip()

    # Remissões e abonos de 1913 — ver `_CITACAO`. Aplica-se em ciclo porque
    # há definições com duas seguidas ("... Cp. fagulha. Cf. Camilo, 11.").
    while True:
        cortada = _CITACAO.sub("", s).strip()
        if cortada == s:
            break
        s = cortada

    # A remissão pode estar dentro do parêntese que ela própria fecha —
    # "(Talvez por afagulhado, de fagulha. Cp. fagulha)" — e o corte leva o
    # fecho com ele. Repõe-se, para não deixar o parêntese aberto no ecrã.
    if s.count("(") > s.count(")"):
        s += ")"
    # E o inverso: um fecho sem abertura é cauda de alguma coisa que já foi
    # levada — tipicamente a marca de domínio, extraída depois desta limpeza,
    # que deixava `adicar` com a aceção `ind.)`. Corre-se esta limpeza outra
    # vez a seguir a essa extração, e é aqui que o parêntese órfão cai.
    while s.endswith(")") and s.count(")") > s.count("("):
        s = s[:-1].rstrip(" .;,")

    s = s.translate(_TREMA)
    s = s.strip(" ;,:")
    # As entradas do Dicionário Aberto acabam frequentemente em ponto solto
    # após remoção de marcação; normaliza para um único ponto final.
    s = re.sub(r"\s+\.$", ".", s)

    # Maiúscula inicial. O Wikcionário escreve as definições em minúscula e o
    # Dicionário Aberto em maiúscula; lado a lado no ecrã — e sobretudo nas
    # três opções do jogo — a mistura parece descuido. 158 mil das 358 mil
    # aceções começavam em minúscula.
    if s and s[0].islower():
        s = s[0].upper() + s[1:]
    return s


def tokens(s: str) -> list[str]:
    """Palavras normalizadas de uma frase, para validação de exemplos."""
    return [t for t in normalize(s).replace("-", " ").split(" ") if t]


# --- glosas de wordnet -----------------------------------------------------
#
# As glosas do PULO foram traduzidas automaticamente do inglês da WordNet de
# Princeton, e trazem duas marcas disso: o formato de Princeton
# (`definição; "frase de exemplo"`) e, em parte delas, ortografia brasileira.
# Uma app de leitura de literatura portuguesa não pode dizer *oxigênio*.

# No Brasil escreve-se ô/ê antes de nasal seguida de vogal (oxig**ê**nio,
# sin**ô**nimo, gênero, econômico) onde em Portugal se escreve ó/é. Junta-se
# o `-éia` pré-reforma (idéia, assembléia) e o `você`. Regra deliberadamente
# estreita: rejeitar uma glosa boa custa pouco, aceitar uma brasileira
# custa a credibilidade da app.
_BRASILEIRO = re.compile(
    r"[ôê][mn][aeiouáéíóúâêôãõ]"      # oxigênio, sinônimo, gênero
    r"|\wéias?\b"                      # idéia, assembléias
    r"|\bvocês?\b",
    re.IGNORECASE,
)

# Princeton separa a definição dos exemplos com `;` e põe os exemplos entre
# aspas. São raros no PULO (33 em 117 mil) mas quando aparecem estragam.
_EXEMPLO_EM_ASPAS = re.compile(r'[;,]?\s*"[^"]*"')


def parece_do_brasil(texto: str) -> bool:
    """A glosa está em português do Brasil?

    Não é um detetor de variante a sério — é um filtro de ortografia, e só
    isso. Chega para o que se lhe pede: tirar da base as glosas que se
    denunciam à primeira leitura.
    """
    return bool(_BRASILEIRO.search(texto))


def limpar_glosa(texto: str) -> str:
    """Reduz uma glosa de wordnet a uma definição apresentável.

    Tira os exemplos entre aspas, normaliza espaços, começa por maiúscula e
    acaba em ponto — para ficar ao lado das aceções do Dicionário Aberto sem
    se distinguir pela pontuação.
    """
    s = _EXEMPLO_EM_ASPAS.sub("", texto)
    s = clean_definition(s)
    if not s:
        return ""
    if s[0].islower():
        s = s[0].upper() + s[1:]
    if s[-1] not in ".!?":
        s += "."
    return s
