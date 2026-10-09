package pt.jorges15.palavrame.jogo

import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.UtilizadorDb
import kotlin.random.Random

/*
 * Qual é a palavra que o jogo vai perguntar — decidido num sítio só.
 *
 * O lembrete e o jogo chegaram a decidir isto cada um por si e a chegar a
 * palavras diferentes: a notificação anunciava "periclitante" e o jogo
 * perguntava "iconoclasta". O lembrete achava a palavra jogável por ter
 * definição com corpo; o jogo, ao montar a pergunta, tirava o pedaço que
 * nomeava a palavra, a definição ficava curta, e passava em silêncio à
 * seguinte da fila.
 *
 * Agora as duas pontas usam as mesmas funções, e o lembrete só anuncia uma
 * palavra depois de ter gerado mesmo a pergunta para ela. A palavra viaja no
 * intent da notificação, e o jogo pergunta essa (ver `JogoViewModel`).
 */

/**
 * A palavra que o lembrete anuncia: a primeira vencida para a qual se
 * consegue fazer uma pergunta. Nulo se não houver nenhuma — e então não há
 * notificação.
 */
suspend fun escolherPalavra(
    dicionario: Dicionario?,
    utilizador: UtilizadorDb,
    agora: Long,
): PalavraGuardada? {
    val vencidas = utilizador.palavras().vencidas(agora)
    if (vencidas.isEmpty()) return null
    val todas = candidatas(dicionario, utilizador.palavras().todasAgora())
    // Com menos do que isto o jogo diz "ainda não dá": não se anuncia nada.
    if (todas.size < MINIMO_PARA_JOGAR) return null
    // A ordem vem do DAO: primeiro as que nunca foram jogadas, depois as mais
    // atrasadas.
    return vencidas.firstOrNull { p ->
        val alvo = todas[p.lemma] ?: return@firstOrNull false
        perguntaPara(dicionario, alvo, todas, Random.Default) != null
    }
}

/**
 * Uma palavra guardada transformada em candidata, ou nulo se o dicionário não
 * lhe der uma definição jogável. Consulta leve: só a classe e as definições.
 */
fun candidata(dicionario: Dicionario?, p: PalavraGuardada): Candidata? {
    val (pos, definicoes) = dicionario?.candidataDeJogo(p.lemma) ?: return null
    val definicao = Perguntas.definicaoParaJogo(definicoes, p.lemma) ?: return null
    return Candidata(
        lemma = p.lemma,
        pos = pos,
        definicao = definicao,
        livro = p.livro,
        frase = p.frase,
    )
}

/** As candidatas de toda a coleção, por lema. */
fun candidatas(dicionario: Dicionario?, guardadas: List<PalavraGuardada>): Map<String, Candidata> =
    guardadas.mapNotNull { p -> candidata(dicionario, p)?.let { p.lemma to it } }.toMap()

/**
 * Distrações do dicionário, para quando a coleção ainda é pequena. Só se vão
 * buscar quando fazem falta: numa coleção crescida não são precisas.
 */
fun reservaPara(
    dicionario: Dicionario?,
    alvo: Candidata,
    todas: Map<String, Candidata>,
): List<Candidata> {
    if (todas.size - 1 >= Perguntas.COLECAO_AUTOSSUFICIENTE) return emptyList()
    val d = dicionario ?: return emptyList()
    val n = alvo.definicao.length
    return d.definicoesParaDistracao(
        pos = alvo.pos,
        minimo = maxOf(Perguntas.MIN_CARACTERES, (n / 2.0).toInt()),
        maximo = n * 2,
    ).map { (lemma, pos, definicao) -> Candidata(lemma, pos, definicao) }
}

/**
 * A pergunta para `alvo`, ou nulo se não houver distrações à altura.
 *
 * Tenta algumas vezes: com a coleção pequena, as distrações do dicionário vêm
 * ao acaso, e uma tentativa sem sorte não deve deixar a palavra de fora — nem
 * fazer o jogo divergir da palavra que o lembrete anunciou.
 */
fun perguntaPara(
    dicionario: Dicionario?,
    alvo: Candidata,
    todas: Map<String, Candidata>,
    aleatorio: Random,
    tentativas: Int = 3,
): Pergunta? {
    val outras = todas.values.filter { it.lemma != alvo.lemma }
    val sinonimos = dicionario?.sinonimosDe(alvo.lemma) ?: emptySet()
    repeat(tentativas) {
        Perguntas.gerar(
            alvo = alvo,
            outras = outras,
            aleatorio = aleatorio,
            reserva = reservaPara(dicionario, alvo, todas),
            sinonimosDoAlvo = sinonimos,
        )?.let { return it }
    }
    return null
}

/** Tem definição com corpo suficiente para render uma pergunta? */
fun jogavel(dicionario: Dicionario?, lemma: String): Boolean {
    val definicoes = dicionario?.candidataDeJogo(lemma)?.second ?: return false
    return Perguntas.definicaoParaJogo(definicoes, lemma) != null
}
