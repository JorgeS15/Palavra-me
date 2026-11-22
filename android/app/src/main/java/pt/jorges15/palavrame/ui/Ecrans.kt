package pt.jorges15.palavrame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.jorges15.palavrame.data.*
import pt.jorges15.palavrame.jogo.LIMITE_MANUAL_POR_DIA
import pt.jorges15.palavrame.jogo.Niveis
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import java.time.LocalDate

@Composable
fun EcraPesquisa(
    vm: PesquisaViewModel,
    preferencias: Preferencias,
    aoAbrirColecao: () -> Unit,
    aoAbrirJogo: () -> Unit = {},
    /** Abre a coleção já no livro tocado na estante. */
    aoAbrirLivro: (String) -> Unit = { aoAbrirColecao() },
    /**
     * Não nulo quando a palavra aberta veio de outro ecrã (a Coleção): o
     * "retroceder" leva de volta para lá em vez de ir para o início.
     */
    aoSairParaOrigem: (() -> Unit)? = null,
) {
    val estado by vm.estado.collectAsState()
    val livros by vm.livrosUsados.collectAsState(initial = emptyList())
    val livroEmCurso by vm.livroEmCurso.collectAsState()
    val guardados by vm.lemasGuardados.collectAsState(initial = emptySet())
    // Do ViewModel, já carregada: voltar a este ecrã não pisca vazio (ver
    // `PesquisaViewModel.colecao`). Nula só antes da primeira leitura.
    val carregadas by vm.colecao.collectAsState()
    val palavras = carregadas.orEmpty()
    val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
    val porRever by vm.porRever.collectAsState(initial = 0)
    val cota by preferencias.cotaManual.collectAsState()
    // Quantas se podem ainda rever à mão hoje. No Modo Desenvolvedor não há
    // limite (é para testar perguntas).
    val restantesHoje = if (desenvolvedor) Int.MAX_VALUE else cota.restantes(LocalDate.now())
    val progresso by vm.progresso.collectAsState(initial = null)
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val fraseSugerida by vm.fraseSugerida.collectAsState()
    // Guarda-se o lema (não a Entrada) e em `rememberSaveable`: a folha de
    // registo aberta sobrevive a rodar o telemóvel, com o que já lá escreveste.
    var lemaARegistar by rememberSaveable { mutableStateOf<String?>(null) }
    // Liga o campo do significado próprio na folha, para palavras que não
    // estão no dicionário ou que lá estão sem definição.
    var pedirSignificadoNoRegisto by rememberSaveable { mutableStateOf(false) }
    val estadoSnackbar = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    // O marco (5, 10, 20, 50 ou 100 palavras) a pedir avaliação agora, ou nulo.
    var marcoAvaliacao by rememberSaveable { mutableStateOf<Int?>(null) }
    // A palavra a partilhar como imagem, com o ecrã de partilha aberto.
    var aPartilhar by remember { mutableStateOf<DadosPartilha?>(null) }

    // Palavra nova: se chegou à 5.ª ou à 50.ª, pede-se uma avaliação, uma vez
    // por marco. Marca-se logo como pedida, aceite ou não: perguntar outra vez
    // seria insistir.
    fun talvezPedirAvaliacao() {
        preferencias.avaliacaoPorPedir(palavras.size + 1)?.let { marco ->
            preferencias.marcarAvaliacaoPedida(marco)
            marcoAvaliacao = marco
        }
    }

    // Ao abrir uma palavra (por pesquisa, sugestão, candidato ou sinónimo)
    // fecha-se o teclado: a definição não deve ser vista com o teclado por
    // cima, e com ele aberto o primeiro "retroceder" fechava só o teclado.
    LaunchedEffect(estado.entrada?.lemmaId) {
        if (estado.entrada != null) focus.clearFocus()
    }

    // Da palavra: anda palavra a palavra pelo histórico; sem histórico, volta
    // à lista de candidatos se a havia, senão ao início. Da "não encontrada":
    // volta ao campo com o termo escrito, para o corrigir. A meio de escrever:
    // limpa. Só no início vazio é que o "retroceder" do sistema sai da app.
    //
    // Se a palavra foi aberta a partir da Coleção, acabado o histórico volta-se
    // à Coleção (`aoSairParaOrigem`), e não ao início: é de lá que se veio.
    val voltar: () -> Unit = {
        when {
            estado.entrada != null && vm.voltarEntrada() -> Unit
            estado.entrada != null && estado.candidatos.size > 1 -> vm.fecharEntrada()
            aoSairParaOrigem != null &&
                (estado.entrada != null || estado.candidatos.isNotEmpty() || estado.procurou) ->
                aoSairParaOrigem()
            estado.entrada != null -> vm.limpar()
            estado.candidatos.isNotEmpty() -> vm.limpar()
            estado.procurou -> vm.escrever(estado.texto)
            else -> vm.limpar()
        }
    }
    BackHandler(
        enabled = estado.entrada != null || estado.texto.isNotEmpty() || estado.procurou,
        onBack = voltar,
    )

    Scaffold(
        containerColor = Cores.bg,
        snackbarHost = { AnfitriaoSnackbar(estadoSnackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val entrada = estado.entrada
            when {
                entrada != null -> VistaEntrada(
                    entrada,
                    guardada = palavras.firstOrNull { it.lemma == entrada.lemma },
                    desenvolvedor = desenvolvedor,
                    aoVoltar = voltar,
                    aoRegistar = {
                        lemaARegistar = entrada.lemma
                        // Sem aceções: pede-se o significado ao registar.
                        pedirSignificadoNoRegisto = entrada.acecoes.isEmpty()
                    },
                    aoPartilhar = {
                        aPartilhar = dadosPartilha(
                            entrada, palavras.firstOrNull { it.lemma == entrada.lemma },
                        )
                    },
                    aoSeguir = { vm.procurar(it) },
                )

                estado.candidatos.isNotEmpty() ->
                    ListaCandidatos(estado.candidatos, aoVoltar = voltar, aoEscolher = vm::abrir)

                estado.procurou -> {
                    val termo = Texto.paraRegisto(estado.texto)
                    val guardada = palavras.firstOrNull { it.lemma.equals(termo, ignoreCase = true) }
                    NaoEncontrada(
                        termo = termo,
                        parecidas = estado.parecidas,
                        aoVoltar = voltar,
                        aoEscolherParecida = { vm.procurar(it) },
                        significadoProprio = guardada?.significado,
                        jaGuardada = guardada != null,
                        aoRegistar = {
                            lemaARegistar = termo
                            pedirSignificadoNoRegisto = true
                        },
                    )
                }

                else -> Inicio(
                    estado = estado,
                    palavras = palavras,
                    aCarregar = carregadas == null,
                    progresso = progresso,
                    // Rever à mão, para toda a gente desde a 1.20 (até aí era
                    // do Modo Desenvolvedor). O limite do dia mantém a app calma.
                    porRever = porRever,
                    restantesHoje = restantesHoje,
                    aoEscrever = vm::escrever,
                    aoProcurar = { termo ->
                        vm.procurar(termo)
                        focus.clearFocus()
                    },
                    aoLimpar = vm::limpar,
                    aoRever = aoAbrirJogo,
                    aoAbrirColecao = aoAbrirColecao,
                    aoAbrirLivro = aoAbrirLivro,
                    livroEmCurso = livroEmCurso?.titulo,
                )
            }
        }
    }

    aPartilhar?.let { dados ->
        val entrada = estado.entrada
        DialogoPartilhar(
            dados,
            aoFechar = { aPartilhar = null },
            aoPartilharTexto = entrada?.let { e -> { partilharEntrada(context, e) } },
        )
    }

    marcoAvaliacao?.let { marco ->
        DialogoAvaliar(
            marco,
            // "Agora não": o marco já ficou marcado ao abrir; o seguinte pergunta.
            aoAdiar = { marcoAvaliacao = null },
            // "Avaliar" e "Não voltar a perguntar": acabou de vez.
            aoTerminar = {
                preferencias.terminarPedidosDeAvaliacao()
                marcoAvaliacao = null
            },
        )
    }

    lemaARegistar?.let { lema ->
        val existente = palavras.firstOrNull { it.lemma == lema }
        FolhaRegistar(
            lemma = lema,
            livrosAnteriores = livros,
            existente = existente,
            fraseInicial = fraseSugerida,
            pedirSignificado = pedirSignificadoNoRegisto,
            livroEmCurso = livroEmCurso?.titulo,
            aoMarcarEmCurso = { vm.comecarLivro(it) },
            aoCancelar = {
                lemaARegistar = null; pedirSignificadoNoRegisto = false; vm.limparContexto()
            },
            aoGuardar = { palavra ->
                // Guarda-se antes de limpar: é o que decide se vale a pena
                // lembrar que esta palavra pode ser proposta ao dicionário.
                val eraFalta = pedirSignificadoNoRegisto && existente == null
                if (existente != null) vm.atualizar(palavra) else vm.registar(palavra)
                lemaARegistar = null
                if (existente == null) talvezPedirAvaliacao()
                pedirSignificadoNoRegisto = false; vm.limparContexto()
                if (existente == null) {
                    ambito.launch {
                        if (eraFalta) {
                            estadoSnackbar.showSnackbar(
                                "Palavra registada. Podes propô-la ao dicionário em "
                                    + "Definições, Contribuir.",
                                duration = SnackbarDuration.Long,
                            )
                        } else {
                            estadoSnackbar.showSnackbar("\"${palavra.lemma}\" está na tua coleção.")
                        }
                    }
                }
            },
        )
    }
}

// --- pedido de avaliação -------------------------------------------------------

/**
 * Pede uma avaliação na Play Store, à 5.ª, 10.ª, 20.ª, 50.ª e 100.ª palavra.
 *
 * Três saídas: "Avaliar" (abre a loja e nunca mais pergunta), "Agora não"
 * (pergunta no marco seguinte; fechar o diálogo conta como isto) e "Não
 * voltar a perguntar".
 *
 * Sem a biblioteca de avaliação da Google: abre-se a página da app na Play
 * Store, como um link. É mais um passo para quem avalia, mas a app continua
 * sem código fechado nem dependências (e pode ir para o F-Droid).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoAvaliar(marco: Int, aoAdiar: () -> Unit, aoTerminar: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = aoAdiar,
        containerColor = Cores.card,
        title = {
            Text(
                "Já guardaste $marco palavras",
                style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                color = Cores.ink,
            )
        },
        text = {
            Text(
                "Se estás a gostar do Palavra-me, uma avaliação na Play Store ajuda "
                    + "outros leitores a encontrá-lo. Leva um minuto.",
                style = Estilo.texto.copy(lineHeight = 1.45.em),
                color = Cores.ink2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { abrirNaPlayStore(context); aoTerminar() },
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Avaliar", style = Estilo.controloForte) }
        },
        dismissButton = {
            // Duas saídas na mesma linha (quebra se não couber).
            FlowRow(horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = aoTerminar,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink3),
                ) { Text("Não voltar a perguntar", style = Estilo.controlo) }
                TextButton(
                    onClick = aoAdiar,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Agora não", style = Estilo.controloForte) }
            }
        },
    )
}

/**
 * A página da app na Play Store: primeiro na própria app da loja; sem ela
 * (um telemóvel sem Play Store), no browser.
 */
internal fun abrirNaPlayStore(context: android.content.Context) {
    val pacote = context.packageName
    val naLoja = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=$pacote"),
    ).apply { setPackage("com.android.vending") }
    try {
        context.startActivity(naLoja)
    } catch (e: android.content.ActivityNotFoundException) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pacote"),
                )
            )
        } catch (_: android.content.ActivityNotFoundException) {
            // Nem loja nem browser: não há nada a fazer, e não vale um erro.
        }
    }
}

// --- início ------------------------------------------------------------------

/**
 * O ecrã antes de se procurar seja o que for: a pergunta, o campo e, por
 * baixo, o que a coleção já tem. Uma app que abre vazia só se abre quando já
 * se sabe o que se quer; mostrar a estante e as últimas palavras convida a
 * voltar a elas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Inicio(
    estado: EstadoPesquisa,
    palavras: List<PalavraGuardada>,
    /** Antes da primeira leitura da coleção: não se mostra o convite de "vazia". */
    aCarregar: Boolean,
    progresso: Progresso?,
    porRever: Int,
    restantesHoje: Int,
    aoEscrever: (String) -> Unit,
    aoProcurar: (String) -> Unit,
    aoLimpar: () -> Unit,
    aoRever: () -> Unit,
    aoAbrirColecao: () -> Unit,
    aoAbrirLivro: (String) -> Unit,
    livroEmCurso: String? = null,
) {
    val sequencia = sequenciaAtiva(progresso)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logotipo()
            Spacer(Modifier.weight(1f))
            if (sequencia >= 1) {
                Pilula(
                    if (sequencia == 1) "1 dia" else "$sequencia dias",
                    fundo = Cores.accentSoft,
                    cor = Cores.accentInk,
                    icone = Icons.Rounded.LocalFireDepartment,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            buildAnnotatedString {
                append("O que encontraste ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Cores.accent)) {
                    append("hoje")
                }
                append("?")
            },
            style = Estilo.pergunta,
            color = Cores.ink,
        )
        Spacer(Modifier.height(22.dp))
        CampoPesquisa(
            texto = estado.texto,
            aoMudar = aoEscrever,
            aoProcurar = { if (estado.texto.isNotBlank()) aoProcurar(estado.texto) },
            aoLimpar = aoLimpar,
        )

        if (estado.texto.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            CartaoSugestoes(estado, aoProcurar)
        } else if (aCarregar) {
            // Um instante no arranque: nada abaixo do campo ainda.
        } else if (palavras.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(
                "Encontraste uma palavra que não conheces?",
                style = Estilo.grupoTitulo,
                color = Cores.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Escreve-a acima. Ou seleciona-a no teu leitor de e-books e "
                    + "escolhe Palavra-me. Nem precisas de a copiar.",
                style = Estilo.texto.copy(lineHeight = 1.45.em),
                color = Cores.ink2,
            )
        } else {
            // O cartão de progresso só depois da primeira resposta: antes seriam
            // zeros a insinuar que se está atrasado em alguma coisa. Antes
            // disso, havendo palavras por rever, aparece só a faixa, para
            // quem ainda nunca recebeu um lembrete (ou não os ligou).
            val respostas = (progresso?.acertos ?: 0) + (progresso?.erros ?: 0)
            if (progresso != null && respostas > 0) {
                Spacer(Modifier.height(22.dp))
                CartaoProgresso(
                    progresso = progresso,
                    total = palavras.size,
                    // "Que já sabes": acertadas pelo menos três vezes seguidas.
                    sabidas = palavras.count { it.mastery >= Niveis.CAIXA_SABIDA },
                    sequencia = sequencia,
                    porRever = porRever,
                    restantesHoje = restantesHoje,
                    aoRever = aoRever,
                )
            } else if (porRever > 0) {
                Spacer(Modifier.height(22.dp))
                FaixaRever(
                    porRever, restantesHoje, aoRever,
                    fundo = Cores.accentSoft, cor = Cores.accentInk,
                )
            }

            val estante = remember(palavras, livroEmCurso) { estanteDe(palavras, livroEmCurso) }
            if (estante.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Overline("Na tua estante", Modifier.weight(1f))
                    Text(
                        "Ver coleção",
                        style = Estilo.controloForte.copy(fontSize = 14.sp),
                        color = Cores.accentInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = aoAbrirColecao)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    estante.forEach { livro ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { aoAbrirLivro(livro.titulo) }
                                .padding(bottom = 4.dp, top = 4.dp, end = 4.dp),
                        ) {
                            CapaLivro(livro.titulo, livro.autor)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Cores.ink)) {
                                        append(livro.quantas.toString())
                                    }
                                    append(if (livro.quantas == 1) " palavra" else " palavras")
                                    if (livro.titulo == livroEmCurso) {
                                        append(" · ")
                                        withStyle(SpanStyle(color = Cores.accentInk)) { append("a ler") }
                                    }
                                },
                                style = Estilo.ajuda,
                                color = Cores.ink2,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Overline("Últimas registadas")
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                palavras.take(8).forEach { palavra ->
                    TextoMarcado(
                        palavra.lemma,
                        Estilo.recente,
                        marcador = Cores.accentSoft,
                        de = 0.68f,
                        ate = 0.94f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { aoProcurar(palavra.lemma) },
                    )
                }
            }
        }
    }
}

/**
 * O cartão por baixo do campo enquanto se escreve: até cinco começos, ou o
 * "Querias dizer" quando nada começa pelo que está escrito, e no fim a linha
 * que procura o que lá está.
 */
@Composable
private fun CartaoSugestoes(estado: EstadoPesquisa, aoProcurar: (String) -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Cores.card)
            .border(1.dp, Cores.line, forma),
    ) {
        if (estado.sugestoes.isNotEmpty()) {
            estado.sugestoes.take(5).forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { aoProcurar(s.lemma) }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.lemma, style = Estilo.sugestao, color = Cores.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.pos.isNotBlank() && s.pos != "desconhecido") {
                        Spacer(Modifier.width(12.dp))
                        Text(rotuloPos(s.pos), style = Estilo.dominio, color = Cores.ink2)
                    }
                }
                DivisoriaGrupo()
            }
        } else if (!estado.procurou && estado.parecidas.isNotEmpty()) {
            // Nada começa pelo que está escrito: provavelmente um erro. As
            // parecidas aparecem logo, sem ser preciso carregar em procurar.
            Overline("Querias dizer", Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
            estado.parecidas.forEach { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { aoProcurar(p.lemma) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(p.lemma, style = Estilo.sugestao, color = Cores.ink)
                    p.definicao?.let {
                        Text(
                            it, style = Estilo.ajuda, color = Cores.ink2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DivisoriaGrupo()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { aoProcurar(estado.texto) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward, null,
                tint = Cores.accentInk, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Procurar \"${estado.texto.trim()}\"",
                style = Estilo.controloForte,
                color = Cores.accentInk,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Um livro da estante: o título, o autor que lhe ficou e quantas palavras tem. */
private data class LivroNaEstante(val titulo: String, val autor: String?, val quantas: Int)

/**
 * Os livros da coleção, o mais recente primeiro (a lista vem por data), com
 * o livro em curso à cabeça.
 */
private fun estanteDe(palavras: List<PalavraGuardada>, emCurso: String? = null): List<LivroNaEstante> =
    palavras.filter { !it.livro.isNullOrBlank() }
        .groupBy { it.livro!!.trim() }
        .map { (titulo, lista) ->
            LivroNaEstante(
                titulo,
                lista.firstNotNullOfOrNull { it.autor?.takeIf(String::isNotBlank) },
                lista.size,
            )
        }
        .sortedByDescending { it.titulo == emCurso }
        .take(12)

/**
 * A sequência que ainda conta: se o último dia jogado foi hoje ou ontem. Uma
 * sequência de há uma semana já se partiu, mesmo que a base ainda a guarde.
 */
private fun sequenciaAtiva(p: Progresso?): Int {
    val dia = p?.ultimoDia ?: return 0
    val hoje = LocalDate.now()
    return if (dia == hoje.toString() || dia == hoje.minusDays(1).toString()) p.sequencia else 0
}

/**
 * O que a coleção já rendeu: o nível, a barra até ao seguinte e três números.
 * Cores invertidas, para se distinguir do resto sem gritar.
 */
@Composable
private fun CartaoProgresso(
    progresso: Progresso,
    total: Int,
    sabidas: Int,
    sequencia: Int,
    porRever: Int,
    restantesHoje: Int,
    aoRever: () -> Unit,
) {
    val nivel = Niveis.para(progresso.acertos)
    val seguinte = Niveis.seguinte(progresso.acertos)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Cores.invBg)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "NÍVEL ${nivel.numero}",
                    style = Estilo.overline.copy(fontSize = 11.sp),
                    color = Cores.invMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(nivel.nome, style = Estilo.nivel, color = Cores.invInk)
            }
            Text(
                if (progresso.pontos == 1) "1 ponto" else "${progresso.pontos} pontos",
                style = Estilo.ajuda,
                color = Cores.invMuted,
            )
        }
        Spacer(Modifier.height(14.dp))
        val fracao = seguinte?.let {
            ((progresso.acertos - nivel.desde).toFloat() / (it.desde - nivel.desde))
                .coerceIn(0f, 1f)
        } ?: 1f
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Cores.invTrack)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fracao)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Cores.invFill)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (seguinte == null) {
                buildAnnotatedString { append("Chegaste ao último nível.") }
            } else {
                val faltam = seguinte.desde - progresso.acertos
                buildAnnotatedString {
                    append(if (faltam == 1) "Falta 1 acerto para " else "Faltam $faltam acertos para ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Cores.invInk)) {
                        append(seguinte.nome)
                    }
                }
            },
            style = Estilo.meta.copy(fontSize = 12.5.sp),
            color = Cores.invMuted,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Cores.invTrack, thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Estatistica(total, if (total == 1) "palavra" else "palavras", Modifier.weight(1f))
            Estatistica(sabidas, "que já sabes", Modifier.weight(1f))
            Estatistica(sequencia, if (sequencia == 1) "dia seguido" else "dias seguidos", Modifier.weight(1f))
        }
        // Rever à mão: para toda a gente, até ao limite do dia.
        if (porRever > 0) {
            Spacer(Modifier.height(16.dp))
            FaixaRever(
                porRever, restantesHoje, aoRever,
                fundo = Cores.invFill, cor = Cores.invCtaInk,
            )
        }
    }
}

/**
 * "N palavras por rever", que abre o jogo. Atingido o limite do dia, diz-o e
 * deixa de abrir: amanhã há mais, e os lembretes continuam.
 */
@Composable
private fun FaixaRever(
    porRever: Int,
    restantesHoje: Int,
    aoRever: () -> Unit,
    fundo: Color,
    cor: Color,
) {
    val podeRever = restantesHoje > 0
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (podeRever) fundo else fundo.copy(alpha = 0.55f))
            .then(if (podeRever) Modifier.clickable(onClick = aoRever) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                !podeRever -> "Já reviste $LIMITE_MANUAL_POR_DIA hoje. Amanhã há mais."
                porRever == 1 -> "1 palavra por rever"
                else -> "$porRever palavras por rever"
            },
            style = Estilo.controloForte,
            color = cor,
            modifier = Modifier.weight(1f),
        )
        if (podeRever) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = cor)
        }
    }
}

@Composable
private fun Estatistica(numero: Int, rotulo: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(numero.toString(), style = Estilo.numeroEstat, color = Cores.invInk)
        Text(rotulo, style = Estilo.meta, color = Cores.invMuted)
    }
}

// --- candidatos ----------------------------------------------------------------

@Composable
private fun ListaCandidatos(
    candidatos: List<Candidato>,
    aoVoltar: () -> Unit,
    aoEscolher: (Candidato) -> Unit,
) {
    // "cantada" pode ser substantivo ou particípio de "cantar": mostram-se
    // todos os candidatos, ordenados por frequência (plano, secção 9).
    Column(Modifier.fillMaxSize()) {
        BarraVoltar(aoVoltar)
        LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)) {
            item {
                Overline("Mais do que uma palavra corresponde")
                Spacer(Modifier.height(10.dp))
            }
            items(candidatos) { c ->
                DivisoriaGrupo()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { aoEscolher(c) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.lemma, style = Estilo.linhaLema, color = Cores.ink)
                        val via = c.viaForma?.let { " · via $it" } ?: ""
                        val classe = c.pos.takeIf { it.isNotBlank() && it != "desconhecido" }
                            ?.let(::rotuloPos).orEmpty()
                        if (classe.isNotEmpty() || via.isNotEmpty()) {
                            Text(
                                (classe + via).removePrefix(" · "),
                                style = Estilo.dominio,
                                color = Cores.ink2,
                            )
                        }
                    }
                    Chevron()
                }
            }
        }
    }
}

// --- palavra -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VistaEntrada(
    entrada: Entrada,
    guardada: PalavraGuardada?,
    desenvolvedor: Boolean,
    aoVoltar: () -> Unit,
    aoRegistar: () -> Unit,
    aoPartilhar: () -> Unit,
    aoSeguir: (String) -> Unit,
) {
    val significadoProprio = guardada?.significado?.takeIf { it.isNotBlank() }
    var aCorrigir by remember(entrada.lemmaId) { mutableStateOf(false) }
    if (aCorrigir) DialogoCorrigir(entrada, aoFechar = { aCorrigir = false })
    // Coluna + lista com peso: o texto rola, mas a barra de registar fica
    // sempre em baixo. Registar é o gesto central da app; numa entrada com
    // sete aceções, o botão não pode desaparecer ao rolar.
    Column(Modifier.fillMaxSize()) {
        BarraVoltar(
            aoVoltar,
            direita = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Partilhar leva o significado para fora da app, sem rede.
                    IconButton(onClick = aoPartilhar, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Share, "Partilhar", tint = Cores.ink)
                    }
                    // "Corrigir uma definição" (1.22): no menu, para não
                    // pôr mais um ícone à vista numa barra que é da palavra.
                    run {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.MoreVert, "Mais", tint = Cores.ink)
                            }
                            DropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false },
                                containerColor = Cores.card,
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text("Corrigir uma definição", style = Estilo.controlo, color = Cores.ink)
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.EditNote, null, tint = Cores.ink) },
                                    onClick = { menu = false; aCorrigir = true },
                                )
                            }
                        }
                    }
                }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 28.dp),
        ) {
            item {
                TextoMarcado(entrada.lemma, Estilo.lema)
                // Todas as classes da palavra, a principal primeiro
                // ("substantivo · adjetivo"); com um dicionário anterior à
                // 1.21, só a do lema, como antes.
                val classe = entrada.classes.takeIf { it.isNotEmpty() }
                    ?.joinToString(" · ") { rotuloPos(it) }
                if (classe != null || entrada.silabas != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        classe?.let { Text(it, style = Estilo.classe, color = Cores.ink2) }
                        if (classe != null && entrada.silabas != null) {
                            Text("  ·  ", style = Estilo.texto, color = Cores.ink3)
                        }
                        entrada.silabas?.let {
                            Text(
                                it,
                                style = Estilo.texto.copy(fontSize = 14.sp, letterSpacing = 0.06.em),
                                color = Cores.ink2,
                            )
                        }
                    }
                }
                if (guardada != null) {
                    Spacer(Modifier.height(14.dp))
                    val partes = buildList {
                        add("Na tua coleção")
                        guardada.livro?.takeIf { it.isNotBlank() }?.let { add(it) }
                        guardada.page?.let { add("p. $it") }
                    }
                    Pilula(
                        partes.joinToString(" · "),
                        fundo = Cores.okSoft,
                        cor = Cores.ok,
                        icone = Icons.Rounded.Bookmark,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }

            // O significado que o próprio escreveu fica sempre visível, tenha
            // a palavra definição ou não: o teu contributo não se perde quando
            // a palavra entra no dicionário numa versão seguinte.
            significadoProprio?.let { meu ->
                item {
                    Overline("O teu significado")
                    Spacer(Modifier.height(6.dp))
                    Text(meu, style = Estilo.acecao, color = Cores.ink)
                    Spacer(Modifier.height(22.dp))
                }
            }

            if (entrada.acecoes.isEmpty()) {
                item {
                    // Sem definição, os sinónimos passam a ser a resposta. Para
                    // quem encontra a palavra a meio de um romance, "o mesmo que
                    // sonolento" resolve tão bem como uma definição.
                    val sinonimos = entrada.relacionadas.filter { it.relacao == "sinonimo" }
                    if (sinonimos.isEmpty()) {
                        if (significadoProprio == null) {
                            Text(
                                "Está no dicionário, mas sem significado atribuído.",
                                style = Estilo.corpo,
                                color = Cores.ink2,
                            )
                            Spacer(Modifier.height(22.dp))
                        }
                    } else {
                        Text(
                            "Sem definição, mas é o mesmo que:",
                            style = Estilo.corpo,
                            color = Cores.ink2,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sinonimos.take(8).forEach { ChipRelacao(it, aoSeguir) }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }

            // Com mais do que uma classe, as aceções agrupam-se por classe,
            // cada grupo com um título pequeno ("lacónico": o adjetivo e, à
            // parte, o substantivo das termas romanas). Os números seguem a
            // ordem em que aparecem no ecrã.
            val classes = entrada.classes
            val grupos = if (classes.size > 1) {
                classes.map { c -> c to entrada.acecoes.filter { (it.pos ?: entrada.pos) == c } }
                    .filter { it.second.isNotEmpty() }
            } else listOf((classes.firstOrNull() ?: "") to entrada.acecoes)
            var numero = 0
            val numeradas = grupos.map { (c, lista) -> c to lista.map { it to ++numero } }

            numeradas.forEachIndexed { g, (classeDoGrupo, lista) ->
              if (numeradas.size > 1) {
                item(key = "classe-$classeDoGrupo") {
                    Text(
                        rotuloPos(classeDoGrupo),
                        style = Estilo.classe,
                        color = Cores.accentInk,
                        modifier = Modifier.padding(top = if (g == 0) 0.dp else 8.dp, bottom = 10.dp),
                    )
                }
              }
              items(lista, key = { it.first.id }) { (acecao, n) ->
                Row(Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "$n",
                        style = Estilo.numeroAcecao,
                        color = Cores.accent,
                        modifier = Modifier.width(26.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        if (acecao.dominios.isNotEmpty()) {
                            Text(
                                acecao.dominios.joinToString(", "),
                                style = Estilo.dominio,
                                color = Cores.ink2,
                            )
                        }
                        DefinicaoComRemissao(acecao, aoSeguir)
                        // A proveniência de cada aceção é ferramenta de quem
                        // constrói a base; a atribuição cumpre-se no ecrã de
                        // licenças.
                        if (desenvolvedor) {
                            Text(acecao.fonte, style = Estilo.meta, color = Cores.ink3)
                        }
                        entrada.exemplosDe(acecao).forEach {
                            Spacer(Modifier.height(10.dp))
                            CaixaExemplo(it, desenvolvedor)
                        }
                    }
                }
              }
            }

            val soltos = entrada.exemplosSoltos
            if (soltos.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Overline("Exemplos de uso")
                    Spacer(Modifier.height(10.dp))
                }
                items(soltos) {
                    CaixaExemplo(it, desenvolvedor)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (entrada.relacionadas.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    entrada.relacionadas.groupBy { it.relacao }.forEach { (relacao, lista) ->
                        Overline(rotuloRelacao(relacao))
                        Spacer(Modifier.height(10.dp))
                        // Tocáveis: um sinónimo é uma palavra que também se
                        // pode não conhecer.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            lista.forEach { ChipRelacao(it, aoSeguir) }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }

        // A barra de registo, sempre visível. Já registada: diz que está e
        // deixa editar; por registar: o botão que a app existe para tornar fácil.
        HorizontalDivider(color = Cores.line, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Cores.bg)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (guardada != null) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Cores.okSoft),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Cores.ok, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Registada", style = Estilo.botao, color = Cores.ok)
                }
                Spacer(Modifier.width(10.dp))
                BotaoContornado(
                    "Editar", aoRegistar,
                    icone = Icons.Rounded.EditNote,
                    altura = 54.dp,
                )
            } else {
                BotaoPrincipal(
                    "Registar", aoRegistar,
                    icone = Icons.Rounded.BookmarkAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * O texto de uma aceção. Se remete para outra palavra ("Vide ação", "O mesmo
 * que e-mail"), o destino fica sublinhado e tocável (abre-o, com o histórico
 * para voltar, como os sinónimos), e a definição dele aparece por baixo, para
 * a resposta estar à vista sem tocar. Antes era só texto, e a pessoa tinha de
 * procurar o destino à mão.
 */
@Composable
private fun DefinicaoComRemissao(acecao: Acecao, aoSeguir: (String) -> Unit) {
    val destino = acecao.remetePara
    if (destino == null) {
        Text(acecao.definicao, style = Estilo.acecao, color = Cores.ink)
        return
    }
    val estiloLigacao = TextLinkStyles(
        SpanStyle(color = Cores.accentInk, textDecoration = TextDecoration.Underline)
    )
    val texto = acecao.definicao
    // O destino como está escrito na aceção; se lá não estiver (outra grafia),
    // acrescenta-se à frente.
    val inicio = texto.indexOf(destino, ignoreCase = true)
    val anotado = buildAnnotatedString {
        if (inicio >= 0) {
            append(texto.substring(0, inicio))
            withLink(LinkAnnotation.Clickable("remissao", estiloLigacao) { aoSeguir(destino) }) {
                append(texto.substring(inicio, inicio + destino.length))
            }
            append(texto.substring(inicio + destino.length))
        } else {
            append(texto)
            append("  ")
            withLink(LinkAnnotation.Clickable("remissao", estiloLigacao) { aoSeguir(destino) }) {
                append(destino)
            }
        }
    }
    Text(anotado, style = Estilo.acecao, color = Cores.ink)
    acecao.definicaoDoDestino?.let { def ->
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(destino) }
                append(": ")
                append(def)
            },
            style = Estilo.texto.copy(fontSize = 14.5.sp, lineHeight = 1.45.em),
            color = Cores.ink2,
        )
    }
}

/**
 * Uma palavra relacionada. As que têm definição levam a borda de destaque; as
 * que não têm ficam apagadas, mas continuam tocáveis: podem ter sinónimos.
 */
@Composable
private fun ChipRelacao(rel: Relacionada, aoSeguir: (String) -> Unit) {
    val forma = RoundedCornerShape(percent = 50)
    Text(
        rel.lemma,
        style = Estilo.chipLivro.copy(fontSize = 15.5.sp),
        color = if (rel.temDefinicao) Cores.accentInk else Cores.ink2,
        modifier = Modifier
            .clip(forma)
            .background(Cores.card)
            .border(1.5.dp, if (rel.temDefinicao) Cores.accent else Cores.line, forma)
            .clickable { aoSeguir(rel.lemma) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Um exemplo de uso: caixa afundada com uma barra de destaque à esquerda.
 * Um exemplo gerado por LLM diz sempre que o é, na base e na interface, sem
 * exceções (plano, instrução 8).
 */
@Composable
private fun CaixaExemplo(exemplo: Exemplo, desenvolvedor: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(Cores.sunk),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Cores.accent))
        Column(Modifier.padding(start = 16.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            Text(exemplo.frase, style = Estilo.citacao, color = Cores.ink)
            Spacer(Modifier.height(6.dp))
            // A fonte da frase fica sempre: o Tatoeba distribui em CC BY com
            // atribuição por frase (ver notas/fontes.md). A referência interna
            // só no Modo Desenvolvedor.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (exemplo.gerado) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = "exemplo gerado",
                        tint = Cores.accentInk,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    buildString {
                        append(exemplo.fonte)
                        if (desenvolvedor) {
                            exemplo.referencia?.let { append(" · ").append(it) }
                        }
                        if (exemplo.gerado) append(" · gerado")
                    },
                    style = Estilo.meta,
                    color = Cores.ink2,
                )
            }
        }
    }
}

/**
 * Manda o significado para fora da app, como texto simples.
 *
 * Sem rede: é o intent de partilha do Android, que entrega a qualquer app
 * (mensagens, notas, email). O texto leva a palavra, a classe, as primeiras
 * aceções e a assinatura da app.
 */
private fun partilharEntrada(context: android.content.Context, entrada: Entrada) {
    val texto = buildString {
        append(entrada.lemma)
        entrada.pos.takeIf { it.isNotBlank() && it != "desconhecido" }
            ?.let { append(" (").append(rotuloPos(it)).append(")") }
        append("\n\n")
        if (entrada.acecoes.isNotEmpty()) {
            entrada.acecoes.take(5).forEachIndexed { i, a ->
                append("${i + 1}. ${a.definicao}\n")
            }
        } else {
            val sin = entrada.relacionadas.filter { it.relacao == "sinonimo" }
            if (sin.isNotEmpty()) {
                append("O mesmo que: ")
                append(sin.take(6).joinToString(", ") { it.lemma })
                append("\n")
            }
        }
        append("\nPalavra-me")
    }
    val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, texto)
        putExtra(android.content.Intent.EXTRA_SUBJECT, entrada.lemma)
    }
    val escolha = android.content.Intent.createChooser(envio, null).apply {
        // A própria app aceita partilhas de texto; não faz sentido aparecer
        // como destino de uma definição que ela própria está a partilhar.
        putExtra(
            android.content.Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(
                android.content.ComponentName(
                    context, pt.jorges15.palavrame.MainActivity::class.java,
                )
            ),
        )
    }
    context.startActivity(escolha)
}

// --- não encontrada ------------------------------------------------------------

/**
 * Palavra não encontrada, mas não um beco sem saída.
 *
 * Primeiro a hipótese de erro de escrita, que se resolve com um toque. Depois
 * a de registar a palavra mesmo assim, com o significado do próprio: a coleção
 * guarda o `lemma` como texto, por isso uma palavra fora do dicionário cabe lá
 * tão bem como as outras.
 */
@Composable
private fun NaoEncontrada(
    termo: String,
    parecidas: List<Parecida>,
    aoVoltar: () -> Unit,
    aoEscolherParecida: (String) -> Unit,
    significadoProprio: String?,
    jaGuardada: Boolean,
    aoRegistar: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BarraVoltar(aoVoltar)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 28.dp),
        ) {
            if (termo.isNotBlank()) {
                Overline("Procuraste")
                Spacer(Modifier.height(6.dp))
                Text(
                    termo,
                    style = Estilo.termo,
                    color = Cores.ink,
                    modifier = Modifier
                        .sublinhadoOndulado(Cores.err)
                        .padding(bottom = 12.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Não está no dicionário. Pode ser só um erro de escrita.",
                    style = Estilo.corpo,
                    color = Cores.ink2,
                )
            } else {
                Text("Não encontrei essa palavra.", style = Estilo.corpo, color = Cores.ink2)
            }

            if (parecidas.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Overline("Querias dizer")
                Spacer(Modifier.height(10.dp))
                parecidas.forEach { p ->
                    val forma = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(forma)
                            .border(1.5.dp, Cores.accent, forma)
                            .clickable { aoEscolherParecida(p.lemma) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.lemma, style = Estilo.parecida, color = Cores.ink)
                            p.definicao?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    it,
                                    style = Estilo.texto.copy(fontSize = 14.5.sp, lineHeight = 1.4.em),
                                    color = Cores.ink2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Cores.accent)
                    }
                }
            }

            significadoProprio?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(22.dp))
                Overline("O teu significado")
                Spacer(Modifier.height(6.dp))
                Text(it, style = Estilo.acecao, color = Cores.ink)
            }

            if (termo.isNotBlank()) {
                Spacer(Modifier.height(22.dp))
                if (jaGuardada) {
                    Pilula(
                        "Já está na tua coleção",
                        fundo = Cores.okSoft,
                        cor = Cores.ok,
                        icone = Icons.Rounded.Bookmark,
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Cores.sunk)
                            .padding(18.dp),
                    ) {
                        Text(
                            "Registar mesmo assim",
                            style = Estilo.botao,
                            color = Cores.ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Podes guardá-la na tua coleção e escrever o que quer "
                                + "dizer, se souberes.",
                            style = Estilo.texto.copy(fontSize = 14.sp, lineHeight = 1.45.em),
                            color = Cores.ink2,
                        )
                        Spacer(Modifier.height(14.dp))
                        BotaoContornado(
                            "Registar \"$termo\"", aoRegistar,
                            icone = Icons.Rounded.BookmarkAdd,
                            cor = Cores.ink,
                            borda = Cores.ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rótulo em português, acentuado, para uma relação que a base guarda por slug
 * ASCII (`sinonimo`, `hiperonimo`...). É uma app de português: o que aparece
 * no ecrã leva os acentos, ainda que o slug interno não os tenha.
 */
private fun rotuloRelacao(relacao: String): String = when (relacao) {
    "sinonimo" -> "Sinónimos"
    "antonimo" -> "Antónimos"
    "hiperonimo" -> "Hiperónimos"
    "hiponimo" -> "Hipónimos"
    else -> relacao.replaceFirstChar { it.uppercase() }
}
