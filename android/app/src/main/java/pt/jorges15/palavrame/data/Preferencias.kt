package pt.jorges15.palavrame.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.jorges15.palavrame.jogo.CotaManual
import java.time.LocalDate

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

    // --- aspeto: cores e letra ----------------------------------------------

    /**
     * A paleta de cores e a letra, escolhidas em Definições, Aspeto. Por
     * omissão as do redesign (Papel e Literata): quem já usa a app não vê
     * diferença até mudar.
     */
    private val _paleta = MutableStateFlow(
        prefs.getString(CHAVE_PALETA, null)
            ?.let { g -> PaletaId.entries.firstOrNull { it.name == g } } ?: PaletaId.PAPEL
    )
    val paleta: StateFlow<PaletaId> = _paleta.asStateFlow()

    fun definirPaleta(nova: PaletaId) {
        prefs.edit().putString(CHAVE_PALETA, nova.name).apply()
        _paleta.value = nova
    }

    private val _letra = MutableStateFlow(
        prefs.getString(CHAVE_LETRA, null)
            ?.let { g -> LetraId.entries.firstOrNull { it.name == g } } ?: LetraId.LITERATA
    )
    val letra: StateFlow<LetraId> = _letra.asStateFlow()

    fun definirLetra(nova: LetraId) {
        prefs.edit().putString(CHAVE_LETRA, nova.name).apply()
        _letra.value = nova
    }

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
     * Quatro por dia, das 9h às 21h, é o ponto de partida: espalha as palavras
     * pelo dia de quem lê sem apanhar a madrugada nem a hora de dormir (9, 13,
     * 17, 21). Quem quiser mais ou menos escolhe quantos e a janela; as horas
     * saem distribuídas uniformemente por `horasDosLembretes`.
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

    /**
     * Mostrar a palavra no lembrete. Desligado, diz só "Tens uma palavra para
     * rever" (pedido de quem não quer a palavra à vista de quem passa). O
     * ecrã bloqueado esconde-a sempre (ver `Lembrete.mostrar`).
     */
    private val _palavraNasNotificacoes =
        MutableStateFlow(prefs.getBoolean(CHAVE_PALAVRA_NOTIFICACAO, true))
    val palavraNasNotificacoes: StateFlow<Boolean> = _palavraNasNotificacoes.asStateFlow()

    /**
     * "Escrever a palavra": nas palavras que já se acertam, metade das
     * perguntas pede para escrever a palavra a partir da definição. Desligado
     * por omissão: é mais difícil, e só para quem o quiser.
     */
    private val _escreverAPalavra = MutableStateFlow(prefs.getBoolean(CHAVE_ESCREVER, false))
    val escreverAPalavra: StateFlow<Boolean> = _escreverAPalavra.asStateFlow()

    fun definirEscreverAPalavra(ligado: Boolean) {
        prefs.edit().putBoolean(CHAVE_ESCREVER, ligado).apply()
        _escreverAPalavra.value = ligado
    }

    fun definirPalavraNasNotificacoes(mostrar: Boolean) {
        prefs.edit().putBoolean(CHAVE_PALAVRA_NOTIFICACAO, mostrar).apply()
        _palavraNasNotificacoes.value = mostrar
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

    // --- pedido de avaliação ---------------------------------------------

    /**
     * O marco de palavras guardadas a que ainda não se pediu avaliação, ou
     * nulo. Fica só neste telemóvel: não se conta nada a ninguém.
     */
    fun avaliacaoPorPedir(totalGuardadas: Int): Int? =
        if (prefs.getBoolean(CHAVE_AVALIACAO_FIM, false)) null
        else marcoDeAvaliacao(totalGuardadas, prefs.getInt(CHAVE_AVALIACAO, 0))

    /**
     * "Agora não" (ou fechar o diálogo): esse marco já não pergunta, mas o
     * seguinte sim.
     */
    fun marcarAvaliacaoPedida(marco: Int) {
        prefs.edit().putInt(CHAVE_AVALIACAO, marco).apply()
    }

    /** "Avaliar" ou "Não voltar a perguntar": nunca mais se pede. */
    fun terminarPedidosDeAvaliacao() {
        prefs.edit().putBoolean(CHAVE_AVALIACAO_FIM, true).apply()
    }

    // --- revisão à mão: o limite do dia --------------------------------------

    private val _cotaManual = MutableStateFlow(
        CotaManual(prefs.getString(CHAVE_COTA_DIA, null), prefs.getInt(CHAVE_COTA_USADAS, 0))
    )

    /** As revisões à mão feitas hoje (ver `LIMITE_MANUAL_POR_DIA`). */
    val cotaManual: StateFlow<CotaManual> = _cotaManual.asStateFlow()

    /** Conta uma resposta dada numa revisão à mão. */
    fun usarCotaManual(hoje: LocalDate = LocalDate.now()) {
        val nova = _cotaManual.value.usar(hoje)
        prefs.edit()
            .putString(CHAVE_COTA_DIA, nova.dia)
            .putInt(CHAVE_COTA_USADAS, nova.usadas)
            .apply()
        _cotaManual.value = nova
    }

    // --- novidades da versão ------------------------------------------------

    /** O código da versão cujas novidades já se mostraram (0 = nenhuma). */
    fun versaoVista(): Int = prefs.getInt(CHAVE_VERSAO_VISTA, 0)

    fun marcarVersaoVista(codigo: Int) {
        prefs.edit().putInt(CHAVE_VERSAO_VISTA, codigo).apply()
    }

    companion object {
        private const val CHAVE_VERSAO_VISTA = "novidades_vistas_ate"
        private const val CHAVE_COTA_DIA = "revisao_manual_dia"
        private const val CHAVE_COTA_USADAS = "revisao_manual_usadas"
        private const val CHAVE_TEMA = "tema"
        private const val CHAVE_PALETA = "paleta"
        private const val CHAVE_LETRA = "letra"
        private const val CHAVE_AVALIACAO = "avaliacao_pedida_ate"
        private const val CHAVE_AVALIACAO_FIM = "avaliacao_terminada"
        private const val CHAVE_JOGO = "jogo_ligado"
        private const val CHAVE_PALAVRA_NOTIFICACAO = "notificacao_com_palavra"
        private const val CHAVE_ESCREVER = "jogo_escrever"

        /** Para o lembrete, que corre sem ecrã e sem esta classe à mão. */
        fun palavraNasNotificacoes(context: Context): Boolean =
            context.getSharedPreferences("palavrame", Context.MODE_PRIVATE)
                .getBoolean(CHAVE_PALAVRA_NOTIFICACAO, true)
        private const val CHAVE_QUANTOS = "jogo_quantos"
        private const val CHAVE_INICIO = "jogo_inicio"
        private const val CHAVE_FIM = "jogo_fim"
        private const val CHAVE_DESENVOLVEDOR = "modo_desenvolvedor"

        const val QUANTOS_POR_OMISSAO = 4
        const val INICIO_POR_OMISSAO = 9
        const val FIM_POR_OMISSAO = 21

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
 * Quando se pede uma avaliação: à 5.ª, 10.ª, 20.ª, 50.ª e 100.ª palavra
 * guardada, uma vez cada, e só enquanto a pessoa não avaliou nem pediu para
 * não voltar a perguntar ("Agora não" passa ao marco seguinte).
 */
val MARCOS_AVALIACAO = listOf(5, 10, 20, 50, 100)

/**
 * O marco a pedir agora, ou nulo. `jaPedido` é o maior marco já pedido.
 *
 * Usa ">=" e não "==": quem importa uma cópia com 70 palavras é perguntado
 * uma vez, ao guardar a seguinte, e pelo marco mais alto, em vez de nunca.
 */
fun marcoDeAvaliacao(total: Int, jaPedido: Int): Int? {
    val marco = MARCOS_AVALIACAO.lastOrNull { total >= it } ?: return null
    return if (marco > jaPedido) marco else null
}

/**
 * Segue o telemóvel por omissão — é o que a maior parte das pessoas espera —
 * mas quem lê à noite pode querer o escuro sempre, e quem lê à luz do dia o
 * claro. Forçar é uma escolha legítima, não um capricho.
 */
/** As paletas de cores (os valores estão em `ui/theme/Paletas.kt`). */
enum class PaletaId(val nome: String) {
    PAPEL("Papel"),
    BRANCO("Branco"),
    CADERNO("Caderno"),
    SALVIA("Sálvia"),
    LACRE("Lacre"),
}

/**
 * A letra da app. "Leitura" é a do conteúdo (lemas, definições, títulos);
 * "interface" a dos controlos. Na Literata são duas (Literata e Instrument
 * Sans); nas outras, a mesma família faz as duas coisas.
 */
enum class LetraId(val nome: String, val descricao: String) {
    LITERATA("Literata", "Serifa, para leitura longa"),
    FIGTREE("Figtree", "Sem serifa, limpa e atual"),
    ATKINSON("Atkinson Hyperlegible", "Feita para máxima legibilidade"),
    SISTEMA("Do telemóvel", "A letra do sistema"),
}

enum class Tema(val etiqueta: String) {
    SISTEMA("Como o telemóvel"),
    CLARO("Claro"),
    ESCURO("Escuro"),
}
