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

    data class AJogar(
        val pergunta: Pergunta,
        val respondida: Int? = null,
        val entrada: Entrada? = null,
        val livro: String? = null,
        val frase: String? = null,
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
         * Deixa encadear palavras.
         *
         * Só no Modo Desenvolvedor, e a razão é prática: verificar perguntas
         * uma a uma, fechando e reabrindo a app entre cada, é trabalho a
         * mais para quem está a caçar defeitos. Foi assim que apareceram os
         * seis problemas de 5 de agosto.
         */
        val podeSeguir: Boolean = false,
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
     * quem lê: deixa jogar **mesmo sem nada vencido** — sem esperar dias que
     * uma palavra volte — e deixa **encadear palavras** em vez de uma por
     * sessão. Para quem lê, o jogo é uma pergunta que chega pelo lembrete, e
     * "não há nada a rever" é uma resposta boa.
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
    private val aleatorio: Random = Random.Default,
    private val agora: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val dicionario: Dicionario? get() = obterDicionario()

    private val _estado = MutableStateFlow<EstadoJogo>(EstadoJogo.ACarregar)
    val estado: StateFlow<EstadoJogo> = _estado.asStateFlow()

    val progresso = utilizador.progresso().observar()

    private var todas: Map<String, Candidata> = emptyMap()
    private var semCalendario = false

    init { comecar() }

    /**
     * Uma sessão é **uma pergunta**.
     *
     * Foi assim que o jogo foi pensado e é assim que se comporta: chega a
     * notificação, responde-se, acabou. Um "palavra seguinte" transformava
     * um gesto de dez segundos numa sessão de estudo, e a app deixava de
     * caber no intervalo em que se pousa o livro.
     *
     * Quem quiser mais responde à notificação seguinte.
     */
    fun comecar() {
        viewModelScope.launch {
            _estado.value = EstadoJogo.ACarregar
            _estado.value = withContext(Dispatchers.IO) { montar() }
        }
    }

    private suspend fun montar(): EstadoJogo {
        val guardadas = utilizador.palavras().todasAgora()
        // As mesmas funções que o lembrete usa (ver `Escolha.kt`): a palavra
        // anunciada na notificação é sempre uma que o jogo consegue perguntar.
        todas = candidatas(dicionario, guardadas)

        if (todas.size < MINIMO_PARA_JOGAR) {
            return EstadoJogo.PoucasPalavras(todas.size)
        }

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
        val fila = bruta.sortedByDescending { it.lemma == lemaPreferido }

        // Percorre-se a fila até uma palavra dar pergunta. Uma que não dê
        // não é falha nenhuma: quer dizer que não havia distrações à altura.
        for (palavra in fila) {
            val alvo = todas[palavra.lemma] ?: continue
            val pergunta = perguntaPara(dicionario, alvo, todas, aleatorio) ?: continue
            return EstadoJogo.AJogar(
                pergunta = pergunta,
                treinoLivre = semCalendario,
                podeSeguir = treinoLivre,
            )
        }
        return EstadoJogo.SemPerguntaPossivel
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
        if (atual.respondida != null || aResponder) return  // já respondeu a esta
        aResponder = true

        viewModelScope.launch {
          try {
            val acertou = opcao == atual.pergunta.indiceCerto
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
                Resultado(
                    dicionario?.entradaPorLema(lemma), avanco, avanco.ganho(anterior),
                    Niveis.subiu(anterior.acertos, avanco.pontuacao.acertos),
                )
            }

            _estado.value = atual.copy(
                respondida = opcao,
                entrada = resultado.entrada,
                livro = todas[lemma]?.livro,
                frase = todas[lemma]?.frase,
                ganho = resultado.ganho,
                bonus = resultado.avanco.bonus,
                total = resultado.avanco.pontuacao.pontos,
                sequencia = resultado.avanco.pontuacao.sequencia,
                subiuDeNivel = resultado.subiuDeNivel,
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
