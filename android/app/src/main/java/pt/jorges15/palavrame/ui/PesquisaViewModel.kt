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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jorges15.palavrame.data.Candidato
import pt.jorges15.palavrame.data.CorrecaoGuardada
import pt.jorges15.palavrame.data.Dicionario
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.Leitura
import pt.jorges15.palavrame.data.Lingua
import pt.jorges15.palavrame.data.LivroCompleto
import pt.jorges15.palavrame.data.acertarLivros
import pt.jorges15.palavrame.data.apagarLivro
import pt.jorges15.palavrame.data.criarLivro
import pt.jorges15.palavrame.data.editarLivro
import pt.jorges15.palavrame.data.escolherEmCurso
import pt.jorges15.palavrame.data.juntarLivros
import pt.jorges15.palavrame.data.livrosCompletos
import pt.jorges15.palavrame.data.marcarPagina
import pt.jorges15.palavrame.data.pararLivro
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Parecida
import pt.jorges15.palavrame.data.Sugestao
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.data.UtilizadorDb
import pt.jorges15.palavrame.data.comecarLivro
import pt.jorges15.palavrame.data.terminarLivro
import pt.jorges15.palavrame.jogo.MINIMO_PARA_JOGAR
import pt.jorges15.palavrame.jogo.chave
import pt.jorges15.palavrame.jogo.chaveDe
import pt.jorges15.palavrame.jogo.lemaDaChave
import pt.jorges15.palavrame.jogo.linguaDaChave

data class EstadoPesquisa(
    val texto: String = "",
    val sugestoes: List<Sugestao> = emptyList(),
    val candidatos: List<Candidato> = emptyList(),
    val entrada: Entrada? = null,
    val procurou: Boolean = false,
    /** "Querias dizer", com a primeira definição de cada uma. */
    val parecidas: List<Parecida> = emptyList(),
    /** Em que dicionário se procura (`Lingua`). */
    val lingua: String = Lingua.PT,
    /**
     * Não encontrada nesta língua, mas existe na outra: a língua onde está,
     * para o ecrã oferecer "Ver em inglês". Nula no caso normal.
     */
    val existeNaOutra: String? = null,
)

/** Um livro já na coleção, com o autor que lhe ficou associado (se algum). */
data class Livro(val titulo: String, val autor: String?, val lingua: String = Lingua.PT)

class PesquisaViewModel(
    /**
     * Pede-se o dicionário à app em cada uso, em vez de guardar a instância
     * do arranque: instalar uma base nova (em "Dicionário e licenças") fecha a
     * antiga, e uma referência guardada rebentava na pesquisa seguinte.
     */
    private val obterDicionario: (lingua: String) -> Dicionario?,
    private val utilizador: UtilizadorDb,
    /** As línguas com dicionário instalado: só essas se oferecem na pesquisa. */
    private val obterLinguas: () -> List<String> = { listOf(Lingua.PT) },
) : ViewModel() {

    /** O dicionário da língua em que se está a procurar. */
    private val dicionario: Dicionario? get() = obterDicionario(_estado.value.lingua)

    /** As línguas a oferecer. Sem o inglês instalado, a escolha não aparece. */
    val linguas: List<String> get() = obterLinguas().ifEmpty { listOf(Lingua.PT) }

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

    /**
     * A coleção já carregada, para a Coleção não piscar vazia ao voltar a ela.
     *
     * Com `collectAsState(initial = emptyList())`, cada vez que o ecrã aparecia
     * o primeiro instante era uma lista vazia, até a base responder (e a
     * posição guardada da lista perdia-se, porque a lista vazia não tem para
     * onde rolar). Aqui a lista fica guardada no ViewModel, que sobrevive à
     * mudança de ecrã, e volta logo com o último valor. Nulo só no arranque,
     * antes da primeira leitura: aí o ecrã ainda não mostra nada.
     */
    val colecao: StateFlow<List<PalavraGuardada>?> = palavrasGuardadas
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    val livrosUsados: Flow<List<Livro>> = combine(
        palavrasGuardadas, utilizador.livros().fichas(),
    ) { lista, fichas ->
        // A lista vem por data decrescente (ver o DAO), portanto o primeiro
        // registo de cada título é o mais recente. O autor da ficha do livro
        // (1.22) vale mais do que o das palavras; sem ficha, o mais recente
        // escrito.
        val autorDaFicha = fichas.associate { it.titulo to it.autor }
        val linguaDaFicha = fichas.associate { it.titulo to it.lingua }
        val dasPalavras = lista.filter { !it.livro.isNullOrBlank() }
            .groupBy { it.livro!!.trim() }
            .map { (titulo, registos) ->
                Livro(
                    titulo,
                    autorDaFicha[titulo]
                        ?: registos.firstNotNullOfOrNull { it.autor?.takeIf(String::isNotBlank) },
                    linguaDaFicha[titulo] ?: registos.first().lingua,
                )
            }
        // Os livros começados sem palavras também se escolhem, os mais novos primeiro.
        val usados = dasPalavras.map { it.titulo }.toSet()
        dasPalavras + fichas.filter { it.titulo !in usados }
            .sortedByDescending { it.criadoEm }
            .map { Livro(it.titulo, it.autor, it.lingua) }
    }

    /**
     * Os livros (1.22): as fichas com as leituras, por título. As palavras
     * apontam para eles pelo título (ver `data/Livros.kt`).
     */
    val livros: StateFlow<Map<String, LivroCompleto>> = combine(
        utilizador.livros().fichas(), utilizador.livros().leituras(),
    ) { fichas, leituras -> livrosCompletos(fichas, leituras) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** O livro que vem já escolhido ao registar uma palavra. */
    val livroEmCurso: StateFlow<Livro?> = livros.map { mapa ->
        mapa.values.firstOrNull { it.ficha.emCurso }?.let { Livro(it.titulo, it.ficha.autor, it.ficha.lingua) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // A pesquisa abre na língua do livro que se está a ler: quem lê um
        // romance em inglês procura em inglês sem ter de o dizer. Muda só
        // quando muda o livro em curso; a escolha à mão vale até lá.
        viewModelScope.launch {
            var anterior: String? = null
            livroEmCurso.collect { livro ->
                val l = livro?.lingua ?: return@collect
                if (l == anterior || l !in linguas) return@collect
                anterior = l
                val e = _estado.value
                if (e.lingua != l && !e.procurou && e.texto.isEmpty() && e.entrada == null) {
                    _estado.value = e.copy(lingua = l)
                }
            }
        }
    }

    /**
     * PT/EN, na pesquisa. Com uma pesquisa feita, refaz-se na outra língua;
     * a meio de escrever, refazem-se as sugestões.
     */
    fun escolherLingua(lingua: String) {
        if (lingua == _estado.value.lingua || lingua !in linguas) return
        val antes = _estado.value
        historico.clear()
        _estado.value = antes.copy(lingua = lingua, existeNaOutra = null, entrada = null, candidatos = emptyList())
        when {
            antes.entrada != null -> procurar(antes.entrada.lemma)
            antes.procurou -> procurar(antes.texto)
            antes.texto.isNotEmpty() -> escrever(antes.texto)
        }
    }

    private fun agora() = System.currentTimeMillis()

    /** "Estou a ler", "Retomar", "Voltar a ler". */
    fun comecarLivro(titulo: String) {
        viewModelScope.launch { utilizador.comecarLivro(titulo.trim(), agora()) }
    }

    /** "Em que página vais?" (e, se vier, as páginas do livro). */
    fun marcarPagina(titulo: String, pagina: Int, paginasDoLivro: Int?) {
        viewModelScope.launch { utilizador.marcarPagina(titulo.trim(), pagina, paginasDoLivro, agora()) }
    }

    fun pararLivro(titulo: String) {
        viewModelScope.launch { utilizador.pararLivro(titulo.trim()) }
    }

    fun terminarLivro(titulo: String) {
        viewModelScope.launch { utilizador.terminarLivro(titulo.trim(), agora()) }
    }

    /** Só "vem já escolhido ao registar" (nulo: nenhum). */
    fun escolherEmCurso(titulo: String?) {
        viewModelScope.launch { utilizador.escolherEmCurso(titulo?.trim()) }
    }

    suspend fun criarLivro(
        titulo: String, autor: String?, paginas: Int?, comecar: Boolean, lingua: String = Lingua.PT,
    ): Boolean = utilizador.criarLivro(titulo, autor, paginas, comecar, agora(), lingua)

    suspend fun editarLivro(
        id: Long, titulo: String, autor: String?, paginas: Int?, nota: String?, lingua: String? = null,
    ) = utilizador.editarLivro(id, titulo, autor, paginas, nota, lingua)

    fun juntarLivros(deId: Long, paraId: Long) {
        viewModelScope.launch { utilizador.juntarLivros(deId, paraId) }
    }

    suspend fun apagarLivro(id: Long): Boolean = utilizador.apagarLivro(id)

    /** Corrigir as datas de uma leitura (ou acrescentar uma, com id 0). */
    fun gravarLeitura(leitura: Leitura) {
        viewModelScope.launch {
            val dao = utilizador.livros()
            if (leitura.id == 0L) dao.inserirLeitura(leitura) else dao.atualizarLeitura(leitura)
        }
    }

    fun apagarLeitura(id: Long) {
        viewModelScope.launch { utilizador.livros().apagarLeitura(id) }
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
                if (d.procurar(texto).isNotEmpty()) emptyList() else d.parecidasComDefinicao(texto)
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

    /**
     * Chamado pela pesquisa e também pelo PROCESS_TEXT vindo do e-book.
     *
     * `depois` corre quando o resultado já está no estado. Serve a quem abre
     * uma palavra a partir de outro ecrã (a Coleção): só se muda para o
     * separador Procurar nessa altura, senão o primeiro instante mostrava o
     * ecrã inicial, ainda sem a palavra.
     */
    fun procurar(texto: String = _estado.value.texto, depois: () -> Unit = {}) {
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
                withContext(Dispatchers.IO) { dicionario?.parecidasComDefinicao(texto).orEmpty() }
            } else emptyList()
            // E se estiver no outro dicionário? Quem procura "wistful" com o
            // português escolhido não deve ficar só com "não encontrada".
            val existeNaOutra = if (candidatos.isEmpty()) {
                val outra = Lingua.outra(_estado.value.lingua)
                withContext(Dispatchers.IO) {
                    outra.takeIf { it in linguas && obterDicionario(it)?.procurar(texto).orEmpty().isNotEmpty() }
                }
            } else null

            empilhar(entrada)
            _estado.value = _estado.value.copy(
                texto = texto,
                sugestoes = emptyList(),
                candidatos = candidatos,
                entrada = entrada,
                procurou = true,
                parecidas = parecidas,
                existeNaOutra = existeNaOutra,
            )
            depois()
        }
    }

    /**
     * Abre uma palavra da coleção, pela chave (`chaveDe`): procura-a no
     * dicionário da língua dela, seja qual for a escolhida.
     */
    fun procurarGuardada(chave: String, depois: () -> Unit = {}) {
        val l = linguaDaChave(chave)
        if (l in linguas && l != _estado.value.lingua) {
            historico.clear()
            _estado.value = _estado.value.copy(lingua = l, entrada = null, candidatos = emptyList())
        }
        procurar(lemaDaChave(chave), depois)
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
        // A língua escolhida fica: limpar a pesquisa não é mudar de livro.
        _estado.value = EstadoPesquisa(lingua = _estado.value.lingua)
    }

    fun fecharEntrada() {
        _estado.value = _estado.value.copy(entrada = null)
    }

    fun registar(palavra: PalavraGuardada) {
        viewModelScope.launch {
            utilizador.palavras().guardar(palavra)
            // Um livro novo ganha a ficha dele.
            utilizador.acertarLivros()
        }
    }

    // --- sugestões ao dicionário (1.30) ---------------------------------------

    /** As correções que a pessoa fez às aceções desta palavra. */
    fun correcoesDe(lemma: String, lingua: String): Flow<List<CorrecaoGuardada>> =
        utilizador.correcoes().daPalavra(lemma, lingua)

    /** Guarda (texto) ou apaga (nulo) a correção a uma aceção; devolve a guardada. */
    suspend fun guardarCorrecao(
        lemma: String,
        lingua: String,
        acecao: pt.jorges15.palavrame.data.Acecao,
        classe: String?,
        texto: String?,
    ): CorrecaoGuardada? = withContext(Dispatchers.IO) {
        val dao = utilizador.correcoes()
        if (texto == null) {
            dao.apagar(lemma, lingua, acecao.ord)
            return@withContext null
        }
        val c = CorrecaoGuardada(
            id = dao.da(lemma, lingua, acecao.ord)?.id ?: 0,
            lemma = lemma,
            lingua = lingua,
            acecaoOrd = acecao.ord,
            acecaoTexto = acecao.definicao,
            acecaoFonte = acecao.fonte + if (acecao.modernizada) " (fraseado adaptado)" else "",
            classe = classe,
            texto = texto,
        )
        c.copy(id = dao.guardar(c))
    }

    /** A palavra como sugestão por enviar, se o é (para o "Submeter ao dicionário"). */
    suspend fun faltaDe(palavra: PalavraGuardada): PalavraEmFalta? =
        faltaDe(obterDicionario(palavra.lingua), palavra)

    suspend fun marcarEnviadas(faltas: List<PalavraEmFalta>, correcoes: List<CorrecaoGuardada>) =
        marcarSugestoesEnviadas(utilizador, faltas, correcoes)

    /** Guarda já, e não em `viewModelScope.launch`: quem chama quer a palavra guardada a seguir. */
    suspend fun guardarAgora(palavra: PalavraGuardada) = withContext(Dispatchers.IO) {
        val existente = utilizador.palavras().porLema(palavra.lemma, palavra.lingua)
        if (existente != null) utilizador.palavras().atualizar(palavra.copy(id = existente.id))
        else utilizador.palavras().guardar(palavra)
        utilizador.acertarLivros()
    }

    fun esquecer(lemma: String, lingua: String = _estado.value.lingua) {
        viewModelScope.launch { utilizador.palavras().esquecer(lemma, lingua) }
    }

    fun atualizar(palavra: PalavraGuardada) {
        viewModelScope.launch {
            utilizador.palavras().atualizar(palavra)
            utilizador.acertarLivros()
        }
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

    /**
     * A entrada do dicionário de uma palavra da coleção, para a partilhar como
     * imagem: pela grafia exata e, se falhar, pelas formas (como a pesquisa).
     * Nula se a palavra não está no dicionário.
     */
    suspend fun entradaPara(lemma: String, lingua: String = Lingua.PT): Entrada? = withContext(Dispatchers.IO) {
        val d = obterDicionario(lingua) ?: return@withContext null
        d.entradaPorLema(lemma) ?: d.procurar(lemma).firstOrNull()?.let { d.entrada(it.lemmaId) }
    }

    /** Palavras já na coleção, pela chave (`chaveDe`): o botão precisa de saber se já lá está. */
    val lemasGuardados: Flow<Set<String>> =
        palavrasGuardadas.map { lista -> lista.map { it.chave }.toSet() }

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
        // Como no jogo: só contam as línguas com o mínimo de palavras (uma
        // primeira palavra inglesa não faz aparecer o "Rever").
        val porLingua = lista.groupingBy { it.lingua }.eachCount()
        val agora = System.currentTimeMillis()
        lista.count {
            (porLingua[it.lingua] ?: 0) >= MINIMO_PARA_JOGAR &&
                (it.proximaRevisao == null || it.proximaRevisao <= agora)
        }
    }
}

/**
 * Como uma palavra registada falha o dicionário.
 *
 * `SENTIDO_EM_FALTA`: a palavra tem definição, mas o utilizador escreveu a
 * sua, o que quer dizer que a encontrou num sentido que o dicionário não tem
 * (o caso de "rebarbativo", que só tinha o sentido de 1913).
 */
enum class TipoFalta { NAO_ENCONTRADA, SEM_SIGNIFICADO, SENTIDO_EM_FALTA }

/** O nome da fonte da curadoria na base (ver `sources/curadoria.py`). */
private const val FONTE_CURADORIA = "Palavra-me"

/** Uma palavra da coleção que o dicionário não cobre — candidata a submissão. */
data class PalavraEmFalta(
    val lemma: String,
    val significadoProprio: String?,
    val tipo: TipoFalta,
    /** De que dicionário falta (1.30: também o inglês). */
    val lingua: String = Lingua.PT,
)

/**
 * Se a palavra falta ao dicionário da língua dela, e como: não existe lá de
 * todo, existe sem significado, ou tem definição mas a pessoa juntou a sua
 * (um sentido em falta). Nula se não há nada a propor.
 *
 * Primeiro a grafia exata; se falhar, pelas formas (como a pesquisa). Assim
 * uma palavra registada no plural ou com outra grafia deixa de contar como
 * "em falta" quando o dicionário a passa a ter.
 */
private fun classificarFalta(dic: Dicionario, p: PalavraGuardada): PalavraEmFalta? {
    val entrada = dic.entradaPorLema(p.lemma)
        ?: dic.procurar(p.lemma).firstOrNull()?.let { dic.entrada(it.lemmaId) }
    val tipo = when {
        entrada == null -> TipoFalta.NAO_ENCONTRADA
        entrada.acecoes.isEmpty() -> TipoFalta.SEM_SIGNIFICADO
        // Com definição e com o significado do próprio: falta um sentido. Se a
        // curadoria já lhe acrescentou um, o pedido foi atendido (o significado
        // do utilizador fica na coleção, mas deixa de se propor).
        !p.significado.isNullOrBlank() &&
            entrada.acecoes.none { it.fonte == FONTE_CURADORIA } -> TipoFalta.SENTIDO_EM_FALTA
        else -> return null
    }
    return PalavraEmFalta(p.lemma, p.significado, tipo, p.lingua)
}

/**
 * As palavras registadas que o dicionário não cobre (ver `classificarFalta`).
 * É o que se submete para a curadoria. Desde a 1.30, de todas as línguas
 * instaladas: as inglesas também se propõem, marcadas como tal no email.
 *
 * Corre em IO: percorre a coleção e consulta o dicionário por cada palavra.
 */
suspend fun palavrasEmFalta(
    /** O dicionário de cada língua; nulo se não está instalado. */
    dicionarioDe: (String) -> Dicionario?,
    utilizador: UtilizadorDb,
    /**
     * Só as que ainda não foram enviadas (1.30): uma palavra enviada não volta
     * a propor-se, a não ser que o significado dela tenha mudado desde então.
     */
    soPorEnviar: Boolean = true,
): List<PalavraEmFalta> = withContext(Dispatchers.IO) {
    utilizador.palavras().todasAgora()
        .filter { !soPorEnviar || it.enviado == null || it.enviado != it.significado.orEmpty() }
        .mapNotNull { p -> dicionarioDe(p.lingua)?.let { classificarFalta(it, p) } }
}

/**
 * Marca como enviadas as sugestões que acabaram de ir por email (1.30), para
 * não voltarem à lista. Não se sabe se o email chegou a sair (é a app de email
 * que o envia), mas abrir o email já é a decisão de enviar.
 */
suspend fun marcarSugestoesEnviadas(
    utilizador: UtilizadorDb,
    faltas: List<PalavraEmFalta>,
    correcoes: List<pt.jorges15.palavrame.data.CorrecaoGuardada>,
) = withContext(Dispatchers.IO) {
    faltas.forEach {
        utilizador.palavras().marcarEnviada(it.lemma, it.lingua, it.significadoProprio.orEmpty())
    }
    if (correcoes.isNotEmpty()) utilizador.correcoes().marcarEnviadas(correcoes.map { it.id })
}

/**
 * A palavra como sugestão por enviar, se o for: o mesmo critério da lista de
 * Definições, Contribuir, para uma palavra só (o "Submeter ao dicionário"
 * depois de guardar). Nula se não há nada a propor.
 */
suspend fun faltaDe(
    dicionario: Dicionario?,
    palavra: PalavraGuardada,
): PalavraEmFalta? = withContext(Dispatchers.IO) {
    dicionario?.let { classificarFalta(it, palavra) }
}
