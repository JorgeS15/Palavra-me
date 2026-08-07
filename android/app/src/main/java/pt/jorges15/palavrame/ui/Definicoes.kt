package pt.jorges15.palavrame.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.ResultadoImportacao
import pt.jorges15.palavrame.data.Backup
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.Tema
import pt.jorges15.palavrame.jogo.Lembrete
import pt.jorges15.palavrame.jogo.horasDosLembretes

/**
 * Definições: tema, estado do dicionário, e a informação sobre a app.
 *
 * O rodapé do ecrã principal ficou só com a versão da app — os números do
 * dicionário são coisa que se consulta uma vez e não precisa de ocupar
 * espaço permanente na frente de quem está a ler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraDefinicoes(
    preferencias: Preferencias,
    aoAbrirLicencas: () -> Unit,
    aoVoltar: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PalavrameApp
    val ambito = rememberCoroutineScope()
    val tema by preferencias.tema.collectAsState()

    var aInstalar by remember { mutableStateOf(false) }
    var mensagemBackup by remember { mutableStateOf<String?>(null) }

    val exportador = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destino ->
        if (destino == null) return@rememberLauncherForActivityResult
        ambito.launch {
            val palavras = app.utilizador.palavras().todasAgora()
            mensagemBackup = when (
                val r = Backup.exportar(context, destino, palavras, BuildConfig.VERSION_NAME)
            ) {
                is Backup.Resultado.Feito -> "Exportadas ${r.novas} palavras."
                is Backup.Resultado.Erro -> "Falhou: ${r.motivo}"
            }
        }
    }

    val importador = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { origem ->
        if (origem == null) return@rememberLauncherForActivityResult
        ambito.launch {
            mensagemBackup = when (
                val r = Backup.importar(context, origem, app.utilizador.palavras())
            ) {
                is Backup.Resultado.Feito -> buildString {
                    append("Importadas ${r.novas} palavras")
                    if (r.repetidas > 0) {
                        append("; ${r.repetidas} já estavam na coleção e ficaram como estavam")
                    }
                    append(".")
                }
                is Backup.Resultado.Erro -> "Falhou: ${r.motivo}"
            }
        }
    }
    var mensagem by remember { mutableStateOf<String?>(null) }
    // Recalcula-se depois de instalar: é o que faz a opção desaparecer
    // quando já não há nada de novo para instalar.
    var haNovoNoApk by remember { mutableStateOf(app.temDicionarioEmpacotado()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Definições") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Seccao("Aspeto")
            Tema.entries.forEach { opcao ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { preferencias.definirTema(opcao) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = tema == opcao, onClick = { preferencias.definirTema(opcao) })
                    Spacer(Modifier.width(8.dp))
                    Text(opcao.etiqueta, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Seccao("Rever")
            SeccaoJogo(preferencias)

            Seccao("As minhas palavras")
            Text(
                "A coleção é a única coisa nesta app que não se reconstrói. "
                    + "O dicionário volta sempre do ficheiro; as palavras que "
                    + "registaste, não.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp, 4.dp),
            )
            Row(
                Modifier.padding(16.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { exportador.launch(Backup.nomeSugerido()) }) {
                    Text("Exportar cópia")
                }
                OutlinedButton(
                    // Aceita qualquer tipo: há gestores de ficheiros que não
                    // atribuem application/json a um .json, e nesses o
                    // seletor mostrava a cópia a cinzento.
                    onClick = { importador.launch(arrayOf("*/*")) }
                ) {
                    Text("Importar cópia")
                }
            }
            mensagemBackup?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
                )
            }

            Seccao("Sobre")
            Linha("Versão da app", BuildConfig.VERSION_NAME)
            // O dicionário e as licenças passam para um ecrã só deles: são
            // informação que se consulta uma vez, e a ocupar espaço aqui
            // faziam as Definições parecerem uma ficha técnica.
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClick = aoAbrirLicencas)
                    .padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Dicionário e licenças",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }

            Seccao("Avançado")
            SeccaoDesenvolvedor(preferencias)
            Spacer(Modifier.height(24.dp))
        }
    }
}



/**
 * Dicionário e licenças.
 *
 * Vive fora das Definições porque é informação de consulta, não de escolha:
 * ninguém vem aqui todos os dias. Mas tem de existir e ser fácil de
 * encontrar — a base deriva de fontes com atribuição obrigatória, e o ecrã
 * de fontes é o que cumpre essa obrigação (plano, F5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraLicencas(aoVoltar: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PalavrameApp
    val ambito = rememberCoroutineScope()
    var aInstalar by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var haNovoNoApk by remember { mutableStateOf(app.temDicionarioEmpacotado()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dicionário e licenças") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Seccao("Dicionário")
            val d = app.dicionario
            Linha("Construído em", d?.construidoEm?.take(10) ?: "—")
            Linha("Lemas", d?.totalLemas?.let { "%,d".format(it) } ?: "—")
            Linha("Aceções", d?.totalAcecoes?.let { "%,d".format(it) } ?: "—")
            Linha("Formas flexionadas", d?.totalFormas?.let { "%,d".format(it) } ?: "—")
            Linha("Exemplos", d?.totalExemplos?.let { "%,d".format(it) } ?: "—")

            if (haNovoNoApk) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Button(
                        enabled = !aInstalar,
                        onClick = {
                            aInstalar = true
                            ambito.launch {
                                mensagem = when (val r = app.instalarDicionarioEmpacotado()) {
                                    is ResultadoImportacao.Feito ->
                                        "Instalado. Fecha e volta a abrir a app."
                                    is ResultadoImportacao.Erro -> "Falhou: ${r.motivo}"
                                }
                                aInstalar = false
                                haNovoNoApk = app.temDicionarioEmpacotado()
                            }
                        },
                    ) {
                        Text(if (aInstalar) "A instalar…" else "Instalar o dicionário da app")
                    }
                }
            }
            mensagem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
            }

            Seccao("Fontes")
            app.dicionario?.fontes()?.forEach { fonte ->
                Column(Modifier.padding(16.dp, 4.dp)) {
                    Text(fonte.nome, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        fonte.licenca,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(
                "A base de dados deriva destas fontes e é distribuída sob "
                    + "CC BY-SA, como elas exigem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp, 8.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Os lembretes: ligar, quantos por dia, e entre que horas.
 *
 * A permissão de notificações é pedida **aqui**, no momento em que se liga o
 * modo, e não ao arrancar a app. Quem nunca ligar os lembretes nunca vê o
 * pedido — que é a diferença entre uma app que pergunta quando precisa e uma
 * que pergunta por precaução.
 */
@Composable
private fun SeccaoJogo(preferencias: Preferencias) {
    val context = LocalContext.current
    val ligado by preferencias.jogoLigado.collectAsState()
    val quantos by preferencias.quantosPorDia.collectAsState()
    val inicio by preferencias.inicioDaJanela.collectAsState()
    val fim by preferencias.fimDaJanela.collectAsState()
    var aEscolher by remember { mutableStateOf<Extremo?>(null) }

    fun reagendar() = Lembrete.agendar(context, quantos, inicio, fim)

    val pedido = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        // Sem permissão não se liga: um interruptor ligado que não notifica
        // seria uma mentira na interface.
        preferencias.definirJogoLigado(concedida)
        if (concedida) reagendar()
    }

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Lembretes", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Uma palavra da tua coleção, para responderes numa pausa. "
                    + "Se não houver nada a rever, não te incomoda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = ligado,
            onCheckedChange = { querLigar ->
                if (!querLigar) {
                    preferencias.definirJogoLigado(false)
                    Lembrete.cancelar(context)
                } else if (Lembrete.temPermissao(context)) {
                    preferencias.definirJogoLigado(true)
                    reagendar()
                } else {
                    pedido.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    if (!ligado) return

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Por dia", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(
            enabled = quantos > 1,
            onClick = { preferencias.definirQuantosPorDia(quantos - 1); reagendar() },
        ) { Icon(Icons.Default.Remove, "Menos um") }
        Text(quantos.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(
            enabled = quantos < Preferencias.MAX_POR_DIA,
            onClick = { preferencias.definirQuantosPorDia(quantos + 1); reagendar() },
        ) { Icon(Icons.Default.Add, "Mais um") }
    }

    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
        Text("Entre as", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { aEscolher = Extremo.INICIO }) { Text("%02d:00".format(inicio)) }
        Text("e as", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { aEscolher = Extremo.FIM }) { Text("%02d:00".format(fim)) }
    }

    // Mostrar as horas calculadas evita a pergunta óbvia — "3 entre as 8h e
    // as 22h dá a que horas?" — e torna a regra visível em vez de mágica.
    Text(
        "Vais receber " + horasDosLembretes(quantos, inicio, fim)
            .joinToString(", ") { "%02dh".format(it) } + ".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp),
    )

    aEscolher?.let { extremo ->
        EscolherHora(
            atual = if (extremo == Extremo.INICIO) inicio else fim,
            aoFechar = { aEscolher = null },
            aoEscolher = { h ->
                if (extremo == Extremo.INICIO) preferencias.definirJanela(h, fim)
                else preferencias.definirJanela(inicio, h)
                aEscolher = null
                Lembrete.agendar(
                    context, quantos,
                    preferencias.inicioDaJanela.value, preferencias.fimDaJanela.value,
                )
            },
        )
    }
}

private enum class Extremo { INICIO, FIM }

@Composable
private fun EscolherHora(atual: Int, aoFechar: () -> Unit, aoEscolher: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("A que horas?") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                (0..23).forEach { h ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { aoEscolher(h) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = h == atual, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text("%02d:00".format(h))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

/**
 * Modo desenvolvedor.
 *
 * Fica no fim das Definições, sem destaque, porque não é para toda a gente.
 * O que faz está escrito por baixo do interruptor: não vale a pena esconder
 * a explicação de quem foi capaz de chegar aqui.
 */
@Composable
private fun SeccaoDesenvolvedor(preferencias: Preferencias) {
    val ligado by preferencias.modoDesenvolvedor.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Modo desenvolvedor", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Mostra de que fonte veio cada definição e permite rever "
                    + "palavras que ainda não estão vencidas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = ligado,
            onCheckedChange = preferencias::definirModoDesenvolvedor,
        )
    }
}

@Composable
private fun Seccao(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun Linha(etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
