package pt.jorges15.palavrame.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** As classes de uma palavra, a partir da classe de cada aceção (1.21). */
class EntradaTest {

    private fun acecao(id: Long, pos: String?) =
        Acecao(id, id.toInt(), "Definição $id.", emptyList(), "Wikcionário", false, pos)

    private fun entrada(pos: String, vararg classes: String?) = Entrada(
        lemmaId = 1, lemma = "computador", pos = pos, silabas = null,
        acecoes = classes.mapIndexed { i, c -> acecao(i + 1L, c) },
        exemplos = emptyList(), relacionadas = emptyList(),
    )

    @Test
    fun `a classe com mais acecoes vem primeiro`() {
        val e = entrada("substantivo", "adjetivo", "substantivo", "substantivo")
        assertEquals(listOf("substantivo", "adjetivo"), e.classes)
    }

    @Test
    fun `empate fica pela ordem em que aparecem`() {
        val e = entrada("substantivo", "substantivo", "verbo")
        assertEquals(listOf("substantivo", "verbo"), e.classes)
    }

    @Test
    fun `dicionario antigo, sem classe por acecao, fica so com a do lema`() {
        val e = entrada("adjetivo", null, null)
        assertEquals(listOf("adjetivo"), e.classes)
    }

    @Test
    fun `classe desconhecida nao se mostra`() {
        assertEquals(emptyList<String>(), entrada("desconhecido", null).classes)
    }
}
