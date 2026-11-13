package pt.jorges15.palavrame.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import pt.jorges15.palavrame.R
import pt.jorges15.palavrame.data.LetraId
import pt.jorges15.palavrame.data.PaletaId

/*
 * O tema do redesign (v2): papel e tinta, um laranja-tijolo de destaque e um
 * marcador amarelo, o gesto de sublinhar uma palavra num livro.
 *
 * Os valores vêm do documento de entrega do design
 * (`Palavra-me app redesign/design_handoff_palavrame_redesign/README.md`),
 * com paridade total entre claro e escuro.
 */

/**
 * Todas as cores do redesign, incluindo as que o Material 3 não tem papel para
 * elas (o marcador, o certo/errado do jogo, o cartão de cores invertidas).
 * Lêem-se nos ecrãs com `Cores.accent`, `Cores.mark`, etc.
 */
@Immutable
data class CoresPalavra(
    val bg: Color,
    val card: Color,
    val sunk: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val mark: Color,
    val ok: Color,
    val okSoft: Color,
    val err: Color,
    val errSoft: Color,
    val invBg: Color,
    val invInk: Color,
    val invMuted: Color,
    val invTrack: Color,
    val invFill: Color,
    val invCtaInk: Color,
    val scrim: Color,
)

val LocalCores = staticCompositionLocalOf { Paletas.PapelClaro }

/** Se o modo em uso é o escuro (para as amostras de paleta das Definições). */
val LocalModoEscuro = staticCompositionLocalOf { false }

/** As cores do tema em uso: `Cores.accent`, `Cores.ink2`... */
val Cores: CoresPalavra
    @Composable @ReadOnlyComposable get() = LocalCores.current

/** O mapeamento para os papéis do Material 3, para os componentes de série. */
private fun esquema(c: CoresPalavra, escuro: Boolean): ColorScheme =
    if (escuro) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accentInk,
        secondary = c.ink2, onSecondary = c.bg,
        secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = c.accentInk, onTertiary = c.onAccent,
        tertiaryContainer = c.accentSoft, onTertiaryContainer = c.accentInk,
        background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.card, surfaceContainerLow = c.card,
        surfaceContainer = c.card, surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.sunk,
        inverseSurface = c.invBg, inverseOnSurface = c.invInk, inversePrimary = c.invFill,
        outline = c.ink3, outlineVariant = c.line,
        error = c.err, onError = c.onAccent, errorContainer = c.errSoft, onErrorContainer = c.err,
        scrim = c.scrim,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accentInk,
        secondary = c.ink2, onSecondary = c.bg,
        secondaryContainer = c.sunk, onSecondaryContainer = c.ink,
        tertiary = c.accentInk, onTertiary = c.onAccent,
        tertiaryContainer = c.accentSoft, onTertiaryContainer = c.accentInk,
        background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink,
        surfaceVariant = c.sunk, onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.card, surfaceContainerLow = c.card,
        surfaceContainer = c.card, surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.sunk,
        inverseSurface = c.invBg, inverseOnSurface = c.invInk, inversePrimary = c.invFill,
        outline = c.ink3, outlineVariant = c.line,
        error = c.err, onError = c.onAccent, errorContainer = c.errSoft, onErrorContainer = c.err,
        scrim = c.scrim,
    )

// --- tipografia ------------------------------------------------------------

/**
 * Literata para o conteúdo (lemas, definições, títulos, números, citações) e
 * Instrument Sans para os controlos (botões, rótulos, separadores, metadados).
 * Vão dentro da app, em `res/font/`, e não descarregadas da internet: a app
 * não tem permissão de rede. Licença SIL OFL 1.1 (ver `assets/licencas/`).
 */
val Literata = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.literata_medium, FontWeight.Medium),
    Font(R.font.literata_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.literata_semibold, FontWeight.SemiBold),
    Font(R.font.literata_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/**
 * Atkinson Hyperlegible **Next** (2025), do Braille Institute: a original só
 * tem normal e negrito, e a app usa também 500 e 600. Aparece no ecrã como
 * "Atkinson Hyperlegible".
 */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_medium, FontWeight.Medium),
    Font(R.font.atkinson_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

/**
 * Os estilos de texto do redesign, pelo papel de cada um (sem cor), numa
 * letra. `leitura` é a família do conteúdo (lemas, definições, títulos,
 * números, citações); `controlos` a dos botões, rótulos e metadados. Os
 * tamanhos, pesos e espaçamentos são os mesmos em todas as letras.
 *
 * Lê-se nos ecrãs com `Estilo.lema`, `Estilo.botao`, como antes: `Estilo` é
 * agora o conjunto da letra escolhida em Definições, Aspeto.
 */
@Immutable
class EstilosPalavra(val leitura: FontFamily, val controlos: FontFamily) {
    private fun lit(tam: Int, peso: FontWeight = FontWeight.Normal, italico: Boolean = false) =
        TextStyle(
            fontFamily = leitura, fontSize = tam.sp, fontWeight = peso,
            fontStyle = if (italico) FontStyle.Italic else FontStyle.Normal,
        )

    private fun sans(tam: Int, peso: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = controlos, fontSize = tam.sp, fontWeight = peso)

    /** O lema na Palavra e no Rever: 50/1.05, 600, marcador por trás. */
    val lema = lit(50, FontWeight.SemiBold).copy(lineHeight = 1.05.em, letterSpacing = (-0.03).em)
    /** "O que encontraste hoje?". */
    val pergunta = lit(36, FontWeight.Medium).copy(lineHeight = 1.06.em, letterSpacing = (-0.025).em)
    /** Títulos de ecrã (Coleção, Definições). */
    val tituloEcra = lit(32, FontWeight.Medium).copy(lineHeight = 1.1.em, letterSpacing = (-0.02).em)
    /** O lema na folha de Registar. */
    val lemaFolha = lit(34, FontWeight.SemiBold).copy(lineHeight = 1.15.em, letterSpacing = (-0.02).em)
    /** O termo na Não encontrada. */
    val termo = lit(44, FontWeight.Medium).copy(lineHeight = 1.1.em, letterSpacing = (-0.02).em)
    /** O logótipo fica sempre em Literata itálico, seja qual for a letra. */
    val logotipo = TextStyle(
        fontFamily = Literata, fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic,
    )
    val nivel = lit(28, FontWeight.Medium, italico = true).copy(lineHeight = 1.15.em)
    val numeroEstat = lit(24, FontWeight.Medium)
    val acecao = lit(17).copy(fontSize = 17.5.sp, lineHeight = 1.55.em)
    val numeroAcecao = lit(26, FontWeight.Medium, italico = true).copy(lineHeight = 1.em)
    val dominio = lit(14, italico = true)
    val classe = lit(17, italico = true)
    val citacao = lit(16, italico = true).copy(fontSize = 16.5.sp, lineHeight = 1.5.em)
    val opcao = lit(16).copy(fontSize = 16.5.sp, lineHeight = 1.45.em)
    val recente = lit(21).copy(lineHeight = 1.35.em)
    val linhaLema = lit(20, FontWeight.Medium)
    val resultado = lit(30, FontWeight.SemiBold)
    val ganho = lit(26)
    val sugestao = lit(19)
    val parecida = lit(26, FontWeight.SemiBold)
    val campo = lit(16)
    val pesquisa = lit(18)
    val chipLivro = lit(15)
    val capaTitulo = lit(16, FontWeight.SemiBold, italico = true).copy(lineHeight = 1.15.em)
    val grupoTitulo = lit(18, FontWeight.SemiBold)
    val corpo = lit(18).copy(lineHeight = 1.45.em)

    /** Sobretítulo de secção: 12/600, maiúsculas, espaçado. */
    val overline = sans(12, FontWeight.SemiBold).copy(letterSpacing = 0.1.em)
    val botao = sans(16, FontWeight.SemiBold)
    val controlo = sans(15, FontWeight.Medium)
    val controloForte = sans(15, FontWeight.SemiBold)
    val texto = sans(15)
    val ajuda = sans(13).copy(lineHeight = 1.4.em)
    val meta = sans(12)
    val rotulo = sans(12, FontWeight.SemiBold)
}

private val EstilosLiterata = EstilosPalavra(Literata, InstrumentSans)
private val EstilosFigtree = EstilosPalavra(Figtree, Figtree)
private val EstilosAtkinson = EstilosPalavra(Atkinson, Atkinson)
private val EstilosSistema = EstilosPalavra(FontFamily.Default, FontFamily.Default)

/** Os estilos de uma letra. "Do telemóvel" é a do sistema, sem ficheiros. */
fun estilosDe(letra: LetraId): EstilosPalavra = when (letra) {
    LetraId.LITERATA -> EstilosLiterata
    LetraId.FIGTREE -> EstilosFigtree
    LetraId.ATKINSON -> EstilosAtkinson
    LetraId.SISTEMA -> EstilosSistema
}

val LocalEstilos = staticCompositionLocalOf { EstilosLiterata }

/** Os estilos da letra em uso: `Estilo.lema`, `Estilo.botao`... */
val Estilo: EstilosPalavra
    @Composable @ReadOnlyComposable get() = LocalEstilos.current

private fun tipografia(e: EstilosPalavra) = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = e.leitura),
        displayMedium = base.displayMedium.copy(fontFamily = e.leitura),
        displaySmall = base.displaySmall.copy(fontFamily = e.leitura),
        headlineLarge = base.headlineLarge.copy(fontFamily = e.leitura),
        headlineMedium = base.headlineMedium.copy(fontFamily = e.leitura),
        headlineSmall = base.headlineSmall.copy(fontFamily = e.leitura),
        titleLarge = base.titleLarge.copy(fontFamily = e.leitura),
        titleMedium = base.titleMedium.copy(fontFamily = e.controlos, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = e.controlos, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = e.leitura, fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = e.controlos),
        bodySmall = base.bodySmall.copy(fontFamily = e.controlos),
        labelLarge = base.labelLarge.copy(fontFamily = e.controlos),
        labelMedium = base.labelMedium.copy(fontFamily = e.controlos),
        labelSmall = base.labelSmall.copy(fontFamily = e.controlos),
    )
}

@Composable
fun TemaPalavrame(
    preferido: pt.jorges15.palavrame.data.Tema = pt.jorges15.palavrame.data.Tema.SISTEMA,
    paleta: PaletaId = PaletaId.PAPEL,
    letra: LetraId = LetraId.LITERATA,
    content: @Composable () -> Unit,
) {
    val escuroAtivo = when (preferido) {
        pt.jorges15.palavrame.data.Tema.SISTEMA -> isSystemInDarkTheme()
        pt.jorges15.palavrame.data.Tema.CLARO -> false
        pt.jorges15.palavrame.data.Tema.ESCURO -> true
    }
    // A mudança de paleta ou de letra aplica-se na hora, sem reiniciar.
    val cores = Paletas.de(paleta, escuroAtivo)
    val estilos = estilosDe(letra)
    val tipo = remember(estilos) { tipografia(estilos) }

    /*
     * As barras do sistema seguem o tema da app, não o do telemóvel.
     *
     * Sem isto, quem punha a app em escuro com o telemóvel em claro ficava
     * com a barra de navegação de baixo clara por cima de um ecrã escuro.
     * `isAppearanceLight` a verdadeiro pede ícones escuros.
     */
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val janela = (vista.context as Activity).window
            WindowCompat.getInsetsController(janela, vista).apply {
                isAppearanceLightStatusBars = !escuroAtivo
                isAppearanceLightNavigationBars = !escuroAtivo
            }
            // O fundo da janela segue a paleta: é o que se vê por trás do
            // teclado e nas transições. (O do arranque, antes do Compose,
            // fica no tom Papel do tema XML.)
            janela.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(cores.bg.toArgb())
            )
        }
    }

    // Sem cores dinâmicas do sistema: o papel e a tinta são a identidade da app.
    CompositionLocalProvider(
        LocalCores provides cores,
        LocalEstilos provides estilos,
        LocalModoEscuro provides escuroAtivo,
    ) {
        MaterialTheme(
            colorScheme = esquema(cores, escuroAtivo),
            typography = tipo,
            content = content,
        )
    }
}
