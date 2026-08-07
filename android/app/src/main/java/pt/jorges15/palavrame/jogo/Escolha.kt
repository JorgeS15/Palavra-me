package pt.jorges15.palavrame.jogo

import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.UtilizadorDb

/**
 * Qual é a palavra que o jogo vai perguntar.
 *
 * Existe porque o lembrete e o jogo estavam a decidir isto cada um por si, e
 * chegavam a palavras diferentes. A notificação anunciava *lugubremente* e o
 * jogo perguntava outra coisa — o que faz a app parecer avariada logo no
 * primeiro gesto que se lhe pede.
 *
 * Havia três causas, e esta função trata da que era sistemática: **o lembrete
 * não verificava se a palavra era jogável**. Pegava na mais atrasada e
 * anunciava-a. O jogo, esse, descarta as que não têm definição utilizável —
 * e há palavras registadas nessas condições, como o `lugubremente`, que não
 * chega aos 25 caracteres em aceção nenhuma.
 *
 * As outras duas resolvem-se por o lembrete levar a palavra escolhida consigo
 * no *intent*, em vez de a deixar ser reescolhida quando a app abre.
 */
suspend fun escolherPalavra(
    dicionario: Dicionario?,
    utilizador: UtilizadorDb,
    agora: Long,
): PalavraGuardada? {
    val vencidas = utilizador.palavras().vencidas(agora)
    if (vencidas.isEmpty()) return null
    // A ordem vem do DAO: primeiro as que nunca foram jogadas, depois as mais
    // atrasadas. A primeira que der uma definição jogável é a escolhida.
    return vencidas.firstOrNull { jogavel(dicionario, it.lemma) }
}

/** Tem definição com corpo suficiente para render uma pergunta? */
fun jogavel(dicionario: Dicionario?, lemma: String): Boolean {
    val entrada = dicionario?.entradaPorLema(lemma) ?: return false
    return Perguntas.definicaoParaJogo(entrada.acecoes.map { it.definicao }) != null
}
