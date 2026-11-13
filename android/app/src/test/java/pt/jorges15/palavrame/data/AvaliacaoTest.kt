package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O pedido de avaliação: à 5.ª, 10.ª, 20.ª, 50.ª e 100.ª palavra, uma vez
 * cada. ("Avaliar" e "Não voltar a perguntar" desligam tudo; isso vive nas
 * Preferencias e não nesta regra.)
 */
class AvaliacaoTest {

    @Test
    fun `antes da 5a palavra nao pede`() {
        assertNull(marcoDeAvaliacao(total = 4, jaPedido = 0))
    }

    @Test
    fun `cada marco pede uma vez`() {
        assertEquals(5, marcoDeAvaliacao(total = 5, jaPedido = 0))
        assertNull(marcoDeAvaliacao(total = 9, jaPedido = 5))
        assertEquals(10, marcoDeAvaliacao(total = 10, jaPedido = 5))
        assertEquals(20, marcoDeAvaliacao(total = 20, jaPedido = 10))
        assertEquals(50, marcoDeAvaliacao(total = 50, jaPedido = 20))
        assertEquals(100, marcoDeAvaliacao(total = 100, jaPedido = 50))
    }

    @Test
    fun `depois da 100a nunca mais`() {
        assertNull(marcoDeAvaliacao(total = 101, jaPedido = 100))
        assertNull(marcoDeAvaliacao(total = 1000, jaPedido = 100))
    }

    @Test
    fun `quem importa muitas palavras e perguntado uma vez, pelo marco mais alto`() {
        assertEquals(50, marcoDeAvaliacao(total = 71, jaPedido = 0))
        assertNull(marcoDeAvaliacao(total = 72, jaPedido = 50))
    }
}
