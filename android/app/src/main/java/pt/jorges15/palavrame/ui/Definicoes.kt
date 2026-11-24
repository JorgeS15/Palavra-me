package pt.jorges15.palavrame.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.animation.animateContentSize
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.ResultadoImportacao
import pt.jorges15.palavrame.data.Anki
import pt.jorges15.palavrame.data.Backup
import pt.jorges15.palavrame.data.CorrecaoGuardada
import pt.jorges15.palavrame.data.Lingua
import pt.jorges15.palavrame.data.livrosCompletos
import pt.jorges15.palavrame.data.Preferencias
import pt.jorges15.palavrame.data.Progresso
import pt.jorges15.palavrame.data.LetraId
import pt.jorges15.palavrame.data.PaletaId
import pt.jorges15.palavrame.data.Tema
import pt.jorges15.palavrame.ui.theme.CoresPalavra
import pt.jorges15.palavrame.ui.theme.LocalModoEscuro
import pt.jorges15.palavrame.ui.theme.Paletas
import pt.jorges15.palavrame.ui.theme.estilosDe
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
    // Vindo da nota de novidades ("Ver o Aspeto"), abre já a secção.
    aspetoAberto: Boolean = false,
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
            val livros = livrosCompletos(
                app.utilizador.livros().fichasAgora(), app.utilizador.livros().leiturasAgora(),
            ).values.toList()
            mensagemBackup = when (
                val r = Backup.exportar(
                    context, destino, palavras, progresso, BuildConfig.VERSION_NAME, livros,
                    app.utilizador.correcoes().todas(),
                )
            ) {
                is Backup.Resultado.Feito -> "Exportadas ${r.novas} palavras."
                is Backup.Resultado.Erro -> "Falhou: ${r.motivo}"
            }
        }
    }

    // Exportar para o Anki (1.31): um ficheiro de texto que o Anki importa
    // sozinho. Ver `data/Anki.kt`.
    val exportadorAnki = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { destino ->
        if (destino == null) return@rememberLauncherForActivityResult
        ambito.launch {
            val palavras = app.utilizador.palavras().todasAgora()
            mensagemBackup = when (val r = Anki.exportar(context, destino, palavras, app::dicionario)) {
                is Backup.Resultado.Feito -> buildString {
                    append(if (r.novas == 1) "Exportada 1 palavra" else "Exportadas ${r.novas} palavras")
                    append(" para o Anki. No Anki: Ficheiro, Importar.")
                    if (r.repetidas > 0) {
                        append(
                            if (r.repetidas == 1) " 1 ficou de fora, por não ter significado."
                            else " ${r.repetidas} ficaram de fora, por não terem significado."
                        )
                    }
                }
                is Backup.Resultado.Erro -> "Falhou: ${r.motivo}"
            }
        }
    }

    // A pergunta da importação: o progresso daqui, o da cópia e a resposta.
    var escolhaProgresso by remember {
        mutableStateOf<Triple<Progresso, Progresso, CompletableDeferred<Boolean>>?>(null)
    }
    escolhaProgresso?.let { (local, daCopia, resposta) ->
        DialogoProgresso(local, daCopia, aoEscolher = { resposta.complete(it) })
    }

    val importador = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { origem ->
        if (origem == null) return@rememberLauncherForActivityResult
        ambito.launch {
            mensagemBackup = when (
                val r = Backup.importar(context, origem, app.utilizador) { local, daCopia ->
                    // Os dois progressos em uso: pergunta-se qual fica.
                    val resposta = CompletableDeferred<Boolean>()
                    escolhaProgresso = Triple(local, daCopia, resposta)
                    resposta.await().also { escolhaProgresso = null }
                }
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

    var verTutorial by remember { mutableStateOf(false) }
    if (verTutorial) Tutorial(aoFechar = { verTutorial = false })

    // Submissão de palavras em falta, sem rede: a app entrega a lista ao
    // email (é o email que envia; a app continua sem permissão de Internet).
    var mensagemFalta by remember { mutableStateOf<String?>(null) }
    var mostrarSubmissao by remember { mutableStateOf(false) }
    // Quantas sugestões há por enviar (1.30); recontam-se ao fechar a lista.
    var porEnviar by remember { mutableStateOf(0) }
    LaunchedEffect(mostrarSubmissao) {
        if (!mostrarSubmissao) {
            porEnviar = palavrasEmFalta(app::dicionario, app.utilizador).size +
                app.utilizador.correcoes().porEnviar().size
        }
    }

    Scaffold(containerColor = Cores.bg) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        ) {
            Text("Definições", style = Estilo.tituloEcra, color = Cores.ink)

            Seccao("Aspeto")
            SeccaoAspeto(preferencias, tema, abertaDeInicio = aspetoAberto)

            Seccao("Rever")
            GrupoCartao { SeccaoJogo(preferencias) }
            Spacer(Modifier.height(12.dp))
            GrupoCartao { LinhaEscrever(preferencias) }

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
                        // Para rever no Anki ou no AnkiDroid. Exportar outra vez
                        // atualiza as notas que já lá estão, sem as repetir.
                        BotaoContornado(
                            "Exportar para Anki",
                            { exportadorAnki.launch(Anki.nomeSugerido()) },
                            icone = Icons.Rounded.Upload,
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
                // Tudo o que se guardou em "Corrigir ou acrescentar", e as
                // palavras registadas que o dicionário não tem, à espera de ir
                // por email (1.30; antes, "Submeter por mail", só as palavras).
                LinhaGrupo(
                    "Submeter ao dicionário",
                    subtitulo = if (porEnviar == 0)
                        "Palavras novas, sentidos e correções que guardares ficam aqui."
                    else if (porEnviar == 1) "1 sugestão por submeter."
                    else "$porEnviar sugestões por submeter.",
                    aoTocar = { mostrarSubmissao = true },
                    direita = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (porEnviar > 0) {
                                Pilula("$porEnviar", fundo = Cores.accentSoft, cor = Cores.accentInk)
                                Spacer(Modifier.width(8.dp))
                            }
                            Chevron()
                        }
                    },
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
                // O tutorial da primeira abertura, para rever quando se quiser.
                LinhaGrupo(
                    "Como funciona",
                    subtitulo = "Procurar, guardar, corrigir, os livros e a revisão, em cinco ecrãs.",
                    aoTocar = { verTutorial = true },
                    direita = { Chevron() },
                )
                DivisoriaGrupo()
                // Abre a página da app na Play Store (como o pedido à 5.ª
                // palavra). Quem avalia por aqui deixa de receber esse pedido.
                LinhaGrupo(
                    "Avaliar na Play Store",
                    subtitulo = "Ajuda outros leitores a encontrar a app.",
                    aoTocar = {
                        abrirNaPlayStore(context)
                        preferencias.terminarPedidosDeAvaliacao()
                    },
                    direita = { Chevron() },
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
                // A versão no fim: é informação, não uma ação.
                DivisoriaGrupo()
                LinhaGrupo(
                    "Versão da app",
                    direita = {
                        Text(BuildConfig.VERSION_NAME, style = Estilo.texto, color = Cores.ink2)
                    },
                )
            }

            Seccao("Avançado")
            GrupoCartao { SeccaoDesenvolvedor(preferencias) }
        }
    }

    if (mostrarSubmissao) {
        DialogoSugestoes(
            app = app,
            aoFechar = { mostrarSubmissao = false },
            aoEnviar = { faltas, correcoes ->
                if (enviarSugestoes(context, faltas, correcoes)) {
                    mensagemFalta = null
                    ambito.launch {
                        marcarSugestoesEnviadas(app.utilizador, faltas, correcoes)
                        porEnviar = palavrasEmFalta(app::dicionario, app.utilizador).size +
                            app.utilizador.correcoes().porEnviar().size
                    }
                } else {
                    mensagemFalta = "Não encontrei uma app de email neste telemóvel."
                }
            },
        )
    }
}

// --- aspeto --------------------------------------------------------------------

/**
 * Aspeto: o modo (Sistema, Claro, Escuro), as cores (cinco paletas) e a letra
 * (quatro). Combinam-se livremente e aplicam-se na hora, a toda a app e às
 * imagens de partilha, sem botão de guardar. Desenho em
 * `design_handoff_temas_letras/`.
 *
 * Responde a duas queixas de quem usa a app: o tom "amarelado" e a serifa.
 */
@Composable
private fun SeccaoAspeto(preferencias: Preferencias, tema: Tema, abertaDeInicio: Boolean) {
    val paleta by preferencias.paleta.collectAsState()
    val letra by preferencias.letra.collectAsState()
    val escuro = LocalModoEscuro.current
    val forma = RoundedCornerShape(20.dp)
    // Fechada por omissão: escolhe-se uma vez e raramente se volta cá, e
    // aberta ocupava o ecrã inteiro antes de tudo o resto. Fechada mostra o
    // que está escolhido, numa linha.
    var aberta by rememberSaveable { mutableStateOf(abertaDeInicio) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Cores.card)
            .border(1.dp, Cores.line, forma)
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { aberta = !aberta }
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(
                        if (tema == Tema.SISTEMA) "Sistema" else tema.etiqueta,
                        paleta.nome,
                        letra.nome,
                    ).joinToString(" · "),
                    style = Estilo.corpo.copy(fontSize = 16.sp),
                    color = Cores.ink,
                )
                Text(
                    "Modo, cores e letra",
                    style = Estilo.ajuda.copy(fontSize = 12.5.sp),
                    color = Cores.ink2,
                )
            }
            Icon(
                if (aberta) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (aberta) "Ocultar o aspeto" else "Mostrar o aspeto",
                tint = Cores.ink3,
            )
        }
        if (aberta) AspetoAberto(preferencias, tema, paleta, letra, escuro)
    }
}

@Composable
private fun AspetoAberto(
    preferencias: Preferencias,
    tema: Tema,
    paleta: PaletaId,
    letra: LetraId,
    escuro: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // O modo. "Como o telemóvel" encurta-se para "Sistema": não cabia, e
        // ao lado de "Claro" e "Escuro" percebe-se.
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

        Column {
            Text("Cores", style = Estilo.rotulo.copy(fontSize = 13.sp), color = Cores.ink2)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaletaId.entries.forEach { id ->
                    AmostraPaleta(
                        nome = id.nome,
                        // Cada amostra nas cores da sua paleta, no modo em uso.
                        p = Paletas.de(id, escuro),
                        ativa = id == paleta,
                        aoTocar = { preferencias.definirPaleta(id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Column {
            Text("Letra", style = Estilo.rotulo.copy(fontSize = 13.sp), color = Cores.ink2)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LetraId.entries.forEach { id ->
                    LinhaLetra(id, ativa = id == letra, aoTocar = { preferencias.definirLetra(id) })
                }
            }
        }

        Text(
            "As cores e a letra aplicam-se a toda a app e às imagens de partilha.",
            style = Estilo.ajuda.copy(fontSize = 12.5.sp, lineHeight = 1.45.em),
            color = Cores.ink2,
        )
    }
}

/** Uma paleta: "Aa" com o marcador e a barra de destaque, nas cores dela. */
@Composable
private fun AmostraPaleta(
    nome: String,
    p: CoresPalavra,
    ativa: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = aoTocar),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A ativa leva um anel de 2 dp na cor do texto, afastado 2 dp; a
        // inativa, o mesmo espaço sem anel (para não saltar de tamanho).
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (ativa) Modifier.border(2.dp, Cores.ink, RoundedCornerShape(16.dp))
                    else Modifier
                )
                .padding(2.dp)
        ) {
            val forma = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(forma)
                    .background(p.bg)
                    .then(if (ativa) Modifier else Modifier.border(1.dp, p.line, forma)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Aa",
                    style = TextStyle(
                        fontFamily = Estilo.leitura, fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = p.ink,
                    modifier = Modifier
                        .drawBehind {
                            drawRect(
                                p.mark,
                                topLeft = Offset(0f, size.height * 0.58f),
                                size = Size(size.width, size.height * 0.34f),
                            )
                        }
                        .padding(horizontal = 3.dp),
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            nome,
            style = Estilo.meta.copy(
                fontSize = 11.5.sp,
                fontWeight = if (ativa) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (ativa) Cores.ink else Cores.ink2,
            maxLines = 1,
        )
    }
}

/** Uma letra: "lôbrego" escrito nela, o nome, a descrição e o rádio. */
@Composable
private fun LinhaLetra(id: LetraId, ativa: Boolean, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (ativa) Cores.accentSoft else Color.Transparent)
            .border(1.5.dp, if (ativa) Cores.accent else Cores.line, forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "lôbrego",
            style = TextStyle(
                fontFamily = estilosDe(id).leitura, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em,
            ),
            color = Cores.ink,
            maxLines = 1,
            modifier = Modifier.width(100.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(id.nome, style = Estilo.controloForte.copy(fontSize = 14.5.sp), color = Cores.ink)
            Text(id.descricao, style = Estilo.ajuda.copy(fontSize = 12.5.sp), color = Cores.ink2)
        }
        Spacer(Modifier.width(8.dp))
        // O rádio: anel grosso de destaque quando ativo, fino quando não.
        Box(
            Modifier
                .size(20.dp)
                .border(
                    if (ativa) 6.dp else 2.dp,
                    if (ativa) Cores.accent else Cores.line,
                    CircleShape,
                )
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
fun EcraLicencas(preferencias: Preferencias, aoVoltar: () -> Unit) {
    val context = LocalContext.current
    val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
    val app = context.applicationContext as PalavrameApp
    val ambito = rememberCoroutineScope()
    var aInstalar by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var porInstalar by remember { mutableStateOf(app.linguasPorInstalar()) }
    var instaladas by remember { mutableStateOf(app.linguasInstaladas()) }

    // Um dicionário escolhido à mão (a língua sai do próprio ficheiro). Só no
    // Modo Desenvolvedor (1.30): os dois dicionários vêm dentro da app, e isto
    // serve para experimentar uma base nova sem compilar.
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        aInstalar = true
        ambito.launch {
            mensagem = when (val r = app.importarDicionario(uri)) {
                is ResultadoImportacao.Feito ->
                    "Instalado o dicionário de ${Lingua.nome(r.lingua).lowercase()}."
                is ResultadoImportacao.Erro -> "Falhou: ${r.motivo}"
            }
            aInstalar = false
            instaladas = app.linguasInstaladas()
            porInstalar = app.linguasPorInstalar()
        }
    }

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

                // Um bloco por língua instalada; o português sempre, mesmo vazio.
                for (lingua in (listOf(Lingua.PT) + instaladas).distinct()) {
                    Seccao(if (lingua == Lingua.PT) "Dicionário" else "Dicionário de ${Lingua.nome(lingua).lowercase()}")
                    val d = app.dicionario(lingua)
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
                }

                if (porInstalar.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    BotaoPrincipal(
                        if (aInstalar) "A instalar…" else "Instalar o dicionário da app",
                        {
                            aInstalar = true
                            ambito.launch {
                                val falhas = porInstalar.mapNotNull { l ->
                                    (app.instalarDicionarioEmpacotado(l) as? ResultadoImportacao.Erro)?.motivo
                                }
                                mensagem = if (falhas.isEmpty()) "Instalado. Fecha e volta a abrir a app."
                                    else "Falhou: ${falhas.first()}"
                                aInstalar = false
                                porInstalar = app.linguasPorInstalar()
                                instaladas = app.linguasInstaladas()
                            }
                        },
                        ativo = !aInstalar,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (desenvolvedor) {
                    Spacer(Modifier.height(12.dp))
                    BotaoContornado(
                        "Instalar um ficheiro de dicionário",
                        { if (!aInstalar) seletor.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                mensagem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Estilo.ajuda, color = Cores.ink)
                }

                // As fontes de cada dicionário instalado, cada um no seu grupo
                // (1.30: o inglês tem as dele, o Wiktionary e o Leipzig ingleses).
                for (lingua in instaladas) {
                    val fontes = app.dicionario(lingua)?.fontes().orEmpty()
                    if (fontes.isEmpty()) continue
                    Seccao(
                        if (instaladas.size == 1) "Fontes"
                        else "Fontes: ${Lingua.nome(lingua).lowercase()}"
                    )
                    GrupoCartao {
                        fontes.forEachIndexed { i, fonte ->
                            if (i > 0) DivisoriaGrupo()
                            LinhaGrupo(fonte.nome, subtitulo = fonte.licenca)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Cada dicionário deriva das suas fontes e é distribuído sob "
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
                    DivisoriaGrupo()
                    LinhaGrupo("Figtree", subtitulo = "SIL Open Font License 1.1")
                    DivisoriaGrupo()
                    LinhaGrupo("Atkinson Hyperlegible Next", subtitulo = "SIL Open Font License 1.1")
                }
            }
        }
    }
}

/**
 * "Escrever a palavra" (1.22): nas palavras que já se acertam, metade das
 * perguntas pede para escrever a palavra a partir da definição. Vale para a
 * revisão toda, com ou sem lembretes.
 */
@Composable
private fun LinhaEscrever(preferencias: Preferencias) {
    val ligado by preferencias.escreverAPalavra.collectAsState()
    LinhaGrupo(
        "Escrever a palavra",
        subtitulo = "Nas palavras que já acertas, às vezes mostra-se a definição "
            + "e escreves tu a palavra. É mais difícil, e ajuda a fixá-la.",
        direita = { SwitchPalavra(ligado) { preferencias.definirEscreverAPalavra(it) } },
    )
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
    val comPalavra by preferencias.palavraNasNotificacoes.collectAsState()
    var aEscolher by remember { mutableStateOf<Extremo?>(null) }

    fun reagendar() = Lembrete.agendar(context, quantos, inicio, fim)

    // A autorização pode mudar fora da app (nas definições do sistema), por
    // isso confere-se cada vez que se volta a este ecrã.
    var notificacoesAtivas by remember { mutableStateOf(Lembrete.notificacoesAtivas(context)) }
    var recusada by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificacoesAtivas = Lembrete.notificacoesAtivas(context)
        if (notificacoesAtivas) recusada = false
    }

    val pedido = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        // Sem permissão não se liga: um interruptor ligado que não notifica
        // seria uma mentira na interface. Mas diz-se porquê: antes ficava só
        // desligado, e numa segunda recusa o Android nem volta a perguntar.
        preferencias.definirJogoLigado(concedida)
        recusada = !concedida
        notificacoesAtivas = Lembrete.notificacoesAtivas(context)
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

    // Recusada agora, ou retirada depois no sistema com os lembretes ligados:
    // os lembretes não chegam, e diz-se, com o atalho para resolver.
    val aviso = when {
        // Entre parênteses: num ramo do `when`, uma linha começada por "+"
        // seria lida como uma instrução nova e não como a continuação.
        recusada && !ligado -> (
            "Sem autorização para notificações, os lembretes não podem chegar. "
                + "Podes dá-la nas definições da app, ou rever à mão no ecrã inicial."
            )
        ligado && !notificacoesAtivas -> (
            "As notificações da Palavra-me estão desligadas no telemóvel, por isso "
                + "os lembretes não estão a chegar."
            )
        else -> null
    }
    if (aviso != null) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp)) {
            Text(aviso, style = Estilo.ajuda, color = Cores.err)
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Abrir definições das notificações", style = Estilo.controloForte) }
        }
    }

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
    LinhaGrupo(
        "Mostrar a palavra",
        subtitulo = if (comPalavra) {
            "\"Ainda te lembras de ...?\" No ecrã bloqueado, a palavra fica sempre escondida."
        } else {
            "\"${Lembrete.TITULO_SEM_PALAVRA}\". A palavra só aparece ao abrir."
        },
        direita = { SwitchPalavra(comPalavra) { preferencias.definirPalavraNasNotificacoes(it) } },
    )
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
private fun DialogoSugestoes(
    app: PalavrameApp,
    aoFechar: () -> Unit,
    aoEnviar: (List<PalavraEmFalta>, List<CorrecaoGuardada>) -> Unit,
) {
    // null = ainda a procurar; listas vazias = não há nada por enviar.
    var faltas by remember { mutableStateOf<List<PalavraEmFalta>?>(null) }
    var correcoes by remember { mutableStateOf<List<CorrecaoGuardada>>(emptyList()) }
    val marcadas = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        val lista = palavrasEmFalta(app::dicionario, app.utilizador)
        val corr = app.utilizador.correcoes().porEnviar()
        lista.forEach { marcadas["f:" + it.lingua + ":" + it.lemma] = true }
        corr.forEach { marcadas["c:" + it.id] = true }
        correcoes = corr
        faltas = lista
    }
    val faltasEscolhidas = faltas.orEmpty().filter { marcadas["f:" + it.lingua + ":" + it.lemma] == true }
    val correcoesEscolhidas = correcoes.filter { marcadas["c:" + it.id] == true }
    val vazio = faltas != null && faltas.orEmpty().isEmpty() && correcoes.isEmpty()

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = { Text("Submeter ao dicionário", style = Estilo.grupoTitulo, color = Cores.ink) },
        text = {
            val lista = faltas
            when {
                lista == null -> Text("A procurar…")
                vazio -> Text(
                    "Não tens nada por submeter. As palavras que o dicionário não tem, "
                        + "e o que guardares em \"Corrigir ou acrescentar\", aparecem aqui."
                )
                else -> Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                ) {
                    lista.forEach { f ->
                        LinhaSugestao(
                            chave = "f:" + f.lingua + ":" + f.lemma,
                            marcadas = marcadas,
                            titulo = if (f.lingua == Lingua.PT) f.lemma
                                else f.lemma + " · " + Lingua.sigla(f.lingua),
                            detalhe = rotuloFalta(f.tipo) +
                                (f.significadoProprio?.let { ": $it" } ?: ""),
                        )
                    }
                    correcoes.forEach { c ->
                        LinhaSugestao(
                            chave = "c:" + c.id,
                            marcadas = marcadas,
                            titulo = c.lemma,
                            detalhe = "corrige o significado ${c.acecaoOrd}: ${c.texto}",
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (haSugestoes(faltas, correcoes)) {
                TextButton(
                    enabled = faltasEscolhidas.isNotEmpty() || correcoesEscolhidas.isNotEmpty(),
                    onClick = { aoEnviar(faltasEscolhidas, correcoesEscolhidas); aoFechar() },
                ) { Text("Submeter por email") }
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}

private fun haSugestoes(faltas: List<PalavraEmFalta>?, correcoes: List<CorrecaoGuardada>) =
    !faltas.isNullOrEmpty() || correcoes.isNotEmpty()

@Composable
private fun LinhaSugestao(
    chave: String,
    marcadas: MutableMap<String, Boolean>,
    titulo: String,
    detalhe: String,
) {
    val marcada = marcadas[chave] ?: true
    Row(
        Modifier.fillMaxWidth()
            .clickable { marcadas[chave] = !marcada }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcada, onCheckedChange = { marcadas[chave] = it })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = Estilo.linhaLema.copy(fontSize = 17.sp), color = Cores.ink)
            Text(
                detalhe,
                style = Estilo.meta,
                color = Cores.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Para onde vão as submissões e como as identificar na caixa de entrada. É o
// email do Jorge, não o de contacto público da app: chega-lhe direto.
internal const val EMAIL_SUBMISSAO = "jorgea15santos@gmail.com"

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
internal fun intentEmail(para: String, assunto: String, corpo: String): Intent {
    val endereco = "mailto:" + Uri.encode(para, "@") +
        "?subject=" + Uri.encode(assunto) +
        "&body=" + Uri.encode(corpo)
    return Intent(Intent.ACTION_SENDTO, Uri.parse(endereco)).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(para))
        putExtra(Intent.EXTRA_SUBJECT, assunto)
        putExtra(Intent.EXTRA_TEXT, corpo)
    }
}

/**
 * Ao importar uma cópia com outro progresso: qual fica? As palavras juntam-se
 * sempre; o progresso (pontos, sequência, respostas) é um ou outro.
 */
@Composable
private fun DialogoProgresso(local: Progresso, daCopia: Progresso, aoEscolher: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { aoEscolher(false) },
        containerColor = Cores.card,
        title = { Text("Que progresso fica?", style = Estilo.grupoTitulo.copy(fontSize = 22.sp), color = Cores.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "As palavras da cópia juntam-se às tuas. O progresso das revisões "
                        + "é um ou outro: escolhe qual fica.",
                    style = Estilo.texto,
                    color = Cores.ink2,
                )
                LinhaProgresso("Neste telemóvel", local)
                LinhaProgresso("Na cópia", daCopia)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { aoEscolher(true) },
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Usar o da cópia", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = { aoEscolher(false) },
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Manter o deste", style = Estilo.controlo) }
        },
    )
}

@Composable
private fun LinhaProgresso(rotulo: String, p: Progresso) {
    Column {
        Text(rotulo, style = Estilo.rotulo.copy(fontSize = 13.sp), color = Cores.ink2)
        Text(resumoProgresso(p), style = Estilo.texto, color = Cores.ink)
    }
}

/** "2704 pontos · 266 respostas · sequência de 34 dias, até 1 de novembro". */
internal fun resumoProgresso(p: Progresso): String = buildList {
    add("${p.pontos} pontos")
    val respostas = p.acertos + p.erros
    add(if (respostas == 1) "1 resposta" else "$respostas respostas")
    if (p.sequencia > 0) {
        val ate = p.ultimoDia?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale.forLanguageTag("pt-PT")))
        add(
            (if (p.sequencia == 1) "sequência de 1 dia" else "sequência de ${p.sequencia} dias")
                + (ate?.let { ", até $it" } ?: "")
        )
    }
}.joinToString(" · ")
