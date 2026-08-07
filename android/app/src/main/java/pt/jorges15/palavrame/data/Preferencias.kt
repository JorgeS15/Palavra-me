package pt.jorges15.palavrame.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferências da app. Poucas, e todas do utilizador.
 *
 * Não é Room: são três valores e um `SharedPreferences` chega. Meter uma
 * base de dados a gerir isto seria peso sem contrapartida.
 */
class Preferencias(context: Context) {

    private val prefs = context.getSharedPreferences("palavrame", Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(lerTema())
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    fun definirTema(novo: Tema) {
        prefs.edit().putString(CHAVE_TEMA, novo.name).apply()
        _tema.value = novo
    }

    private fun lerTema(): Tema =
        prefs.getString(CHAVE_TEMA, null)
            ?.let { guardado -> Tema.entries.firstOrNull { it.name == guardado } }
            ?: Tema.SISTEMA

    // --- modo jogo --------------------------------------------------------

    /**
     * Desligado por omissão, e não é timidez.
     *
     * Uma app de leitura que começa a notificar sem se pedir é uma app que se
     * desinstala. Quem quiser o lembrete diário liga-o; quem não quiser nunca
     * dá por ele.
     */
    private val _jogoLigado = MutableStateFlow(prefs.getBoolean(CHAVE_JOGO, false))
    val jogoLigado: StateFlow<Boolean> = _jogoLigado.asStateFlow()

    /**
     * Quantos lembretes por dia e entre que horas.
     *
     * Um por dia, às 21h, é o ponto de partida — o serão de quem lê. Quem
     * quiser mais escolhe quantos e a janela; as horas saem distribuídas
     * uniformemente por `horasDosLembretes`.
     */
    private val _quantosPorDia =
        MutableStateFlow(prefs.getInt(CHAVE_QUANTOS, QUANTOS_POR_OMISSAO))
    val quantosPorDia: StateFlow<Int> = _quantosPorDia.asStateFlow()

    private val _inicioDaJanela =
        MutableStateFlow(prefs.getInt(CHAVE_INICIO, INICIO_POR_OMISSAO))
    val inicioDaJanela: StateFlow<Int> = _inicioDaJanela.asStateFlow()

    private val _fimDaJanela = MutableStateFlow(prefs.getInt(CHAVE_FIM, FIM_POR_OMISSAO))
    val fimDaJanela: StateFlow<Int> = _fimDaJanela.asStateFlow()

    fun definirJogoLigado(ligado: Boolean) {
        prefs.edit().putBoolean(CHAVE_JOGO, ligado).apply()
        _jogoLigado.value = ligado
    }

    fun definirQuantosPorDia(quantos: Int) {
        val valido = quantos.coerceIn(1, MAX_POR_DIA)
        prefs.edit().putInt(CHAVE_QUANTOS, valido).apply()
        _quantosPorDia.value = valido
    }

    /** A janela guarda-se ordenada: não há aqui janelas que atravessam a meia-noite. */
    fun definirJanela(inicio: Int, fim: Int) {
        val a = minOf(inicio, fim).coerceIn(0, 23)
        val b = maxOf(inicio, fim).coerceIn(0, 23)
        prefs.edit().putInt(CHAVE_INICIO, a).putInt(CHAVE_FIM, b).apply()
        _inicioDaJanela.value = a
        _fimDaJanela.value = b
    }

    // --- modo desenvolvedor -----------------------------------------------

    /**
     * Mostra a maquinaria por trás do dicionário.
     *
     * Serve duas coisas que interessam a quem constrói a base e não a quem
     * lê um livro: a **proveniência de cada aceção** — saber que aquela
     * definição veio do Dicionário Aberto de 1913 e não do Wikcionário é
     * essencial para caçar defeitos, e foi assim que apareceram as citações
     * bibliográficas e os parênteses partidos — e o **treino livre** no
     * jogo, que serve para testar perguntas sem esperar que uma palavra
     * fique vencida.
     *
     * Desligado por omissão. Quem lê não quer saber de nada disto.
     */
    private val _modoDesenvolvedor =
        MutableStateFlow(prefs.getBoolean(CHAVE_DESENVOLVEDOR, false))
    val modoDesenvolvedor: StateFlow<Boolean> = _modoDesenvolvedor.asStateFlow()

    fun definirModoDesenvolvedor(ligado: Boolean) {
        prefs.edit().putBoolean(CHAVE_DESENVOLVEDOR, ligado).apply()
        _modoDesenvolvedor.value = ligado
    }

    companion object {
        private const val CHAVE_TEMA = "tema"
        private const val CHAVE_JOGO = "jogo_ligado"
        private const val CHAVE_QUANTOS = "jogo_quantos"
        private const val CHAVE_INICIO = "jogo_inicio"
        private const val CHAVE_FIM = "jogo_fim"
        private const val CHAVE_DESENVOLVEDOR = "modo_desenvolvedor"

        const val QUANTOS_POR_OMISSAO = 1
        const val INICIO_POR_OMISSAO = 21
        const val FIM_POR_OMISSAO = 22

        /**
         * Teto de lembretes por dia.
         *
         * Seis já é muito para uma app de leitura. O teto não existe por
         * limitação técnica — existe porque a app não deve ajudar ninguém a
         * transformá-la em algo de que se farte numa semana.
         */
        const val MAX_POR_DIA = 6
    }
}

/**
 * Segue o telemóvel por omissão — é o que a maior parte das pessoas espera —
 * mas quem lê à noite pode querer o escuro sempre, e quem lê à luz do dia o
 * claro. Forçar é uma escolha legítima, não um capricho.
 */
enum class Tema(val etiqueta: String) {
    SISTEMA("Como o telemóvel"),
    CLARO("Claro"),
    ESCURO("Escuro"),
}
