package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.jorges15.palavrame.data.Lingua

/**
 * O mínimo de palavras para jogar conta-se por língua (1.30): a primeira
 * palavra inglesa não entra no jogo nem nos lembretes, só à terceira.
 */
class AlvosTest {

    private fun cand(lemma: String, lingua: String) =
        Candidata(lemma, "substantivo", "Uma definição qualquer de $lemma.", lingua = lingua)

    private fun mapa(vararg c: Candidata) = c.associateBy { chaveDe(it.lemma, it.lingua) }

    @Test
    fun `tres portuguesas e uma inglesa so jogam as portuguesas`() {
        val todas = mapa(
            cand("soturno", Lingua.PT), cand("esgar", Lingua.PT), cand("exaurir", Lingua.PT),
            cand("bribe", Lingua.EN),
        )
        val alvos = alvosPossiveis(todas)
        assertEquals(setOf("soturno", "esgar", "exaurir"), alvos.values.map { it.lemma }.toSet())
    }

    @Test
    fun `a terceira inglesa poe o ingles no jogo`() {
        val todas = mapa(
            cand("soturno", Lingua.PT), cand("esgar", Lingua.PT), cand("exaurir", Lingua.PT),
            cand("bribe", Lingua.EN), cand("payoff", Lingua.EN), cand("inducement", Lingua.EN),
        )
        assertEquals(6, alvosPossiveis(todas).size)
    }

    @Test
    fun `duas e duas nao chegam para nada`() {
        val todas = mapa(
            cand("soturno", Lingua.PT), cand("esgar", Lingua.PT),
            cand("bribe", Lingua.EN), cand("payoff", Lingua.EN),
        )
        assertTrue(alvosPossiveis(todas).isEmpty())
        // O "ainda não dá" diz quantas tem a língua com mais, não a soma.
        assertEquals(2, maisNumaLingua(todas))
    }
}
