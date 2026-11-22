package pt.jorges15.palavrame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.data.EstadoLeitura
import pt.jorges15.palavrame.data.Leitura
import pt.jorges15.palavrame.data.LivroCompleto
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.ResultadoFicha
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Os livros (1.22): o separador "Livros", a página de cada livro e os seus
 * diálogos. Estilo diário de leitura, calmo: as leituras com as datas, uma
 * nota, o progresso pelas páginas das palavras. Sem estrelas, metas nem
 * desafios.
 *
 * Os dados estão em `data/Livros.kt`; aqui, o que se mostra. O texto das
 * datas e do resumo vive em funções puras, testadas em `LivrosTest`.
 */

// --- texto (puro, testado) -------------------------------------------------------

private val PT = Locale("pt", "PT")
private val MES = DateTimeFormatter.ofPattern("MMMM", PT)
private val MES_ANO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT)
private val DIA_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", PT)
private val DIA_MES_ANO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT)

internal fun diaDe(millis: Long, zona: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zona).toLocalDate()

/**
 * Um dia guardado como meio-dia local: longe da meia-noite, uma mudança de
 * hora ou de fuso não o passa para o dia ao lado.
 */
internal fun millisDoDia(dia: LocalDate, zona: ZoneId = ZoneId.systemDefault()): Long =
    dia.atTime(12, 0).atZone(zona).toInstant().toEpochMilli()

/** "março a maio de 2026", "dezembro de 2025 a fevereiro de 2026", "maio de 2026". */
private fun meses(inicio: LocalDate?, fim: LocalDate): String = when {
    inicio == null || inicio.isAfter(fim) -> fim.format(MES_ANO)
    inicio.year == fim.year && inicio.month == fim.month -> fim.format(MES_ANO)
    inicio.year == fim.year -> "${inicio.format(MES)} a ${fim.format(MES_ANO)}"
    else -> "${inicio.format(MES_ANO)} a ${fim.format(MES_ANO)}"
}

/**
 * A linha por baixo do livro (na lista, na Coleção), ou nula se não há nada a
 * dizer: "A ler desde 3 de março", "Parado", "Lido de março a maio de 2026",
 * "Lido 2 vezes, a última em maio de 2026".
 */
internal fun periodoDeLeitura(
    livro: LivroCompleto?,
    hoje: LocalDate = LocalDate.now(),
    zona: ZoneId = ZoneId.systemDefault(),
): String? {
    livro ?: return null
    val atual = livro.atual ?: return null
    val inicio = atual.iniciadaEm?.let { diaDe(it, zona) }
    return when (livro.estado) {
        EstadoLeitura.SEM_LEITURA -> null
        EstadoLeitura.A_LER -> if (inicio == null) "A ler" else
            "A ler desde " + inicio.format(if (inicio.year == hoje.year) DIA_MES else DIA_MES_ANO)
        EstadoLeitura.PARADO -> "Parado"
        EstadoLeitura.LIDO -> {
            val fim = diaDe(atual.terminadaEm!!, zona)
            if (livro.lidas.size > 1) "Lido ${livro.lidas.size} vezes, a última em ${fim.format(MES_ANO)}"
            else periodoDaLeituraAcabada(atual, zona)
        }
    }
}

/** "Lido de março a maio de 2026", "Lido em maio de 2026"; nulo se a leitura não acabou. */
internal fun periodoDaLeituraAcabada(l: Leitura?, zona: ZoneId = ZoneId.systemDefault()): String? {
    val fim = l?.terminadaEm?.let { diaDe(it, zona) } ?: return null
    val inicio = l.iniciadaEm?.let { diaDe(it, zona) }
    val noMesmoMes = inicio == null || inicio.isAfter(fim) ||
        inicio.month == fim.month && inicio.year == fim.year
    return "Lido " + (if (noMesmoMes) "em " else "de ") + meses(inicio, fim)
}

/**
 * Uma leitura, na página do livro: "De 3 de março a 20 de maio de 2026",
 * "Desde 3 de março de 2026", "Acabada a 20 de maio de 2026", com "parada" se
 * foi posta de lado.
 */
internal fun textoDaLeitura(l: Leitura, zona: ZoneId = ZoneId.systemDefault()): String {
    val inicio = l.iniciadaEm?.let { diaDe(it, zona) }
    val fim = l.terminadaEm?.let { diaDe(it, zona) }
    val base = when {
        inicio != null && fim != null -> {
            val de = if (inicio.year == fim.year) inicio.format(DIA_MES) else inicio.format(DIA_MES_ANO)
            "De $de a ${fim.format(DIA_MES_ANO)}"
        }
        fim != null -> "Acabada a ${fim.format(DIA_MES_ANO)}"
        inicio != null -> "Desde ${inicio.format(DIA_MES_ANO)}"
        else -> "Sem datas"
    }
    return if (l.parada && fim == null) "$base, parada" else base
}

/** Quantos dias levou a ler, contando o primeiro e o último. */
internal fun diasDeLeitura(inicio: Long?, fim: Long?, zona: ZoneId = ZoneId.systemDefault()): Int? {
    if (inicio == null || fim == null) return null
    val a = diaDe(inicio, zona)
    val b = diaDe(fim, zona)
    if (b.isBefore(a)) return null
    return ChronoUnit.DAYS.between(a, b).toInt() + 1
}

/** O resumo de um livro. */
internal data class ResumoLivro(
    val palavras: Int,
    /** Caixa 3 ou mais: já respondidas certas várias vezes seguidas. */
    val sabidas: Int,
    /** As que ainda custam: as de caixa mais baixa, até quatro. */
    val aCustar: List<String>,
    /** Lido: em quantos dias. A ler: há quantos dias (até hoje). */
    val dias: Int?,
    val lido: Boolean = false,
)

/** O resumo, da leitura mais recente. Num livro por acabar, os dias contam até hoje. */
internal fun resumoDoLivro(
    palavras: List<PalavraGuardada>,
    livro: LivroCompleto?,
    agora: Long = System.currentTimeMillis(),
): ResumoLivro {
    val atual = livro?.atual
    return ResumoLivro(
        palavras = palavras.size,
        sabidas = palavras.count { it.mastery >= 3 },
        aCustar = palavras.filter { it.mastery < 3 }
            .sortedWith(compareBy({ it.mastery }, { it.guardadaEm }))
            .take(4)
            .map { it.lemma },
        dias = if (livro?.estado == EstadoLeitura.PARADO) null
        else diasDeLeitura(atual?.iniciadaEm, atual?.terminadaEm ?: atual?.let { agora }),
        lido = livro?.estado == EstadoLeitura.LIDO,
    )
}

/** As frases do resumo, numa linha: "A ler há 12 dias. Guardaste 8 palavras. Já sabes 3." */
internal fun frasesDoResumo(r: ResumoLivro): String = buildList {
    r.dias?.let {
        add(
            when {
                r.lido && it == 1 -> "Lido num dia."
                r.lido -> "Lido em $it dias."
                it == 1 -> "Começado hoje."
                else -> "A ler há $it dias."
            }
        )
    }
    add(
        when (r.palavras) {
            0 -> "Ainda não guardaste palavras dele."
            1 -> "Guardaste 1 palavra."
            else -> "Guardaste ${r.palavras} palavras."
        }
    )
    if (r.palavras > 0) {
        add(
            when (r.sabidas) {
                0 -> "Ainda nenhuma está sabida."
                r.palavras -> if (r.palavras == 1) "Já a sabes." else "Já as sabes todas."
                1 -> "Já sabes 1."
                else -> "Já sabes ${r.sabidas}."
            }
        )
    }
}.joinToString(" ")

/**
 * A frase da imagem do livro lido e a parte dela em destaque:
 * "Guardei 32 palavras neste livro, em 47 dias de leitura." Na primeira
 * pessoa, porque a imagem é de quem a partilha.
 */
internal fun fraseDoLivroLido(palavras: Int, dias: Int?): Pair<String, String?> {
    val emDias = when (dias) {
        null -> ""
        1 -> " num dia"
        else -> " em $dias dias"
    }
    if (palavras <= 0) return "Li este livro$emDias." to null
    val destaque = if (palavras == 1) "1 palavra" else "$palavras palavras"
    val leitura = when (dias) {
        null -> ""
        1 -> ", num dia de leitura"
        else -> ", em $dias dias de leitura"
    }
    return "Guardei $destaque neste livro$leitura." to destaque
}

/** As palavras que vão na imagem: as primeiras guardadas, pela ordem da leitura. */
internal fun palavrasParaImagem(palavras: List<PalavraGuardada>, quantas: Int = 4): List<PalavraGuardada> =
    palavras.sortedBy { it.guardadaEm }.take(quantas)

/**
 * O que vai na imagem do livro lido, da última leitura acabada (ou da mais
 * recente). `significados`: uma linha para cada palavra, vinda do dicionário
 * ou do significado escrito pelo próprio.
 */
internal fun dadosLivroLido(
    livro: LivroCompleto,
    palavras: List<PalavraGuardada>,
    significados: Map<String, String?>,
    zona: ZoneId = ZoneId.systemDefault(),
): DadosLivroLido {
    val leitura = livro.lidas.lastOrNull() ?: livro.atual
    val (frase, destaque) = fraseDoLivroLido(
        palavras.size, diasDeLeitura(leitura?.iniciadaEm, leitura?.terminadaEm, zona),
    )
    return DadosLivroLido(
        titulo = livro.titulo,
        autor = livro.ficha.autor?.trim()?.takeIf { it.isNotEmpty() },
        periodo = periodoDaLeituraAcabada(leitura, zona),
        frase = frase,
        destaque = destaque,
        palavras = palavrasParaImagem(palavras).map { it.lemma to significados[it.lemma] },
    )
}

/**
 * O progresso pelas palavras (L6): a página da palavra guardada mais recente
 * que tem página, sobre o total do livro. Nulo sem total ou sem páginas.
 */
internal fun progressoDoLivro(palavras: List<PalavraGuardada>, paginas: Int?): Pair<Int, Int>? {
    val total = paginas?.takeIf { it > 0 } ?: return null
    val pagina = palavras.sortedByDescending { it.guardadaEm }.firstNotNullOfOrNull { it.page }
        ?: return null
    return minOf(pagina, total) to total
}

/** As palavras de um livro, a mais recente primeiro. */
internal fun palavrasDoLivro(todas: List<PalavraGuardada>, titulo: String): List<PalavraGuardada> =
    todas.filter { it.livro?.trim() == titulo }

// --- o separador "Livros" ------------------------------------------------------------

/**
 * Os livros: "A ler", "Parados", "Lidos" por ano e "Outros livros" (os que
 * têm palavras mas nenhuma leitura marcada). Tocar abre a página do livro.
 */
@Composable
fun EcraLivros(
    vm: PesquisaViewModel,
    /** Livro a abrir logo (tocado na estante ou na Coleção), ou nulo. */
    livroInicial: String? = null,
    aoUsarLivroInicial: () -> Unit = {},
    aoAbrirPalavra: (String) -> Unit,
    aoReverLivro: (String) -> Unit,
) {
    val livros by vm.livros.collectAsState()
    val carregadas by vm.colecao.collectAsState()
    val palavras = carregadas.orEmpty()
    var aberto by rememberSaveable { mutableStateOf<String?>(null) }
    var aCriar by remember { mutableStateOf(false) }

    LaunchedEffect(livroInicial) {
        if (livroInicial != null) {
            aberto = livroInicial.trim()
            aoUsarLivroInicial()
        }
    }
    val livroAberto = aberto?.let { livros[it] }
    // Só com a página à vista: um livro ainda sem ficha (acabado de registar)
    // não abre, e o "retroceder" não fica preso a uma página invisível.
    BackHandler(enabled = livroAberto != null) { aberto = null }
    LaunchedEffect(aberto, livros) {
        if (aberto != null && livros.isNotEmpty() && livros[aberto] == null) aberto = null
    }
    if (livroAberto != null) {
        PaginaLivro(
            livro = livroAberto,
            palavras = palavrasDoLivro(palavras, livroAberto.titulo),
            todos = livros,
            vm = vm,
            aoVoltar = { aberto = null },
            aoMudarDeLivro = { aberto = it },
            aoAbrirPalavra = aoAbrirPalavra,
            aoRever = { aoReverLivro(livroAberto.titulo) },
        )
        return
    }

    if (aCriar) {
        DialogoFicha(
            livro = null,
            aoCancelar = { aCriar = false },
            aoCriar = { titulo, autor, paginas, comecar ->
                val ok = vm.criarLivro(titulo, autor, paginas, comecar)
                if (ok) { aCriar = false; aberto = titulo.trim() }
                ok
            },
        )
    }

    val porEstado = livros.values.groupBy { it.estado }
    val contagem = remember(palavras) {
        palavras.mapNotNull { it.livro?.trim()?.takeIf(String::isNotEmpty) }.groupingBy { it }.eachCount()
    }

    Scaffold(containerColor = Cores.bg) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        ) {
            Text("Os meus livros", style = Estilo.tituloEcra, color = Cores.ink)
            Spacer(Modifier.height(4.dp))
            val lidos = porEstado[EstadoLeitura.LIDO].orEmpty().size
            Text(
                buildString {
                    append(if (livros.size == 1) "1 livro" else "${livros.size} livros")
                    if (lidos > 0) append(" · ").append(if (lidos == 1) "1 lido" else "$lidos lidos")
                },
                style = Estilo.texto.copy(fontSize = 14.sp),
                color = Cores.ink2,
            )
            Spacer(Modifier.height(16.dp))
            BotaoContornado("Começar um livro", { aCriar = true }, icone = Icons.Rounded.Add)

            if (livros.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Ainda não tens livros. Regista uma palavra com o livro onde a "
                        + "encontraste, ou começa um livro aqui.",
                    style = Estilo.corpo,
                    color = Cores.ink2,
                )
                return@Column
            }

            SeccaoLivros(
                "A ler",
                porEstado[EstadoLeitura.A_LER].orEmpty()
                    .sortedByDescending { it.atual?.iniciadaEm ?: 0L },
                contagem,
            ) { aberto = it }
            SeccaoLivros(
                "Parados",
                porEstado[EstadoLeitura.PARADO].orEmpty().sortedBy { it.titulo.lowercase() },
                contagem,
            ) { aberto = it }
            porEstado[EstadoLeitura.LIDO].orEmpty()
                .groupBy { l -> l.ultimaVezLido?.let { diaDe(it).year } ?: 0 }
                .toSortedMap(compareByDescending { it })
                .forEach { (ano, lista) ->
                    SeccaoLivros(
                        if (ano == 0) "Lidos" else "Lidos em $ano",
                        lista.sortedByDescending { it.ultimaVezLido ?: 0L },
                        contagem,
                    ) { aberto = it }
                }
            SeccaoLivros(
                "Outros livros",
                porEstado[EstadoLeitura.SEM_LEITURA].orEmpty().sortedByDescending { it.ficha.criadoEm },
                contagem,
            ) { aberto = it }
        }
    }
}

@Composable
private fun SeccaoLivros(
    titulo: String,
    lista: List<LivroCompleto>,
    contagem: Map<String, Int>,
    aoAbrir: (String) -> Unit,
) {
    if (lista.isEmpty()) return
    Spacer(Modifier.height(22.dp))
    Overline(titulo, cor = Cores.accentInk)
    Spacer(Modifier.height(6.dp))
    lista.forEach { livro ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { aoAbrir(livro.titulo) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapaLivro(livro.titulo, livro.ficha.autor, largura = 44.dp, altura = 60.dp, compacta = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    livro.titulo, style = Estilo.grupoTitulo, color = Cores.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                livro.ficha.autor?.let {
                    Text(it, style = Estilo.meta.copy(fontSize = 12.5.sp), color = Cores.ink2)
                }
                periodoDeLeitura(livro)?.let {
                    Text(it, style = Estilo.meta.copy(fontSize = 12.5.sp), color = Cores.accentInk)
                }
            }
            val n = contagem[livro.titulo] ?: 0
            if (n > 0) {
                Spacer(Modifier.width(10.dp))
                Text(n.toString(), style = Estilo.numeroEstat.copy(fontSize = 20.sp), color = Cores.accent)
            }
        }
    }
}

// --- a página do livro -----------------------------------------------------------------

@Composable
private fun PaginaLivro(
    livro: LivroCompleto,
    palavras: List<PalavraGuardada>,
    todos: Map<String, LivroCompleto>,
    vm: PesquisaViewModel,
    aoVoltar: () -> Unit,
    aoMudarDeLivro: (String) -> Unit,
    aoAbrirPalavra: (String) -> Unit,
    aoRever: () -> Unit,
) {
    val ficha = livro.ficha
    val ambito = rememberCoroutineScope()
    var editar by remember { mutableStateOf(false) }
    var juntar by remember { mutableStateOf(false) }
    var leituraAEditar by remember { mutableStateOf<Leitura?>(null) }
    var resumoAoAcabar by remember { mutableStateOf<Pair<ResumoLivro, LivroCompleto>?>(null) }
    var aPartilhar by remember { mutableStateOf<DadosLivroLido?>(null) }
    // A imagem do livro lido: uma linha de significado para cada palavra que vai nela.
    val partilhar: (LivroCompleto) -> Unit = { l ->
        ambito.launch {
            val significados = palavrasParaImagem(palavras).associate { p ->
                p.lemma to dadosPartilha(vm.entradaPara(p.lemma), p)?.let { it.meuSignificado ?: it.acecoes.firstOrNull() }
            }
            aPartilhar = dadosLivroLido(l, palavras, significados)
        }
    }
    var menu by remember { mutableStateOf(false) }

    if (editar) {
        DialogoFicha(
            livro = livro,
            aoCancelar = { editar = false },
            aoEditar = { titulo, autor, paginas, nota ->
                val r = vm.editarLivro(ficha.id, titulo, autor, paginas, nota)
                if (r == ResultadoFicha.FEITO) { editar = false; aoMudarDeLivro(titulo.trim()) }
                r
            },
            aoJuntarCom = { outro ->
                todos[outro]?.let { vm.juntarLivros(ficha.id, it.ficha.id) }
                editar = false
                aoMudarDeLivro(outro)
            },
        )
    }
    if (juntar) {
        DialogoJuntar(
            livro = livro,
            palavras = palavras.size,
            outros = todos.values.filter { it.ficha.id != ficha.id }.sortedBy { it.titulo.lowercase() },
            aoCancelar = { juntar = false },
            aoJuntar = { destino ->
                vm.juntarLivros(ficha.id, destino.ficha.id)
                juntar = false
                aoMudarDeLivro(destino.titulo)
            },
        )
    }
    leituraAEditar?.let { l ->
        DialogoLeitura(
            leitura = l,
            aoCancelar = { leituraAEditar = null },
            aoGuardar = { vm.gravarLeitura(it); leituraAEditar = null },
            aoApagar = if (l.id != 0L) { { vm.apagarLeitura(l.id); leituraAEditar = null } } else null,
        )
    }
    resumoAoAcabar?.let { (resumo, lido) ->
        DialogoResumoLivro(
            livro.titulo, resumo,
            aoFechar = { resumoAoAcabar = null },
            aoPartilhar = { resumoAoAcabar = null; partilhar(lido) },
        )
    }
    aPartilhar?.let { DialogoPartilharLivro(it, aoFechar = { aPartilhar = null }) }

    Column(Modifier.fillMaxSize()) {
        BarraVoltar(
            aoVoltar,
            direita = {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.MoreVert, "Opções do livro", tint = Cores.ink)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Cores.card) {
                        ItemLivro("Editar a ficha", Icons.Rounded.EditNote) { menu = false; editar = true }
                        if (livro.estado == EstadoLeitura.A_LER) {
                            if (ficha.emCurso) {
                                ItemLivro("Deixar de vir escolhido", Icons.Rounded.RadioButtonUnchecked) {
                                    menu = false; vm.escolherEmCurso(null)
                                }
                            } else {
                                ItemLivro("Vir escolhido ao registar", Icons.Rounded.AutoStories) {
                                    menu = false; vm.escolherEmCurso(ficha.titulo)
                                }
                            }
                        }
                        if (livro.estado == EstadoLeitura.LIDO) {
                            ItemLivro("Partilhar o livro lido", Icons.Rounded.IosShare) {
                                menu = false; partilhar(livro)
                            }
                        }
                        if (todos.size > 1) {
                            ItemLivro("Juntar a outro livro", Icons.Rounded.Link) { menu = false; juntar = true }
                        }
                        if (palavras.isEmpty()) {
                            ItemLivro("Apagar o livro", Icons.Rounded.DeleteOutline, Cores.err) {
                                menu = false
                                ambito.launch { if (vm.apagarLivro(ficha.id)) aoVoltar() }
                            }
                        }
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                CapaLivro(ficha.titulo, ficha.autor, largura = 92.dp, altura = 126.dp, compacta = true)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(ficha.titulo, style = Estilo.grupoTitulo.copy(fontSize = 22.sp), color = Cores.ink)
                    ficha.autor?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = Estilo.texto, color = Cores.ink2)
                    }
                    periodoDeLeitura(livro)?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.accentInk)
                    }
                    if (ficha.emCurso) {
                        Text("Vem escolhido ao registar", style = Estilo.meta, color = Cores.ink3)
                    }
                    // L6: o progresso sai das páginas das palavras.
                    if (livro.estado != EstadoLeitura.LIDO) {
                        progressoDoLivro(palavras, ficha.paginas)?.let { (p, total) ->
                            Spacer(Modifier.height(10.dp))
                            Text("p. $p de $total", style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.ink2)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { p.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Cores.accent,
                                trackColor = Cores.sunk,
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }

            // O que fazer com o livro agora, conforme o estado.
            Spacer(Modifier.height(20.dp))
            val acabar = {
                val agora = System.currentTimeMillis()
                vm.terminarLivro(ficha.titulo)
                val fechada = (livro.atual?.takeIf { it.terminadaEm == null }
                    ?: Leitura(livroId = ficha.id, iniciadaEm = palavras.minOfOrNull { it.guardadaEm }))
                    .copy(terminadaEm = agora, parada = false)
                val lido = LivroCompleto(ficha, listOf(fechada))
                resumoAoAcabar = resumoDoLivro(palavras, lido, agora) to lido
            }
            when (livro.estado) {
                EstadoLeitura.SEM_LEITURA -> {
                    BotaoPrincipal("Estou a ler este livro", { vm.comecarLivro(ficha.titulo) }, Modifier.fillMaxWidth())
                    TextoAcao("Já o li") { acabar() }
                }
                EstadoLeitura.A_LER -> {
                    BotaoPrincipal("Acabei este livro", acabar, Modifier.fillMaxWidth())
                    TextoAcao("Parar de ler") { vm.pararLivro(ficha.titulo) }
                }
                EstadoLeitura.PARADO -> {
                    BotaoPrincipal("Retomar", { vm.comecarLivro(ficha.titulo) }, Modifier.fillMaxWidth())
                    TextoAcao("Acabei este livro") { acabar() }
                }
                EstadoLeitura.LIDO ->
                    BotaoContornado("Voltar a ler", { vm.comecarLivro(ficha.titulo) }, Modifier.fillMaxWidth())
            }
            if (palavras.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                BotaoContornado("Rever este livro", aoRever, Modifier.fillMaxWidth(), icone = Icons.Rounded.Quiz)
            }

            // O resumo.
            Spacer(Modifier.height(26.dp))
            Overline("Resumo")
            Spacer(Modifier.height(8.dp))
            val resumo = resumoDoLivro(palavras, livro)
            Text(frasesDoResumo(resumo), style = Estilo.texto.copy(lineHeight = 1.45.em), color = Cores.ink2)
            if (resumo.aCustar.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Ainda custam", style = Estilo.rotulo.copy(fontSize = 13.sp), color = Cores.ink2)
                Spacer(Modifier.height(2.dp))
                Text(resumo.aCustar.joinToString(" · "), style = Estilo.corpo.copy(fontSize = 17.sp), color = Cores.ink)
            }

            // As leituras.
            Spacer(Modifier.height(26.dp))
            Overline("Leituras")
            Spacer(Modifier.height(4.dp))
            val leituras = livro.leituras.sortedByDescending { it.iniciadaEm ?: it.terminadaEm ?: 0L }
            if (leituras.isEmpty()) {
                Text("Nenhuma marcada.", style = Estilo.texto, color = Cores.ink3, modifier = Modifier.padding(vertical = 6.dp))
            }
            leituras.forEach { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { leituraAEditar = l }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(textoDaLeitura(l), style = Estilo.texto, color = Cores.ink)
                        diasDeLeitura(l.iniciadaEm, l.terminadaEm)?.let {
                            Text(if (it == 1) "1 dia" else "$it dias", style = Estilo.meta, color = Cores.ink3)
                        }
                    }
                    Icon(Icons.Rounded.EditNote, "Corrigir as datas", tint = Cores.ink3)
                }
            }
            TextoAcao("Acrescentar uma leitura") {
                leituraAEditar = Leitura(livroId = ficha.id)
            }

            // A nota.
            Spacer(Modifier.height(20.dp))
            Overline("Nota")
            Spacer(Modifier.height(6.dp))
            Text(
                ficha.nota ?: "Escreve uma nota sobre o livro.",
                style = if (ficha.nota != null) Estilo.texto.copy(lineHeight = 1.45.em)
                else Estilo.texto.copy(fontStyle = FontStyle.Italic),
                color = if (ficha.nota != null) Cores.ink else Cores.ink3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { editar = true }
                    .padding(vertical = 6.dp),
            )

            // As palavras.
            Spacer(Modifier.height(20.dp))
            Overline(if (palavras.isEmpty()) "Palavras" else "Palavras (${palavras.size})")
            Spacer(Modifier.height(4.dp))
            if (palavras.isEmpty()) {
                Text(
                    "Ainda nenhuma. Quando registares uma palavra, escolhe este livro.",
                    style = Estilo.texto, color = Cores.ink3,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            palavras.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { aoAbrirPalavra(p.lemma) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.lemma, style = Estilo.linhaLema.copy(fontSize = 18.sp), color = Cores.ink, modifier = Modifier.weight(1f))
                    p.page?.let { Text("p. $it", style = Estilo.meta, color = Cores.ink3) }
                }
            }
        }
    }
}

@Composable
private fun TextoAcao(texto: String, aoTocar: () -> Unit) {
    Text(
        texto,
        style = Estilo.controloForte.copy(fontSize = 14.sp),
        color = Cores.accentInk,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = aoTocar)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

@Composable
private fun ItemLivro(
    texto: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    cor: androidx.compose.ui.graphics.Color = Cores.ink,
    aoTocar: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(texto, style = Estilo.controlo, color = cor) },
        leadingIcon = { Icon(icone, contentDescription = null, tint = cor) },
        onClick = aoTocar,
    )
}

// --- diálogos ------------------------------------------------------------------------------

/**
 * A ficha do livro: criar ("Começar um livro") ou editar. Ao editar, um título
 * que já é de outro livro não se aceita, mas oferece-se juntar os dois.
 */
@Composable
private fun DialogoFicha(
    livro: LivroCompleto?,
    aoCancelar: () -> Unit,
    aoCriar: suspend (titulo: String, autor: String?, paginas: Int?, comecar: Boolean) -> Boolean = { _, _, _, _ -> false },
    aoEditar: suspend (titulo: String, autor: String?, paginas: Int?, nota: String?) -> ResultadoFicha = { _, _, _, _ -> ResultadoFicha.FEITO },
    aoJuntarCom: (String) -> Unit = {},
) {
    val ambito = rememberCoroutineScope()
    val f = livro?.ficha
    var titulo by rememberSaveable { mutableStateOf(f?.titulo.orEmpty()) }
    var autor by rememberSaveable { mutableStateOf(f?.autor.orEmpty()) }
    var paginas by rememberSaveable { mutableStateOf(f?.paginas?.toString().orEmpty()) }
    var nota by rememberSaveable { mutableStateOf(f?.nota.orEmpty()) }
    var comecar by rememberSaveable { mutableStateOf(true) }
    var conflito by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.card,
        title = {
            Text(
                if (livro == null) "Começar um livro" else "A ficha do livro",
                style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                color = Cores.ink,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CampoFormulario(
                    "Título", titulo, { titulo = it; conflito = null; erro = null },
                    capitalizacao = KeyboardCapitalization.Words,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoFormulario(
                        "Autor", autor, { autor = it },
                        modifier = Modifier.weight(1f),
                        capitalizacao = KeyboardCapitalization.Words,
                    )
                    CampoFormulario(
                        "Páginas", paginas,
                        { novo -> paginas = novo.filter { it.isDigit() }.take(5) },
                        modifier = Modifier.width(90.dp),
                        numerico = true,
                    )
                }
                if (livro != null) {
                    CampoFormulario("Nota", nota, { nota = it }, linhas = 3, placeholder = "Sobre o livro, para ti")
                    Text(
                        "O título e o autor mudam também nas palavras deste livro.",
                        style = Estilo.ajuda, color = Cores.ink2,
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { comecar = !comecar },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = comecar,
                            onCheckedChange = { comecar = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Cores.accent, uncheckedColor = Cores.ink3, checkmarkColor = Cores.bg,
                            ),
                        )
                        Text("Estou a lê-lo agora", style = Estilo.controlo.copy(fontSize = 15.sp), color = Cores.ink)
                    }
                }
                conflito?.let { outro ->
                    Text(
                        "Já tens um livro chamado \"$outro\". Queres juntar os dois? As palavras "
                            + "e as leituras deste passam para esse.",
                        style = Estilo.ajuda, color = Cores.ink2,
                    )
                    TextoAcao("Juntar a \"$outro\"") { aoJuntarCom(outro) }
                }
                erro?.let { Text(it, style = Estilo.ajuda, color = Cores.err) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = paginas.toIntOrNull()?.takeIf { it > 0 }
                    ambito.launch {
                        if (livro == null) {
                            if (titulo.isBlank()) { erro = "Falta o título."; return@launch }
                            if (!aoCriar(titulo, autor, p, comecar)) erro = "Já tens um livro com esse título."
                        } else {
                            when (aoEditar(titulo, autor, p, nota)) {
                                ResultadoFicha.TITULO_EXISTE -> conflito = titulo.trim()
                                ResultadoFicha.TITULO_VAZIO -> erro = "Falta o título."
                                ResultadoFicha.FEITO -> Unit
                            }
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text(if (livro == null) "Começar" else "Guardar", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = aoCancelar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Cancelar", style = Estilo.controlo) }
        },
    )
}

/** Juntar este livro a outro: escolhe-se o destino e confirma-se. */
@Composable
private fun DialogoJuntar(
    livro: LivroCompleto,
    palavras: Int,
    outros: List<LivroCompleto>,
    aoCancelar: () -> Unit,
    aoJuntar: (LivroCompleto) -> Unit,
) {
    var destino by remember { mutableStateOf<LivroCompleto?>(null) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.card,
        title = {
            Text("Juntar a outro livro", style = Estilo.grupoTitulo.copy(fontSize = 22.sp), color = Cores.ink)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Para quando o mesmo livro ficou com dois nomes (\"Os maias\" e \"Os Maias\").",
                    style = Estilo.ajuda, color = Cores.ink2,
                )
                Spacer(Modifier.height(8.dp))
                outros.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { destino = o },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = destino?.ficha?.id == o.ficha.id,
                            onClick = { destino = o },
                            colors = RadioButtonDefaults.colors(selectedColor = Cores.accent, unselectedColor = Cores.ink3),
                        )
                        Text(o.titulo, style = Estilo.texto, color = Cores.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                destino?.let { d ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (palavras == 1) "A palavra" else "As $palavras palavras")
                            + " e as leituras de \"${livro.titulo}\" passam para \"${d.titulo}\", "
                            + "e \"${livro.titulo}\" deixa de existir.",
                        style = Estilo.ajuda, color = Cores.ink,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { destino?.let(aoJuntar) },
                enabled = destino != null,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Juntar", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = aoCancelar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Cancelar", style = Estilo.controlo) }
        },
    )
}

/**
 * O resumo ao acabar um livro: em quantos dias se leu, quantas palavras se
 * guardaram, quantas já se sabem e as que ainda custam. Sem festa: um
 * registo. Um livro lido pode partilhar-se como imagem (`aoPartilhar`).
 */
@Composable
internal fun DialogoResumoLivro(
    titulo: String,
    resumo: ResumoLivro,
    aoFechar: () -> Unit,
    aoPartilhar: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = {
            Text(
                if (resumo.lido) "Acabaste \"$titulo\"" else "\"$titulo\"",
                style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                color = Cores.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(frasesDoResumo(resumo), style = Estilo.texto.copy(lineHeight = 1.45.em), color = Cores.ink2)
                if (resumo.aCustar.isNotEmpty()) {
                    Column {
                        Text("Ainda custam", style = Estilo.rotulo.copy(fontSize = 13.sp), color = Cores.ink2)
                        Spacer(Modifier.height(4.dp))
                        Text(resumo.aCustar.joinToString(" · "), style = Estilo.corpo.copy(fontSize = 17.sp), color = Cores.ink)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = aoFechar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Fechar", style = Estilo.controloForte) }
        },
        dismissButton = if (resumo.lido && aoPartilhar != null) {
            {
                TextButton(
                    onClick = aoPartilhar,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Partilhar imagem", style = Estilo.controlo) }
            }
        } else null,
    )
}

private enum class QualData { INICIO, FIM }

/**
 * As datas de uma leitura. Sem fim, está a decorrer (ou parada); "Apagar esta
 * leitura" para uma marcada por engano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoLeitura(
    leitura: Leitura,
    aoCancelar: () -> Unit,
    aoGuardar: (Leitura) -> Unit,
    aoApagar: (() -> Unit)?,
) {
    var inicio by remember { mutableStateOf(leitura.iniciadaEm?.let { diaDe(it) }) }
    var fim by remember { mutableStateOf(leitura.terminadaEm?.let { diaDe(it) }) }
    var aEscolher by remember { mutableStateOf<QualData?>(null) }
    val i = inicio
    val f = fim
    val invalido = i != null && f != null && f.isBefore(i)

    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.card,
        title = {
            Text(
                if (leitura.id == 0L) "Uma leitura" else "Datas da leitura",
                style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                color = Cores.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinhaData("Comecei", inicio, "Sem data") { aEscolher = QualData.INICIO }
                LinhaData("Acabei", fim, "Ainda a ler") { aEscolher = QualData.FIM }
                if (fim != null) TextoAcao("Ainda não acabei") { fim = null }
                if (invalido) {
                    Text("A data de fim é anterior à de início.", style = Estilo.ajuda, color = Cores.err)
                }
                aoApagar?.let { apagar ->
                    Text(
                        "Apagar esta leitura",
                        style = Estilo.controloForte.copy(fontSize = 14.sp),
                        color = Cores.err,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = apagar)
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    aoGuardar(
                        leitura.copy(
                            iniciadaEm = inicio?.let { millisDoDia(it) },
                            terminadaEm = fim?.let { millisDoDia(it) },
                            parada = leitura.parada && fim == null,
                        )
                    )
                },
                enabled = !invalido && (inicio != null || fim != null),
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Guardar", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = aoCancelar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Cancelar", style = Estilo.controlo) }
        },
    )

    aEscolher?.let { qual ->
        val atual = if (qual == QualData.INICIO) inicio else fim
        // O DatePicker trabalha em UTC: o dia escolhido é a meia-noite UTC.
        val estadoData = rememberDatePickerState(
            initialSelectedDateMillis = (atual ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { aEscolher = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        estadoData.selectedDateMillis?.let { ms ->
                            val dia = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            if (qual == QualData.INICIO) inicio = dia else fim = dia
                        }
                        aEscolher = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
                ) { Text("Escolher", style = Estilo.controloForte) }
            },
            dismissButton = {
                TextButton(
                    onClick = { aEscolher = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Cancelar", style = Estilo.controlo) }
            },
        ) {
            DatePicker(state = estadoData, showModeToggle = false)
        }
    }
}

@Composable
private fun LinhaData(rotulo: String, dia: LocalDate?, semData: String, aoTocar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = aoTocar)
            .padding(vertical = 10.dp, horizontal = 2.dp),
    ) {
        Text(rotulo, style = Estilo.controlo.copy(fontSize = 15.sp), color = Cores.ink2, modifier = Modifier.width(80.dp))
        Text(
            dia?.format(DIA_MES_ANO) ?: semData,
            style = Estilo.controloForte.copy(fontSize = 15.sp),
            color = if (dia != null) Cores.ink else Cores.ink3,
        )
    }
}
