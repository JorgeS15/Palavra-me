package pt.jorges15.palavrame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A palavra chega por cada um dos caminhos dos leitores de e-books. */
class PedidoDeTextoTest {

    private fun extras(vararg pares: Pair<String, String>): (String) -> String? =
        pares.toMap()::get

    @Test
    fun `menu de selecao e partilhar`() {
        assertEquals("ensonado", PedidoDeTexto.texto(
            PedidoDeTexto.PROCESS_TEXT, extras("android.intent.extra.PROCESS_TEXT" to "ensonado")))
        assertEquals("ensonado", PedidoDeTexto.texto(
            PedidoDeTexto.SEND, extras("android.intent.extra.TEXT" to " ensonado ")))
    }

    @Test
    fun `o pedido do ColorDict, que o ReadEra e outros leitores usam`() {
        assertEquals("soturno", PedidoDeTexto.texto(
            PedidoDeTexto.COLORDICT, extras("EXTRA_QUERY" to "soturno")))
        // Um leitor que manda a palavra na chave da pesquisa do Android.
        assertEquals("soturno", PedidoDeTexto.texto(
            PedidoDeTexto.COLORDICT, extras("query" to "soturno")))
    }

    @Test
    fun `aard2 e a pesquisa do Android`() {
        assertEquals("esgar", PedidoDeTexto.texto(PedidoDeTexto.AARD2, extras("query" to "esgar")))
        assertEquals("esgar", PedidoDeTexto.texto(PedidoDeTexto.SEARCH, extras("query" to "esgar")))
    }

    @Test
    fun `sem texto, ou outro pedido, nao ha nada a procurar`() {
        assertNull(PedidoDeTexto.texto(PedidoDeTexto.COLORDICT, extras("EXTRA_QUERY" to "  ")))
        assertNull(PedidoDeTexto.texto("android.intent.action.MAIN", extras("query" to "x")))
        assertNull(PedidoDeTexto.texto(null, extras()))
    }
}
