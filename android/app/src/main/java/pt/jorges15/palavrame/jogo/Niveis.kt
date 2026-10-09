package pt.jorges15.palavrame.jogo

/**
 * Um nível do jogo: número, nome e quantos acertos são precisos para lá chegar.
 */
data class Nivel(val numero: Int, val nome: String, val desde: Int) {
    /** "Nível 3 · Leitor atento". */
    val rotulo: String get() = "Nível $numero · $nome"
}

/**
 * Os níveis, contados pelos **acertos totais**.
 *
 * Três decisões, tomadas com o Jorge:
 *
 * * **Medem o que se aprendeu, não o quanto se joga.** Os pontos misturam
 *   acertos, castigos e bónus de sequência; os acertos só contam respostas
 *   certas. Já estão guardados no progresso e vão na cópia de segurança.
 * * **Nunca descem.** Errar faz parte da repetição espaçada; perder um nível
 *   por isso seria um castigo que faz desistir. Os acertos só sobem.
 * * **Nomes ligados à leitura**, com número à frente, para se ver logo onde se
 *   está e quanto falta.
 */
object Niveis {

    val TODOS = listOf(
        Nivel(1, "Folheador", 0),
        Nivel(2, "Leitor", 10),
        Nivel(3, "Leitor atento", 25),
        Nivel(4, "Bibliófilo", 50),
        Nivel(5, "Letrado", 100),
        Nivel(6, "Erudito", 200),
        Nivel(7, "Dicionário vivo", 400),
    )

    /** O nível de quem tem estes acertos. */
    fun para(acertos: Int): Nivel = TODOS.last { acertos >= it.desde }

    /** O nível seguinte, ou nulo se já se está no último. */
    fun seguinte(acertos: Int): Nivel? = TODOS.firstOrNull { it.desde > acertos }

    /** O nível novo, se a resposta fez subir; nulo se não subiu. */
    fun subiu(acertosAntes: Int, acertosDepois: Int): Nivel? =
        para(acertosDepois).takeIf { it.numero > para(acertosAntes).numero }

    /**
     * Uma palavra "que já sabes": acertada pelo menos três vezes seguidas
     * (caixa de Leitner 3 ou mais). Diz mais sobre o que se aprendeu do que
     * o total de pontos.
     */
    const val CAIXA_SABIDA = 3
}
