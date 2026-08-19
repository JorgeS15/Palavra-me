package pt.jorges15.palavrame.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.data.*
import pt.jorges15.palavrame.jogo.MINIMO_PARA_JOGAR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcraPesquisa(
    vm: PesquisaViewModel,
    preferencias: Preferencias,
    aoAbrirColecao: () -> Unit,
    aoAbrirDefinicoes: () -> Unit,
    aoAbrirJogo: () -> Unit = {},
) {
    val estado by vm.estado.collectAsState()
    val livros by vm.livrosUsados.collectAsState(initial = emptyList())
    val guardados by vm.lemasGuardados.collectAsState(initial = emptySet())
    val palavras by vm.palavrasGuardadas.collectAsState(initial = emptyList())
    val desenvolvedor by preferencias.modoDesenvolvedor.collectAsState()
    val progresso by vm.progresso.collectAsState(initial = null)
    val context = LocalContext.current
    var aRegistar by remember { mutableStateOf<Entrada?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // O nome da app é o gesto que ela nomeia — merece o
                    // peso de um título, não o de uma etiqueta.
                    Text(
                        "Palavra-me",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = aoAbrirDefinicoes) {
                        Icon(Icons.Default.Settings, "Definições")
                    }
                },
                actions = {
                    // Rever à mão é do Modo Desenvolvedor.
                    //
                    // Para quem lê, o jogo vem pelo lembrete: chega, responde-se,
                    // acabou. Um botão permanente convidava a sessões de estudo,
                    // que é o contrário do que esta app é. Perder um lembrete
                    // não perde nada — a palavra continua vencida e volta no
                    // lembrete seguinte.
                    if (desenvolvedor && guardados.size >= MINIMO_PARA_JOGAR) {
                        IconButton(onClick = aoAbrirJogo) {
                            Icon(Icons.Default.School, "Rever")
                        }
                    }
                    // Sem contador em cima do marcador: cortava contra a
                    // margem do ecrã e dizia o que já está dito em dois
                    // sítios — na linha de estatísticas aqui abaixo e no
                    // topo do ecrã da coleção.
                    IconButton(onClick = aoAbrirColecao) {
                        Icon(Icons.Default.Bookmark, "As minhas palavras")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {

            OutlinedTextField(
                value = estado.texto,
                onValueChange = vm::escrever,
                label = { Text("Procurar uma palavra") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (estado.texto.isNotEmpty()) {
                        IconButton(onClick = { vm.limpar() }) {
                            Icon(Icons.Default.Close, "Limpar")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.procurar() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f)) {
              when {
                estado.entrada != null ->
                    VistaEntrada(
                        estado.entrada!!,
                        jaGuardada = estado.entrada!!.lemma in guardados,
                        desenvolvedor = desenvolvedor,
                        aoRegistar = { aRegistar = it },
                        aoPartilhar = { partilharEntrada(context, it) },
                        aoSeguir = { vm.procurar(it) },
                    )

                estado.candidatos.isNotEmpty() -> ListaCandidatos(estado.candidatos, vm::abrir)

                estado.sugestoes.isNotEmpty() ->
                    LazyColumn {
                        items(estado.sugestoes) { s ->
                            ListItem(
                                headlineContent = { Text(s) },
                                modifier = Modifier.clickable { vm.procurar(s) },
                            )
                            HorizontalDivider()
                        }
                    }

                estado.procurou -> Aviso("Não encontrei essa palavra.")

                else -> EcraInicial(
                    recentes = palavras.take(12),
                    total = palavras.size,
                    progresso = progresso,
                    aoEscolher = { vm.procurar(it) },
                )
              }
            }

            Rodape()
        }
    }

    aRegistar?.let { entrada ->
        DialogoRegistar(
            lemma = entrada.lemma,
            livrosAnteriores = livros,
            aoCancelar = { aRegistar = null },
            aoGuardar = { palavra -> vm.registar(palavra); aRegistar = null },
        )
    }
}

@Composable
private fun ListaCandidatos(candidatos: List<Candidato>, aoEscolher: (Candidato) -> Unit) {
    // "cantada" pode ser substantivo ou particípio de "cantar": mostram-se
    // todos os candidatos, ordenados por frequência (plano, secção 9).
    Text(
        "Mais do que uma palavra corresponde:",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    LazyColumn {
        items(candidatos) { c ->
            ListItem(
                headlineContent = { Text(c.lemma, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    val via = c.viaForma?.let { " · via $it" } ?: ""
                    Text(c.pos + via)
                },
                modifier = Modifier.clickable { aoEscolher(c) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VistaEntrada(
    entrada: Entrada,
    jaGuardada: Boolean,
    desenvolvedor: Boolean,
    aoRegistar: (Entrada) -> Unit,
    aoPartilhar: (Entrada) -> Unit,
    aoSeguir: (String) -> Unit,
) {
  // Column + LazyColumn com peso: o texto rola, mas o botão de registar fica
  // sempre em baixo. Registar é o gesto central da app; numa entrada com sete
  // aceções, o botão não pode desaparecer ao rolar.
  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entrada.lemma, style = MaterialTheme.typography.headlineMedium)
                    val sub = listOfNotNull(
                        entrada.pos.takeIf { it.isNotBlank() && it != "desconhecido" },
                        entrada.silabas,
                    )
                    if (sub.isNotEmpty()) {
                        Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Partilhar leva o significado para fora da app — a peça que
                // faltava para uma palavra bonita chegar a outra pessoa, sem
                // rede e sem servidor.
                IconButton(onClick = { aoPartilhar(entrada) }) {
                    Icon(Icons.Default.Share, "Partilhar")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (entrada.acecoes.isEmpty()) {
            item {
                // Sem definição, os sinónimos deixam de ser um extra no fim
                // da entrada e passam a ser a resposta. Para quem encontra
                // uma palavra a meio de um romance, "o mesmo que sonolento"
                // resolve tão bem como uma definição — e é frequentemente
                // tudo o que as fontes abertas têm sobre a palavra.
                val sinonimos = entrada.relacionadas.filter { it.relacao == "sinonimo" }
                if (sinonimos.isEmpty()) {
                    Aviso("Sem definição em nenhuma fonte.")
                } else {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(
                            "Sem definição, mas é o mesmo que:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sinonimos.take(8).forEach { rel ->
                                SuggestionChip(
                                    onClick = { aoSeguir(rel.lemma) },
                                    label = { Text(rel.lemma) },
                                )
                            }
                        }
                    }
                }
            }
        }

        items(entrada.acecoes) { acecao ->
            Column(Modifier.padding(bottom = 16.dp)) {
                Row {
                    Text("${acecao.ord}. ", style = MaterialTheme.typography.bodyLarge)
                    Column {
                        if (acecao.dominios.isNotEmpty()) {
                            Text(
                                acecao.dominios.joinToString(" "),
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                            )
                        }
                        Text(acecao.definicao, style = MaterialTheme.typography.bodyLarge)
                        // A proveniência de cada aceção é ferramenta de
                        // quem constrói a base, não informação de quem lê
                        // um livro. A obrigação de atribuição das obras
                        // cumpre-se no ecrã "Fontes e licenças".
                        if (desenvolvedor) {
                            Text(
                                acecao.fonte,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        entrada.exemplosDe(acecao).forEach {
                            LinhaExemplo(it, desenvolvedor)
                        }
                    }
                }
            }
        }

        val soltos = entrada.exemplosSoltos
        if (soltos.isNotEmpty()) {
            item {
                Text(
                    "Exemplos de uso",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(soltos) { LinhaExemplo(it) }
        }

        if (entrada.relacionadas.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                    entrada.relacionadas.groupBy { it.relacao }.forEach { (relacao, lista) ->
                        Text(
                            relacao.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // Tocáveis: um sinónimo é uma palavra que também se
                        // pode não conhecer, e ter de a copiar para a caixa
                        // de pesquisa era um passo a mais no meio da leitura.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lista.forEach { rel ->
                                SuggestionChip(
                                    onClick = { aoSeguir(rel.lemma) },
                                    label = { Text(rel.lemma) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // A barra de registo, sempre visível. Já registada: informa em vez de
    // duplicar; por registar: o botão que a app existe para tornar fácil.
    HorizontalDivider()
    Box(Modifier.fillMaxWidth().padding(16.dp, 12.dp)) {
        if (jaGuardada) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Registada") },
                leadingIcon = { Icon(Icons.Default.Bookmark, null) },
            )
        } else {
            FilledTonalButton(
                onClick = { aoRegistar(entrada) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.BookmarkBorder, null)
                Spacer(Modifier.width(8.dp))
                Text("Registar")
            }
        }
    }
  }
}

/**
 * Manda o significado para fora da app, como texto simples.
 *
 * Sem rede: é o intent de partilha do Android, que entrega a qualquer app —
 * mensagens, notas, email. O texto leva a palavra, a classe, as primeiras
 * aceções e a assinatura da app.
 */
private fun partilharEntrada(context: android.content.Context, entrada: Entrada) {
    val texto = buildString {
        append(entrada.lemma)
        entrada.pos.takeIf { it.isNotBlank() && it != "desconhecido" }
            ?.let { append(" ($it)") }
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
        append("\n— Palavra-me")
    }
    val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, texto)
        putExtra(android.content.Intent.EXTRA_SUBJECT, entrada.lemma)
    }
    context.startActivity(android.content.Intent.createChooser(envio, null))
}

/**
 * O ecrã antes de se procurar seja o que for.
 *
 * Estava vazio, e uma app que abre vazia é uma app que só se abre quando já
 * se sabe o que se quer. Mostrar a coleção recente transforma-a em algo que
 * se abre para rever — que é meio propósito do projeto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EcraInicial(
    recentes: List<PalavraGuardada>,
    total: Int,
    progresso: Progresso?,
    aoEscolher: (String) -> Unit,
) {
    if (recentes.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp)) {
            Text(
                "Encontraste uma palavra que não conheces?",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Escreve-a acima. Ou seleciona-a no teu leitor de e-books e "
                    + "escolhe Palavra-me — nem precisas de a copiar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    Column(Modifier.padding(top = 24.dp)) {
        Estatisticas(total, progresso)
        Text(
            if (total > recentes.size) "As últimas que registaste (de $total)"
            else "As que registaste",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            recentes.forEach { palavra ->
                SuggestionChip(
                    onClick = { aoEscolher(palavra.lemma) },
                    label = { Text(palavra.lemma) },
                )
            }
        }
    }
}

/**
 * Uma linha com o que a coleção já rendeu.
 *
 * Deliberadamente uma linha e em letra miúda. A app abre-se para procurar
 * uma palavra a meio de um livro; os números são para se olhar de passagem,
 * não para tomarem conta do ecrã.
 *
 * Só aparece quando já houve jogo — antes disso seriam quatro zeros a
 * ocupar espaço e a insinuar que se está atrasado em alguma coisa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Estatisticas(total: Int, progresso: Progresso?) {
    val respostas = (progresso?.acertos ?: 0) + (progresso?.erros ?: 0)
    if (progresso == null || respostas == 0) return

    val partes = buildList {
        add("$total palavras")
        add("${progresso.pontos} pontos")
        add("${progresso.acertos} certas")
        if (progresso.erros > 0) add("${progresso.erros} erradas")
        if (progresso.sequencia > 1) add("${progresso.sequencia} dias seguidos")
    }
    Text(
        partes.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 20.dp),
    )
}

@Composable
private fun LinhaExemplo(exemplo: Exemplo, desenvolvedor: Boolean = false) {
    // Um exemplo gerado por LLM diz sempre que o é — na base e na interface,
    // sem exceções (plano, instrução 8).
    Row(Modifier.padding(top = 6.dp)) {
        if (exemplo.gerado) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "exemplo gerado",
                modifier = Modifier.size(14.dp).padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        Column {
            Text(
                exemplo.frase,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
            )
            // A fonte da frase fica sempre — o Tatoeba distribui em CC BY
            // com atribuição **por frase**, e isso é obrigação, não estilo
            // (ver docs/fontes.md). O que sai é a referência interna, do
            // género `por-pt_web_2015_1M:282349`, que não diz nada a
            // ninguém e fazia a entrada parecer uma ficha técnica.
            Text(
                buildString {
                    append(exemplo.fonte)
                    if (desenvolvedor) {
                        exemplo.referencia?.let { append(" · ").append(it) }
                    }
                    if (exemplo.gerado) append(" · gerado")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Versões da app e do dicionário, em letra miúda.
 *
 * Parece detalhe, mas resolve uma dúvida real: depois de reconstruir a base
 * é preciso saber se a que está no telemóvel é a nova. Sem isto, a única
 * forma era procurar uma palavra e adivinhar pelo resultado.
 */
/** Só a versão da app. O estado do dicionário vive nas Definições. */
@Composable
private fun Rodape() {
    Text(
        "Palavra-me ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
