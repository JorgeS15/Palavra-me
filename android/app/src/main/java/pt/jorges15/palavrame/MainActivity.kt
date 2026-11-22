package pt.jorges15.palavrame

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.jogo.JogoViewModel
import pt.jorges15.palavrame.jogo.Lembrete
import pt.jorges15.palavrame.ui.EcraColecao
import pt.jorges15.palavrame.ui.EcraDefinicoes
import pt.jorges15.palavrame.ui.EcraJogo
import pt.jorges15.palavrame.ui.EcraLicencas
import pt.jorges15.palavrame.ui.EcraLivros
import pt.jorges15.palavrame.ui.EcraPesquisa
import pt.jorges15.palavrame.ui.PesquisaViewModel
import pt.jorges15.palavrame.ui.AcaoNovidade
import pt.jorges15.palavrame.ui.DialogoNovidades
import pt.jorges15.palavrame.ui.NOVIDADES
import pt.jorges15.palavrame.ui.mostrarNovidades
import pt.jorges15.palavrame.ui.notasPorVer
import pt.jorges15.palavrame.ui.BotaoPrincipal
import pt.jorges15.palavrame.ui.Logotipo
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import pt.jorges15.palavrame.ui.theme.TemaPalavrame

private enum class Ecra { PESQUISA, COLECAO, LIVROS, DEFINICOES, LICENCAS, JOGO }

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // A partir do Android 15 o sistema desenha as barras por cima da app
        // quer se peça quer não. Declará-lo é o que faz os `Scaffold`
        // receberem os `insets` certos e o conteúdo deixar de ficar por
        // baixo da barra de navegação.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PalavrameApp
        val preferencias = Preferencias(this)
        // O texto vindo do leitor de e-books trata-se só na primeira criação.
        // Ao rodar o ecrã a atividade é recriada com o mesmo intent, e repetir
        // a pesquisa deitava fora a navegação entretanto feita (um sinónimo
        // aberto, a frase de contexto).
        val primeiraCriacao = savedInstanceState == null

        setContent {
            val tema by preferencias.tema.collectAsState()
            val paleta by preferencias.paleta.collectAsState()
            val letra by preferencias.letra.collectAsState()

            TemaPalavrame(preferido = tema, paleta = paleta, letra = letra) {
                // Falta instalar se nao ha base nenhuma, ou se a que veio no
                // APK e diferente da instalada.
                var temDicionario by remember {
                    mutableStateOf(app.dicionario != null && !app.temDicionarioEmpacotado())
                }

                // A atualização do dicionário falhou, mas a base antiga abriu:
                // a app segue com ela e diz o que se passou. Volta a tentar no
                // arranque seguinte (a versão instalada não mudou).
                var falhaDaAtualizacao by rememberSaveable { mutableStateOf<String?>(null) }

                if (!temDicionario) {
                    EcraInstalarDicionario(
                        app,
                        aoFicarPronto = { temDicionario = true },
                        aoFalharComBaseAntiga = { motivo ->
                            falhaDaAtualizacao = motivo
                            temDicionario = true
                        },
                    )
                    return@TemaPalavrame
                }

                val vm: PesquisaViewModel = viewModel(factory = fabrica(app))
                // A notificação abre a app já no jogo — é para isso que
                // serve tocar-lhe.
                val daNotificacao =
                    intent?.getBooleanExtra(Lembrete.EXTRA_ABRIR_JOGO, false) == true
                // A palavra anunciada viaja no intent, para o jogo não a
                // reescolher ao abrir. Ver `escolherPalavra`.
                val lemaDaNotificacao = remember {
                    if (daNotificacao) intent?.getStringExtra(Lembrete.EXTRA_LEMA) else null
                }
                var ecra by rememberSaveable {
                    mutableStateOf(if (daNotificacao) Ecra.JOGO else Ecra.PESQUISA)
                }
                // O livro tocado na estante do ecrã inicial: a coleção abre já
                // nele e depois esquece-o (volta a nulo), para a visita seguinte
                // pelo separador abrir como sempre.
                var livroPedido by rememberSaveable { mutableStateOf<String?>(null) }
                // A nota de novidades "Ver o Aspeto" abre as Definições com essa
                // secção aberta, só dessa vez: ao sair das Definições esquece-se.
                var abrirAspeto by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(ecra) { if (ecra != Ecra.DEFINICOES) abrirAspeto = false }
                // Cada abertura do jogo é uma sessão nova (um ViewModel novo).
                // A sessão 0, quando a app abriu pela notificação, é a do
                // lembrete; as outras são revisões à mão ("Rever agora").
                var sessaoJogo by rememberSaveable { mutableStateOf(0) }
                // O livro a rever (pedido na Coleção), ou nulo para a revisão normal.
                var livroARever by rememberSaveable { mutableStateOf<String?>(null) }
                // Conta os toques no separador Livros já aberto (volta à lista).
                var voltarAosLivros by remember { mutableStateOf(0) }
                // A palavra aberta veio da Coleção: o "retroceder" da palavra
                // leva de volta para lá, e não para o ecrã inicial.
                // (Ou dos Livros, desde a 1.22: a página do livro abre palavras.)
                var origemDaPalavra by rememberSaveable { mutableStateOf<Ecra?>(null) }
                // Guarda o estado de cada separador (a posição na lista, o
                // filtro, os livros recolhidos) enquanto se está noutro: voltar
                // à Coleção encontra-a como se deixou.
                val estados = rememberSaveableStateHolder()

                // Retroceder num subecrã volta à pesquisa (das licenças, às
                // definições); só na pesquisa é que o retroceder do sistema
                // fecha a app. Desligado na pesquisa, para o histórico de
                // palavras (dentro do EcraPesquisa) e a saída normal tratarem
                // disso.
                BackHandler(enabled = ecra != Ecra.PESQUISA) {
                    origemDaPalavra = null
                    ecra = when {
                        ecra == Ecra.LICENCAS -> Ecra.DEFINICOES
                        // A rever um livro, volta-se ao livro, como no "×".
                        ecra == Ecra.JOGO && livroARever != null -> Ecra.LIVROS
                        else -> Ecra.PESQUISA
                    }
                    livroARever = null
                }

                // Palavra vinda de outra app (o leitor de e-books): procura-se
                // logo, sem passar pelo teclado. Se a seleção trouxer mais do
                // que uma palavra, procura-se a principal e guarda-se a frase
                // inteira como contexto, para pré-preencher o registo (3.1).
                LaunchedEffect(Unit) {
                    if (!primeiraCriacao) return@LaunchedEffect
                    val recebido = textoRecebido() ?: return@LaunchedEffect
                    // Pela partilha pode vir um link ou aspas agarradas; sai
                    // tudo isso antes de decidir o que procurar.
                    val frase = Texto.limparPartilha(recebido)
                    if (frase.isBlank()) return@LaunchedEffect
                    if (frase.any { it.isWhitespace() }) {
                        val palavra = Texto.palavraPrincipal(frase)
                        if (palavra.isNotBlank()) {
                            vm.procurarComContexto(palavra, frase.take(300))
                        }
                    } else {
                        vm.procurar(Texto.limparSelecao(frase))
                    }
                }

                // As novidades desta versão, uma vez, ao abrir depois de uma
                // atualização. Não interrompe quem chegou pelo lembrete do
                // Rever ou com uma palavra do leitor: nesse caso fica para a
                // próxima vez que a app se abrir normalmente (e por isso a
                // versão só se marca como vista quando a nota aparece).
                // O código da versão cujas notas estão abertas (nulo: nenhuma).
                var notasAbertas by rememberSaveable { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    if (!primeiraCriacao || daNotificacao || textoRecebido() != null) {
                        return@LaunchedEffect
                    }
                    val atual = BuildConfig.VERSION_CODE
                    val vista = preferencias.versaoVista()
                    preferencias.marcarVersaoVista(atual)
                    // Instalação nova (ou já vista): nada a mostrar, e as notas
                    // ficam dadas como vistas. Senão, as mais recentes por ver,
                    // mesmo que sejam de uma versão que não chegou a sair.
                    if (mostrarNovidades(atual, vista, foiAtualizacao())) {
                        notasAbertas = notasPorVer(atual, vista)
                    }
                }
                falhaDaAtualizacao?.let { motivo ->
                    AlertDialog(
                        onDismissRequest = { falhaDaAtualizacao = null },
                        containerColor = Cores.card,
                        title = {
                            Text(
                                "O dicionário não foi atualizado",
                                style = Estilo.grupoTitulo, color = Cores.ink,
                            )
                        },
                        text = {
                            Text(
                                "$motivo\n\nContinuas a usar o dicionário que já tinhas, e as "
                                    + "tuas palavras estão todas aqui. A app volta a tentar "
                                    + "da próxima vez que a abrires.",
                                style = Estilo.texto, color = Cores.ink2,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { falhaDaAtualizacao = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
                            ) { Text("Continuar", style = Estilo.controloForte) }
                        },
                    )
                }

                // As novidades esperam que o aviso acima feche: dois diálogos
                // de uma vez era demais.
                if (falhaDaAtualizacao == null) {
                    notasAbertas?.let { NOVIDADES[it] }?.let { notas ->
                        DialogoNovidades(
                            versao = BuildConfig.VERSION_NAME,
                            novidades = notas,
                            aoFechar = { notasAbertas = null },
                            aoAgir = { acao ->
                                notasAbertas = null
                                when (acao) {
                                    AcaoNovidade.ASPETO -> {
                                        abrirAspeto = true
                                        ecra = Ecra.DEFINICOES
                                    }
                                }
                            },
                        )
                    }
                }

                val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
                // O Rever é um ecrã inteiro, com o seu "×"; as licenças são um
                // subecrã das definições. Nos outros três há a barra de baixo.
                val comBarra = ecra == Ecra.PESQUISA || ecra == Ecra.COLECAO || ecra == Ecra.LIVROS ||
                    ecra == Ecra.DEFINICOES

                Scaffold(
                    containerColor = Cores.bg,
                    // Os ecrãs de dentro tratam das barras do sistema; este só
                    // reserva o espaço da barra de navegação.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (comBarra) {
                            BarraNavegacao(
                                atual = ecra,
                                aoEscolher = { destino ->
                                    // Tocar outra vez em Procurar volta ao início.
                                    if (destino == Ecra.PESQUISA && ecra == Ecra.PESQUISA) vm.limpar()
                                    // Tocar outra vez em Livros volta à lista dos livros.
                                    if (destino == Ecra.LIVROS && ecra == Ecra.LIVROS) voltarAosLivros++
                                    // Mudar de separador à mão corta a ligação à Coleção.
                                    origemDaPalavra = null
                                    ecra = destino
                                },
                            )
                        }
                    },
                ) { p ->
                    Box(Modifier.padding(p).consumeWindowInsets(p)) {
                        LarguraDeLeitura {
                          estados.SaveableStateProvider(ecra.name) {
                            when (ecra) {
                                Ecra.COLECAO -> EcraColecao(
                                    vm,
                                    desenvolvedor = desenvolvedor,
                                    aoVoltar = { ecra = Ecra.PESQUISA },
                                    aoAbrirPalavra = { lema ->
                                        // Só se muda de ecrã com a palavra já
                                        // pronta: senão via-se o início por um
                                        // instante.
                                        vm.procurar(lema) {
                                            origemDaPalavra = Ecra.COLECAO
                                            ecra = Ecra.PESQUISA
                                        }
                                    },
                                    aoAbrirLivro = { titulo ->
                                        livroPedido = titulo
                                        ecra = Ecra.LIVROS
                                    },
                                )
                                Ecra.LIVROS -> EcraLivros(
                                    vm,
                                    voltarALista = voltarAosLivros,
                                    livroInicial = livroPedido,
                                    aoUsarLivroInicial = { livroPedido = null },
                                    aoAbrirPalavra = { lema ->
                                        vm.procurar(lema) {
                                            origemDaPalavra = Ecra.LIVROS
                                            ecra = Ecra.PESQUISA
                                        }
                                    },
                                    aoReverLivro = { titulo ->
                                        livroARever = titulo
                                        sessaoJogo++
                                        ecra = Ecra.JOGO
                                    },
                                )
                                Ecra.DEFINICOES -> EcraDefinicoes(
                                    preferencias,
                                    aoAbrirLicencas = { ecra = Ecra.LICENCAS },
                                    aoVoltar = { ecra = Ecra.PESQUISA },
                                    aspetoAberto = abrirAspeto,
                                )
                                Ecra.LICENCAS -> EcraLicencas(aoVoltar = { ecra = Ecra.DEFINICOES })
                                Ecra.JOGO -> {
                                    // A chave leva a sessão e o modo dentro: cada abertura
                                    // começa do zero (a fila mudou, o limite do dia
                                    // também), e ligar o Modo Desenvolvedor faz efeito
                                    // logo, sem fechar a app.
                                    val doLembrete = daNotificacao && sessaoJogo == 0
                                    val jogo: JogoViewModel = viewModel(
                                        key = "jogo-$sessaoJogo-$desenvolvedor-${livroARever.orEmpty()}",
                                        factory = fabricaJogo(
                                            app, preferencias, desenvolvedor,
                                            lemaPreferido = if (doLembrete) lemaDaNotificacao else null,
                                            deLembrete = doLembrete,
                                            livro = livroARever,
                                        ),
                                    )
                                    // De um livro, volta-se à Coleção, de onde se veio.
                                    EcraJogo(jogo, aoVoltar = {
                                        ecra = if (livroARever != null) Ecra.LIVROS else Ecra.PESQUISA
                                        livroARever = null
                                    })
                                }
                                Ecra.PESQUISA -> EcraPesquisa(
                                    vm,
                                    preferencias,
                                    aoAbrirColecao = { ecra = Ecra.COLECAO },
                                    aoAbrirLivros = {
                                        livroPedido = null
                                        voltarAosLivros++
                                        ecra = Ecra.LIVROS
                                    },
                                    aoAbrirJogo = {
                                        livroARever = null
                                        sessaoJogo++
                                        ecra = Ecra.JOGO
                                    },
                                    aoAbrirLivro = { titulo ->
                                        livroPedido = titulo
                                        ecra = Ecra.LIVROS
                                    },
                                    aoSairParaOrigem = origemDaPalavra?.let { origem ->
                                        {
                                            vm.limpar()
                                            origemDaPalavra = null
                                            ecra = origem
                                        }
                                    },
                                )
                            }
                          }
                        }
                    }
                }
            }
        }
    }

    /**
     * A app foi atualizada (e não acabada de instalar)? O Android guarda as
     * duas datas: numa instalação nova são iguais.
     */
    private fun foiAtualizacao(): Boolean = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (e: Exception) {
        false
    }

    private fun textoRecebido(): String? = when (intent?.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun fabrica(app: PalavrameApp) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesquisaViewModel({ app.dicionario }, app.utilizador) as T
    }

    private fun fabricaJogo(
        app: PalavrameApp,
        preferencias: Preferencias,
        treinoLivre: Boolean,
        lemaPreferido: String?,
        deLembrete: Boolean,
        livro: String? = null,
    ) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JogoViewModel(
                { app.dicionario }, app.utilizador,
                treinoLivre = treinoLivre,
                lemaPreferido = lemaPreferido,
                deLembrete = deLembrete,
                cotaRestante = { preferencias.cotaManual.value.restantes(LocalDate.now()) },
                gastarCota = { preferencias.usarCotaManual() },
                aoResponder = { lemma -> Lembrete.esquecerSeAnunciou(app, lemma) },
                escreverAPalavra = { preferencias.escreverAPalavra.value },
                livro = livro,
            ) as T
    }
}

/** Os três destinos da barra de baixo. */
private data class Destino(val ecra: Ecra, val rotulo: String, val icone: ImageVector)

private val DESTINOS = listOf(
    Destino(Ecra.PESQUISA, "Procurar", Icons.Rounded.Search),
    Destino(Ecra.COLECAO, "Coleção", Icons.Rounded.Bookmarks),
    Destino(Ecra.LIVROS, "Livros", Icons.Rounded.AutoStories),
    Destino(Ecra.DEFINICOES, "Definições", Icons.Rounded.Tune),
)

/**
 * A navegação inferior: Procurar, Coleção, Definições. Fundo de papel, uma
 * linha fina em cima, e a pílula do destino ativo em accentSoft.
 */
@Composable
private fun BarraNavegacao(atual: Ecra, aoEscolher: (Ecra) -> Unit) {
    val linha = Cores.line
    NavigationBar(
        containerColor = Cores.bg,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(linha, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
    ) {
        DESTINOS.forEach { d ->
            val ativo = d.ecra == atual
            NavigationBarItem(
                selected = ativo,
                onClick = { aoEscolher(d.ecra) },
                icon = { Icon(d.icone, null) },
                label = {
                    Text(
                        d.rotulo,
                        style = Estilo.meta.copy(
                            fontSize = 12.sp,
                            fontWeight = if (ativo) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cores.accentInk,
                    selectedTextColor = Cores.ink,
                    indicatorColor = Cores.accentSoft,
                    unselectedIconColor = Cores.ink2,
                    unselectedTextColor = Cores.ink2,
                ),
            )
        }
    }
}

/**
 * Limita a largura do conteúdo a uma coluna de leitura.
 *
 * Num telemóvel não muda nada. Num tablet, ou num telemóvel deitado, as
 * definições estendiam-se de margem a margem, com linhas de 150 caracteres
 * que se leem mal. Centra-se uma coluna de no máximo 720 dp; o fundo à volta é
 * o mesmo papel, por isso a fronteira não se vê.
 */
@Composable
private fun LarguraDeLeitura(conteudo: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Cores.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth()) {
            conteudo()
        }
    }
}

/**
 * Primeiro arranque: a base ainda nao esta instalada.
 *
 * O caso normal e o dicionario vir dentro do APK e instalar-se sozinho. O
 * seletor de ficheiros fica como alternativa, e serve tambem para trocar a
 * base por uma versao nova sem recompilar a app.
 */
@Composable
private fun EcraInstalarDicionario(
    app: PalavrameApp,
    aoFicarPronto: () -> Unit,
    /**
     * A atualização falhou mas havia uma base a funcionar. Antes, a pessoa
     * ficava presa neste ecrã, com a mensagem de erro e as palavras todas
     * guardadas mas sem app; agora segue com o dicionário que já tinha.
     */
    aoFalharComBaseAntiga: (String) -> Unit,
) {
    var aCopiar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val ambito = rememberCoroutineScope()
    val empacotado = remember { app.temDicionarioEmpacotado() }
    // O tamanho real, para o texto de espera (era "São 200 MB", escrito à mão).
    val tamanhoMb = remember { app.tamanhoDoDicionarioEmpacotado()?.let { (it + (1 shl 20) - 1) shr 20 } }

    LaunchedEffect(empacotado) {
        if (!empacotado) return@LaunchedEffect
        aCopiar = true
        when (val r = app.instalarDicionarioEmpacotado()) {
            is ResultadoImportacao.Feito -> { aCopiar = false; aoFicarPronto() }
            is ResultadoImportacao.Erro -> {
                aCopiar = false
                if (app.dicionario != null) aoFalharComBaseAntiga(r.motivo) else erro = r.motivo
            }
        }
    }

    val seletor = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        aCopiar = true
        erro = null
        ambito.launch {
            when (val r = app.importarDicionario(uri)) {
                is ResultadoImportacao.Feito -> { aCopiar = false; aoFicarPronto() }
                is ResultadoImportacao.Erro -> { aCopiar = false; erro = r.motivo }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Cores.bg) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logotipo()
            Spacer(Modifier.height(12.dp))
            Text(
                if (app.dicionario != null) "A atualizar o dicionário."
                else "A preparar o dicionário.",
                style = Estilo.corpo,
                color = Cores.ink,
            )
            Spacer(Modifier.height(24.dp))

            if (aCopiar) {
                CircularProgressIndicator(color = Cores.accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (tamanhoMb != null)
                        "São $tamanhoMb MB. Demora alguns segundos e só acontece uma vez."
                    else "Demora alguns segundos e só acontece uma vez.",
                    style = Estilo.texto,
                    color = Cores.ink2,
                )
            } else {
                Text(
                    "Se preferires, podes escolher tu o ficheiro do dicionário.",
                    style = Estilo.texto,
                    color = Cores.ink2,
                )
                Spacer(Modifier.height(24.dp))
                BotaoPrincipal("Escolher o ficheiro", { seletor.launch(arrayOf("*/*")) })
                erro?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = Estilo.texto, color = Cores.err)
                }
            }
        }
    }
}
