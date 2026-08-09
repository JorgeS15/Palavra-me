package pt.jorges15.palavrame.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import pt.jorges15.palavrame.data.UtilizadorDb

data class EstadoPesquisa(
    val texto: String = "",
    val sugestoes: List<String> = emptyList(),
    val candidatos: List<Candidato> = emptyList(),
    val entrada: Entrada? = null,
    val procurou: Boolean = false,
)

/** Um livro já na coleção, com o autor que lhe ficou associado (se algum). */
data class Livro(val titulo: String, val autor: String?)

class PesquisaViewModel(
    private val dicionario: Dicionario?,
    private val utilizador: UtilizadorDb,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoPesquisa())
    val estado: StateFlow<EstadoPesquisa> = _estado.asStateFlow()

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
        // registo de cada título é o mais recente. `associateBy` mantém o
        // primeiro que vê para cada chave, que é exatamente o que se quer.
        lista.filter { !it.livro.isNullOrBlank() }
            .associate { it.livro!! to it.autor }
            .map { (titulo, autor) -> Livro(titulo, autor) }
    }

    fun escrever(texto: String) {
        _estado.value = _estado.value.copy(texto = texto, procurou = false)
        viewModelScope.launch {
            val sugestoes = withContext(Dispatchers.IO) {
                dicionario?.sugerir(texto).orEmpty()
            }
            if (_estado.value.texto == texto) {
                _estado.value = _estado.value.copy(sugestoes = sugestoes)
            }
        }
    }

    /** Chamado pela pesquisa e também pelo PROCESS_TEXT vindo do e-book. */
    fun procurar(texto: String = _estado.value.texto) {
        viewModelScope.launch {
            val candidatos = withContext(Dispatchers.IO) {
                dicionario?.procurar(texto).orEmpty()
            }
            // Um candidato só: abre-se logo a entrada, que é o caso comum.
            val entrada = if (candidatos.size == 1) {
                withContext(Dispatchers.IO) { dicionario?.entrada(candidatos[0].lemmaId) }
            } else null

            _estado.value = _estado.value.copy(
                texto = texto,
                sugestoes = emptyList(),
                candidatos = candidatos,
                entrada = entrada,
                procurou = true,
            )
        }
    }

    fun abrir(candidato: Candidato) {
        viewModelScope.launch {
            val entrada = withContext(Dispatchers.IO) { dicionario?.entrada(candidato.lemmaId) }
            _estado.value = _estado.value.copy(entrada = entrada)
        }
    }

    /** Limpa a pesquisa e volta ao ecrã vazio. */
    fun limpar() {
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

    /** Lemas já na coleção — o botão precisa de saber se já lá está. */
    val lemasGuardados: Flow<Set<String>> =
        palavrasGuardadas.map { lista -> lista.map { it.lemma }.toSet() }
}
