package pt.jorges15.palavrame.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.jorges15.palavrame.data.PalavraGuardada
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A coleção — a razão de a app se chamar Palavra-me. Não é uma lista de
 * favoritos: é o registo do que se foi aprendendo, e por isso agrupa-se por
 * livro quando o livro é conhecido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraColecao(
    vm: PesquisaViewModel,
    aoVoltar: () -> Unit,
) {
    val palavras by vm.palavrasGuardadas.collectAsState(initial = emptyList())
    var agruparPorLivro by remember { mutableStateOf(true) }
    var porData by remember { mutableStateOf(true) }
    var aEditar by remember { mutableStateOf<PalavraGuardada?>(null) }
    val livros by vm.livrosUsados.collectAsState(initial = emptyList())

    aEditar?.let { palavra ->
        DialogoRegistar(
            lemma = palavra.lemma,
            livrosAnteriores = livros,
            existente = palavra,
            aoCancelar = { aEditar = null },
            aoGuardar = { atualizada -> vm.atualizar(atualizada); aEditar = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("As minhas palavras (${palavras.size})") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
            )
        }
    ) { padding ->
        if (palavras.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp)) {
                Text(
                    "Ainda não registaste nenhuma palavra.\n\n" +
                        "Procura uma que tenhas encontrado a ler e toca em Registar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Agrupar por livro",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Switch(checked = agruparPorLivro, onCheckedChange = { agruparPorLivro = it })
                Spacer(Modifier.width(8.dp))
                // Por data é o que serve para rever o que se aprendeu há
                // pouco; alfabética é o que serve para procurar. Dois usos
                // diferentes da mesma lista.
                IconButton(onClick = { porData = !porData }) {
                    Icon(
                        if (porData) Icons.Default.Schedule else Icons.Default.SortByAlpha,
                        contentDescription =
                            if (porData) "Ordenado por data" else "Ordenado alfabeticamente",
                    )
                }
            }

            val ordenadas = if (porData) palavras
            else palavras.sortedBy { it.lemma.lowercase() }

            val grupos: Map<String, List<PalavraGuardada>> = if (agruparPorLivro) {
                ordenadas.groupBy { it.livro ?: "Sem livro" }
            } else {
                mapOf("" to ordenadas)
            }

            LazyColumn {
                grupos.forEach { (livro, lista) ->
                    if (livro.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(16.dp, 20.dp, 16.dp, 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    livro,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                // A contagem por livro é o número que dá
                                // gosto ver crescer.
                                Text(
                                    lista.size.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                    items(lista, key = { it.id }) { p ->
                        LinhaPalavra(
                            p,
                            aoProcurar = { vm.procurar(p.lemma); aoVoltar() },
                            aoEditar = { aEditar = p },
                            aoApagar = { vm.apagar(p) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaPalavra(
    palavra: PalavraGuardada,
    aoProcurar: () -> Unit,
    aoEditar: () -> Unit,
    aoApagar: () -> Unit,
) {
    val formato = remember { SimpleDateFormat("d MMM yyyy", Locale("pt", "PT")) }
    var aConfirmar by remember { mutableStateOf(false) }

    if (aConfirmar) {
        AlertDialog(
            onDismissRequest = { aConfirmar = false },
            title = { Text("Esquecer «${palavra.lemma}»?") },
            text = { Text("Sai da tua coleção. O dicionário não muda.") },
            confirmButton = {
                TextButton(onClick = { aConfirmar = false; aoApagar() }) { Text("Esquecer") }
            },
            dismissButton = {
                TextButton(onClick = { aConfirmar = false }) { Text("Cancelar") }
            },
        )
    }
    ListItem(
        headlineContent = { Text(palavra.lemma, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                palavra.frase?.let {
                    Text("«$it»", fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    buildString {
                        append(formato.format(Date(palavra.guardadaEm)))
                        palavra.page?.let { append(" · p. ").append(it) }
                        palavra.autor?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = aoEditar) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                IconButton(onClick = { aConfirmar = true }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Esquecer",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = aoProcurar),
    )
}
