package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NiveisTest {

    @Test
    fun `o nivel vem dos acertos`() {
        assertEquals("Nível 1 · Folheador", Niveis.para(0).rotulo)
        assertEquals(1, Niveis.para(9).numero)
        assertEquals(2, Niveis.para(10).numero)
        assertEquals("Nível 3 · Leitor atento", Niveis.para(37).rotulo)
        assertEquals(7, Niveis.para(5000).numero)
    }

    @Test
    fun `o seguinte e quanto falta`() {
        assertEquals(4, Niveis.seguinte(37)!!.numero)
        assertEquals(50, Niveis.seguinte(37)!!.desde)
        assertNull(Niveis.seguinte(400))   // já no último
    }

    @Test
    fun `so se sobe ao passar a meta`() {
        assertEquals("Bibliófilo", Niveis.subiu(49, 50)!!.nome)
        assertNull(Niveis.subiu(50, 51))
        assertNull(Niveis.subiu(12, 12))   // uma resposta errada não mexe
    }

    @Test
    fun `os niveis estao por ordem e comecam no zero`() {
        assertEquals(0, Niveis.TODOS.first().desde)
        Niveis.TODOS.zipWithNext().forEach { (a, b) ->
            assertEquals(a.numero + 1, b.numero)
            assert(b.desde > a.desde)
        }
    }
}
