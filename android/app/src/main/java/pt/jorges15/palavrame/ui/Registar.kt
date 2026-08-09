package pt.jorges15.palavrame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.jorges15.palavrame.data.PalavraGuardada
// `Livro` vive no PesquisaViewModel, no mesmo pacote — sem import.

/**
 * O diálogo de registo. **Tudo é opcional menos a palavra**: quem está a
 * meio de um livro não deve ter de preencher burocracia para guardar uma
 * palavra. O livro e a frase são o que dá valor à coleção mais tarde, mas
 * exigi-los travaria o gesto que a app existe para tornar fácil.
 *
 * Por isso nenhum campo diz "(opcional)": dizê-lo em dois e não nos outros
 * insinuava que os restantes eram obrigatórios. O aviso vive aqui em cima,
 * onde não estorva ninguém.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoRegistar(
    lemma: String,
    livrosAnteriores: List<Livro> = emptyList(),
    /** Não nulo = estamos a editar um registo que já existe. */
    existente: PalavraGuardada? = null,
    aoCancelar: () -> Unit,
    aoGuardar: (PalavraGuardada) -> Unit,
) {
    // Quem está a ler um livro regista dele muitas palavras seguidas. Voltar
    // a escrever o título de cada vez é a fricção mais fácil de eliminar:
    // o último livro vem já preenchido e os anteriores estão a um toque.
    val livroInicial = existente?.livro ?: livrosAnteriores.firstOrNull()?.titulo.orEmpty()
    var livro by remember { mutableStateOf(livroInicial) }
    // Ao editar, o autor é o que já lá estava. Ao registar de novo, é o que
    // ficou associado ao livro pré-preenchido — se registaste dez palavras de
    // "Os Maias", não escreves "Eça de Queirós" onze vezes.
    var autor by remember {
        mutableStateOf(
            existente?.autor
                ?: livrosAnteriores.firstOrNull { it.titulo == livroInicial }?.autor
                ?: ""
        )
    }
    var pagina by remember { mutableStateOf(existente?.page?.toString().orEmpty()) }
    var frase by remember { mutableStateOf(existente?.frase.orEmpty()) }
    var nota by remember { mutableStateOf(existente?.note.orEmpty()) }
    var listaAberta by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoCancelar,
        title = {
            Text(if (existente == null) "Registar «$lemma»" else "Editar «$lemma»")
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = listaAberta && livrosAnteriores.isNotEmpty(),
                    onExpandedChange = { listaAberta = it },
                ) {
                    OutlinedTextField(
                        value = livro,
                        onValueChange = { livro = it },
                        label = { Text("Livro") },
                        singleLine = true,
                        trailingIcon = {
                            if (livrosAnteriores.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = listaAberta)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = listaAberta && livrosAnteriores.isNotEmpty(),
                        onDismissRequest = { listaAberta = false },
                    ) {
                        livrosAnteriores.forEach { anterior ->
                            DropdownMenuItem(
                                text = { Text(anterior.titulo) },
                                onClick = {
                                    livro = anterior.titulo
                                    // O autor vem atrás do livro. Só se
                                    // preenche quando está vazio: se já
                                    // escreveste um autor à mão, escolher o
                                    // título não to apaga.
                                    if (autor.isBlank()) {
                                        anterior.autor?.let { autor = it }
                                    }
                                    listaAberta = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = autor, onValueChange = { autor = it },
                    label = { Text("Autor") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pagina,
                    onValueChange = { novo -> pagina = novo.filter { it.isDigit() } },
                    label = { Text("Página") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = frase, onValueChange = { frase = it },
                    label = { Text("A frase onde a encontraste") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nota, onValueChange = { nota = it },
                    label = { Text("Nota") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                aoGuardar(
                    // `copy` quando existe, para preservar o id, a data de
                    // registo e o progresso de revisão — editar as notas de
                    // uma palavra não é registá-la de novo.
                    (existente ?: PalavraGuardada(lemma = lemma)).copy(
                        // TEXTO, não id: a coleção sobrevive a uma
                        // substituição do dicionario.db.
                        lemma = lemma,
                        livro = livro.ifBlank { null },
                        autor = autor.ifBlank { null },
                        page = pagina.toIntOrNull(),
                        frase = frase.ifBlank { null },
                        note = nota.ifBlank { null },
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
