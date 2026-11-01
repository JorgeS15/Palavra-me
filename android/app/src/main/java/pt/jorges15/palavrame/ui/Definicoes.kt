package pt.jorges15.palavrame.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.ResultadoImportacao
import pt.jorges15.palavrame.data.Backup
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.Tema
import pt.jorges15.palavrame.jogo.Lembrete
import pt.jorges15.palavrame.jogo.horasDosLembretes
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo

/**
 * Definições: tema, lembretes, cópia da coleção, contributos e a informação
 * sobre a app. Cada secção é um grupo em cartão, com o título em destaque.
 */
@Composable
fun EcraDefinicoes(
    preferencias: Preferencias,
    aoAbrirLicencas: () -> Unit,
    @Suppress("UNUSED_PARAMETER") aoVoltar: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PalavrameApp
    val ambito = rememberCoroutineScope()
    val tema by preferencias.tema.collectAsState()

    var mensagemBackup by remember { mutableStateOf<String?>(null) }

    val exportador = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destino ->
        if (destino == null) return@rememberLauncherForActivityResult
        ambito.launch {
            val palavras = app.utilizador.palavras().todasAgora()
            val progresso = app.utilizador.progresso().atual()
            mensagemBackup = when (
                val r = Backup.exportar(
                    context, destino, palavras, progresso, BuildConfig.VERSION_NAME
                )
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
                val r = Backup.importar(
                    context, origem,
                    app.utilizador.palavras(), app.utilizador.progresso(),
                )
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

    // Submissão de palavras em falta, sem rede: a app entrega a lista ao
    // email (é o email que envia; a app continua sem permissão de Internet).
    var mensagemFalta by remember { mutableStateOf<String?>(null) }
    var mostrarSubmissao by remember { mutableStateOf(false) }

    Scaffold(containerColor = Cores.bg) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        ) {
            Text("Definições", style = Estilo.tituloEcra, color = Cores.ink)

            Seccao("Tema")
            // As três opções numa linha. "Como o telemóvel" encurta-se para
            // "Sistema": não cabia, e ao lado de "Claro" e "Escuro" percebe-se.
            Segmentado(
                Tema.entries.map { if (it == Tema.SISTEMA) "Sistema" else it.etiqueta },
                selecionado = Tema.entries.indexOf(tema),
                aoEscolher = { preferencias.definirTema(Tema.entries[it]) },
                icones = Tema.entries.map {
                    when (it) {
                        Tema.SISTEMA -> Icons.Rounded.Contrast
                        Tema.CLARO -> Icons.Rounded.LightMode
                        Tema.ESCURO -> Icons.Rounded.DarkMode
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Seccao("Rever")
            GrupoCartao { SeccaoJogo(preferencias) }

            Seccao("As minhas palavras")
            GrupoCartao {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Faz uma cópia de segurança das tuas palavras, para não as "
                            + "perderes se mudares de telemóvel.",
                        style = Estilo.ajuda,
                        color = Cores.ink2,
                    )
                    Spacer(Modifier.height(14.dp))
                    // Um por linha: lado a lado, num telemóvel estreito ou com a
                    // letra aumentada, o texto dos botões ficava cortado.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotaoContornado(
                            "Exportar cópia",
                            { exportador.launch(Backup.nomeSugerido()) },
                            icone = Icons.Rounded.Upload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BotaoContornado(
                            "Importar cópia",
                            // Aceita qualquer tipo: há gestores de ficheiros que
                            // não atribuem application/json a um .json, e nesses
                            // o seletor mostrava a cópia a cinzento.
                            { importador.launch(arrayOf("*/*")) },
                            icone = Icons.Rounded.Download,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    mensagemBackup?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = Estilo.ajuda, color = Cores.ink)
                    }
                }
            }

            Seccao("Contribuir")
            GrupoCartao {
                LinhaGrupo(
                    "Submeter por mail",
                    subtitulo = "Contribui com novas palavras para o dicionário.",
                    aoTocar = { mostrarSubmissao = true },
                    direita = { Chevron() },
                )
                mensagemFalta?.let {
                    Text(
                        it, style = Estilo.ajuda, color = Cores.err,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    )
                }
            }

            Seccao("Sobre")
            GrupoCartao {
                LinhaGrupo(
                    "Versão da app",
                    direita = {
                        Text(BuildConfig.VERSION_NAME, style = Estilo.texto, color = Cores.ink2)
                    },
                )
                DivisoriaGrupo()
                // O dicionário e as licenças têm um ecrã só deles: são
                // informação que se consulta uma vez.
                LinhaGrupo(
                    "Dicionário e licenças",
                    aoTocar = aoAbrirLicencas,
                    direita = { Chevron() },
                )
                DivisoriaGrupo()
                LinhaGrupo(
                    "Contactar por email",
                    subtitulo = "Sugestões, problemas ou ajuda.",
                    aoTocar = {
                        // Espaço para a mensagem em cima; a versão da app e a
                        // data do dicionário vão no fundo, para saber de que
                        // build veio o contacto (como na submissão de palavras).
                        val intent = intentEmail(
                            para = CONTACTO_EMAIL,
                            assunto = "Palavra-me: contacto",
                            corpo = "\n\n" + linhaVersao(
                                BuildConfig.VERSION_NAME, app.dicionario?.construidoEm,
                            ),
                        )
                        mensagem = try {
                            context.startActivity(intent); null
                        } catch (e: Exception) {
                            "Não encontrei uma app de email neste telemóvel."
                        }
                    },
                    direita = { Chevron() },
                )
                mensagem?.let {
                    Text(
                        it, style = Estilo.ajuda, color = Cores.err,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    )
                }
            }

            Seccao("Avançado")
            GrupoCartao { SeccaoDesenvolvedor(preferencias) }
        }
    }

    if (mostrarSubmissao) {
        DialogoSubmeterFaltas(
            app = app,
            aoFechar = { mostrarSubmissao = false },
            aoEnviar = { escolhidas ->
                val intent = intentEmail(
                    para = EMAIL_SUBMISSAO,
                    assunto = assuntoSubmissao(escolhidas.size),
                    corpo = corpoDeFalta(
                        escolhidas, BuildConfig.VERSION_NAME,
                        app.dicionario?.construidoEm,
                    ),
                )
                try {
                    context.startActivity(intent)
                    mensagemFalta = null
                } catch (e: Exception) {
                    mensagemFalta = "Não encontrei uma app de email neste telemóvel."
                }
            },
        )
    }
}

/**
 * Dicionário e licenças.
 *
 * Vive fora das Definições porque é informação de consulta, não de escolha.
 * Mas tem de existir e ser fácil de encontrar: a base deriva de fontes com
 * atribuição obrigatória, e este ecrã é o que cumpre essa obrigação (plano, F5).
 */
@Composable
fun EcraLicencas(aoVoltar: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PalavrameApp
    val ambito = rememberCoroutineScope()
    var aInstalar by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var haNovoNoApk by remember { mutableStateOf(app.temDicionarioEmpacotado()) }

    Scaffold(containerColor = Cores.bg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BarraVoltar(aoVoltar)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            ) {
                Text("Dicionário e licenças", style = Estilo.tituloEcra, color = Cores.ink)

                Seccao("Dicionário")
                val d = app.dicionario
                GrupoCartao {
                    Linha("Construído em", d?.construidoEm?.take(10) ?: "-")
                    DivisoriaGrupo()
                    Linha("Lemas", d?.totalLemas?.let { "%,d".format(it) } ?: "-")
                    DivisoriaGrupo()
                    Linha("Aceções", d?.totalAcecoes?.let { "%,d".format(it) } ?: "-")
                    DivisoriaGrupo()
                    Linha("Formas flexionadas", d?.totalFormas?.let { "%,d".format(it) } ?: "-")
                    DivisoriaGrupo()
                    Linha("Exemplos", d?.totalExemplos?.let { "%,d".format(it) } ?: "-")
                }

                if (haNovoNoApk) {
                    Spacer(Modifier.height(12.dp))
                    BotaoPrincipal(
                        if (aInstalar) "A instalar…" else "Instalar o dicionário da app",
                        {
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
                        ativo = !aInstalar,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                mensagem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Estilo.ajuda, color = Cores.ink)
                }

                Seccao("Fontes")
                val fontes = app.dicionario?.fontes().orEmpty()
                if (fontes.isNotEmpty()) {
                    GrupoCartao {
                        fontes.forEachIndexed { i, fonte ->
                            if (i > 0) DivisoriaGrupo()
                            LinhaGrupo(fonte.nome, subtitulo = fonte.licenca)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "A base de dados deriva destas fontes e é distribuída sob "
                        + "CC BY-SA, como elas exigem.",
                    style = Estilo.ajuda,
                    color = Cores.ink2,
                )

                // As letras vão dentro da app, sem descarregar nada. A licença
                // OFL pede que o texto dela as acompanhe: está nos assets
                // (licencas/OFL-*.txt) e no NOTICE do repositório.
                Seccao("Tipografia")
                GrupoCartao {
                    LinhaGrupo("Literata", subtitulo = "SIL Open Font License 1.1")
                    DivisoriaGrupo()
                    LinhaGrupo("Instrument Sans", subtitulo = "SIL Open Font License 1.1")
                }
            }
        }
    }
}

/**
 * Os lembretes: ligar, quantos por dia, e entre que horas.
 *
 * A permissão de notificações é pedida **aqui**, no momento em que se liga o
 * modo, e não ao arrancar a app. Quem nunca ligar os lembretes nunca vê o
 * pedido: é a diferença entre uma app que pergunta quando precisa e uma que
 * pergunta por precaução.
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

    LinhaGrupo(
        "Lembretes",
        subtitulo = "Uma palavra da tua coleção, para responderes numa pausa. "
            + "Se não houver nada a rever, não te incomoda.",
        direita = {
            SwitchPalavra(ligado) { querLigar ->
                if (!querLigar) {
                    preferencias.definirJogoLigado(false)
                    Lembrete.cancelar(context)
                } else if (Lembrete.temPermissao(context)) {
                    preferencias.definirJogoLigado(true)
                    reagendar()
                } else {
                    pedido.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
    )

    if (!ligado) return

    DivisoriaGrupo()
    LinhaGrupo(
        "Por dia",
        direita = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotaoPasso(Icons.Rounded.Remove, "Menos um", quantos > 1) {
                    preferencias.definirQuantosPorDia(quantos - 1); reagendar()
                }
                Text(
                    quantos.toString(),
                    style = Estilo.numeroEstat.copy(fontSize = 20.sp),
                    color = Cores.ink,
                    modifier = Modifier.widthIn(min = 36.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                BotaoPasso(Icons.Rounded.Add, "Mais um", quantos < Preferencias.MAX_POR_DIA) {
                    preferencias.definirQuantosPorDia(quantos + 1); reagendar()
                }
            }
        },
    )
    DivisoriaGrupo()
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Entre as", style = Estilo.controlo.copy(fontSize = 16.sp), color = Cores.ink)
            Spacer(Modifier.width(8.dp))
            ChipHora(inicio) { aEscolher = Extremo.INICIO }
            Spacer(Modifier.width(8.dp))
            Text("e as", style = Estilo.controlo.copy(fontSize = 16.sp), color = Cores.ink)
            Spacer(Modifier.width(8.dp))
            ChipHora(fim) { aEscolher = Extremo.FIM }
        }
        Spacer(Modifier.height(10.dp))
        // Mostrar as horas calculadas evita a pergunta óbvia ("3 entre as 8h e
        // as 22h dá a que horas?") e torna a regra visível em vez de mágica.
        Text(
            "Vais receber " + horasDosLembretes(quantos, inicio, fim)
                .joinToString(", ") { "%02dh".format(it) } + ".",
            style = Estilo.ajuda,
            color = Cores.ink2,
        )
    }
    DivisoriaGrupo()
    // Empurrão à bateria. Em muitos telemóveis (Xiaomi, Samsung, Huawei...) o
    // sistema mata o trabalho em segundo plano e os lembretes não chegam. Não
    // se pede a permissão de isenção, que é restrita na Play Store; abrem-se
    // as definições da app, onde a bateria se põe em "Sem restrições".
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)) {
        Text(
            "Não recebes os lembretes? O telemóvel pode estar a poupar bateria à "
                + "custa da app. Nas definições da app, põe a bateria em "
                + "\"Sem restrições\".",
            style = Estilo.ajuda,
            color = Cores.ink2,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
        ) { Text("Abrir definições da app", style = Estilo.controloForte) }
    }

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

/** Um botão do contador: 38 de lado, fundo afundado. */
@Composable
private fun BotaoPasso(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    descricao: String,
    ativo: Boolean,
    aoTocar: () -> Unit,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.sunk)
            .clickable(enabled = ativo, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, descricao, tint = if (ativo) Cores.ink else Cores.ink3, modifier = Modifier.size(20.dp))
    }
}

/** A hora de um extremo da janela, num chip afundado que abre o seletor. */
@Composable
private fun ChipHora(hora: Int, aoTocar: () -> Unit) {
    Text(
        "%02d:00".format(hora),
        style = Estilo.controloForte,
        color = Cores.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Cores.sunk)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private enum class Extremo { INICIO, FIM }

@Composable
private fun EscolherHora(atual: Int, aoFechar: () -> Unit, aoEscolher: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = { Text("A que horas?", style = Estilo.grupoTitulo, color = Cores.ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                (0..23).forEach { h ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { aoEscolher(h) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = h == atual, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Cores.accent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("%02d:00".format(h), style = Estilo.texto, color = Cores.ink)
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
    LinhaGrupo(
        "Modo desenvolvedor",
        subtitulo = "Mostra de que fonte veio cada definição e permite rever "
            + "palavras que ainda não estão vencidas.",
        direita = { SwitchPalavra(ligado, preferencias::definirModoDesenvolvedor) },
    )
}

/** O título de uma secção: sobretítulo em destaque, por cima do grupo. */
@Composable
private fun Seccao(titulo: String) {
    Overline(
        titulo,
        cor = Cores.accentInk,
        modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun Linha(etiqueta: String, valor: String) {
    LinhaGrupo(
        etiqueta,
        direita = { Text(valor, style = Estilo.texto, color = Cores.ink2) },
    )
}

// --- submissão de palavras em falta ---------------------------------------

/**
 * Escolher quais palavras em falta submeter.
 *
 * Abre com todas marcadas — o caso comum é enviá-las todas — e deixa desmarcar
 * as que não se quer mandar (um registo a meio, uma palavra que afinal não
 * interessa). A submissão vai por email.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoSubmeterFaltas(
    app: PalavrameApp,
    aoFechar: () -> Unit,
    aoEnviar: (List<PalavraEmFalta>) -> Unit,
) {
    // null = ainda a procurar; lista vazia = não há nada em falta.
    var faltas by remember { mutableStateOf<List<PalavraEmFalta>?>(null) }
    val marcadas = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        val lista = palavrasEmFalta(app.dicionario, app.utilizador)
        lista.forEach { marcadas[it.lemma] = true }
        faltas = lista
    }
    val escolhidas = faltas.orEmpty().filter { marcadas[it.lemma] == true }

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = { Text("Submeter palavras em falta", style = Estilo.grupoTitulo, color = Cores.ink) },
        text = {
            val lista = faltas
            when {
                lista == null -> Text("A procurar…")
                lista.isEmpty() ->
                    Text("Não tens palavras em falta. Está tudo no dicionário.")
                else -> Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    lista.forEach { f ->
                        val marcada = marcadas[f.lemma] ?: true
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { marcadas[f.lemma] = !marcada }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = marcada,
                                onCheckedChange = { marcadas[f.lemma] = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.lemma, style = Estilo.linhaLema.copy(fontSize = 17.sp), color = Cores.ink)
                                Text(
                                    if (f.tipo == TipoFalta.NAO_ENCONTRADA)
                                        "não está no dicionário"
                                    else "sem significado",
                                    style = Estilo.meta,
                                    color = Cores.ink3,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!faltas.isNullOrEmpty()) {
                TextButton(
                    enabled = escolhidas.isNotEmpty(),
                    onClick = { aoEnviar(escolhidas); aoFechar() },
                ) { Text("Enviar por email") }
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

// Para onde vão as submissões e como as identificar na caixa de entrada. É o
// email do Jorge, não o de contacto público da app: chega-lhe direto.
private const val EMAIL_SUBMISSAO = "jorgea15santos@gmail.com"

// Contacto geral (sugestões, problemas, ajuda). O mesmo email das submissões.
private const val CONTACTO_EMAIL = "jorgea15santos@gmail.com"

// O assunto, o corpo e o CSV da submissão vivem em `Submissao.kt`, sem
// Android, para se poderem testar.

/**
 * Um email pronto a enviar, para abrir na app de email da pessoa.
 *
 * O destinatário, o assunto e o corpo vão em dois sítios: no endereço
 * `mailto:` (que todas as apps de email leem) e nos extras do intent (que a
 * maioria prefere). Algumas apps ignoram os extras e abriam o email vazio;
 * outras ignoram o endereço. Com os dois, todas recebem tudo.
 */
private fun intentEmail(para: String, assunto: String, corpo: String): Intent {
    val endereco = "mailto:" + Uri.encode(para, "@") +
        "?subject=" + Uri.encode(assunto) +
        "&body=" + Uri.encode(corpo)
    return Intent(Intent.ACTION_SENDTO, Uri.parse(endereco)).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(para))
        putExtra(Intent.EXTRA_SUBJECT, assunto)
        putExtra(Intent.EXTRA_TEXT, corpo)
    }
}

