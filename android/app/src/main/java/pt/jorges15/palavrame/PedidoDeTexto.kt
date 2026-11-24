package pt.jorges15.palavrame

/**
 * A palavra que outra app manda procurar, e por que caminho.
 *
 * - O menu de seleção de texto do Android (`PROCESS_TEXT`).
 * - O "Partilhar" (`SEND`), para os leitores que só deixam partilhar.
 * - O "procurar no dicionário" dos leitores de e-books (1.31): o ReadEra, o
 *   Moon+ Reader, o FBReader e outros chamam um dicionário pelo pedido do
 *   ColorDict (`colordict.intent.action.SEARCH`, a palavra em `EXTRA_QUERY`),
 *   pelo do Aard 2 (`aard2.lookup`) ou pela pesquisa do Android (`SEARCH`,
 *   a palavra em `query`).
 *
 * Sem nada de Android, para se testar na JVM: quem chama dá a ação e uma
 * forma de ler os extras do pedido.
 */
object PedidoDeTexto {

    const val PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
    const val SEND = "android.intent.action.SEND"
    const val COLORDICT = "colordict.intent.action.SEARCH"
    const val AARD2 = "aard2.lookup"
    const val SEARCH = "android.intent.action.SEARCH"

    private const val EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT"
    private const val EXTRA_TEXT = "android.intent.extra.TEXT"
    private const val EXTRA_QUERY_COLORDICT = "EXTRA_QUERY"
    /** `SearchManager.QUERY`. */
    private const val QUERY = "query"

    /** O texto a procurar, ou nulo se o pedido não traz nenhum (ou não é para nós). */
    fun texto(acao: String?, extra: (String) -> String?): String? {
        val chaves = when (acao) {
            PROCESS_TEXT -> listOf(EXTRA_PROCESS_TEXT)
            SEND -> listOf(EXTRA_TEXT)
            // Há leitores que mandam a palavra noutra chave do que a do
            // dicionário que imitam: tenta-se a dele primeiro, depois as outras.
            COLORDICT -> listOf(EXTRA_QUERY_COLORDICT, QUERY, EXTRA_TEXT)
            AARD2, SEARCH -> listOf(QUERY, EXTRA_QUERY_COLORDICT, EXTRA_TEXT)
            else -> return null
        }
        return chaves.firstNotNullOfOrNull { extra(it)?.trim()?.takeIf { t -> t.isNotEmpty() } }
    }
}
