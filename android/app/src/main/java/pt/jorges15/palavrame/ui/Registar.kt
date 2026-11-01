package pt.jorges15.palavrame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
// `Livro` vive no PesquisaViewModel, no mesmo pacote — sem import.

/**
 * A folha de registo (e de edição). **Tudo é opcional menos a palavra**: quem está a
 * meio de um livro não deve ter de preencher burocracia para guardar uma
 * palavra. O livro e a frase são o que dá valor à coleção mais tarde, mas
 * exigi-los travaria o gesto que a app existe para tornar fácil.
 *
 * Por isso nenhum campo diz "(opcional)": dizê-lo em dois e não nos outros
 * insinuava que os restantes eram obrigatórios. O aviso vive aqui em cima,
 * onde não estorva ninguém.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolhaRegistar(
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
    // Um livro que não está entre os chips (um novo, ou um antigo que já saiu
    // da lista) escreve-se à mão, no "+ Outro".
    val chips = livrosAnteriores.take(6)
    var escreverOutro by rememberSaveable(lemma) {
        mutableStateOf(
            livroInicial.isNotBlank() &&
                chips.none { it.titulo.equals(livroInicial.trim(), ignoreCase = true) }
        )
    }

    ModalBottomSheet(
        onDismissRequest = aoCancelar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Cores.card,
        contentColor = Cores.ink,
        scrimColor = Cores.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Cores.line)
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Overline(if (existente == null) "Registar" else "Editar", cor = Cores.accentInk)
                Spacer(Modifier.height(4.dp))
                Text(lemma, style = Estilo.lemaFolha, color = Cores.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Só a palavra é obrigatória. O resto ajuda-te a lembrar.",
                    style = Estilo.ajuda,
                    color = Cores.ink2,
                )
            }

            // Primeiro, quando é pedido: numa palavra que falta ao dicionário,
            // o significado é o que mais interessa guardar.
            if (mostrarSignificado) {
                Column {
                    CampoFormulario(
                        "Significado", significado, { significado = it },
                        linhas = 2, borda = Cores.accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Esta palavra não tem definição no dicionário. "
                            + "Escreve o que ela quer dizer, se souberes.",
                        style = Estilo.ajuda,
                        color = Cores.ink2,
                    )
                }
            }

            Column {
                Text("Livro", style = Estilo.rotulo, color = Cores.ink2)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { anterior ->
                        ChipLivro(
                            anterior.titulo,
                            cor = corDoLivro(anterior.titulo),
                            ativo = !escreverOutro &&
                                livro.trim().equals(anterior.titulo, ignoreCase = true),
                            aoTocar = {
                                // Escolher um livro é dizer qual é: o autor passa
                                // a ser o desse livro (ou nenhum), sempre.
                                escreverOutro = false
                                livro = anterior.titulo
                                autor = anterior.autor.orEmpty()
                                autorAutomatico = autor
                            },
                        )
                    }
                    ChipLivro(
                        "Sem livro",
                        cor = Cores.ink3,
                        ativo = !escreverOutro && livro.isBlank(),
                        aoTocar = {
                            escreverOutro = false
                            livro = ""
                            if (autor == autorAutomatico) autor = ""
                            autorAutomatico = autor
                        },
                    )
                    ChipLivro(
                        "+ Outro",
                        cor = null,
                        ativo = escreverOutro,
                        aoTocar = {
                            if (!escreverOutro) {
                                escreverOutro = true
                                mudarLivro("")
                            }
                        },
                    )
                }
                if (escreverOutro) {
                    Spacer(Modifier.height(10.dp))
                    CampoFormulario(
                        null, livro, { mudarLivro(it) },
                        placeholder = "Título do livro",
                        capitalizacao = KeyboardCapitalization.Words,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoFormulario(
                    "Autor", autor, { autor = it },
                    modifier = Modifier.weight(1f),
                    capitalizacao = KeyboardCapitalization.Words,
                )
                CampoFormulario(
                    "Página", pagina,
                    { novo -> pagina = novo.filter { it.isDigit() }.take(5) },
                    modifier = Modifier.width(86.dp),
                    numerico = true,
                )
            }
            CampoFormulario(
                "A frase onde a encontraste", frase, { frase = it },
                linhas = 3, italico = true,
            )
            CampoFormulario("Nota", nota, { nota = it }, placeholder = "Para ti")

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = aoCancelar,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                ) { Text("Cancelar", style = Estilo.botao) }
                BotaoPrincipal(
                    "Guardar",
                    aoTocar = {
                        aoGuardar(
                            // `copy` quando existe, para preservar o id, a data de
                            // registo e o progresso de revisão: editar as notas de
                            // uma palavra não é registá-la de novo.
                            (existente ?: PalavraGuardada(lemma = lemma)).copy(
                                // TEXTO, não id: a coleção sobrevive a uma
                                // substituição do dicionario.db.
                                lemma = lemma,
                                livro = livro.trim().ifBlank { null },
                                autor = autor.trim().ifBlank { null },
                                page = pagina.toIntOrNull(),
                                frase = frase.trim().ifBlank { null },
                                note = nota.trim().ifBlank { null },
                                significado = significado.trim().ifBlank { null },
                            )
                        )
                    },
                    modifier = Modifier.weight(2f),
                    altura = 52.dp,
                )
            }
        }
    }
}

/** Um chip de livro: mini-lombada com a cor do livro e o título. */
@Composable
private fun ChipLivro(titulo: String, cor: Color?, ativo: Boolean, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .height(38.dp)
            .clip(forma)
            .background(if (ativo) Cores.ink else Cores.bg)
            .then(if (ativo) Modifier else Modifier.border(1.5.dp, Cores.line, forma))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cor != null) {
            Lombada(cor, 9.dp, 14.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            titulo,
            style = Estilo.chipLivro,
            color = if (ativo) Cores.bg else Cores.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
