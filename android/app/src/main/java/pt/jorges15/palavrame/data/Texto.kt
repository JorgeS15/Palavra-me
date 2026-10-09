package pt.jorges15.palavrame.data

import java.text.Normalizer

/**
 * Normalização de texto — tem de dar exatamente o mesmo resultado que
 * `palavrame.text.normalize` no pipeline.
 *
 * Se as duas divergirem, a pesquisa deixa de encontrar o que está na base:
 * a coluna `forms.normalized` foi escrita pela versão em Python, e é contra
 * ela que esta compara. É a costura mais frágil entre o pipeline e a app,
 * e por isso está isolada aqui, com testes dos dois lados.
 */
object Texto {

    private val marcasDeAcento = Regex("\\p{Mn}+")

    // Espelham `_LIMPEZA` e `_ESPACOS` em palavrame/text.py. O `\w` do
    // Python em modo Unicode é letra, dígito ou underscore — daí \p{L}\p{N}_.
    private val limpeza = Regex("[^\\p{L}\\p{N}_\\-']+")
    private val espacos = Regex("\\s+")

    fun normalizar(texto: String): String {
        val semAcentos = Normalizer
            .normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(marcasDeAcento, "")
        return espacos.replace(limpeza.replace(semAcentos, " "), " ").trim()
    }

    /**
     * Limpa o que vem de uma seleção num livro: aspas, pontuação agarrada,
     * hífen de translineação. O utilizador seleciona `«ensonados,` e a app
     * tem de procurar `ensonados`.
     */
    fun limparSelecao(bruto: String): String =
        bruto.trim()
            .removeSurrounding("«", "»")
            .trim { !it.isLetter() && it != '-' && it != '\'' }
            .substringBefore(' ')

    /**
     * A forma a guardar quando se regista uma palavra que o dicionário não tem.
     *
     * Limpa como uma seleção (aspas, pontuação, só a primeira palavra) e tira
     * a maiúscula de início de frase: "Ensonados," passa a "ensonados". Sem
     * isto ficavam duas entradas que só diferiam na maiúscula, o CSV submetido
     * vinha sujo, e a palavra nunca saía da lista "em falta". Siglas e nomes
     * com mais maiúsculas (ONU, McDonald) ficam como estão.
     */
    fun paraRegisto(bruto: String): String {
        val limpo = limparSelecao(bruto)
        if (limpo.length < 2) return limpo
        val soInicial = limpo[0].isUpperCase() && limpo.drop(1).none { it.isUpperCase() }
        return if (soInicial) limpo[0].lowercase() + limpo.drop(1) else limpo
    }

    /**
     * De uma seleção com várias palavras (frase enviada do leitor), a mais
     * provável de ser a que se quer procurar: a **mais comprida**. As palavras
     * que fazem parar a leitura costumam ser as longas, e "o rosto ensonado"
     * deve procurar `ensonado`, não `o`. Uma seleção de uma só palavra
     * devolve-a. A frase inteira guarda-se à parte, como contexto.
     */
    fun palavraPrincipal(bruto: String): String =
        bruto.split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetter() && c != '-' && c != '\'' } }
            // Só palavras: um endereço, um número de página ou "p.123" que
            // venham agarrados à partilha não podem ganhar por serem compridos.
            .filter { t -> t.isNotBlank() && t.all { it.isLetter() || it == '-' || it == '\'' } }
            .maxByOrNull { it.length }
            .orEmpty()

    private val enderecos = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)
    private val aspas = "\"'“”‘’«»„"

    /**
     * Limpa o texto que chega pelo "Partilhar" de outra app.
     *
     * Ao contrário da seleção direta, a partilha vem muitas vezes embrulhada: há
     * leitores que juntam um link para o livro ou põem a passagem entre aspas.
     * Tira-se o link, as aspas das pontas e os espaços a mais; o resto é tratado
     * como uma seleção normal.
     */
    fun limparPartilha(bruto: String): String =
        espacos.replace(enderecos.replace(bruto, " "), " ")
            .trim()
            .trim { it in aspas || it.isWhitespace() }
}
