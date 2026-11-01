package pt.jorges15.palavrame.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A coleção: a razão de a app se chamar Palavra-me. Não é uma lista de
 * favoritos, é o registo do que se foi aprendendo, e por isso agrupa-se por
 * livro quando o livro é conhecido.
 */
@Composable
fun EcraColecao(
    vm: PesquisaViewModel,
    desenvolvedor: Boolean = false,
    aoVoltar: () -> Unit,
    aoAbrirPalavra: (String) -> Unit = { vm.procurar(it); aoVoltar() },
    /** Livro em que abrir (tocado na estante do ecrã inicial), ou nulo. */
    livroInicial: String? = null,
    /** Avisa que o livro inicial já foi aplicado, para não se repetir. */
    aoUsarLivroInicial: () -> Unit = {},
) {
    val palavras by vm.palavrasGuardadas.collectAsState(initial = emptyList())
    var agruparPorLivro by rememberSaveable { mutableStateOf(true) }
    var porData by rememberSaveable { mutableStateOf(true) }
    var filtro by rememberSaveable { mutableStateOf("") }
    // Os livros recolhidos (só o cabeçalho, sem as palavras). Tocar no
    // cabeçalho de um livro recolhe-o ou volta a abri-lo.
    var recolhidos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val estadoLista = rememberLazyListState()

    // Vindo da estante: abre esse livro e recolhe os outros, com o livro no
    // topo da lista. Espera pelas palavras (o primeiro valor é a lista vazia),
    // senão não haveria livros para recolher nem posição para onde ir.
    LaunchedEffect(livroInicial, palavras.isNotEmpty()) {
        val alvo = livroInicial?.trim() ?: return@LaunchedEffect
        if (palavras.isEmpty()) return@LaunchedEffect
        agruparPorLivro = true
        filtro = ""
        val grupos = agrupar(if (porData) palavras else palavras.sortedBy { Texto.normalizar(it.lemma) })
        recolhidos = grupos.map { it.first }.filter { it != alvo }
        // Com os outros recolhidos, cada livro antes do alvo ocupa uma linha.
        val indice = grupos.indexOfFirst { it.first == alvo }
        if (indice >= 0) estadoLista.scrollToItem(indice)
        aoUsarLivroInicial()
    }
    var aEditar by remember { mutableStateOf<PalavraGuardada?>(null) }
    val livros by vm.livrosUsados.collectAsState(initial = emptyList())
    val focus = LocalFocusManager.current

    // Esquecer uma palavra apaga o livro e a frase que lhe estavam anotados,
    // e isso não se reconstrói. Em vez de um diálogo de confirmação (que
    // trava sempre, mesmo quando o toque foi de propósito) apaga-se já e
    // dá-se uns segundos para anular. É o padrão do Android para isto.
    val estadoSnackbar = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    fun esquecerComAnular(p: PalavraGuardada) {
        vm.apagar(p)
        ambito.launch {
            estadoSnackbar.currentSnackbarData?.dismiss()
            val r = estadoSnackbar.showSnackbar(
                message = "Esqueceste \"${p.lemma}\"",
                actionLabel = "Anular",
                duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) vm.repor(p)
        }
    }

    aEditar?.let { palavra ->
        FolhaRegistar(
            lemma = palavra.lemma,
            livrosAnteriores = livros,
            existente = palavra,
            aoCancelar = { aEditar = null },
            aoGuardar = { atualizada -> vm.atualizar(atualizada); aEditar = null },
        )
    }

    val nLivros = remember(palavras) {
        palavras.mapNotNull { it.livro?.trim()?.takeIf(String::isNotEmpty) }.distinct().size
    }

    Scaffold(
        containerColor = Cores.bg,
        snackbarHost = { AnfitriaoSnackbar(estadoSnackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Text("As minhas palavras", style = Estilo.tituloEcra, color = Cores.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(if (palavras.size == 1) "1 palavra" else "${palavras.size} palavras")
                        if (nLivros > 0) {
                            append(" · ")
                            append(if (nLivros == 1) "1 livro" else "$nLivros livros")
                        }
                    },
                    style = Estilo.texto.copy(fontSize = 14.sp),
                    color = Cores.ink2,
                )
            }

            if (palavras.isEmpty()) {
                Text(
                    "Ainda não registaste nenhuma palavra.\n\n" +
                        "Procura uma que tenhas encontrado a ler e toca em Registar.",
                    style = Estilo.corpo,
                    color = Cores.ink2,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                Spacer(Modifier.height(16.dp))
                CampoPesquisa(
                    texto = filtro,
                    aoMudar = { filtro = it },
                    aoProcurar = { focus.clearFocus() },
                    aoLimpar = { filtro = "" },
                    placeholder = "Filtrar por palavra, livro ou frase",
                    altura = 46.dp,
                    raio = 14.dp,
                    estilo = Estilo.campo,
                    tamanhoIcone = 20.dp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Segmentado(
                        listOf("Por livro", "Todas"),
                        selecionado = if (agruparPorLivro) 0 else 1,
                        aoEscolher = { agruparPorLivro = it == 0 },
                        modifier = Modifier.width(210.dp),
                        altura = 40.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    // Por data é o que serve para rever o que se aprendeu há
                    // pouco; alfabética é o que serve para procurar.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { porData = !porData }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (porData) Icons.Rounded.Schedule else Icons.Rounded.SortByAlpha,
                            contentDescription = null,
                            tint = Cores.ink2,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (porData) "Recentes" else "A-Z",
                            style = Estilo.controlo.copy(fontSize = 14.sp),
                            color = Cores.ink2,
                        )
                    }
                }

                val ordenadas = if (porData) palavras
                else palavras.sortedBy { Texto.normalizar(it.lemma) }

                // Filtro sem acentos, por qualquer campo (a palavra, o livro, o
                // autor ou a frase), para se poder puxar "tudo do livro X".
                val consulta = Texto.normalizar(filtro)
                val filtradas = if (consulta.isBlank()) ordenadas
                else ordenadas.filter { p ->
                    listOfNotNull(p.lemma, p.livro, p.autor, p.frase)
                        .any { Texto.normalizar(it).contains(consulta) }
                }

                if (filtradas.isEmpty()) {
                    Text(
                        "Nenhuma palavra corresponde a \"$filtro\".",
                        style = Estilo.texto,
                        color = Cores.ink2,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    val grupos: List<Pair<String, List<PalavraGuardada>>> =
                        if (agruparPorLivro) agrupar(filtradas) else listOf("" to filtradas)
                    // A filtrar, mostra-se tudo o que corresponde: um resultado
                    // escondido num livro recolhido parecia não existir.
                    val aFiltrar = consulta.isNotBlank()

                    LazyColumn(
                        state = estadoLista,
                        contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                    ) {
                        grupos.forEach { (livro, lista) ->
                            val recolhido = livro.isNotEmpty() && !aFiltrar && livro in recolhidos
                            if (livro.isNotEmpty()) {
                                item(key = "grupo-$livro") {
                                    CabecalhoGrupo(
                                        titulo = livro,
                                        autor = if (livro == SEM_LIVRO) null
                                        else lista.firstNotNullOfOrNull { it.autor?.takeIf(String::isNotBlank) },
                                        quantas = lista.size,
                                        semLivro = livro == SEM_LIVRO,
                                        recolhido = recolhido,
                                        aoTocar = if (aFiltrar) null else {
                                            {
                                                recolhidos = if (livro in recolhidos) recolhidos - livro
                                                else recolhidos + livro
                                            }
                                        },
                                    )
                                }
                            }
                            if (!recolhido) items(lista, key = { it.id }) { p ->
                                LinhaPalavra(
                                    p,
                                    desenvolvedor = desenvolvedor,
                                    mostrarLivro = !agruparPorLivro,
                                    aoProcurar = { aoAbrirPalavra(p.lemma) },
                                    aoEditar = { aEditar = p },
                                    aoApagar = { esquecerComAnular(p) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** O grupo das palavras registadas sem livro. */
private const val SEM_LIVRO = "Sem livro"

/**
 * As palavras por livro, na ordem em que chegam (a mais recente primeiro, ou
 * alfabética). "Sem livro" vai sempre no fim.
 */
private fun agrupar(palavras: List<PalavraGuardada>): List<Pair<String, List<PalavraGuardada>>> {
    val todos = palavras
        .groupBy { it.livro?.trim()?.takeIf(String::isNotEmpty) ?: SEM_LIVRO }
        .toList()
    return todos.filter { it.first != SEM_LIVRO } + todos.filter { it.first == SEM_LIVRO }
}

@Composable
private fun CabecalhoGrupo(
    titulo: String,
    autor: String?,
    quantas: Int,
    semLivro: Boolean,
    recolhido: Boolean = false,
    /** Nulo quando não se pode recolher (a filtrar). */
    aoTocar: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lombada(if (semLivro) Cores.ink3 else (corDoLivro(titulo) ?: Cores.ink3), 8.dp, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo, style = Estilo.grupoTitulo, color = Cores.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            autor?.let {
                Text(it, style = Estilo.meta.copy(fontSize = 12.5.sp), color = Cores.ink2)
            }
        }
        Spacer(Modifier.width(12.dp))
        // A contagem por livro é o número que dá gosto ver crescer.
        Text(
            quantas.toString(),
            style = Estilo.numeroEstat.copy(fontSize = 22.sp),
            color = Cores.accent,
        )
        if (aoTocar != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (recolhido) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = if (recolhido) "Mostrar as palavras" else "Ocultar as palavras",
                tint = Cores.ink3,
            )
        }
    }
}

@Composable
private fun LinhaPalavra(
    palavra: PalavraGuardada,
    desenvolvedor: Boolean,
    mostrarLivro: Boolean,
    aoProcurar: () -> Unit,
    aoEditar: () -> Unit,
    aoApagar: () -> Unit,
) {
    val esteAno = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val curto = remember { SimpleDateFormat("d MMM", Locale("pt", "PT")) }
    val longo = remember { SimpleDateFormat("d MMM yyyy", Locale("pt", "PT")) }
    val data = remember(palavra.guardadaEm) {
        val c = Calendar.getInstance().apply { timeInMillis = palavra.guardadaEm }
        val f = if (c.get(Calendar.YEAR) == esteAno) curto else longo
        f.format(Date(palavra.guardadaEm)).replace(".", "")
    }

    Column {
        DivisoriaGrupo()
        // Sem diálogo de confirmação: o apagar é reversível pelo "Anular" da
        // snackbar (ver `esquecerComAnular`).
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(onClick = aoProcurar)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        palavra.lemma, style = Estilo.linhaLema, color = Cores.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Os pontinhos do nível de domínio são do Modo Desenvolvedor.
                    if (desenvolvedor) {
                        Spacer(Modifier.width(10.dp))
                        PontosDominio(palavra.mastery)
                    }
                }
                palavra.frase?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "\"$it\"",
                        style = Estilo.dominio.copy(fontStyle = FontStyle.Italic),
                        color = Cores.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(data)
                        if (mostrarLivro) palavra.livro?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                        palavra.page?.let { append(" · p. ").append(it) }
                    },
                    style = Estilo.meta,
                    color = Cores.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = aoEditar, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.EditNote, contentDescription = "Editar", tint = Cores.ink3)
            }
            IconButton(onClick = aoApagar, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Esquecer", tint = Cores.ink3)
            }
        }
    }
}
