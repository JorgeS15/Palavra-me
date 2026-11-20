package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** O limite diário da revisão à mão. */
class CotaManualTest {

    private val hoje = LocalDate.of(2026, 11, 15)

    @Test
    fun `sem nada usado ha o limite inteiro`() {
        assertEquals(LIMITE_MANUAL_POR_DIA, CotaManual().restantes(hoje))
    }

    @Test
    fun `cada resposta gasta uma e o limite nao desce abaixo de zero`() {
        var c = CotaManual()
        repeat(3) { c = c.usar(hoje) }
        assertEquals(LIMITE_MANUAL_POR_DIA - 3, c.restantes(hoje))
        repeat(20) { c = c.usar(hoje) }
        assertEquals(0, c.restantes(hoje))
    }

    @Test
    fun `no dia seguinte volta tudo, sem ninguem apagar nada`() {
        var c = CotaManual()
        repeat(LIMITE_MANUAL_POR_DIA) { c = c.usar(hoje) }
        assertEquals(0, c.restantes(hoje))
        val amanha = hoje.plusDays(1)
        assertEquals(LIMITE_MANUAL_POR_DIA, c.restantes(amanha))
        // E a primeira de amanhã conta a partir de zero, não de 10.
        assertEquals(LIMITE_MANUAL_POR_DIA - 1, c.usar(amanha).restantes(amanha))
    }
}
