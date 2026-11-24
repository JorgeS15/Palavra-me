package pt.jorges15.palavrame.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.jorges15.palavrame.data.Acecao

class CorrecaoTest {

    private val acecao = Acecao(
        id = 1, ord = 2,
        definicao = "Aquele que tem direito a compensação útil.",
        dominios = emptyList(),
        fonte = "Dicionário Aberto",
        modernizada = false,
    )

    @Test
    fun `o assunto leva a palavra`() {
        assertEquals("Palavra-me: corrigir \"credor\"", assuntoCorrecao("credor"))
    }

    @Test
    fun `o corpo leva a palavra, a acecao, a fonte, o comentario e a versao`() {
        val corpo = corpoCorrecao(
            "credor", acecao, "substantivo", "  Fraseado antigo. ",
            "Palavra-me 1.22.0 · dicionário de 2026-11-20",
        )
        assertTrue(corpo.startsWith("Palavra: credor (substantivo)\n"))
        assertTrue(corpo.contains("Significado 2: Aquele que tem direito a compensação útil.\n"))
        assertTrue(corpo.contains("Fonte: Dicionário Aberto\n"))
        assertTrue(corpo.contains("O que está mal, ou como devia ser:\nFraseado antigo.\n"))
        assertTrue(corpo.trimEnd().endsWith("Palavra-me 1.22.0 · dicionário de 2026-11-20"))
    }

    @Test
    fun `sem comentario diz que nao se escreveu nada`() {
        val corpo = corpoCorrecao("credor", acecao.copy(modernizada = true), null, "", "v")
        assertTrue(corpo.startsWith("Palavra: credor\n"))
        assertTrue(corpo.contains("Fonte: Dicionário Aberto (fraseado adaptado)\n"))
        assertTrue(corpo.contains("(não escrevi nada)"))
    }

    @Test
    fun `sem acecao e um significado que falta`() {
        val corpo = corpoCorrecao("credor", null, "substantivo", " Quem empresta dinheiro. ", "v")
        assertTrue(corpo.startsWith("Palavra: credor (substantivo)\n"))
        assertTrue(corpo.contains("Falta um significado:\nQuem empresta dinheiro.\n"))
        assertTrue(!corpo.contains("Fonte:"))
    }

    @Test
    fun `o resumo do progresso diz pontos, respostas e a sequencia`() {
        val p = pt.jorges15.palavrame.data.Progresso(
            pontos = 2704, sequencia = 34, ultimoDia = "2026-11-01", acertos = 241, erros = 25,
        )
        assertEquals(
            "2704 pontos · 266 respostas · sequência de 34 dias, até 1 de novembro",
            resumoProgresso(p),
        )
        assertEquals("0 pontos · 1 resposta", resumoProgresso(pt.jorges15.palavrame.data.Progresso(acertos = 1)))
    }
}
