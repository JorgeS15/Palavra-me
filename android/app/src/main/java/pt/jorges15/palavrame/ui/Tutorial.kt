package pt.jorges15.palavrame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo

/*
 * "Como funciona" (1.22): cinco ecrãs curtos, para quem abre a app pela
 * primeira vez. Aparece uma vez só, na instalação nova; quem atualiza chega
 * cá pelo botão das Novidades, e qualquer pessoa pelas Definições.
 *
 * Calmo: "Saltar" sempre à mão, nada de setas a apontar para botões, e não
 * pede permissões (as notificações continuam a pedir-se só quando se ligam os
 * lembretes). As ilustrações são peças da própria app, com as cores do tema.
 * Desenho no Claude Design ("Palavra-me: tutorial").
 *
 * 1.30: o inglês no primeiro passo (o PT/EN da caixa de pesquisa) e um passo
 * novo, "Corrigir ou acrescentar", entre guardar e os livros.
 */

private class PassoTutorial(val titulo: String, val texto: String)

private val PASSOS = listOf(
    PassoTutorial(
        "Encontraste uma palavra?",
        "Escreve-a, ou seleciona-a no teu leitor de e-books e escolhe Palavra-me. "
            + "Mesmo flexionada, a app chega à palavra. Em português ou em inglês: "
            + "PT ou EN, na caixa de pesquisa.",
    ),
    PassoTutorial(
        "Guarda-a com o livro",
        "Com a página e a frase onde a encontraste. O livro que estás a ler já vem escolhido.",
    ),
    PassoTutorial(
        "Falta um sentido?",
        "Se uma definição está errada, ou falta o sentido em que a leste, toca em "
            + "Corrigir ou acrescentar. Fica para ti, e podes submetê-lo ao dicionário.",
    ),
    PassoTutorial(
        "Os teus livros, numa estante",
        "O que estás a ler e a página onde vais, os lidos por ano e as datas de cada "
            + "leitura. Adiciona livros no separador Livros.",
    ),
    PassoTutorial(
        "E não as esqueças",
        "Umas perguntas de vez em quando, sem pressa. Os lembretes são opcionais: "
            + "ligas-os em Definições, quando quiseres.",
    ),
)

/** O tutorial, em ecrã inteiro. `aoFechar` corre ao saltar ou ao começar. */
@Composable
fun Tutorial(aoFechar: () -> Unit) {
    var passo by rememberSaveable { mutableStateOf(0) }
    val ultimo = passo == PASSOS.lastIndex
    Dialog(
        // O "retroceder" volta um passo; no primeiro, fecha.
        onDismissRequest = { if (passo > 0) passo-- else aoFechar() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Cores.bg) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (passo > 0) {
                        IconButton(onClick = { passo-- }, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Passo anterior", tint = Cores.ink)
                        }
                    } else {
                        Logotipo()
                    }
                    Spacer(Modifier.weight(1f))
                    if (!ultimo) {
                        TextButton(
                            onClick = aoFechar,
                            colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                        ) { Text("Saltar", style = Estilo.controloForte.copy(fontSize = 14.sp)) }
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                ) {
                    Ilustracao(passo)
                    Spacer(Modifier.height(28.dp))
                    Overline("${passo + 1} de ${PASSOS.size}", cor = Cores.accentInk)
                    Spacer(Modifier.height(8.dp))
                    Text(PASSOS[passo].titulo, style = Estilo.grupoTitulo.copy(fontSize = 27.sp), color = Cores.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        PASSOS[passo].texto,
                        style = Estilo.texto.copy(fontSize = 17.sp, lineHeight = 1.5.em),
                        color = Cores.ink2,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pontos(passo, PASSOS.size)
                    Spacer(Modifier.weight(1f))
                    BotaoPrincipal(
                        if (ultimo) "Começar" else "Seguinte",
                        aoTocar = { if (ultimo) aoFechar() else passo++ },
                        modifier = Modifier.widthIn(min = 140.dp),
                        altura = 52.dp,
                    )
                }
            }
        }
    }
}

/** Os pontos do progresso: o passo atual mais comprido. */
@Composable
private fun Pontos(atual: Int, total: Int) {
    Row(
        Modifier.semantics { contentDescription = "Passo ${atual + 1} de $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == atual) 22.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == atual) Cores.accent else Cores.line)
            )
        }
    }
}

// --- as ilustrações ---------------------------------------------------------------

@Composable
private fun Ilustracao(passo: Int) {
    val forma = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp)
            .clip(forma)
            .background(Cores.card)
            .border(1.dp, Cores.line, forma)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (passo) {
            0 -> IlustracaoProcurar()
            1 -> IlustracaoGuardar()
            2 -> IlustracaoCorrigir()
            3 -> IlustracaoLivros()
            else -> IlustracaoRever()
        }
    }
}

/** Uma frase de um livro, com a palavra selecionada e o menu do sistema; e o campo. */
@Composable
private fun IlustracaoProcurar() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.sunk)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Cores.invBg),
        ) {
            listOf("Copiar", "Partilhar").forEach {
                Text(
                    it, style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.invMuted,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }
            Text(
                "Palavra-me",
                style = Estilo.controloForte.copy(fontSize = 13.sp),
                color = Cores.onAccent,
                modifier = Modifier.background(Cores.accent).padding(horizontal = 11.dp, vertical = 8.dp),
            )
        }
        Text(
            buildAnnotatedString {
                append("Ficou muito tempo à janela, os olhos ")
                withStyle(SpanStyle(background = Cores.mark, color = Cores.ink)) { append("ensonados") }
                append(", a ver a chuva cair no largo.")
            },
            style = Estilo.texto.copy(fontSize = 16.sp, lineHeight = 1.6.em),
            color = Cores.ink,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Cores.line)
        Text(
            "ou escreve-a", style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.ink3,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = Cores.line)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Cores.bg)
            .border(1.dp, Cores.line, RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Cores.ink3)
        Spacer(Modifier.width(10.dp))
        Text("ensonados", style = Estilo.texto.copy(fontSize = 17.sp), color = Cores.ink, modifier = Modifier.weight(1f))
        Text("ensonado", style = Estilo.meta.copy(fontSize = 12.sp), color = Cores.ink3)
        Spacer(Modifier.width(10.dp))
        // O PT/EN da caixa de pesquisa, com o português escolhido.
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Cores.sunk).padding(2.dp),
        ) {
            listOf("PT", "EN").forEachIndexed { i, sigla ->
                Text(
                    sigla,
                    style = Estilo.controloForte.copy(fontSize = 12.sp),
                    color = if (i == 0) Cores.ink else Cores.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (i == 0) Cores.card else Cores.sunk)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** O registo: a palavra, o livro em curso já escolhido, a página e a frase. */
@Composable
private fun IlustracaoGuardar() {
    Row(verticalAlignment = Alignment.Bottom) {
        TextoMarcado("ensonado", Estilo.lema.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
        Text("adjetivo", style = Estilo.texto.copy(fontStyle = FontStyle.Italic, fontSize = 14.sp), color = Cores.ink2)
    }
    CampoDeExemplo("Livro") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Cores.ink)
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Lombada(corDoLivro("Memorial do Convento") ?: Cores.accent, 8.dp, 16.dp)
                Spacer(Modifier.width(7.dp))
                Text("Memorial do Convento", style = Estilo.controloForte.copy(fontSize = 13.sp), color = Cores.bg)
            }
            Text(
                "Outro", style = Estilo.controlo.copy(fontSize = 13.sp), color = Cores.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Cores.line, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoDeExemplo("Autor", Modifier.weight(1f)) { CaixaDeExemplo("José Saramago") }
        CampoDeExemplo("Página", Modifier.width(84.dp)) { CaixaDeExemplo("214", destaque = true) }
    }
    CampoDeExemplo("A frase") {
        CaixaDeExemplo("\"...os olhos ensonados, a ver a chuva cair no largo.\"", italico = true)
    }
}

@Composable
private fun CampoDeExemplo(rotulo: String, modifier: Modifier = Modifier, conteudo: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(rotulo, style = Estilo.rotulo.copy(fontSize = 12.sp), color = Cores.ink2)
        conteudo()
    }
}

@Composable
private fun CaixaDeExemplo(texto: String, destaque: Boolean = false, italico: Boolean = false) {
    val forma = RoundedCornerShape(12.dp)
    Text(
        texto,
        style = Estilo.texto.copy(fontSize = 14.sp, fontStyle = if (italico) FontStyle.Italic else FontStyle.Normal),
        color = Cores.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (destaque) Cores.accent else Cores.line, forma)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

/** Uma aceção, o botão "Corrigir ou acrescentar" e o teu significado por cima. */
@Composable
private fun IlustracaoCorrigir() {
    TextoMarcado("rebarbativo", Estilo.lema.copy(fontSize = 26.sp))
    Column {
        Overline("O teu significado", cor = Cores.accentInk)
        Spacer(Modifier.height(4.dp))
        Text("Antipático, desagradável.", style = Estilo.acecao.copy(fontSize = 17.sp), color = Cores.ink)
    }
    Row {
        Text("1", style = Estilo.numeroAcecao, color = Cores.accent, modifier = Modifier.width(22.dp))
        Text(
            "Que tem rebarba; áspero.",
            style = Estilo.texto.copy(fontSize = 15.sp, lineHeight = 1.45.em),
            color = Cores.ink2,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Cores.accent, RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EditNote, contentDescription = null, tint = Cores.accentInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Corrigir ou acrescentar", style = Estilo.controloForte, color = Cores.accentInk)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.invBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Significado guardado.", style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.invInk, modifier = Modifier.weight(1f))
        Text("Submeter", style = Estilo.controloForte.copy(fontSize = 13.sp), color = Cores.invFill)
    }
}

/** "A ler agora" com a página e o lápis, e uma prateleira de lidos. */
@Composable
private fun IlustracaoLivros() {
    Overline("A ler agora", cor = Cores.accentInk)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Cores.bg)
            .border(1.dp, Cores.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapaLivro("Memorial do Convento", "José Saramago", largura = 54.dp, altura = 78.dp, compacta = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Memorial do Convento", style = Estilo.grupoTitulo.copy(fontSize = 17.sp), color = Cores.ink)
            Spacer(Modifier.height(4.dp))
            Text("p. 214 de 480 · 14 palavras", style = Estilo.meta.copy(fontSize = 13.sp), color = Cores.ink2)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 214f / 480f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Cores.accent,
                trackColor = Cores.sunk,
                drawStopIndicator = {},
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.EditNote, contentDescription = null, tint = Cores.ink3)
    }
    Overline("Lidos em 2026", cor = Cores.accentInk)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Os Maias", "A Sibila", "Ensaio sobre a Cegueira", "Mensagem").forEach { titulo ->
            BoxWithConstraints(Modifier.weight(1f)) {
                CapaLivro(titulo, null, largura = maxWidth, altura = maxWidth * 1.45f, compacta = true)
            }
        }
    }
}

/** Uma pergunta, com a resposta certa marcada. */
@Composable
private fun IlustracaoRever() {
    Overline("O que quer dizer")
    TextoMarcado("soturno", Estilo.lema.copy(fontSize = 30.sp))
    OpcaoDeExemplo("Que fala muito e depressa.")
    OpcaoDeExemplo("Sombrio, triste, melancólico.", certa = true)
    OpcaoDeExemplo("Feito com pouco cuidado.")
}

@Composable
private fun OpcaoDeExemplo(texto: String, certa: Boolean = false) {
    val forma = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(forma)
            .background(if (certa) Cores.okSoft else Cores.card)
            .border(if (certa) 2.dp else 1.dp, if (certa) Cores.ok else Cores.line, forma)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto, style = Estilo.texto.copy(fontSize = 15.sp),
            color = if (certa) Cores.ink else Cores.ink2, modifier = Modifier.weight(1f),
        )
        if (certa) Icon(Icons.Rounded.Check, "Certa", tint = Cores.ok)
    }
}
