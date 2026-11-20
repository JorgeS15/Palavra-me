package pt.jorges15.palavrame.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jorges15.palavrame.data.Entrada
import pt.jorges15.palavrame.data.PalavraGuardada
import pt.jorges15.palavrame.data.Texto
import pt.jorges15.palavrame.data.rotuloPos
import pt.jorges15.palavrame.jogo.Perguntas
import pt.jorges15.palavrame.ui.theme.Cores
import pt.jorges15.palavrame.ui.theme.Estilo
import pt.jorges15.palavrame.ui.theme.Literata
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * "Partilhar palavra": uma imagem com a palavra, o significado e o livro,
 * pronta para Stories ou para uma publicação. Desenho em
 * `design_handoff_partilhar_palavra/`.
 *
 * Tudo no telemóvel e sem rede: o cartão é um composable desenhado a 360 dp
 * de largura com a densidade fixada em 3, e por isso sai sempre com 1080 px,
 * seja qual for o ecrã. Grava-se em PNG na cache e entrega-se à partilha do
 * Android por um FileProvider.
 */

/** O que vai na imagem. */
data class DadosPartilha(
    val lemma: String,
    val classe: String?,
    /** Até duas aceções do dicionário (ou "O mesmo que: ..." sem definição). */
    val acecoes: List<String>,
    /** O significado escrito pelo próprio: tem prioridade sobre as aceções. */
    val meuSignificado: String?,
    val livro: String?,
    val autor: String?,
    val pagina: Int?,
    val frase: String?,
)

/** Junta a entrada do dicionário (se houver) com o registo da coleção (se houver). */
fun dadosPartilha(entrada: Entrada?, guardada: PalavraGuardada?): DadosPartilha? {
    val lemma = entrada?.lemma ?: guardada?.lemma ?: return null
    // As aceções da classe principal, que é a que o cartão mostra: com a
    // classe por aceção (1.21), as duas primeiras podiam ser de classes
    // diferentes e ficar "substantivo" por cima de uma definição de adjetivo.
    val daPrincipal = entrada?.let { e ->
        e.acecoes.filter { (it.pos ?: e.pos) == e.pos }.ifEmpty { e.acecoes }
    }
    // Uma remissão pura ("Vide ação") não diz nada numa imagem: mostra-se a
    // definição do destino, quando a há.
    val acecoes = daPrincipal?.take(2)?.map {
        val remissao = Perguntas.eRemissao(it.definicao) ||
            (it.remetePara != null && it.definicao.startsWith("Ver "))
        if (remissao && it.definicaoDoDestino != null) it.definicaoDoDestino
        else it.definicao
    }.orEmpty().ifEmpty {
        val sinonimos = entrada?.relacionadas.orEmpty().filter { it.relacao == "sinonimo" }
        if (sinonimos.isEmpty()) emptyList()
        else listOf("O mesmo que " + sinonimos.take(4).joinToString(", ") { it.lemma } + ".")
    }
    return DadosPartilha(
        lemma = lemma,
        classe = entrada?.pos?.takeIf { it.isNotBlank() && it != "desconhecido" }?.let(::rotuloPos),
        acecoes = acecoes,
        meuSignificado = guardada?.significado?.trim()?.takeIf { it.isNotEmpty() },
        livro = guardada?.livro?.trim()?.takeIf { it.isNotEmpty() },
        autor = guardada?.autor?.trim()?.takeIf { it.isNotEmpty() },
        pagina = guardada?.page,
        frase = guardada?.frase?.trim()?.takeIf { it.isNotEmpty() },
    )
}

enum class FormatoPartilha(val rotulo: String) { STORIES("Stories"), PUBLICACAO("Publicação") }

// --- medidas e cores (do handoff) ---------------------------------------------

private class Medidas(
    val altura: Dp,
    val cima: Dp, val lados: Dp, val baixo: Dp,
    val espaco: Dp,
    val palavraMin: Float, val palavraMax: Float,
    val util: Float,
    val classe: Float,
    val acecao: Float, val acecaoEntrelinha: Float,
    val numero: Float,
    val raioBloco: Dp, val paddingBloco: Dp, val espacoBloco: Dp,
    val frase: Float, val fraseEntrelinha: Float, val fraseLinhas: Int,
    val meta: Float,
    /** Linhas do significado: total e da 1.ª aceção, sem e com livro. */
    val linhasSemLivro: Int, val primeiraSemLivro: Int,
    val linhasComLivro: Int, val primeiraComLivro: Int,
    /** Capa grande (com título e autor) ou só um retângulo de cor. */
    val capaGrande: Boolean,
)

private val STORIES = Medidas(
    altura = 640.dp, cima = 60.dp, lados = 30.dp, baixo = 72.dp, espaco = 28.dp,
    palavraMin = 36f, palavraMax = 64f, util = 300f,
    classe = 18f, acecao = 18f, acecaoEntrelinha = 1.45f, numero = 22f,
    raioBloco = 16.dp, paddingBloco = 14.dp, espacoBloco = 14.dp,
    frase = 15f, fraseEntrelinha = 1.45f, fraseLinhas = 3, meta = 11.5f,
    linhasSemLivro = 6, primeiraSemLivro = 3, linhasComLivro = 5, primeiraComLivro = 3,
    capaGrande = true,
)

private val PUBLICACAO = Medidas(
    altura = 450.dp, cima = 30.dp, lados = 28.dp, baixo = 26.dp, espaco = 18.dp,
    palavraMin = 32f, palavraMax = 54f, util = 304f,
    classe = 16f, acecao = 16f, acecaoEntrelinha = 1.42f, numero = 19f,
    raioBloco = 14.dp, paddingBloco = 12.dp, espacoBloco = 12.dp,
    frase = 13.5f, fraseEntrelinha = 1.4f, fraseLinhas = 2, meta = 11f,
    linhasSemLivro = 4, primeiraSemLivro = 2, linhasComLivro = 3, primeiraComLivro = 2,
    capaGrande = false,
)

private fun medidas(f: FormatoPartilha) = if (f == FormatoPartilha.STORIES) STORIES else PUBLICACAO

/** A largura do cartão em dp; a densidade fixa em 3 dá os 1080 px. */
private const val LARGURA_CARTAO = 360
private const val DENSIDADE_IMAGEM = 3f

private class CoresCartao(
    val bg: Color, val ink: Color, val ink2: Color,
    val accent: Color, val mark: Color, val bloco: Color,
)

/** As cores do cartão: as da paleta e do modo em uso (Definições, Aspeto). */
@Composable
private fun coresDaPaleta() = CoresCartao(
    bg = Cores.bg, ink = Cores.ink, ink2 = Cores.ink2,
    accent = Cores.accent, mark = Cores.mark, bloco = Cores.sunk,
)

private fun cartaoCorDoLivro(cor: Color) = CoresCartao(
    bg = cor, ink = TextoCapa, ink2 = TextoCapa.copy(alpha = 0.8f),
    accent = Color(0xFFF6D77F), mark = Color(0xFFF6D77F).copy(alpha = 0.34f),
    bloco = Color.Black.copy(alpha = 0.2f),
)

// --- o cartão -------------------------------------------------------------------

/**
 * O cartão, a 360 dp de largura. Desenhar dentro de uma densidade fixa (ver
 * [PreVisualizacao]) para a imagem ter sempre 1080 px.
 */
@Composable
private fun CartaoPartilha(
    dados: DadosPartilha,
    formato: FormatoPartilha,
    usarCorDoLivro: Boolean,
) {
    val m = medidas(formato)
    val corLivro = if (usarCorDoLivro) corDoLivro(dados.livro) else null
    val c = if (corLivro != null) cartaoCorDoLivro(corLivro) else coresDaPaleta()
    // Na variante cor do livro, a lombada de 12 dp come margem e largura útil.
    val lombada = if (corLivro != null) 12.dp else 0.dp
    val util = m.util - lombada.value

    Box(Modifier.size(LARGURA_CARTAO.dp, m.altura).background(c.bg)) {
        if (corLivro != null) {
            Box(Modifier.fillMaxHeight().width(12.dp).background(Color.Black.copy(alpha = 0.24f)))
            Box(
                Modifier.fillMaxHeight().width(1.dp).offset(x = 12.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
        Column(
            Modifier.fillMaxSize()
                .padding(start = m.lados + lombada, end = m.lados, top = m.cima, bottom = m.baixo),
        ) {
            // O conteúdo centrado na vertical; a assinatura sempre em baixo.
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                PalavraAjustada(dados.lemma, m, util, c)
                dados.classe?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = TextStyle(
                            fontFamily = Estilo.leitura, fontStyle = FontStyle.Italic,
                            fontSize = m.classe.sp,
                        ),
                        color = c.ink2,
                    )
                }
                val temLivro = dados.livro != null
                if (dados.meuSignificado != null || dados.acecoes.isNotEmpty()) {
                    Spacer(Modifier.height(m.espaco))
                    Significado(dados, m, c, temLivro)
                }
                if (temLivro) {
                    Spacer(Modifier.height(m.espaco))
                    BlocoLivro(dados, m, c, mostrarCapa = corLivro == null)
                }
            }
            Assinatura(c)
        }
    }
}

/** A palavra, ajustada à largura e partida em duas linhas se for preciso. */
@Composable
private fun PalavraAjustada(lemma: String, m: Medidas, util: Float, c: CoresCartao) {
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    val base = TextStyle(
        fontFamily = Estilo.leitura, fontWeight = FontWeight.SemiBold,
        lineHeight = 1.02.em, letterSpacing = (-0.03).em,
    )
    val (linhas, tamanho) = remember(lemma, util, m, base) {
        ajustarPalavra(lemma, util, m.palavraMin, m.palavraMax) { texto ->
            val px = medidor.measure(
                texto, base.copy(fontSize = 100.sp), softWrap = false, maxLines = 1,
            ).size.width
            with(densidade) { px.toDp().value }
        }
    }
    val estilo = base.copy(fontSize = tamanho.sp)
    Column {
        linhas.forEach { linha ->
            Text(
                linha,
                style = estilo,
                color = c.ink,
                maxLines = 1,
                softWrap = false,
                // O marcador: de 60% a 90% da altura da linha, com 0.06em de
                // folga para cada lado (puxado para fora da margem).
                modifier = Modifier
                    .offset(x = (-0.06f * tamanho).dp)
                    .drawBehind {
                        drawRect(
                            c.mark,
                            topLeft = Offset(0f, size.height * 0.60f),
                            size = Size(size.width, size.height * 0.30f),
                        )
                    }
                    .padding(horizontal = (0.06f * tamanho).dp),
            )
        }
    }
}

/** O significado do próprio (com rótulo) ou até duas aceções numeradas. */
@Composable
private fun Significado(dados: DadosPartilha, m: Medidas, c: CoresCartao, temLivro: Boolean) {
    val total = if (temLivro) m.linhasComLivro else m.linhasSemLivro
    val primeira = if (temLivro) m.primeiraComLivro else m.primeiraSemLivro
    val estilo = TextStyle(
        fontFamily = Estilo.leitura, fontSize = m.acecao.sp, lineHeight = m.acecaoEntrelinha.em,
    )
    val meu = dados.meuSignificado
    if (meu != null) {
        Text(
            "O MEU SIGNIFICADO",
            style = TextStyle(
                fontFamily = Estilo.controlos, fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp, letterSpacing = 0.12.em,
            ),
            color = c.accent,
        )
        Spacer(Modifier.height(6.dp))
        Text(meu, style = estilo, color = c.ink, maxLines = total, overflow = TextOverflow.Ellipsis)
        return
    }
    val acecoes = dados.acecoes.take(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        acecoes.forEachIndexed { i, texto ->
            val linhas = when {
                acecoes.size == 1 -> total
                i == 0 -> primeira
                else -> total - primeira
            }
            Row {
                Text(
                    "${i + 1}",
                    style = TextStyle(
                        fontFamily = Estilo.leitura, fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium, fontSize = m.numero.sp,
                        lineHeight = (m.acecao * m.acecaoEntrelinha).sp,
                    ),
                    color = c.accent,
                    modifier = Modifier.width(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    texto, style = estilo, color = c.ink,
                    maxLines = linhas, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A frase do livro, com a capa (ou só a cor) e "Livro · Autor · p. N". */
@Composable
private fun BlocoLivro(dados: DadosPartilha, m: Medidas, c: CoresCartao, mostrarCapa: Boolean) {
    val livro = dados.livro ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(m.raioBloco))
            .background(c.bloco)
            .padding(m.paddingBloco),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mostrarCapa) {
            if (m.capaGrande) CapaCartao(livro, dados.autor)
            else Box(
                Modifier.size(34.dp, 48.dp)
                    .clip(RoundedCornerShape(2.dp, 5.dp, 5.dp, 2.dp))
                    .background(corDoLivro(livro) ?: Color.Gray)
            )
            Spacer(Modifier.width(m.espacoBloco))
        }
        Column(Modifier.weight(1f)) {
            dados.frase?.let {
                Text(
                    "\"$it\"",
                    style = TextStyle(
                        fontFamily = Estilo.leitura, fontStyle = FontStyle.Italic,
                        fontSize = m.frase.sp, lineHeight = m.fraseEntrelinha.em,
                    ),
                    color = c.ink,
                    maxLines = m.fraseLinhas,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                listOfNotNull(livro, dados.autor, dados.pagina?.let { "p. $it" }).joinToString(" · "),
                style = TextStyle(fontFamily = Estilo.controlos, fontSize = m.meta.sp),
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A capa pequena do Stories: 64 × 90, título em itálico e autor em baixo. */
@Composable
private fun CapaCartao(titulo: String, autor: String?) {
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    // O título desce de 8.5 até 6.5 para a palavra mais comprida caber nos
    // 51 dp úteis; parte só entre palavras.
    val estiloBase = TextStyle(
        fontFamily = Estilo.leitura, fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.SemiBold, lineHeight = 1.15.em,
    )
    val tamanho = remember(titulo, estiloBase) {
        val maior = titulo.split(" ").filter { it.isNotBlank() }.maxOfOrNull { palavra ->
            val px = medidor.measure(palavra, estiloBase.copy(fontSize = 8.5.sp), softWrap = false).size.width
            with(densidade) { px.toDp().value }
        } ?: 0f
        if (maior <= 51f) 8.5f else (8.5f * 51f / maior).coerceAtLeast(6.5f)
    }
    Box(
        Modifier.size(64.dp, 90.dp)
            .clip(RoundedCornerShape(2.dp, 5.dp, 5.dp, 2.dp))
            .background(corDoLivro(titulo) ?: Color.Gray)
    ) {
        Box(Modifier.fillMaxHeight().width(4.dp).background(Color.Black.copy(alpha = 0.22f)))
        Column(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 5.dp, top = 7.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                titulo, style = estiloBase.copy(fontSize = tamanho.sp), color = TextoCapa,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            autor?.let {
                Text(
                    it.uppercase(),
                    style = TextStyle(fontFamily = Estilo.controlos, fontSize = 6.5.sp, letterSpacing = 0.04.em),
                    color = TextoCapa.copy(alpha = 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Palavra-me" com o "-me" no marcador, a linha curta e "Google Play". */
@Composable
private fun Assinatura(c: CoresCartao) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row {
                val logo = TextStyle(
                    // O logótipo fica sempre em Literata, seja qual for a letra.
                    fontFamily = Literata, fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                )
                Text("Palavra", style = logo, color = c.ink)
                Text(
                    "-me", style = logo, color = c.ink,
                    modifier = Modifier.drawBehind {
                        drawRect(
                            c.mark,
                            topLeft = Offset(0f, size.height * 0.55f),
                            size = Size(size.width, size.height * 0.37f),
                        )
                    },
                )
            }
            Text(
                "Um dicionário para quem lê",
                style = TextStyle(fontFamily = Estilo.controlos, fontSize = 10.5.sp),
                color = c.ink2,
            )
        }
        Text(
            "Google Play",
            style = TextStyle(fontFamily = Estilo.controlos, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
            color = c.ink2,
        )
    }
}

// --- ajuste e corte da palavra (puro, testado) ----------------------------------

private const val VOGAIS = "aeiouáéíóúâêôãõàüy"

private fun vogal(ch: Char) = ch in VOGAIS

/** Pares que não se separam: "br", "pl", "tr"..., os dígrafos "ch", "lh", "nh", e "qu"/"gu". */
private fun inseparavel(a: Char, b: Char) =
    (b in "lr" && a in "bcdfgptv") || (b == 'h' && a in "cln") || (a in "qg" && b == 'u')

/**
 * Onde se pode partir a palavra em duas linhas: índices `i` em que a 1.ª linha
 * fica com `palavra[0, i)`.
 *
 * O dicionário não traz a silabação, por isso usa-se a regra simples do
 * português: parte-se antes de uma consoante seguida de vogal (so-tur-no),
 * entre duas consoantes (car-ro, nas-cer), nunca dentro de "br", "pl", "ch",
 * "lh", "nh", "qu" (a-bra-ço, ma-lha). Entre vogais não se parte, por
 * cautela (os hiatos e os ditongos confundem-se). Também se parte num espaço
 * (locuções) ou a seguir a um hífen que já lá esteja.
 */
internal fun pontosDeCorte(palavra: String): List<Int> {
    val w = palavra.lowercase()
    val out = mutableListOf<Int>()
    for (i in 1 until w.length) {
        val p = w[i - 1]
        val ch = w[i]
        if (ch == ' ' || p == '-') { out.add(i); continue }
        if (ch == '-' || p == ' ') continue
        if (i < 2 || w.length - i < 2) continue
        if (w.substring(0, i).none(::vogal) || w.substring(i).none(::vogal)) continue
        val s = w.getOrNull(i + 1)
        val pode = when {
            vogal(p) && !vogal(ch) -> s != null && (vogal(s) || inseparavel(ch, s))
            !vogal(p) && !vogal(ch) ->
                !inseparavel(p, ch) && (s == null || vogal(s) || inseparavel(ch, s))
            else -> false
        }
        if (pode) out.add(i)
    }
    return out
}

/** As duas linhas de um corte: com hífen, ou sem ele num espaço ou num hífen já existente. */
internal fun partir(palavra: String, i: Int): Pair<String, String> = when {
    palavra[i] == ' ' -> palavra.substring(0, i) to palavra.substring(i + 1)
    palavra[i - 1] == '-' -> palavra.substring(0, i) to palavra.substring(i)
    else -> (palavra.substring(0, i) + "-") to palavra.substring(i)
}

/**
 * O tamanho da palavra (e as linhas): o maior que cabe na largura útil, até
 * ao máximo, com 3% de folga. Se numa linha ficar abaixo do mínimo, parte-se
 * em duas no corte que dá o tamanho maior; nunca abaixo do mínimo nem mais de
 * duas linhas.
 *
 * `largura100` mede um texto a 100 sp e devolve a largura em dp.
 */
internal fun ajustarPalavra(
    palavra: String,
    util: Float,
    minimo: Float,
    maximo: Float,
    largura100: (String) -> Float,
): Pair<List<String>, Float> {
    fun tamanho(vararg linhas: String): Float {
        val maior = linhas.maxOf { largura100(it) }
        return if (maior <= 0f) maximo else min(maximo, util / maior * 100f * 0.97f)
    }
    val umaLinha = tamanho(palavra)
    if (umaLinha >= minimo) return listOf(palavra) to umaLinha

    var melhor: Pair<List<String>, Float>? = null
    for (i in pontosDeCorte(palavra)) {
        val (a, b) = partir(palavra, i)
        val t = tamanho(a, b)
        if (melhor == null || t > melhor.second) melhor = listOf(a, b) to t
    }
    return melhor?.let { it.first to maxOf(it.second, minimo) } ?: (listOf(palavra) to minimo)
}

// --- o ecrã de partilha ---------------------------------------------------------

/**
 * Pré-visualização e partilha. Abre-se do ecrã da palavra e da coleção.
 *
 * `aoPartilharTexto`, quando existe, oferece também a partilha antiga, só com
 * texto (para quem prefere mandar a definição numa mensagem).
 */
@Composable
fun DialogoPartilhar(
    dados: DadosPartilha,
    aoFechar: () -> Unit,
    aoPartilharTexto: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val ambito = rememberCoroutineScope()
    var formato by rememberSaveable { mutableStateOf(FormatoPartilha.STORIES) }
    var comCorDoLivro by rememberSaveable { mutableStateOf(false) }
    var aGerar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val camada = rememberGraphicsLayer()

    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Cores.bg) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                BarraVoltar(
                    aoFechar,
                    icone = Icons.Rounded.Close,
                    descricao = "Fechar",
                    centro = { Text("Partilhar", style = Estilo.controloForte, color = Cores.ink) },
                )
                PreVisualizacao(
                    formato = formato,
                    camada = camada,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    CartaoPartilha(dados, formato, comCorDoLivro && dados.livro != null)
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Segmentado(
                        FormatoPartilha.entries.map { it.rotulo },
                        selecionado = formato.ordinal,
                        aoEscolher = { formato = FormatoPartilha.entries[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (dados.livro != null) {
                        Segmentado(
                            listOf("Cores da app", "Cor do livro"),
                            selecionado = if (comCorDoLivro) 1 else 0,
                            aoEscolher = { comCorDoLivro = it == 1 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    erro?.let { Text(it, style = Estilo.ajuda, color = Cores.err) }
                    BotaoPrincipal(
                        if (aGerar) "A preparar…" else "Partilhar imagem",
                        aoTocar = {
                            aGerar = true
                            erro = null
                            ambito.launch {
                                erro = try {
                                    partilharImagem(context, camada, dados.lemma); null
                                } catch (e: Exception) {
                                    "Não foi possível preparar a imagem."
                                }
                                aGerar = false
                            }
                        },
                        ativo = !aGerar,
                        icone = Icons.Rounded.IosShare,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (aoPartilharTexto != null) {
                        TextButton(
                            onClick = aoPartilharTexto,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = Cores.ink2),
                        ) { Text("Partilhar só o texto", style = Estilo.controlo) }
                    }
                }
            }
        }
    }
}

/**
 * O cartão desenhado em tamanho real (1080 px de largura, densidade 3) e
 * mostrado reduzido para caber no ecrã. O que se grava na [camada] é o
 * desenho em tamanho real: a redução é só da pré-visualização.
 */
@Composable
private fun PreVisualizacao(
    formato: FormatoPartilha,
    camada: GraphicsLayer,
    modifier: Modifier = Modifier,
    cartao: @Composable () -> Unit,
) {
    val larguraPx = (LARGURA_CARTAO * DENSIDADE_IMAGEM).roundToInt()
    val alturaPx = (medidas(formato).altura.value * DENSIDADE_IMAGEM).roundToInt()
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .layout { mensuravel, limites ->
                    val escala = min(
                        limites.maxWidth.toFloat() / larguraPx,
                        limites.maxHeight.toFloat() / alturaPx,
                    )
                    val p = mensuravel.measure(Constraints.fixed(larguraPx, alturaPx))
                    layout((larguraPx * escala).roundToInt(), (alturaPx * escala).roundToInt()) {
                        p.placeWithLayer(0, 0) {
                            scaleX = escala
                            scaleY = escala
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
        ) {
            CompositionLocalProvider(LocalDensity provides Density(DENSIDADE_IMAGEM, 1f)) {
                Box(
                    Modifier.drawWithContent {
                        camada.record { this@drawWithContent.drawContent() }
                        drawLayer(camada)
                    }
                ) { cartao() }
            }
        }
    }
}

/** Grava a imagem na cache e abre a partilha do Android. */
private suspend fun partilharImagem(context: Context, camada: GraphicsLayer, lemma: String) {
    val imagem = camada.toImageBitmap().asAndroidBitmap()
    val ficheiro = withContext(Dispatchers.IO) {
        // Uma pasta só para isto, limpa a cada partilha: não se acumulam
        // imagens antigas na cache.
        val pasta = File(context.cacheDir, "partilha").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val nome = Texto.normalizar(lemma).filter { it.isLetterOrDigit() }.ifEmpty { "palavra" }
        val f = File(pasta, "palavra-me-$nome.png")
        // Um bitmap de hardware não se escreve diretamente.
        val escrevivel = if (imagem.config == Bitmap.Config.HARDWARE)
            imagem.copy(Bitmap.Config.ARGB_8888, false) else imagem
        f.outputStream().use { escrevivel.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.partilha", ficheiro)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(envio, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
}
