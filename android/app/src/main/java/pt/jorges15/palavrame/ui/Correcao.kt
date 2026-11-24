package pt.jorges15.palavrame.ui

import android.content.Context
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jorges15.palavrame.BuildConfig
import pt.jorges15.palavrame.PalavrameApp
import pt.jorges15.palavrame.data.Acecao
import pt.jorges15.palavrame.data.CorrecaoGuardada
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

/** O que se guarda no diálogo de sugerir. Texto nulo = apagar o que havia. */
internal sealed class Escrito {
    /** Uma correção à aceção `acecao`. */
    data class Correcao(val acecao: Acecao, val texto: String?) : Escrito()
    /** O teu significado: um sentido em falta, ou o de uma palavra sem definição. */
    data class Sentido(val texto: String?) : Escrito()
}

/**
 * Corrigir ou acrescentar um significado (1.30). Um só gesto: escolhe-se a
 * aceção errada (ou "Falta um sentido"), escreve-se, e Guardar. O que se
 * escreve fica sempre para a pessoa (por cima das definições, ou por baixo da
 * aceção corrigida) **e** no que há para submeter ao dicionário; quem quiser
 * submeter logo tem o "Submeter ao dicionário" na mensagem que aparece a seguir.
 *
 * Antes (1.22 a 1.24) havia que escolher entre guardar e enviar, e as palavras
 * que não estão no dicionário tinham outro caminho. Agora é este diálogo em
 * todo o lado: na palavra e na "não encontrada" (sem aceções, só o campo).
 *
 * Abrir com algo já escrito traz esse texto, para o mudar; apagá-lo todo e
 * guardar apaga-o.
 */
@Composable
internal fun DialogoSugerir(
    lemma: String,
    /** As aceções da palavra; vazia se não tem definição ou não está no dicionário. */
    acecoes: List<Acecao>,
    significadoProprio: String?,
    correcoes: List<CorrecaoGuardada>,
    aoGuardar: (Escrito) -> Unit,
    aoFechar: () -> Unit,
    /** A aceção a abrir já escolhida (tocar numa correção tua); nula = a primeira. */
    acecaoInicial: Int? = null,
) {
    fun corrigidaDe(a: Acecao) = correcoes.firstOrNull { it.acecaoOrd == a.ord }?.texto
    fun existenteDe(i: Int): String? =
        if (i >= acecoes.size) significadoProprio else corrigidaDe(acecoes[i])
    // O índice a seguir às aceções é "Falta um sentido". Sem aceções, é o único.
    var escolhida by remember(lemma) {
        mutableStateOf(
            acecaoInicial?.let { ord -> acecoes.indexOfFirst { it.ord == ord }.takeIf { it >= 0 } }
                ?: if (significadoProprio != null || acecoes.isEmpty()) acecoes.size else 0
        )
    }
    var texto by remember(lemma) { mutableStateOf(existenteDe(escolhida).orEmpty()) }
    val acrescentar = escolhida >= acecoes.size
    fun escolher(i: Int) {
        // Mudar de opção traz o que já lá estava escrito, se alguma coisa.
        if (i != escolhida) texto = existenteDe(i).orEmpty()
        escolhida = i
    }
    val apagar = texto.isBlank() && existenteDe(escolhida) != null

    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Cores.card,
        title = {
            Text(
                if (acecoes.isEmpty()) "Acrescentar um significado" else "Corrigir ou acrescentar",
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
                    if (acecoes.isEmpty())
                        "O que quer dizer \"$lemma\"? Fica para ti, e podes submetê-lo ao dicionário."
                    else "Qual está errado ou antiquado? Ou falta o sentido em que a encontraste? "
                        + "Fica para ti, e podes submetê-lo ao dicionário.",
                    style = Estilo.ajuda,
                    color = Cores.ink2,
                )
                if (acecoes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    acecoes.forEachIndexed { i, a ->
                        OpcaoSugerir(
                            selecionada = escolhida == i,
                            aoEscolher = { escolher(i) },
                            texto = "${a.ord}. ${a.definicao}",
                            marca = if (corrigidaDe(a) != null) "corrigido por ti" else null,
                        )
                    }
                    OpcaoSugerir(
                        selecionada = acrescentar,
                        aoEscolher = { escolher(acecoes.size) },
                        texto = "Falta um sentido",
                        marca = if (significadoProprio != null) "o teu significado" else null,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                CampoFormulario(
                    when {
                        acecoes.isEmpty() -> "Significado"
                        acrescentar -> "O sentido que falta"
                        else -> "Como devia ser"
                    },
                    texto, { texto = it },
                    linhas = 3,
                    placeholder = if (acrescentar) "O sentido em que a encontraste" else "Ou o que está mal",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = texto.trim().ifBlank { null }
                    aoGuardar(
                        if (acrescentar) Escrito.Sentido(t)
                        else Escrito.Correcao(acecoes[escolhida], t)
                    )
                    aoFechar()
                },
                enabled = texto.isNotBlank() || apagar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.accentInk),
            ) { Text(if (apagar) "Apagar" else "Guardar", style = Estilo.controloForte) }
        },
        dismissButton = {
            TextButton(
                onClick = aoFechar,
                colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
            ) { Text("Cancelar", style = Estilo.controlo) }
        },
    )
}

@Composable
private fun OpcaoSugerir(selecionada: Boolean, aoEscolher: () -> Unit, texto: String, marca: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = aoEscolher)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selecionada,
            onClick = aoEscolher,
            colors = RadioButtonDefaults.colors(
                selectedColor = Cores.accent,
                unselectedColor = Cores.ink3,
            ),
        )
        Column(Modifier.padding(top = 12.dp)) {
            Text(
                texto,
                style = Estilo.texto.copy(fontSize = 15.sp),
                color = Cores.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            marca?.let { Text(it, style = Estilo.ajuda, color = Cores.accentInk) }
        }
    }
}

/**
 * Abre o email com as sugestões escolhidas. Devolve falso se não há app de
 * email. Quem chama marca-as como enviadas (`marcarSugestoesEnviadas`).
 */
internal fun enviarSugestoes(
    context: Context,
    faltas: List<PalavraEmFalta>,
    correcoes: List<CorrecaoGuardada>,
): Boolean {
    val app = context.applicationContext as PalavrameApp
    val intent = intentEmail(
        para = EMAIL_SUBMISSAO,
        assunto = assuntoSugestoes(faltas, correcoes),
        corpo = corpoSugestoes(
            faltas, correcoes, BuildConfig.VERSION_NAME, app.dicionario?.construidoEm,
        ),
    )
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
