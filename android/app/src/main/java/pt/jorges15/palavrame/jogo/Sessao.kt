package pt.jorges15.palavrame.jogo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * As regras do jogo que não precisam de Android para existirem.
 *
 * Estão separadas do ecrã e da base de dados de propósito: são a parte onde
 * um erro passa despercebido durante semanas — uma palavra que volta cedo
 * demais, uns pontos que descem abaixo de zero — e a única forma de as ver é
 * escrevê-las onde se possam correr num teste.
 *
 * O desenho está em `docs/jogo.md`.
 */

/** Sistema de caixas (Leitner). Não é SM-2, e a razão está no `docs/jogo.md`. */
object Leitner {

    /**
     * Dias até a palavra voltar, por caixa.
     *
     * Duplica de cada vez. Uma palavra acertada seis vezes seguidas passa a
     * aparecer de mês a mês, que é a forma de a repetição espaçada dizer
     * "esta já sabes".
     */
    val INTERVALOS = intArrayOf(1, 2, 4, 8, 16, 32)

    val CAIXA_MAXIMA = INTERVALOS.lastIndex

    /**
     * Acerto sobe uma caixa; erro devolve à primeira.
     *
     * O erro não é castigado com um recuo parcial: volta ao princípio. É
     * severo de propósito — se falhaste, não sabes, e o intervalo de dezasseis
     * dias que tinhas ganho estava errado.
     */
    fun proximaCaixa(caixa: Int, acertou: Boolean): Int = when {
        !acertou -> 0
        else -> (caixa + 1).coerceAtMost(CAIXA_MAXIMA)
    }

    /** Quando a palavra volta ao jogo, em milissegundos. */
    fun proximaRevisao(caixa: Int, agora: Long): Long {
        val dias = INTERVALOS[caixa.coerceIn(0, CAIXA_MAXIMA)]
        return agora + dias * 24L * 60 * 60 * 1000
    }
}

/**
 * Pontos e sequência de dias.
 *
 * O −5 por erro foi decisão do Jorge, e há uma tensão real nele: num sistema
 * de repetição espaçada o erro é o *objetivo*, porque é ele que diz o que
 * falta rever. O piso em zero é o que impede a penalização de virar dívida.
 */
data class Pontuacao(
    val pontos: Int = 0,
    val sequencia: Int = 0,
    val ultimoDia: String? = null,
    val acertos: Int = 0,
    val erros: Int = 0,
) {
    companion object {
        const val POR_ACERTO = 10
        const val POR_ERRO = 5
        const val BONUS_POR_DIA = 2
        const val BONUS_MAXIMO = 10
    }

    /**
     * Regista uma resposta e devolve a pontuação nova.
     *
     * O bónus de sequência é atribuído **uma vez por dia**, na primeira
     * resposta — senão bastava responder muitas vezes no mesmo dia para o
     * acumular, e o que se quer premiar é voltar amanhã, não jogar mais hoje.
     */
    fun responder(acertou: Boolean, hoje: LocalDate): Pontuacao {
        val dia = hoje.toString()
        val primeiraDoDia = ultimoDia != dia

        val novaSequencia = when {
            !primeiraDoDia -> sequencia
            ultimoDia == hoje.minusDays(1).toString() -> sequencia + 1
            else -> 1                     // faltou um dia: recomeça
        }
        val bonus = if (primeiraDoDia) {
            (novaSequencia * BONUS_POR_DIA).coerceAtMost(BONUS_MAXIMO)
        } else 0

        val delta = if (acertou) POR_ACERTO else -POR_ERRO
        return copy(
            // O piso é zero. Uma falha reinicia a sequência, nunca os pontos.
            pontos = (pontos + delta + bonus).coerceAtLeast(0),
            sequencia = novaSequencia,
            ultimoDia = dia,
            acertos = acertos + if (acertou) 1 else 0,
            erros = erros + if (acertou) 0 else 1,
        )
    }
}

/**
 * O resultado de responder a uma pergunta: o que muda na palavra e no total.
 */
data class Avanco(
    val caixa: Int,
    val proximaRevisao: Long?,
    val pontuacao: Pontuacao,
) {
    /** Quanto o total subiu ou desceu — é isto que o ecrã mostra. */
    fun ganho(anterior: Pontuacao): Int = pontuacao.pontos - anterior.pontos
}

/**
 * Aplica uma resposta a uma palavra.
 *
 * `contaParaOCalendario` a falso é o treino livre: quando não há nada
 * vencido e a pessoa quer jogar na mesma, os pontos contam mas a caixa não
 * avança. Sem isto, bastava jogar muito num dia para nunca mais rever nada —
 * que é precisamente o contrário do que a repetição espaçada faz.
 */
fun aplicarResposta(
    caixaAtual: Int,
    pontuacaoAtual: Pontuacao,
    acertou: Boolean,
    agora: Long,
    contaParaOCalendario: Boolean = true,
    zona: ZoneId = ZoneId.systemDefault(),
): Avanco {
    val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
    val pontuacao = pontuacaoAtual.responder(acertou, hoje)
    if (!contaParaOCalendario) {
        return Avanco(caixaAtual, null, pontuacao)
    }
    val caixa = Leitner.proximaCaixa(caixaAtual, acertou)
    return Avanco(caixa, Leitner.proximaRevisao(caixa, agora), pontuacao)
}

/**
 * A que horas tocam os lembretes de um dia.
 *
 * Distribui `quantos` lembretes pela janela `[inicio, fim]`, **com os
 * extremos incluídos** — três entre as 8h e as 22h dão 8h, 15h e 22h, que
 * foi o exemplo com que o Jorge descreveu o que queria.
 *
 * Casos que a fórmula tem de aguentar sem pensar duas vezes:
 *
 * * **um só lembrete** fica no início da janela. Podia ficar no fim ou no
 *   meio; o início é o que respeita mais literalmente "a partir das 8h".
 * * **mais lembretes do que horas na janela** — cinco entre as 8h e as 10h —
 *   não pode dar horas repetidas, senão ficavam dois avisos ao mesmo tempo.
 *   Corta-se ao que a janela comporta.
 * * **janela invertida** (das 22h às 8h) não se trata como janela noturna:
 *   ordena-se. Uma app que decide sozinha que 22h-8h significa "durante a
 *   noite" acaba a acordar quem a instalou.
 */
fun horasDosLembretes(quantos: Int, inicio: Int, fim: Int): List<Int> {
    val a = minOf(inicio, fim).coerceIn(0, 23)
    val b = maxOf(inicio, fim).coerceIn(0, 23)
    val cabem = b - a + 1
    val n = quantos.coerceIn(1, cabem)
    if (n == 1) return listOf(a)

    val passo = (b - a).toDouble() / (n - 1)
    return (0 until n)
        .map { (a + Math.round(it * passo)).toInt() }
        .distinct()
}

/**
 * Minutos daqui até à próxima ocorrência de uma hora do dia.
 *
 * Vive aqui e não junto da notificação porque é aritmética de calendário e
 * mais nada — e assim testa-se sem Android por perto. É o que decide quando
 * cada lembrete aparece pela primeira vez depois de o ligares.
 */
fun minutosAte(hora: Int, agora: Calendar = Calendar.getInstance()): Long {
    val alvo = (agora.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hora.coerceIn(0, 23))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // Se a hora de hoje já passou, é para amanhã.
    if (!alvo.after(agora)) alvo.add(Calendar.DAY_OF_YEAR, 1)
    return (alvo.timeInMillis - agora.timeInMillis) / 60_000
}
