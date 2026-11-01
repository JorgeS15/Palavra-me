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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.jogo.JogoViewModel
import pt.jorges15.palavrame.jogo.Lembrete
import pt.jorges15.palavrame.ui.EcraColecao
import pt.jorges15.palavrame.ui.EcraDefinicoes
import pt.jorges15.palavrame.ui.EcraJogo
import pt.jorges15.palavrame.ui.EcraLicencas
import pt.jorges15.palavrame.ui.EcraPesquisa
import pt.jorges15.palavrame.ui.PesquisaViewModel
import pt.jorges15.palavrame.ui.BotaoPrincipal
import pt.jorges15.palavrame.ui.Logotipo
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import pt.jorges15.palavrame.ui.theme.TemaPalavrame

private enum class Ecra { PESQUISA, COLECAO, DEFINICOES, LICENCAS, JOGO }

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

            TemaPalavrame(preferido = tema) {
                // Falta instalar se nao ha base nenhuma, ou se a que veio no
                // APK e diferente da instalada.
                var temDicionario by remember {
                    mutableStateOf(app.dicionario != null && !app.temDicionarioEmpacotado())
                }

                if (!temDicionario) {
                    EcraInstalarDicionario(app, aoFicarPronto = { temDicionario = true })
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

                // Retroceder num subecrã volta à pesquisa (das licenças, às
                // definições); só na pesquisa é que o retroceder do sistema
                // fecha a app. Desligado na pesquisa, para o histórico de
                // palavras (dentro do EcraPesquisa) e a saída normal tratarem
                // disso.
                BackHandler(enabled = ecra != Ecra.PESQUISA) {
                    ecra = if (ecra == Ecra.LICENCAS) Ecra.DEFINICOES else Ecra.PESQUISA
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

                val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
                // O Rever é um ecrã inteiro, com o seu "×"; as licenças são um
                // subecrã das definições. Nos outros três há a barra de baixo.
                val comBarra = ecra == Ecra.PESQUISA || ecra == Ecra.COLECAO ||
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
                                    ecra = destino
                                },
                            )
                        }
                    },
                ) { p ->
                    Box(Modifier.padding(p).consumeWindowInsets(p)) {
                        LarguraDeLeitura {
                            when (ecra) {
                                Ecra.COLECAO -> EcraColecao(
                                    vm,
                                    desenvolvedor = desenvolvedor,
                                    aoVoltar = { ecra = Ecra.PESQUISA },
                                    aoAbrirPalavra = { lema ->
                                        vm.procurar(lema)
                                        ecra = Ecra.PESQUISA
                                    },
                                    livroInicial = livroPedido,
                                    aoUsarLivroInicial = { livroPedido = null },
                                )
                                Ecra.DEFINICOES -> EcraDefinicoes(
                                    preferencias,
                                    aoAbrirLicencas = { ecra = Ecra.LICENCAS },
                                    aoVoltar = { ecra = Ecra.PESQUISA },
                                )
                                Ecra.LICENCAS -> EcraLicencas(aoVoltar = { ecra = Ecra.DEFINICOES })
                                Ecra.JOGO -> {
                                    // A chave leva o modo dentro: sem isso, ligar o Modo
                                    // Desenvolvedor e voltar ao jogo reaproveitava o
                                    // ViewModel antigo e o modo só fazia efeito depois
                                    // de fechar a app.
                                    val jogo: JogoViewModel = viewModel(
                                        key = "jogo-$desenvolvedor-$lemaDaNotificacao",
                                        factory = fabricaJogo(app, desenvolvedor, lemaDaNotificacao),
                                    )
                                    EcraJogo(jogo, aoVoltar = { ecra = Ecra.PESQUISA })
                                }
                                Ecra.PESQUISA -> EcraPesquisa(
                                    vm,
                                    preferencias,
                                    aoAbrirColecao = { ecra = Ecra.COLECAO },
                                    aoAbrirJogo = { ecra = Ecra.JOGO },
                                    aoAbrirLivro = { titulo ->
                                        livroPedido = titulo
                                        ecra = Ecra.COLECAO
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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
        treinoLivre: Boolean,
        lemaPreferido: String?,
    ) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JogoViewModel(
                { app.dicionario }, app.utilizador,
                treinoLivre = treinoLivre,
                lemaPreferido = lemaPreferido,
            ) as T
    }
}

/** Os três destinos da barra de baixo. */
private data class Destino(val ecra: Ecra, val rotulo: String, val icone: ImageVector)

private val DESTINOS = listOf(
    Destino(Ecra.PESQUISA, "Procurar", Icons.Rounded.Search),
    Destino(Ecra.COLECAO, "Coleção", Icons.Rounded.Bookmarks),
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
private fun EcraInstalarDicionario(app: PalavrameApp, aoFicarPronto: () -> Unit) {
    var aCopiar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val ambito = rememberCoroutineScope()
    val empacotado = remember { app.temDicionarioEmpacotado() }

    LaunchedEffect(empacotado) {
        if (!empacotado) return@LaunchedEffect
        aCopiar = true
        when (val r = app.instalarDicionarioEmpacotado()) {
            is ResultadoImportacao.Feito -> { aCopiar = false; aoFicarPronto() }
            is ResultadoImportacao.Erro -> { aCopiar = false; erro = r.motivo }
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
                    "São 200 MB. Demora alguns segundos e só acontece uma vez.",
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
