package pt.jorges15.palavrame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** Frase de contexto a pré-preencher (seleção vinda do leitor de e-books). */
    fraseInicial: String? = null,
    /**
     * Mostra o campo do significado próprio. Liga-se quando a palavra não está
     * no dicionário ou está lá sem definição — os casos em que faz sentido a
     * pessoa escrever o que a palavra quer dizer.
     */
    pedirSignificado: Boolean = false,
    aoCancelar: () -> Unit,
    aoGuardar: (PalavraGuardada) -> Unit,
) {
    // Quem está a ler um livro regista dele muitas palavras seguidas. Voltar
    // a escrever o título de cada vez é a fricção mais fácil de eliminar:
    // o último livro vem já preenchido e os anteriores estão a um toque.
    // `rememberSaveable` (com chave no lema): o que já foi escrito sobrevive a
    // rodar o telemóvel ou ao sistema recriar o processo. Perder o livro/frase
    // a meio de um registo, num diálogo com cinco campos, é a fricção que mais
    // dói. A chave no lema reinicia os campos quando se abre outra palavra.
    // Ao editar, o que já lá estava; senão o último livro usado.
    val livroInicial = existente?.livro
        ?: livrosAnteriores.firstOrNull()?.titulo.orEmpty()
    var livro by rememberSaveable(lemma) { mutableStateOf(livroInicial) }
    // Ao editar, o autor é o que já lá estava. Ao registar de novo, é o que
    // ficou associado ao livro pré-preenchido — se registaste dez palavras de
    // "Os Maias", não escreves "Eça de Queirós" onze vezes.
    var autor by rememberSaveable(lemma) {
        mutableStateOf(
            existente?.autor
                ?: livrosAnteriores.firstOrNull { it.titulo == livroInicial }?.autor
                ?: ""
        )
    }
    // O autor vai agarrado ao livro. Guarda-se o último autor que a app pôs
    // sozinha: enquanto a caixa tiver esse valor, mudar de livro muda também o
    // autor; se o escreveste à mão, fica como está.
    var autorAutomatico by rememberSaveable(lemma) { mutableStateOf(autor) }

    /** O autor conhecido para um título, dos livros já usados. */
    fun autorDoLivro(titulo: String): String {
        val t = titulo.trim()
        return livrosAnteriores.firstOrNull { it.titulo.equals(t, ignoreCase = true) }?.autor
            ?: ""
    }

    fun mudarLivro(novo: String) {
        livro = novo
        if (autor == autorAutomatico) {
            autor = autorDoLivro(novo)
            autorAutomatico = autor
        }
    }
    var pagina by rememberSaveable(lemma) { mutableStateOf(existente?.page?.toString().orEmpty()) }
    // A frase pré-preenche-se com a seleção do leitor, quando vem uma; ao
    // editar, é a que já lá estava.
    var frase by rememberSaveable(lemma) {
        mutableStateOf(existente?.frase ?: fraseInicial ?: "")
    }
    var nota by rememberSaveable(lemma) { mutableStateOf(existente?.note.orEmpty()) }
    var significado by rememberSaveable(lemma) { mutableStateOf(existente?.significado.orEmpty()) }
    // O campo do significado próprio só faz sentido quando a palavra não tem
    // uma no dicionário — ou quando já lá escreveste uma e a estás a editar.
    val mostrarSignificado = pedirSignificado || !existente?.significado.isNullOrBlank()
    var listaAberta by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoCancelar,
        title = {
            Text(if (existente == null) "Registar \"$lemma\"" else "Editar \"$lemma\"")
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Primeiro, quando é pedido: numa palavra que falta ao
                // dicionário, o significado é o que mais interessa guardar.
                if (mostrarSignificado) {
                    OutlinedTextField(
                        value = significado, onValueChange = { significado = it },
                        label = { Text("Significado") },
                        supportingText = {
                            Text("Esta palavra não tem definição no dicionário. "
                                + "Escreve o que ela quer dizer, se souberes.")
                        },
                        minLines = 2, modifier = Modifier.fillMaxWidth(),
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = listaAberta && livrosAnteriores.isNotEmpty(),
                    onExpandedChange = { listaAberta = it },
                ) {
                    OutlinedTextField(
                        value = livro,
                        onValueChange = { mudarLivro(it) },
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
                                    // Escolher um livro da lista é dizer qual
                                    // é: o autor passa a ser o desse livro
                                    // (ou nenhum, se não tiver), sempre.
                                    livro = anterior.titulo
                                    autor = anterior.autor.orEmpty()
                                    autorAutomatico = autor
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
                        significado = significado.ifBlank { null },
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}
