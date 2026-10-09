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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import pt.jorges15.palavrame.ui.theme.TemaPalavrame

private enum class Ecra { PESQUISA, COLECAO, DEFINICOES, LICENCAS, JOGO }

class MainActivity : ComponentActivity() {

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

                LarguraDeLeitura {
                when (ecra) {
                    Ecra.COLECAO -> {
                        val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
                        EcraColecao(
                            vm,
                            desenvolvedor = desenvolvedor,
                            aoVoltar = { ecra = Ecra.PESQUISA },
                        )
                    }
                    Ecra.DEFINICOES -> EcraDefinicoes(
                        preferencias,
                        aoAbrirLicencas = { ecra = Ecra.LICENCAS },
                        aoVoltar = { ecra = Ecra.PESQUISA },
                    )
                    Ecra.LICENCAS -> EcraLicencas(aoVoltar = { ecra = Ecra.DEFINICOES })
                    Ecra.JOGO -> {
                        val treinoLivre by preferencias.modoDesenvolvedor
                            .collectAsState()
                        // A chave leva o modo dentro: sem isso, ligar o Modo
                        // Desenvolvedor e voltar ao jogo reaproveitava o
                        // ViewModel antigo e o modo só fazia efeito depois
                        // de fechar a app.
                        val jogo: JogoViewModel = viewModel(
                            key = "jogo-$treinoLivre-$lemaDaNotificacao",
                            factory = fabricaJogo(app, treinoLivre, lemaDaNotificacao),
                        )
                        EcraJogo(jogo, aoVoltar = { ecra = Ecra.PESQUISA })
                    }
                    Ecra.PESQUISA -> EcraPesquisa(
                        vm,
                        preferencias,
                        aoAbrirColecao = { ecra = Ecra.COLECAO },
                        aoAbrirDefinicoes = { ecra = Ecra.DEFINICOES },
                        aoAbrirJogo = { ecra = Ecra.JOGO },
                    )
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
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Palavra-me", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (app.dicionario != null) "A atualizar o dicionário."
                else "A preparar o dicionário.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))

            if (aCopiar) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "São 200 MB. Demora alguns segundos e só acontece uma vez.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Se preferires, podes escolher tu o ficheiro do dicionário.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { seletor.launch(arrayOf("*/*")) }) {
                    Text("Escolher o ficheiro")
                }
                erro?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
