package pt.jorges15.palavrame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jorges15.palavrame.data.Candidato
import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.data.UtilizadorDb
import pt.jorges15.palavrame.jogo.MINIMO_PARA_JOGAR

data class EstadoPesquisa(
    val texto: String = "",
    val sugestoes: List<String> = emptyList(),
    val candidatos: List<Candidato> = emptyList(),
    val entrada: Entrada? = null,
    val procurou: Boolean = false,
    /** "Querias dizer…?" — só preenchido quando a pesquisa não encontrou nada. */
    val parecidas: List<String> = emptyList(),
)

/** Um livro já na coleção, com o autor que lhe ficou associado (se algum). */
data class Livro(val titulo: String, val autor: String?)

class PesquisaViewModel(
    /**
     * Pede-se o dicionário à app em cada uso, em vez de guardar a instância
     * do arranque: instalar uma base nova (em "Dicionário e licenças") fecha a
     * antiga, e uma referência guardada rebentava na pesquisa seguinte.
     */
    private val obterDicionario: () -> Dicionario?,
    private val utilizador: UtilizadorDb,
) : ViewModel() {

    private val dicionario: Dicionario? get() = obterDicionario()

    private val _estado = MutableStateFlow(EstadoPesquisa())
    val estado: StateFlow<EstadoPesquisa> = _estado.asStateFlow()

    // Histórico de palavras visitadas, para o "retroceder" andar palavra a
    // palavra como um navegador. Só se toca na thread principal (as mutações
    // de estado acontecem sempre aí), portanto não precisa de sincronização.
    private val historico = ArrayDeque<Entrada>()

    // Frase de contexto vinda de uma seleção multi-palavra no leitor de
    // e-books, para pré-preencher o campo da frase no registo. Sobrevive a
    // corrigir o termo de pesquisa à mão; limpa-se ao registar, ao cancelar ou
    // ao limpar a pesquisa.
    private val _fraseSugerida = MutableStateFlow<String?>(null)
    val fraseSugerida: StateFlow<String?> = _fraseSugerida.asStateFlow()

    val palavrasGuardadas = utilizador.palavras().todas()

    /** Alimenta a linha de estatísticas do ecrã inicial. */
    val progresso = utilizador.progresso().observar()

    /**
     * Livros já usados, o mais recente primeiro, cada um com o autor que lhe
     * ficou associado — alimenta a caixa de seleção do diálogo de registo.
     *
     * Sai das palavras guardadas em vez de uma consulta própria: são poucas e
     * assim segue sempre o que lá está. Para cada título fica o **autor mais
     * recente** que foi escrito para ele: se o corrigiste na última palavra
     * que registaste desse livro, é essa correção que passa a preencher-se.
     */
    val livrosUsados: Flow<List<Livro>> = palavrasGuardadas.map { lista ->
        // A lista vem por data decrescente (ver o DAO), portanto o primeiro
        // registo de cada título é o mais recente. `groupBy` mantém essa ordem
        // dentro de cada grupo, e fica-se com o primeiro autor escrito. (Era
        // `associate`, que guarda o ÚLTIMO valor de cada chave: pré-preenchia
        // o autor mais antigo, ao contrário do que se queria.)
        lista.filter { !it.livro.isNullOrBlank() }
            .groupBy { it.livro!! }
            .map { (titulo, registos) ->
                Livro(titulo, registos.firstNotNullOfOrNull { it.autor?.takeIf(String::isNotBlank) })
            }
    }

    /** O trabalho da tecla anterior: cancela-se quando se escreve outra. */
    private var aEscrever: Job? = null

    fun escrever(texto: String) {
        // Escrever é começar uma pesquisa nova: a palavra que estava aberta (e
        // a lista de candidatos) sai da frente, senão tapava as sugestões — o
        // ecrã dá prioridade à entrada aberta, e nem a lista de começos nem o
        // "Querias dizer" chegavam a aparecer. A palavra fica no histórico, e o
        // "retroceder" volta a ela.
        _estado.value.entrada?.let { historico.addLast(it) }
        _estado.value = _estado.value.copy(
            texto = texto, procurou = false, parecidas = emptyList(),
            entrada = null, candidatos = emptyList(),
        )
        aEscrever?.cancel()
        aEscrever = viewModelScope.launch {
            val sugestoes = withContext(Dispatchers.IO) {
                dicionario?.sugerir(texto).orEmpty()
            }
            if (_estado.value.texto != texto) return@launch
            _estado.value = _estado.value.copy(sugestoes = sugestoes)

            // "Querias dizer…?" enquanto se escreve: quando nada começa pelo
            // que foi escrito, é provável que haja um erro. Espera-se um
            // instante (se vier outra tecla, isto é cancelado) e só se sugere
            // se o que está escrito não existir — "couberam" não aparece na
            // lista de começos, mas é uma flexão certa e não merece sugestões.
            if (sugestoes.isNotEmpty() || Texto.normalizar(texto).length < 4) return@launch
            delay(350)
            val parecidas = withContext(Dispatchers.IO) {
                val d = dicionario ?: return@withContext emptyList()
                if (d.procurar(texto).isNotEmpty()) emptyList() else d.parecidas(texto)
            }
            if (_estado.value.texto == texto && !_estado.value.procurou) {
                _estado.value = _estado.value.copy(parecidas = parecidas)
            }
        }
    }

    /**
     * Pesquisa uma palavra e guarda a frase de contexto — uma seleção com mais
     * do que uma palavra vinda do leitor. O termo de pesquisa é a palavra
     * principal; a frase inteira fica para pré-preencher o registo.
     */
    fun procurarComContexto(palavra: String, frase: String) {
        _fraseSugerida.value = frase
        viewModelScope.launch {
            // Uma seleção curta pode ser ela própria uma locução ("de facto",
            // "a fim de"). Se for, procura-se a expressão inteira; senão, a
            // palavra principal, como antes.
            val curta = frase.trim().split(Regex("\\s+")).size in 2..4
            val eExpressao = curta && withContext(Dispatchers.IO) {
                dicionario?.procurarExpressao(frase).orEmpty().isNotEmpty()
            }
            procurar(if (eExpressao) frase.trim() else palavra)
        }
    }

    /** A frase de contexto foi usada (ou dispensada): esquece-a. */
    fun limparContexto() {
        _fraseSugerida.value = null
    }

    /** Chamado pela pesquisa e também pelo PROCESS_TEXT vindo do e-book. */
    fun procurar(texto: String = _estado.value.texto) {
        aEscrever?.cancel()   // a pesquisa substitui as sugestões da escrita
        viewModelScope.launch {
            val candidatos = withContext(Dispatchers.IO) {
                dicionario?.procurar(texto).orEmpty()
            }
            // Um candidato só: abre-se logo a entrada, que é o caso comum.
            val entrada = if (candidatos.size == 1) {
                withContext(Dispatchers.IO) { dicionario?.entrada(candidatos[0].lemmaId) }
            } else null
            // Nada encontrado: palavras parecidas, para o caso de ser um erro
            // de escrita, antes de oferecer registá-la como nova.
            val parecidas = if (candidatos.isEmpty()) {
                withContext(Dispatchers.IO) { dicionario?.parecidas(texto).orEmpty() }
            } else emptyList()

            empilhar(entrada)
            _estado.value = _estado.value.copy(
                texto = texto,
                sugestoes = emptyList(),
                candidatos = candidatos,
                entrada = entrada,
                procurou = true,
                parecidas = parecidas,
            )
        }
    }

    fun abrir(candidato: Candidato) {
        viewModelScope.launch {
            val entrada = withContext(Dispatchers.IO) { dicionario?.entrada(candidato.lemmaId) }
            empilhar(entrada)
            _estado.value = _estado.value.copy(entrada = entrada)
        }
    }

    /** Guarda a palavra atual no histórico antes de a trocar por outra. */
    private fun empilhar(nova: Entrada?) {
        val atual = _estado.value.entrada
        if (nova != null && atual != null && atual.lemmaId != nova.lemmaId) {
            historico.addLast(atual)
        }
    }

    /**
     * Volta à palavra anterior, se houver. Devolve `false` quando o histórico
     * está vazio — aí o ecrã trata o "retroceder" como dantes (fecha a entrada).
     */
    fun voltarEntrada(): Boolean {
        val anterior = historico.removeLastOrNull() ?: return false
        _estado.value = _estado.value.copy(
            texto = anterior.lemma,
            entrada = anterior,
            candidatos = emptyList(),
            sugestoes = emptyList(),
            procurou = true,
            parecidas = emptyList(),
        )
        return true
    }

    /** Limpa a pesquisa e volta ao ecrã vazio. */
    fun limpar() {
        aEscrever?.cancel()
        historico.clear()
        _fraseSugerida.value = null
        _estado.value = EstadoPesquisa()
    }

    fun fecharEntrada() {
        _estado.value = _estado.value.copy(entrada = null)
    }

    fun registar(palavra: PalavraGuardada) {
        viewModelScope.launch { utilizador.palavras().guardar(palavra) }
    }

    fun esquecer(lemma: String) {
        viewModelScope.launch { utilizador.palavras().esquecer(lemma) }
    }

    fun atualizar(palavra: PalavraGuardada) {
        viewModelScope.launch { utilizador.palavras().atualizar(palavra) }
    }

    fun apagar(palavra: PalavraGuardada) {
        viewModelScope.launch { utilizador.palavras().apagar(palavra.id) }
    }

    /**
     * Repõe uma palavra que se acabou de apagar — o "Anular" do Snackbar.
     *
     * Reinsere o objeto inteiro, com o id, a data e o progresso de revisão
     * intactos: anular um esquecimento devolve a palavra tal como estava, não
     * um registo novo. Como a linha foi mesmo apagada, não há conflito.
     */
    fun repor(palavra: PalavraGuardada) {
        viewModelScope.launch { utilizador.palavras().guardar(palavra) }
    }

    /** Lemas já na coleção — o botão precisa de saber se já lá está. */
    val lemasGuardados: Flow<Set<String>> =
        palavrasGuardadas.map { lista -> lista.map { it.lemma }.toSet() }

    /**
     * Há mesmo alguma coisa a rever agora? Alimenta o botão "Rever" do ecrã
     * inicial — que só aparece quando há algo vencido, para dar acesso ao jogo
     * a quem não liga os lembretes sem quebrar a regra de nunca convidar quando
     * não há nada a dizer (ponto 3.5 da revisão).
     *
     * Não confirma a jogabilidade (isso exigiria o dicionário para cada
     * palavra); se a única vencida não der pergunta, o ecrã do jogo trata
     * disso com elegância.
     */
    val porRever: Flow<Int> = palavrasGuardadas.map { lista ->
        if (lista.size < MINIMO_PARA_JOGAR) return@map 0
        val agora = System.currentTimeMillis()
        lista.count { it.proximaRevisao == null || it.proximaRevisao <= agora }
    }
}

/** Como uma palavra registada falha o dicionário. */
enum class TipoFalta { NAO_ENCONTRADA, SEM_SIGNIFICADO }

/** Uma palavra da coleção que o dicionário não cobre — candidata a submissão. */
data class PalavraEmFalta(
    val lemma: String,
    val significadoProprio: String?,
    val tipo: TipoFalta,
)

/**
 * As palavras registadas que o dicionário não cobre: as que não existem lá de
 * todo, e as que existem mas sem significado atribuído. É o que se submete
 * para a curadoria — e distingue-se um caso do outro, como o Jorge pediu.
 *
 * Corre em IO: percorre a coleção e consulta o dicionário por cada palavra.
 */
suspend fun palavrasEmFalta(
    dicionario: Dicionario?,
    utilizador: UtilizadorDb,
): List<PalavraEmFalta> = withContext(Dispatchers.IO) {
    val dic = dicionario ?: return@withContext emptyList()
    utilizador.palavras().todasAgora().mapNotNull { p ->
        // Primeiro a grafia exata; se falhar, pelas formas (como a pesquisa).
        // Assim uma palavra registada no plural ou com outra grafia deixa de
        // contar como "em falta" quando o dicionário a passa a ter.
        val entrada = dic.entradaPorLema(p.lemma)
            ?: dic.procurar(p.lemma).firstOrNull()?.let { dic.entrada(it.lemmaId) }
        when {
            entrada == null ->
                PalavraEmFalta(p.lemma, p.significado, TipoFalta.NAO_ENCONTRADA)
            entrada.acecoes.isEmpty() ->
                PalavraEmFalta(p.lemma, p.significado, TipoFalta.SEM_SIGNIFICADO)
            else -> null
        }
    }
}
