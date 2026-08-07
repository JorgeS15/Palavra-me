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
}
