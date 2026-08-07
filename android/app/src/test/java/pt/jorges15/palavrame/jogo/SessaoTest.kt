package pt.jorges15.palavrame.jogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * As regras do jogo, testadas onde ainda se conseguem ver.
 *
 * Um erro aqui não rebenta nada: uma palavra volta cedo demais, ou uns pontos
 * ficam negativos, e passam semanas até alguém reparar. É precisamente por
 * isso que estas regras vivem fora do ecrã e da base de dados.
 */
class SessaoTest {

    private val DIA = 24L * 60 * 60 * 1000
    private val agora = 1_800_000_000_000L
    private val lisboa: ZoneId = ZoneId.of("Europe/Lisbon")

    // --- caixas -----------------------------------------------------------

    @Test
    fun `acertar sobe uma caixa`() {
        assertEquals(1, Leitner.proximaCaixa(0, acertou = true))
        assertEquals(4, Leitner.proximaCaixa(3, acertou = true))
    }

    @Test
    fun `a ultima caixa nao transborda`() {
        val topo = Leitner.CAIXA_MAXIMA
        assertEquals(topo, Leitner.proximaCaixa(topo, acertou = true))
    }

    @Test
    fun `errar devolve ao principio, nao recua um passo`() {
        // Severo de propósito: se falhaste, o intervalo de 32 dias que tinhas
        // ganho estava errado.
        assertEquals(0, Leitner.proximaCaixa(5, acertou = false))
        assertEquals(0, Leitner.proximaCaixa(1, acertou = false))
    }

    @Test
    fun `os intervalos duplicam`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32), Leitner.INTERVALOS.toList())
    }

    @Test
    fun `a proxima revisao respeita o intervalo da caixa`() {
        assertEquals(agora + 1 * DIA, Leitner.proximaRevisao(0, agora))
        assertEquals(agora + 32 * DIA, Leitner.proximaRevisao(5, agora))
    }

    // --- pontos -----------------------------------------------------------

    private val hoje = LocalDate.of(2026, 8, 6)
    private val ontem = hoje.minusDays(1)

    @Test
    fun `acerto da dez pontos`() {
        // Primeira resposta de sempre: 10 do acerto + 2 do primeiro dia.
        val p = Pontuacao().responder(acertou = true, hoje = hoje)
        assertEquals(12, p.pontos)
        assertEquals(1, p.acertos)
    }

    @Test
    fun `erro tira cinco`() {
        val base = Pontuacao(pontos = 100, ultimoDia = hoje.toString())
        val p = base.responder(acertou = false, hoje = hoje)
        assertEquals(95, p.pontos)
        assertEquals(1, p.erros)
    }

    @Test
    fun `os pontos nunca descem abaixo de zero`() {
        // O piso é o que impede a penalização de virar dívida desanimadora —
        // ver a discussão no docs/jogo.md.
        val base = Pontuacao(pontos = 2, ultimoDia = hoje.toString())
        assertEquals(0, base.responder(acertou = false, hoje = hoje).pontos)
    }

    @Test
    fun `o bonus de sequencia e uma vez por dia, nao por resposta`() {
        // Senão bastava responder muitas vezes hoje para o acumular, e o que
        // se quer premiar é voltar amanhã.
        var p = Pontuacao(ultimoDia = ontem.toString(), sequencia = 1)
        p = p.responder(acertou = true, hoje = hoje)    // 10 + bónus 4
        val depoisDaPrimeira = p.pontos
        p = p.responder(acertou = true, hoje = hoje)    // só 10
        assertEquals(depoisDaPrimeira + Pontuacao.POR_ACERTO, p.pontos)
        assertEquals(2, p.sequencia)
    }

    @Test
    fun `faltar um dia reinicia a sequencia mas nao os pontos`() {
        val base = Pontuacao(
            pontos = 250,
            sequencia = 9,
            ultimoDia = hoje.minusDays(3).toString(),
        )
        val p = base.responder(acertou = true, hoje = hoje)
        assertEquals(1, p.sequencia)
        assertTrue("os pontos não podem ser apagados", p.pontos > 250)
    }

    @Test
    fun `o bonus tem teto`() {
        val base = Pontuacao(sequencia = 40, ultimoDia = ontem.toString())
        val p = base.responder(acertou = true, hoje = hoje)
        assertEquals(Pontuacao.POR_ACERTO + Pontuacao.BONUS_MAXIMO, p.pontos)
    }

    // --- as duas coisas juntas -------------------------------------------

    @Test
    fun `uma resposta certa avanca a caixa e o calendario`() {
        val a = aplicarResposta(
            caixaAtual = 2, pontuacaoAtual = Pontuacao(), acertou = true,
            agora = agora, zona = lisboa,
        )
        assertEquals(3, a.caixa)
        assertEquals(agora + 8 * DIA, a.proximaRevisao)
    }

    @Test
    fun `no treino livre os pontos contam mas o calendario nao mexe`() {
        // Sem isto bastava jogar muito num dia para nunca mais rever nada,
        // que é o contrário do que a repetição espaçada faz.
        val antes = Pontuacao(pontos = 30, ultimoDia = hoje.toString())
        val a = aplicarResposta(
            caixaAtual = 4, pontuacaoAtual = antes, acertou = true,
            agora = agora, contaParaOCalendario = false, zona = lisboa,
        )
        assertEquals("a caixa fica onde estava", 4, a.caixa)
        assertNull("não se marca nova revisão", a.proximaRevisao)
        assertEquals(40, a.pontuacao.pontos)
        assertEquals(10, a.ganho(antes))
    }

    @Test
    fun `errar no treino livre tambem nao devolve a palavra a caixa zero`() {
        val a = aplicarResposta(
            caixaAtual = 5, pontuacaoAtual = Pontuacao(), acertou = false,
            agora = agora, contaParaOCalendario = false, zona = lisboa,
        )
        assertEquals(5, a.caixa)
    }

    // --- quando o lembrete toca ------------------------------------------

    private fun as(hora: Int, minuto: Int) = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.AUGUST, 6, hora, minuto, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    @Test
    fun `a hora de hoje que ainda nao passou e hoje`() {
        assertEquals(120, minutosAte(21, as(19, 0)))
    }

    @Test
    fun `a hora de hoje que ja passou e amanha`() {
        // 22:00 com lembrete às 21:00 -> faltam 23 horas.
        assertEquals(23 * 60, minutosAte(21, as(22, 0)))
    }

    @Test
    fun `a hora exata conta para o dia seguinte, nao dispara ja`() {
        assertEquals(24 * 60, minutosAte(21, as(21, 0)))
    }

    @Test
    fun `hora fora do intervalo nao rebenta`() {
        assertTrue(minutosAte(99, as(12, 0)) > 0)
        assertTrue(minutosAte(-3, as(12, 0)) > 0)
    }

    // --- distribuicao dos lembretes pela janela ---------------------------

    @Test
    fun `tres entre as oito e as vinte e duas`() {
        // O exemplo com que o Jorge descreveu o que queria.
        assertEquals(listOf(8, 15, 22), horasDosLembretes(3, 8, 22))
    }

    @Test
    fun `os extremos da janela estao sempre incluidos`() {
        for (n in 2..6) {
            val horas = horasDosLembretes(n, 9, 21)
            assertEquals("com $n lembretes", 9, horas.first())
            assertEquals("com $n lembretes", 21, horas.last())
        }
    }

    @Test
    fun `um lembrete fica no inicio da janela`() {
        assertEquals(listOf(8), horasDosLembretes(1, 8, 22))
    }

    @Test
    fun `nunca ha duas notificacoes a mesma hora`() {
        // Cinco entre as 8h e as 10h não cabem: a janela só tem três horas.
        val horas = horasDosLembretes(5, 8, 10)
        assertEquals(horas.distinct(), horas)
        assertEquals(listOf(8, 9, 10), horas)
    }

    @Test
    fun `janela invertida ordena-se em vez de atravessar a meia-noite`() {
        // Uma app que decide sozinha que 22h-8h significa "durante a noite"
        // acaba a acordar quem a instalou.
        assertEquals(horasDosLembretes(3, 8, 22), horasDosLembretes(3, 22, 8))
    }

    @Test
    fun `janela de uma hora so da uma notificacao`() {
        assertEquals(listOf(21), horasDosLembretes(4, 21, 21))
    }

    @Test
    fun `as horas saem sempre por ordem e dentro da janela`() {
        for (n in 1..6) {
            val horas = horasDosLembretes(n, 7, 23)
            assertEquals(horas.sorted(), horas)
            assertTrue(horas.all { it in 7..23 })
        }
    }
}
