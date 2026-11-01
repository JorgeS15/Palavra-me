package pt.jorges15.palavrame.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import kotlin.math.PI
import kotlin.math.sin

/*
 * As peças do redesign que se repetem pelos ecrãs. Ficam aqui para os ecrãs
 * dizerem o que mostram e não como se desenha cada coisa.
 */

/** Sobretítulo de secção: 12/600, maiúsculas, espaçado. */
@Composable
fun Overline(texto: String, modifier: Modifier = Modifier, cor: Color = Cores.ink2) {
    Text(texto.uppercase(), style = Estilo.overline, color = cor, modifier = modifier)
}

/**
 * Texto com o marcador por trás, entre `de` e `ate` da altura: o gesto de
 * sublinhar uma palavra num livro. O marcador sai 4dp para cada lado.
 */
@Composable
fun TextoMarcado(
    texto: String,
    estilo: TextStyle,
    modifier: Modifier = Modifier,
    cor: Color = Cores.ink,
    marcador: Color = Cores.mark,
    de: Float = 0.60f,
    ate: Float = 0.90f,
) {
    Text(
        texto,
        style = estilo,
        color = cor,
        modifier = modifier
            .drawBehind {
                drawRect(
                    marcador,
                    topLeft = Offset(0f, size.height * de),
                    size = Size(size.width, size.height * (ate - de)),
                )
            }
            .padding(horizontal = 4.dp),
    )
}

/** "Palavra-me", com o "-me" no marcador. */
@Composable
fun Logotipo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Palavra", style = Estilo.logotipo, color = Cores.ink)
        TextoMarcado(
            "-me", Estilo.logotipo,
            de = 0.55f, ate = 0.92f,
            modifier = Modifier.offset(x = (-4).dp),
        )
    }
}

// --- livros ------------------------------------------------------------------

private val LOMBADAS = listOf(
    Color(0xFF2F4B3B),   // verde
    Color(0xFF7B2F2C),   // bordô
    Color(0xFF2B3D62),   // azul
    Color(0xFF6B4E2E),   // castanho
    Color(0xFF4A3B5C),   // violeta
)

/** O texto das capas, sempre o mesmo creme. */
val TextoCapa = Color(0xFFF7F0E3)

/**
 * A cor de um livro, tirada do título. É um dado, não tema: o mesmo livro tem
 * sempre a mesma cor, sem ser preciso guardá-la. O título normaliza-se antes,
 * para "Os Maias" e "os maias" darem a mesma cor. Nulo sem livro.
 */
fun corDoLivro(titulo: String?): Color? {
    val t = titulo?.takeIf { it.isNotBlank() } ?: return null
    return LOMBADAS[Math.floorMod(Texto.normalizar(t).hashCode(), LOMBADAS.size)]
}

/** Uma lombada pequena (grupos da coleção, chips, "Onde a encontraste"). */
@Composable
fun Lombada(cor: Color, largura: Dp, altura: Dp, modifier: Modifier = Modifier, raio: Dp = 2.dp) {
    Box(modifier.size(largura, altura).clip(RoundedCornerShape(raio)).background(cor))
}

/** Uma capa tipográfica: cor do livro, título em itálico, autor em baixo. */
@Composable
fun CapaLivro(
    titulo: String,
    autor: String?,
    modifier: Modifier = Modifier,
    largura: Dp = 118.dp,
    altura: Dp = 160.dp,
    /** Capa pequena (o "Onde a encontraste" do Rever): letra e margens menores. */
    compacta: Boolean = false,
) {
    val forma = RoundedCornerShape(topStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 3.dp)
    Box(
        modifier
            .size(largura, altura)
            .shadow(if (compacta) 4.dp else 8.dp, forma)
            .clip(forma)
            .background(corDoLivro(titulo) ?: Cores.ink3)
    ) {
        // A sombra da lombada, à esquerda.
        Box(
            Modifier.fillMaxHeight().width(if (compacta) 4.dp else 5.dp)
                .background(Color.Black.copy(alpha = 0.22f))
        )
        Column(
            Modifier.fillMaxSize().then(
                if (compacta) Modifier.padding(start = 11.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                else Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                titulo,
                style = if (compacta) Estilo.capaTitulo.copy(fontSize = 12.5.sp) else Estilo.capaTitulo,
                color = TextoCapa,
                maxLines = if (compacta) 5 else 4, overflow = TextOverflow.Ellipsis,
            )
            autor?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.uppercase(),
                    style = Estilo.meta.copy(
                        fontSize = if (compacta) 8.5.sp else 10.5.sp,
                        letterSpacing = 0.06.em,
                    ),
                    color = TextoCapa.copy(alpha = 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- controlos -----------------------------------------------------------------

/** Pílula com ícone opcional (sequência, "Na tua coleção", nível). */
@Composable
fun Pilula(
    texto: String,
    fundo: Color,
    cor: Color,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fundo)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, style = Estilo.rotulo.copy(fontSize = 13.sp), color = cor)
    }
}

/**
 * Segmentado: fundo afundado, o segmento ativo em cartão com sombra leve. As
 * opções dividem a largura que o `modifier` lhe der.
 */
@Composable
fun Segmentado(
    opcoes: List<String>,
    selecionado: Int,
    aoEscolher: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icones: List<ImageVector>? = null,
    altura: Dp = 42.dp,
) {
    Row(
        modifier
            .height(altura)
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.sunk)
            .padding(3.dp),
    ) {
        opcoes.forEachIndexed { i, texto ->
            val ativo = i == selecionado
            val forma = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (ativo) Modifier.shadow(1.dp, forma).clip(forma).background(Cores.card)
                        else Modifier.clip(forma)
                    )
                    .clickable { aoEscolher(i) }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icones?.getOrNull(i)?.let {
                    Icon(
                        it, null,
                        tint = if (ativo) Cores.ink else Cores.ink2,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    texto,
                    style = if (ativo) Estilo.controloForte.copy(fontSize = 14.sp)
                    else Estilo.controlo.copy(fontSize = 14.sp),
                    color = if (ativo) Cores.ink else Cores.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}

/** O campo de pesquisa grande da entrada (e, mais baixo, o filtro da coleção). */
@Composable
fun CampoPesquisa(
    texto: String,
    aoMudar: (String) -> Unit,
    aoProcurar: () -> Unit,
    aoLimpar: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Procurar uma palavra",
    altura: Dp = 58.dp,
    raio: Dp = 18.dp,
    estilo: TextStyle = Estilo.pesquisa,
    tamanhoIcone: Dp = 24.dp,
) {
    val forma = RoundedCornerShape(raio)
    BasicTextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        textStyle = estilo.copy(color = Cores.ink),
        cursorBrush = SolidColor(Cores.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { aoProcurar() }),
        modifier = modifier.fillMaxWidth().height(altura),
        decorationBox = { campo ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(forma)
                    .background(Cores.card)
                    .border(1.5.dp, Cores.line, forma)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Cores.ink2, modifier = Modifier.size(tamanhoIcone))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (texto.isEmpty()) {
                        Text(placeholder, style = estilo, color = Cores.ink3, maxLines = 1)
                    }
                    campo()
                }
                if (texto.isNotEmpty()) {
                    IconButton(onClick = aoLimpar, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, "Limpar", tint = Cores.ink2)
                    }
                } else {
                    Spacer(Modifier.width(14.dp))
                }
            }
        },
    )
}

/** Um campo de formulário: rótulo pequeno em cima, caixa de 46 com raio 12. */
@Composable
fun CampoFormulario(
    rotulo: String?,
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    linhas: Int = 1,
    italico: Boolean = false,
    numerico: Boolean = false,
    capitalizacao: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    borda: Color = Cores.line,
) {
    Column(modifier) {
        if (rotulo != null) {
            Text(rotulo, style = Estilo.rotulo, color = Cores.ink2)
            Spacer(Modifier.height(6.dp))
        }
        val forma = RoundedCornerShape(12.dp)
        val estilo = Estilo.campo.copy(
            color = Cores.ink,
            fontStyle = if (italico) FontStyle.Italic else FontStyle.Normal,
        )
        BasicTextField(
            value = valor,
            onValueChange = aoMudar,
            textStyle = estilo,
            cursorBrush = SolidColor(Cores.accent),
            singleLine = linhas == 1,
            minLines = linhas,
            maxLines = if (linhas == 1) 1 else linhas + 3,
            keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Number)
            else KeyboardOptions(capitalization = capitalizacao),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { campo ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(forma)
                        .background(Cores.bg)
                        .border(1.5.dp, borda, forma)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = if (linhas == 1) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (valor.isEmpty() && placeholder != null) {
                        Text(placeholder, style = estilo, color = Cores.ink3)
                    }
                    campo()
                }
            },
        )
    }
}

/** O botão principal: cheio, cor de destaque, raio 16. */
@Composable
fun BotaoPrincipal(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    altura: Dp = 54.dp,
    ativo: Boolean = true,
) {
    Button(
        onClick = aoTocar,
        enabled = ativo,
        modifier = modifier.height(altura),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cores.accent,
            contentColor = Cores.onAccent,
            disabledContainerColor = Cores.sunk,
            disabledContentColor = Cores.ink3,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icone != null) {
            Icon(icone, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, style = Estilo.botao)
    }
}

/** O botão contornado: fundo transparente, borda de 1.5. */
@Composable
fun BotaoContornado(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    cor: Color = Cores.ink,
    borda: Color = Cores.line,
    altura: Dp = 46.dp,
) {
    OutlinedButton(
        onClick = aoTocar,
        modifier = modifier.height(altura),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, borda),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cor),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icone != null) {
            Icon(icone, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = Estilo.controloForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Um grupo das Definições: cartão com raio 20 e borda. */
@Composable
fun GrupoCartao(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Cores.card)
            .border(1.dp, Cores.line, forma),
        content = conteudo,
    )
}

@Composable
fun DivisoriaGrupo() {
    HorizontalDivider(color = Cores.line, thickness = 1.dp)
}

/** Uma linha de um grupo: título, subtítulo opcional e algo à direita. */
@Composable
fun LinhaGrupo(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    aoTocar: (() -> Unit)? = null,
    direita: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (aoTocar != null) Modifier.clickable(onClick = aoTocar) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = Estilo.controlo.copy(fontSize = 16.sp), color = Cores.ink)
            if (subtitulo != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitulo, style = Estilo.ajuda, color = Cores.ink2)
            }
        }
        if (direita != null) {
            Spacer(Modifier.width(12.dp))
            direita()
        }
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Cores.ink3)
}

/** O interruptor com as cores do redesign. */
@Composable
fun SwitchPalavra(ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Switch(
        checked = ligado,
        onCheckedChange = aoMudar,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Cores.onAccent,
            checkedTrackColor = Cores.accent,
            checkedBorderColor = Cores.accent,
            uncheckedThumbColor = Cores.ink3,
            uncheckedTrackColor = Cores.sunk,
            uncheckedBorderColor = Cores.line,
        ),
    )
}

/** A snackbar global: cores invertidas, raio 14, ação em amarelo. */
@Composable
fun AnfitriaoSnackbar(estado: SnackbarHostState) {
    SnackbarHost(estado) { dados ->
        Snackbar(
            snackbarData = dados,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = Cores.invBg,
            contentColor = Cores.invInk,
            actionColor = Cores.invFill,
        )
    }
}

/**
 * A barra de cima dos ecrãs interiores: seta (ou ×) à esquerda, algo opcional
 * ao centro e à direita. 52 de altura, sem título por omissão.
 */
@Composable
fun BarraVoltar(
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    descricao: String = "Voltar",
    centro: (@Composable () -> Unit)? = null,
    direita: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = aoVoltar, modifier = Modifier.size(44.dp)) {
            Icon(icone, descricao, tint = Cores.ink)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (centro != null) centro()
        }
        Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.CenterEnd) {
            if (direita != null) direita()
        }
    }
}

/**
 * Sublinhado ondulado, como o de um corretor ortográfico: o termo que não se
 * encontrou. Desenha-se no fundo do espaço do texto; o texto deve ter algum
 * espaço em baixo (por exemplo `padding(bottom = 10.dp)` depois disto).
 */
fun Modifier.sublinhadoOndulado(cor: Color, espessura: Dp = 2.dp): Modifier = drawBehind {
    val amplitude = 2.dp.toPx()
    val comprimento = 9.dp.toPx()
    val y = size.height - 4.dp.toPx()
    val caminho = Path()
    var x = 0f
    caminho.moveTo(0f, y)
    while (x <= size.width) {
        caminho.lineTo(x, y + amplitude * sin(2 * PI * x / comprimento).toFloat())
        x += 1.5f
    }
    drawPath(caminho, cor, style = Stroke(width = espessura.toPx()))
}

/** Os cinco pontos de domínio de uma palavra (caixa de Leitner 0 a 5). */
@Composable
fun PontosDominio(nivel: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (i < nivel) Cores.accent else Cores.line)
            )
        }
    }
}
