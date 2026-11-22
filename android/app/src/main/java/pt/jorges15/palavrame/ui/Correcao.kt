package pt.jorges15.palavrame.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.data.Acecao
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.rotuloPos
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo

/*
 * "Corrigir uma definição" (1.22): avisar que uma aceção está errada ou
 * antiquada. Como a submissão de palavras em falta, sem rede: a app prepara o
 * email e é a app de email que o envia. Alimenta a curadoria.
 *
 * O texto vive em funções puras, testadas na JVM (`CorrecaoTest`).
 */

/** "Palavra-me: corrigir "crèdor"", para triar a caixa de entrada sem abrir. */
internal fun assuntoCorrecao(lemma: String): String = "Palavra-me: corrigir \"$lemma\""

/**
 * O corpo do email: a palavra, a aceção como está (com a fonte, que diz se é
 * do Wikcionário ou de 1913), o que a pessoa escreveu, e a versão da app e
 * do dicionário. Sem aceção, é um significado que falta, escrito pela pessoa.
 */
internal fun corpoCorrecao(
    lemma: String,
    acecao: Acecao?,
    classe: String?,
    comentario: String,
    versao: String,
): String = buildString {
    append("Palavra: ").append(lemma)
    classe?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
    append("\n")
    val c = comentario.trim()
    if (acecao == null) {
        append("\nFalta um significado:\n")
    } else {
        append("Significado ").append(acecao.ord).append(": ").append(acecao.definicao).append("\n")
        append("Fonte: ").append(acecao.fonte)
        if (acecao.modernizada) append(" (fraseado adaptado)")
        append("\n\n")
        append("O que está mal, ou como devia ser:\n")
    }
    append(if (c.isEmpty()) "(não escrevi nada)" else c)
    append("\n\n").append(versao).append("\n")
}

/**
 * Escolhe-se a aceção (a primeira vem escolhida), escreve-se o que está mal,
 * se se quiser, e abre-se o email. A última opção, "Falta um significado",
 * é para acrescentar um: aí é preciso escrevê-lo.
 */
@Composable
internal fun DialogoCorrigir(entrada: Entrada, aoFechar: () -> Unit) {
    val context = LocalContext.current
    var escolhida by remember(entrada.lemmaId) { mutableStateOf(0) }
    var comentario by remember(entrada.lemmaId) { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    val acecoes = entrada.acecoes
    // O índice a seguir às aceções é "Falta um significado".
    val acrescentar = escolhida >= acecoes.size

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = {
            Text(
                "Corrigir uma definição",
                style = Estilo.grupoTitulo.copy(fontSize = 22.sp),
                color = Cores.ink,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Qual está errada ou antiquada? Ou falta um significado? O email "
                        + "vai para quem cuida do dicionário, com a palavra e a fonte.",
                    style = Estilo.ajuda,
                    color = Cores.ink2,
                )
                Spacer(Modifier.height(4.dp))
                acecoes.forEachIndexed { i, a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { escolhida = i }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(
                            selected = escolhida == i,
                            onClick = { escolhida = i },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Cores.accent,
                                unselectedColor = Cores.ink3,
                            ),
                        )
                        Text(
                            "${a.ord}. ${a.definicao}",
                            style = Estilo.texto.copy(fontSize = 15.sp),
                            color = Cores.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { escolhida = acecoes.size }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = acrescentar,
                        onClick = { escolhida = acecoes.size },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Cores.accent,
                            unselectedColor = Cores.ink3,
                        ),
                    )
                    Text("Falta um significado", style = Estilo.texto.copy(fontSize = 15.sp), color = Cores.ink)
                }
                Spacer(Modifier.height(6.dp))
                CampoFormulario(
                    if (acrescentar) "O significado que falta" else "O que está mal, ou como devia ser",
                    comentario, { comentario = it },
                    linhas = 3,
                    placeholder = if (acrescentar) "Escreve-o aqui" else "Se quiseres",
                )
                erro?.let { Text(it, style = Estilo.ajuda, color = Cores.err) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = acecoes.getOrNull(escolhida)
                    val app = context.applicationContext as PalavrameApp
                    val intent = intentEmail(
                        para = EMAIL_SUBMISSAO,
                        assunto = assuntoCorrecao(entrada.lemma),
                        corpo = corpoCorrecao(
                            lemma = entrada.lemma,
                            acecao = a,
                            classe = (a?.pos ?: entrada.pos)
                                .takeIf { it.isNotBlank() && it != "desconhecido" }?.let(::rotuloPos),
                            comentario = comentario,
                            versao = linhaVersao(BuildConfig.VERSION_NAME, app.dicionario?.construidoEm),
                        ),
                    )
                    try {
                        context.startActivity(intent)
                        aoFechar()
                    } catch (e: Exception) {
                        erro = "Não encontrei uma app de email neste telemóvel."
                    }
                },
                // Para acrescentar, o significado tem de estar escrito.
                enabled = !acrescentar || comentario.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text("Escrever email", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = aoFechar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Cancelar", style = Estilo.controlo) }
        },
    )
}
