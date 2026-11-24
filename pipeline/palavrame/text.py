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
# Apóstrofos tipográficos -> o simples. Os ebooks escrevem `wouldn’t` e
# `d’água` com o ’ (U+2019), e sem isto o ’ virava espaço: `wouldn t` não
# encontrava nada. A app (`Texto.kt`) faz exatamente o mesmo.
_APOSTROFOS = str.maketrans({"’": "'", "‘": "'", "ʼ": "'", "′": "'"})
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

# Ligações de wikitexto que escapam à extração do Wikcionário e chegam ao ecrã
# cruas: `[[manguito:2]]`, `[[fruto|manga]]`. Fica só o texto legível — o texto
# de apresentação quando há `|`, senão o alvo sem a âncora de secção (`:2`, `#`).
_WIKILINK = re.compile(r"\[\[(.+?)\]\]")


def _texto_de_wikilink(m: "re.Match[str]") -> str:
    interior = m.group(1)
    if "|" in interior:
        return interior.split("|")[-1]
    return re.split(r"[:#]", interior, 1)[0]


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
    s = strip_accents(s.strip().lower().translate(_APOSTROFOS))
    s = _LIMPEZA.sub(" ", s)
    return _ESPACOS.sub(" ", s).strip()


def clean_definition(s: str) -> str:
    """Limpa uma definição vinda de uma fonte.

    Colapsa espaços e tira pontuação órfã nas pontas. Não reescreve nada — a
    modernização de fraseado é um passo explícito e marcado (`modernized`),
    nunca um efeito secundário da limpeza.
    """
    s = _ESPACOS.sub(" ", s.replace("\n", " ")).strip()
    # Ligações de wikitexto que sobraram: `[[manguito:2]]` -> `manguito`.
    s = _WIKILINK.sub(_texto_de_wikilink, s)
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


def sem_trema(s: str) -> str:
    """Remove o trema, abolido no português (`reünir` -> `reunir`, `lângüido`
    -> `lânguido`). Ao contrário do acento, o trema não distingue palavras: é
    sempre grafia velha da mesma palavra."""
    return s.translate(_TREMA)


_GRAVES = str.maketrans("àèìòùÀÈÌÒÙ", "aeiouAEIOU")
_PALAVRA = re.compile(r"[^\W\d_]+(?:-[^\W\d_]+)*")
# O acento grave que ficou: a contração ("à", "às", "àquele", "àquilo"...).
_CONTRACAO = re.compile(r"^(?:às?|àquel\w*|àquil\w*)$", re.IGNORECASE)


def sem_graves(s: str) -> str:
    """Tira o acento grave da ortografia de 1911, que o de 1945 aboliu.

    O Dicionário Aberto (1913) marca com grave as vogais abertas átonas das
    derivadas: `crèdor`, `sòzinho`, `chàzeiro`, `prègar`. Hoje escreve-se
    `credor`, `sozinho`, `chazeiro`, `pregar`. Sem isto, `crèdor` era uma
    entrada à parte ao lado de `credor` (do Wikcionário), e a pesquisa por
    "credor" dava as duas.

    Fica o grave das contrações, que continua a existir: "à", "às",
    "àquele", "àquilo" ("barco à vela" não muda).
    """
    if not any(ch in s for ch in "àèìòùÀÈÌÒÙ"):
        return s
    def trocar(m: "re.Match[str]") -> str:
        palavra = m.group(0)
        return palavra if _CONTRACAO.match(palavra) else palavra.translate(_GRAVES)
    return _PALAVRA.sub(trocar, s)


def tokens(s: str) -> list[str]:
    """Palavras normalizadas de uma frase, para validação de exemplos."""
    return [t for t in normalize(s).replace("-", " ").split(" ") if t]


def surface_words(s: str) -> list[str]:
    """Palavras de uma frase, com acentos e **com a caixa original**.

    Preserva a maiúscula para se poder distinguir um nome próprio a meio da
    frase (*Nené*, o jogador) do substantivo comum (*nené*). É NFC para a
    grafia bater certo com a das formas na fusão.
    """
    limpa = _LIMPEZA.sub(" ", unicodedata.normalize("NFC", s).translate(_TREMA))
    return [t for t in limpa.replace("-", " ").split(" ") if t]


def nomes_proprios(palavras: list[str]) -> set[str]:
    """Palavras (minúsculas) que na frase só aparecem como nome próprio.

    Uma palavra capitalizada **a meio** da frase, e que nunca aparece em
    minúscula nem no início, é quase de certeza um nome próprio — *Nené* o
    jogador, e não o substantivo *nené*. Ligar um exemplo desses ao
    substantivo comum não ensina nada. Maiúsculas por inteiro (siglas, ADN)
    não contam.
    """
    proprias: set[str] = set()
    comuns: set[str] = set()
    for i, w in enumerate(palavras):
        base = w.lower()
        if i > 0 and w[:1].isupper() and w != w.upper():
            proprias.add(base)
        else:
            comuns.add(base)
    return proprias - comuns


def surface_tokens(s: str) -> list[str]:
    """Palavras de uma frase em minúsculas, mas **com os acentos**.

    Ao contrário de `tokens`, não remove diacríticos. Serve para ligar um
    exemplo ao lema pela forma exata: "bebe" (flexão de *beber*) e "bebé" (o
    nome) normalizam para a mesma coisa, mas são palavras diferentes, e uma
    frase com "bebe" não pode ir parar à entrada de "bebé". A ligação por
    forma normalizada colava-as; esta preserva a distinção.
    """
    return [w.lower() for w in surface_words(s)]


# Sinais de que uma "frase" é na verdade uma fórmula ou uma remissão, e não um
# exemplo de uso: `reciclar + -abilidade = reciclabilidade`, `a → b`.
_FORMULA = re.compile(r"\s[=+]\s|→|::")
_PALAVRA = re.compile(r"[^\W\d_]{2,}")


def frase_utilizavel(texto: str, minimo: int = 20, maximo: int = 140) -> bool:
    """Serve esta frase para mostrar como exemplo de uso?

    O Wikcionário aceita como exemplo qualquer coisa que um editor lá tenha
    posto, e nem tudo é uma frase: apanhava fragmentos de verso (*"26 poetas
    hoje"*, cortado a meio), fórmulas de derivação, listas de números, títulos
    de livros e palavras soltas. Nenhuma dessas ensina a palavra a quem a
    encontrou a ler.

    As regras são deliberadamente tolerantes com a **pontuação**: muitas frases
    boas do Wikcionário não acabam em ponto (*"o prisioneiro foi atado"*), e
    exigi-lo deitava fora exemplos perfeitamente úteis. O que se exige é que
    aquilo *pareça* uma frase — comprimento de leitura, palavras que cheguem,
    e texto e não símbolos.
    """
    t = texto.strip()
    if not (minimo <= len(t) <= maximo):
        return False
    # Menos de quatro palavras de verdade não demonstra uso — é um termo, um
    # título, um par ("cebolada, papelada"), não uma frase.
    if len(_PALAVRA.findall(t)) < 4:
        return False
    # Dominado por dígitos ou símbolos: "1, 2, 3, ..., 99, 100".
    if sum(ch.isalpha() for ch in t) < len(t) * 0.55:
        return False
    if t == t.upper():                     # TÍTULO OU CABEÇALHO EM MAIÚSCULAS
        return False
    if _FORMULA.search(t):
        return False
    return True


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

    Não é um detetor de variante a sério — é um crivo estreito de marcas
    ortográficas inequívocas (ver `_BRASILEIRO`). Um falso positivo perde uma
    glosa boa, o que é barato; um falso negativo põe *oxigênio* no ecrã de uma
    app de literatura portuguesa, o que custa a credibilidade. Na dúvida,
    rejeita-se.
    """
    return bool(_BRASILEIRO.search(texto))


def limpar_glosa(texto: str) -> str:
    """Dá a uma glosa do wordnet o aspeto de uma definição de dicionário.

    Tira o exemplo entre aspas do formato de Princeton (*definição; "frase"*),
    e o que sobra ganha inicial maiúscula e um ponto final — para, ao lado das
    aceções de 1913, não se distinguir pela pontuação. Uma glosa que era só um
    exemplo entre aspas fica vazia, e nesse caso não dá definição nenhuma.
    """
    t = _EXEMPLO_EM_ASPAS.sub("", texto).strip()
    t = t.rstrip(" ;,.")
    if not t:
        return ""
    if t[0].islower():
        t = t[0].upper() + t[1:]
    return t + "." 