package pt.jorges15.palavrame.jogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Progresso
import pt.jorges15.palavrame.data.UtilizadorDb
import kotlin.random.Random

/** Abaixo disto não há jogo: falta uma palavra para a pergunta e duas para as distrações. */
const val MINIMO_PARA_JOGAR = 3

sealed interface EstadoJogo {

    data object ACarregar : EstadoJogo

    /** A coleção ainda não dá para uma pergunta honesta. */
    data class PoucasPalavras(val jogaveis: Int) : EstadoJogo

    /** Há palavras, mas nenhuma delas rendeu uma pergunta decente hoje. */
    data object SemPerguntaPossivel : EstadoJogo

    /**
     * Nada vencido — e o treino livre está desligado.
     *
     * É o estado normal de quem já reviu tudo hoje, e é uma boa notícia:
     * a repetição espaçada existe para não perguntar o que já está sabido.
     */
    data object NadaARever : EstadoJogo

    /** Já se reviram à mão as do dia (`LIMITE_MANUAL_POR_DIA`). */
    data class LimiteDoDia(val limite: Int) : EstadoJogo

    /** A rever um livro: já passaram todas as palavras dele (ou nenhuma dá pergunta). */
    data class LivroRevisto(val titulo: String, val revistas: Int) : EstadoJogo

    data class AJogar(
        val pergunta: Pergunta,
        val respondida: Int? = null,
        val entrada: Entrada? = null,
        val livro: String? = null,
        val frase: String? = null,
        val autor: String? = null,
        val pagina: Int? = null,
        val ganho: Int = 0,
        /** A parte do ganho que veio do bónus de sequência (0 se não houve). */
        val bonus: Int = 0,
        val total: Int = 0,
        val sequencia: Int = 0,
        /** O nível novo, se esta resposta fez subir (nulo se não subiu). */
        val subiuDeNivel: Nivel? = null,
        /** Não havia nada vencido: joga-se, mas o calendário não mexe. */
        val treinoLivre: Boolean = false,
        /**
         * Deixa seguir para outra palavra depois de responder: há mais
         * vencidas e o limite do dia ainda o permite (no Modo Desenvolvedor,
         * sempre).
         */
        val podeSeguir: Boolean = false,
        /** O texto do botão de seguir: "Palavra seguinte" ou, depois de um lembrete, "Rever mais". */
        val rotuloSeguir: String = "Palavra seguinte",
        /**
         * "Feito" é o botão grande e seguir fica discreto. Na pergunta do
         * lembrete: quem veio da notificação veio por uma palavra, e o normal
         * é acabar. Em "Rever" é ao contrário: veio-se para rever.
         */
        val feitoEmDestaque: Boolean = false,
        /** Quantas ainda se podem rever à mão hoje, para uma linha discreta; nulo sem limite. */
        val restamHoje: Int? = null,
        /**
         * Escrever a palavra a partir da definição, em vez de escolher entre
         * três (1.22, opção nas Definições). Respondida: `respondida` é o
         * índice certo se acertou, -1 se não.
         */
        val escrever: Boolean = false,
        /** O que se escreveu (vazio em "Não sei"). */
        val escrito: String? = null,
    ) : EstadoJogo {
        val acertou: Boolean? get() = respondida?.let { it == pergunta.indiceCerto }
    }
}

/**
 * O jogo: junta a coleção do utilizador ao dicionário e faz perguntas.
 *
 * A geração está em `Perguntas` e as regras em `Sessao`, ambas sem Android.
 * Aqui só se lê, se escreve e se decide o que mostrar a seguir.
 */
class JogoViewModel(
    /**
     * Pede-se o dicionário à app em cada uso, em vez de guardar a instância
     * do arranque: instalar uma base nova fecha a antiga, e uma referência
     * guardada rebentava na consulta seguinte ("already-closed object").
     */
    private val obterDicionario: () -> Dicionario?,
    private val utilizador: UtilizadorDb,
    /**
     * O Modo Desenvolvedor está ligado.
     *
     * Muda duas coisas, ambas para quem está a caçar defeitos e nenhuma para
     * quem lê: deixa jogar **mesmo sem nada vencido** (sem esperar dias que
     * uma palavra volte, e sem mexer no calendário) e **sem o limite do
     * dia**. Para quem lê, revê-se o que está vencido, e "não há nada a
     * rever" é uma resposta boa.
     */
    private val treinoLivre: Boolean = false,
    /**
     * A palavra que a notificação anunciou.
     *
     * Quando existe, é essa que se pergunta — desde que continue vencida e
     * jogável. Sem isto, a app reescolhia ao abrir e podia calhar-lhe outra:
     * bastava registares uma palavra nova entre o aviso e o toque, porque as
     * acabadas de registar são as primeiras da fila.
     */
    private val lemaPreferido: String? = null,
    /**
     * A sessão abriu pelo lembrete. A primeira pergunta é a do lembrete e não
     * conta para o limite do dia; se a pessoa quiser continuar ("Rever
     * mais"), as seguintes já contam.
     */
    deLembrete: Boolean = false,
    /** Quantas revisões à mão ainda se podem fazer hoje. */
    private val cotaRestante: () -> Int = { Int.MAX_VALUE },
    /** Conta uma revisão à mão (uma resposta). */
    private val gastarCota: () -> Unit = {},
    /** Uma palavra acabou de ser respondida (para tirar a notificação que a anunciava). */
    private val aoResponder: (lemma: String) -> Unit = {},
    /** A opção "Escrever a palavra", nas Definições. */
    private val escreverAPalavra: () -> Boolean = { false },
    /**
     * Rever só as palavras deste livro (1.22, a partir da Coleção). Primeiro
     * as que estão por rever; depois as outras, para retomar a leitura com
     * elas frescas. Nessas o calendário não mexe (como no treino livre), mas
     * contam para o limite do dia: é uma revisão à mão como as outras.
     */
    val livro: String? = null,
    private val aleatorio: Random = Random.Default,
    private val agora: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val dicionario: Dicionario? get() = obterDicionario()

    private val _estado = MutableStateFlow<EstadoJogo>(EstadoJogo.ACarregar)
    val estado: StateFlow<EstadoJogo> = _estado.asStateFlow()

    val progresso = utilizador.progresso().observar()

    private var todas: Map<String, Candidata> = emptyMap()
    private var semCalendario = false

    /**
     * As respostas contam para o limite do dia: é uma revisão à mão (aberta
     * em "Rever agora", ou continuada depois de um lembrete). O treino livre
     * do Modo Desenvolvedor não conta nem tem limite.
     */
    private var manual = !deLembrete && !treinoLivre

    /** A palavra do lembrete só vale para a primeira pergunta. */
    private var preferido = lemaPreferido

    /** A rever um livro: as que já se perguntaram nesta sessão, para não repetir. */
    private val perguntadas = mutableSetOf<String>()

    init { comecar() }

    /**
     * Uma pergunta de cada vez, e a pessoa decide se segue.
     *
     * Até à 1.19 uma sessão era **uma** pergunta, só pelo lembrete: chega a
     * notificação, responde-se, acabou. Não acompanhava quem lê muito: com
     * no máximo 6 lembretes por dia e umas 6 revisões por palavra nos
     * primeiros dois meses, quem registava várias palavras por dia acumulava
     * uma fila sem fim, e quem não ligava as notificações não tinha jogo
     * nenhum. Agora pode-se rever à mão, encadeando perguntas, até um limite
     * diário (`LIMITE_MANUAL_POR_DIA`) que mantém a app calma.
     */
    fun comecar() {
        viewModelScope.launch {
            _estado.value = EstadoJogo.ACarregar
            _estado.value = withContext(Dispatchers.IO) { montar() }
        }
    }

    /** "Palavra seguinte" ou "Rever mais": a partir daqui, as respostas contam para o limite. */
    fun continuar() {
        if (!treinoLivre) manual = true
        comecar()
    }

    private suspend fun montar(): EstadoJogo {
        if (manual && cotaRestante() <= 0) return EstadoJogo.LimiteDoDia(LIMITE_MANUAL_POR_DIA)
        val guardadas = utilizador.palavras().todasAgora()
        // As mesmas funções que o lembrete usa (ver `Escolha.kt`): a palavra
        // anunciada na notificação é sempre uma que o jogo consegue perguntar.
        todas = candidatas(dicionario, guardadas)

        if (todas.size < MINIMO_PARA_JOGAR) {
            return EstadoJogo.PoucasPalavras(todas.size)
        }

        if (livro != null) return montarLivro(livro, guardadas)

        val vencidas = utilizador.palavras().vencidas(agora())
            .filter { it.lemma in todas }
        semCalendario = vencidas.isEmpty()

        val bruta = when {
            vencidas.isNotEmpty() -> vencidas
            // Nada vencido. Sem treino livre, fica-se por aqui.
            !treinoLivre -> return EstadoJogo.NadaARever
            // Com treino livre: a menos recentemente revista, e o
            // calendário não mexe (ver `aplicarResposta`).
            else -> guardadas.filter { it.lemma in todas }
                .sortedBy { it.revistaEm ?: 0L }
        }
        // A palavra anunciada pela notificação passa à frente. Se já não
        // estiver na fila — porque foi respondida entretanto, ou esquecida —
        // segue-se a ordem normal, sem drama.
        val fila = bruta.sortedByDescending { it.lemma == preferido }
        preferido = null

        // Percorre-se a fila até uma palavra dar pergunta. Uma que não dê
        // não é falha nenhuma: quer dizer que não havia distrações à altura.
        for (palavra in fila) {
            val alvo = todas[palavra.lemma] ?: continue
            val pergunta = perguntaPara(dicionario, alvo, todas, aleatorio) ?: continue
            return EstadoJogo.AJogar(
                pergunta = pergunta,
                treinoLivre = semCalendario,
                escrever = Perguntas.pedeEscrita(
                    escreverAPalavra(), palavra.mastery, palavra.lemma, aleatorio,
                ),
            )
        }
        return EstadoJogo.SemPerguntaPossivel
    }

    /** As palavras do livro ainda por perguntar nesta sessão, as vencidas primeiro. */
    private suspend fun filaDoLivro(titulo: String, guardadas: List<PalavraGuardada>): List<PalavraGuardada> {
        val doLivro = guardadas.filter {
            it.livro?.trim() == titulo && it.lemma in todas && it.lemma !in perguntadas
        }
        val vencidas = utilizador.palavras().vencidas(agora()).map { it.lemma }.toSet()
        return doLivro.sortedWith(
            compareByDescending<PalavraGuardada> { it.lemma in vencidas }
                .thenBy { it.revistaEm ?: 0L }
        )
    }

    private suspend fun montarLivro(titulo: String, guardadas: List<PalavraGuardada>): EstadoJogo {
        val vencidas = utilizador.palavras().vencidas(agora()).map { it.lemma }.toSet()
        for (palavra in filaDoLivro(titulo, guardadas)) {
            val alvo = todas[palavra.lemma] ?: continue
            val pergunta = perguntaPara(dicionario, alvo, todas, aleatorio) ?: continue
            semCalendario = palavra.lemma !in vencidas
            return EstadoJogo.AJogar(
                pergunta = pergunta,
                escrever = Perguntas.pedeEscrita(
                    escreverAPalavra(), palavra.mastery, palavra.lemma, aleatorio,
                ),
            )
        }
        return EstadoJogo.LivroRevisto(titulo, perguntadas.size)
    }

    /**
     * Uma resposta já a caminho da base. O estado só passa a "respondida"
     * depois de gravar, e nesse intervalo as opções continuavam tocáveis: um
     * toque duplo contava a resposta duas vezes (pontos e caixa de Leitner).
     * Marca-se aqui, no fio principal, antes de lançar a escrita.
     */
    private var aResponder = false

    fun responder(opcao: Int) {
        val atual = _estado.value as? EstadoJogo.AJogar ?: return
        if (atual.escrever) return
        processar(atual, opcao == atual.pergunta.indiceCerto) { it.copy(respondida = opcao) }
    }

    /** A resposta escrita; vazia é "Não sei". */
    fun responderEscrita(texto: String) {
        val atual = _estado.value as? EstadoJogo.AJogar ?: return
        if (!atual.escrever) return
        val acertou = Perguntas.escritaCerta(texto, atual.pergunta.lemma)
        processar(atual, acertou) {
            it.copy(
                respondida = if (acertou) atual.pergunta.indiceCerto else -1,
                escrito = texto.trim(),
            )
        }
    }

    private fun processar(
        atual: EstadoJogo.AJogar,
        acertou: Boolean,
        marcar: (EstadoJogo.AJogar) -> EstadoJogo.AJogar,
    ) {
        if (atual.respondida != null || aResponder) return  // já respondeu a esta
        aResponder = true

        viewModelScope.launch {
          try {
            val lemma = atual.pergunta.lemma

            val resultado = withContext(Dispatchers.IO) {
                val guardada = utilizador.palavras().porLema(lemma)
                val anterior = pontuacaoAtual()
                val avanco = aplicarResposta(
                    caixaAtual = guardada?.mastery ?: 0,
                    pontuacaoAtual = anterior,
                    acertou = acertou,
                    agora = agora(),
                    contaParaOCalendario = !semCalendario,
                )
                gravar(guardada, avanco)
                // A rever um livro, todas as respostas contam para o limite.
                if (manual && (!semCalendario || livro != null)) gastarCota()
                aoResponder(lemma)
                // Há mais para rever? (Esta já não está vencida.) A rever um
                // livro: as dele que ainda não se perguntaram.
                perguntadas += lemma
                val maisVencidas = if (livro != null) {
                    // Só conta se alguma ainda dá pergunta: senão "Palavra
                    // seguinte" levava direto a "Livro revisto".
                    val haMais = filaDoLivro(livro, utilizador.palavras().todasAgora()).any { p ->
                        todas[p.lemma]?.let { perguntaPara(dicionario, it, todas, aleatorio) } != null
                    }
                    if (haMais) 1 else 0
                } else {
                    utilizador.palavras().vencidas(agora())
                        .count { it.lemma in todas && it.lemma != lemma }
                }
                Resultado(
                    dicionario?.entradaPorLema(lemma), avanco, avanco.ganho(anterior),
                    Niveis.subiu(anterior.acertos, avanco.pontuacao.acertos),
                    maisVencidas,
                )
            }
            val restantes = cotaRestante()
            val podeSeguir = treinoLivre || (resultado.maisVencidas > 0 && restantes > 0)

            _estado.value = marcar(atual).copy(
                entrada = resultado.entrada,
                livro = todas[lemma]?.livro,
                frase = todas[lemma]?.frase,
                autor = todas[lemma]?.autor,
                pagina = todas[lemma]?.page,
                ganho = resultado.ganho,
                bonus = resultado.avanco.bonus,
                total = resultado.avanco.pontuacao.pontos,
                sequencia = resultado.avanco.pontuacao.sequencia,
                subiuDeNivel = resultado.subiuDeNivel,
                podeSeguir = podeSeguir,
                rotuloSeguir = if (manual || treinoLivre) "Palavra seguinte" else "Rever mais",
                feitoEmDestaque = !manual && !treinoLivre,
                restamHoje = if (podeSeguir && !treinoLivre) restantes else null,
            )
          } finally {
            aResponder = false
          }
        }
    }

    /** O que a resposta produziu, passado do fio de fundo para o estado. */
    private data class Resultado(
        val entrada: Entrada?,
        val avanco: Avanco,
        val ganho: Int,
        val subiuDeNivel: Nivel?,
        val maisVencidas: Int,
    )

    private suspend fun pontuacaoAtual(): Pontuacao {
        val p = utilizador.progresso().atual() ?: Progresso()
        return Pontuacao(p.pontos, p.sequencia, p.ultimoDia, p.acertos, p.erros)
    }

    private suspend fun gravar(guardada: PalavraGuardada?, avanco: Avanco) {
        if (guardada != null) {
            utilizador.palavras().atualizar(
                guardada.copy(
                    mastery = avanco.caixa,
                    revistaEm = agora(),
                    // No treino livre a data de revisão não mexe — ver
                    // `aplicarResposta`.
                    proximaRevisao = avanco.proximaRevisao ?: guardada.proximaRevisao,
                )
            )
        }
        val p = avanco.pontuacao
        utilizador.progresso().guardar(
            Progresso(1, p.pontos, p.sequencia, p.ultimoDia, p.acertos, p.erros)
        )
    }

}
