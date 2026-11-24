package pt.jorges15.palavrame.jogo

import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.Lingua
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
 *
 * Desde a 1.23 há palavras de duas línguas. Cada uma joga com o dicionário
 * dela e só contra palavras da mesma língua: uma definição em inglês entre
 * duas em português dava a resposta sem ler.
 */

/** O dicionário de cada língua (nulo se não estiver instalado). */
typealias Dicionarios = (lingua: String) -> Dicionario?

/**
 * A chave de uma palavra no jogo. O português fica só com o lema, como sempre
 * (uma notificação de antes da 1.23 continua a abrir a palavra certa); as
 * outras línguas levam-na à frente: `en:real` não é `real`.
 */
fun chaveDe(lemma: String, lingua: String): String =
    if (lingua == Lingua.PT) lemma else "$lingua:$lemma"

val PalavraGuardada.chave: String get() = chaveDe(lemma, lingua)
val Candidata.chave: String get() = chaveDe(lemma, lingua)
val Pergunta.chave: String get() = chaveDe(lemma, lingua)

/** O lema de uma chave, para mostrar: `en:wistful` -> `wistful`. */
fun lemaDaChave(chave: String): String {
    val i = chave.indexOf(':')
    return if (i > 0 && chave.substring(0, i) in Lingua.todas) chave.substring(i + 1) else chave
}

/** A língua de uma chave: `en:wistful` -> `en`; sem prefixo, português. */
fun linguaDaChave(chave: String): String {
    val i = chave.indexOf(':')
    val prefixo = if (i > 0) chave.substring(0, i) else null
    return if (prefixo != null && prefixo in Lingua.todas) prefixo else Lingua.PT
}

/**
 * A palavra que o lembrete anuncia: a primeira vencida para a qual se
 * consegue fazer uma pergunta. Nulo se não houver nenhuma — e então não há
 * notificação.
 */
suspend fun escolherPalavra(
    dicionarios: Dicionarios,
    utilizador: UtilizadorDb,
    agora: Long,
): PalavraGuardada? {
    val vencidas = utilizador.palavras().vencidas(agora)
    if (vencidas.isEmpty()) return null
    val todas = candidatas(dicionarios, utilizador.palavras().todasAgora())
    // Só se anunciam palavras de uma língua com o mínimo na coleção (ver
    // `alvosPossiveis`); sem nenhuma, o jogo diz "ainda não dá".
    val alvos = alvosPossiveis(todas)
    if (alvos.isEmpty()) return null
    // A ordem vem do DAO: primeiro as que nunca foram jogadas, depois as mais
    // atrasadas.
    return vencidas.firstOrNull { p ->
        val alvo = alvos[p.chave] ?: return@firstOrNull false
        perguntaPara(dicionarios, alvo, todas, Random.Default) != null
    }
}

/**
 * Uma palavra guardada transformada em candidata, ou nulo se o dicionário
 * da língua dela não lhe der uma definição jogável. Consulta leve: só a
 * classe e as definições.
 */
fun candidata(dicionarios: Dicionarios, p: PalavraGuardada): Candidata? {
    val (pos, definicoes) = dicionarios(p.lingua)?.candidataDeJogo(p.lemma) ?: return null
    val definicao = Perguntas.definicaoParaJogo(definicoes, p.lemma) ?: return null
    return Candidata(
        lemma = p.lemma,
        pos = pos,
        definicao = definicao,
        livro = p.livro,
        frase = p.frase,
        autor = p.autor,
        page = p.page,
        lingua = p.lingua,
    )
}

/**
 * As candidatas que podem ser perguntadas: só as de uma língua com pelo menos
 * `MINIMO_PARA_JOGAR` palavras jogáveis na coleção (1.30). Antes o mínimo
 * contava as línguas todas juntas, e a primeira palavra inglesa entrava logo
 * no jogo e nos lembretes, com as opções todas tiradas do dicionário. Agora o
 * inglês só começa à terceira palavra inglesa, como o português começou.
 *
 * As distrações continuam a vir de `todas` (e sempre da mesma língua).
 */
fun alvosPossiveis(todas: Map<String, Candidata>): Map<String, Candidata> {
    val porLingua = todas.values.groupingBy { it.lingua }.eachCount()
    return todas.filterValues { (porLingua[it.lingua] ?: 0) >= MINIMO_PARA_JOGAR }
}

/** Quantas palavras jogáveis tem a língua com mais (para o "ainda não dá"). */
fun maisNumaLingua(todas: Map<String, Candidata>): Int =
    todas.values.groupingBy { it.lingua }.eachCount().values.maxOrNull() ?: 0

/** As candidatas de toda a coleção, pela chave (ver `chaveDe`). */
fun candidatas(dicionarios: Dicionarios, guardadas: List<PalavraGuardada>): Map<String, Candidata> =
    guardadas.mapNotNull { p -> candidata(dicionarios, p)?.let { p.chave to it } }.toMap()

/**
 * Distrações do dicionário, para quando a coleção ainda é pequena. Só se vão
 * buscar quando fazem falta: numa coleção crescida não são precisas. Contam
 * só as palavras da mesma língua do alvo.
 */
fun reservaPara(
    dicionarios: Dicionarios,
    alvo: Candidata,
    todas: Map<String, Candidata>,
): List<Candidata> {
    val daLingua = todas.values.count { it.lingua == alvo.lingua }
    if (daLingua - 1 >= Perguntas.COLECAO_AUTOSSUFICIENTE) return emptyList()
    val d = dicionarios(alvo.lingua) ?: return emptyList()
    val n = alvo.definicao.length
    return d.definicoesParaDistracao(
        pos = alvo.pos,
        minimo = maxOf(Perguntas.MIN_CARACTERES, (n / 2.0).toInt()),
        maximo = n * 2,
    ).map { (lemma, pos, definicao) -> Candidata(lemma, pos, definicao, lingua = alvo.lingua) }
}

/**
 * A pergunta para `alvo`, ou nulo se não houver distrações à altura.
 *
 * Tenta algumas vezes: com a coleção pequena, as distrações do dicionário vêm
 * ao acaso, e uma tentativa sem sorte não deve deixar a palavra de fora — nem
 * fazer o jogo divergir da palavra que o lembrete anunciou.
 */
fun perguntaPara(
    dicionarios: Dicionarios,
    alvo: Candidata,
    todas: Map<String, Candidata>,
    aleatorio: Random,
    tentativas: Int = 3,
): Pergunta? {
    val outras = todas.values.filter { it.lingua == alvo.lingua && it.lemma != alvo.lemma }
    val sinonimos = dicionarios(alvo.lingua)?.sinonimosDe(alvo.lemma) ?: emptySet()
    repeat(tentativas) {
        Perguntas.gerar(
            alvo = alvo,
            outras = outras,
            aleatorio = aleatorio,
            reserva = reservaPara(dicionarios, alvo, todas),
            sinonimosDoAlvo = sinonimos,
        )?.let { return it }
    }
    return null
}

/** Tem definição com corpo suficiente para render uma pergunta? */
fun jogavel(dicionarios: Dicionarios, lemma: String, lingua: String = Lingua.PT): Boolean {
    val definicoes = dicionarios(lingua)?.candidataDeJogo(lemma)?.second ?: return false
    return Perguntas.definicaoParaJogo(definicoes, lemma) != null
}
