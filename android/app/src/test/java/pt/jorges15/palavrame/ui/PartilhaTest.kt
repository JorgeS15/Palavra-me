package pt.jorges15.palavrame.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O ajuste e o corte da palavra na imagem de partilha. */
class PartilhaTest {

    private fun cortes(p: String) = pontosDeCorte(p).map { partir(p, it).first }

    @Test
    fun `parte nas silabas simples`() {
        assertEquals(listOf("so-", "sotur-"), cortes("soturno"))
    }

    @Test
    fun `nao separa br, ch, lh, nh nem qu`() {
        val abraco = cortes("abraçar")
        assertTrue(abraco.contains("abra-"))
        assertFalse(abraco.contains("ab-"))
        assertTrue(cortes("malhado").contains("ma-"))
        assertFalse(cortes("malhado").contains("mal-"))
        assertTrue(cortes("chocolate").contains("cho-"))
        assertFalse(cortes("aquecer").contains("aq-"))
    }

    @Test
    fun `tres consoantes partem antes do grupo inseparavel`() {
        val c = cortes("instrutor")
        assertTrue(c.contains("ins-"))
        assertFalse(c.contains("in-"))
    }

    @Test
    fun `o exemplo do design`() {
        assertTrue(cortes("inconstitucionalissimamente").contains("inconstitucio-"))
    }

    @Test
    fun `locucoes partem no espaco, sem hifen`() {
        assertEquals("de" to "facto", partir("de facto", 2))
        assertTrue(pontosDeCorte("de facto").contains(2))
    }

    // Uma medida falsa: 55 dp por letra a 100 sp.
    private val medida: (String) -> Float = { it.length * 55f }

    @Test
    fun `palavra curta fica no maximo`() {
        val (linhas, tamanho) = ajustarPalavra("parco", 300f, 36f, 64f, medida)
        assertEquals(listOf("parco"), linhas)
        assertEquals(64f, tamanho, 0.01f)
    }

    @Test
    fun `palavra media encolhe numa linha`() {
        val (linhas, tamanho) = ajustarPalavra("rebarbativo", 300f, 36f, 64f, medida)
        assertEquals(1, linhas.size)
        assertTrue(tamanho in 36f..64f)
    }

    @Test
    fun `palavra muito comprida parte em duas e nunca fica abaixo do minimo`() {
        val (linhas, tamanho) =
            ajustarPalavra("inconstitucionalissimamente", 300f, 36f, 64f, medida)
        assertEquals(2, linhas.size)
        assertTrue(linhas[0].endsWith("-"))
        assertEquals("inconstitucionalissimamente", linhas[0].dropLast(1) + linhas[1])
        assertTrue(tamanho >= 36f)
    }
}
