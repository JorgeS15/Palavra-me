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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

private val CoresClaras = CoresPalavra(
    bg = Color(0xFFF4EEE3),
    card = Color(0xFFFBF8F2),
    sunk = Color(0xFFEBE3D5),
    ink = Color(0xFF1E1A15),
    ink2 = Color(0xFF5C5147),
    ink3 = Color(0xFF76695D),
    line = Color(0xFFDCD1C0),
    accent = Color(0xFFB0502A),
    accentInk = Color(0xFF8A3B1B),
    accentSoft = Color(0xFFF2DCCB),
    onAccent = Color(0xFFFFFFFF),
    mark = Color(0xFFF6D77F),
    ok = Color(0xFF2E6A46),
    okSoft = Color(0xFFDDEBDF),
    err = Color(0xFFA6321F),
    errSoft = Color(0xFFF6DCD4),
    invBg = Color(0xFF1E1A15),
    invInk = Color(0xFFF4EEE3),
    invMuted = Color(0xFFB9AD9E),
    invTrack = Color(0xFF3B342B),
    invFill = Color(0xFFF2C14E),
    invCtaInk = Color(0xFF1E1A15),
    scrim = Color(0x7314100A),          // rgba(20,16,10,.45)
)

private val CoresEscuras = CoresPalavra(
    bg = Color(0xFF14110D),
    card = Color(0xFF1C1813),
    sunk = Color(0xFF26201A),
    ink = Color(0xFFF0E8DB),
    ink2 = Color(0xFFC9BDAD),
    ink3 = Color(0xFFA19483),
    line = Color(0xFF382F27),
    accent = Color(0xFFE58A5C),
    accentInk = Color(0xFFF2A47E),
    accentSoft = Color(0xFF3A2418),
    onAccent = Color(0xFF1E120B),
    mark = Color(0xFF5E4A14),
    ok = Color(0xFF86C9A0),
    okSoft = Color(0xFF1C3124),
    err = Color(0xFFF08C76),
    errSoft = Color(0xFF3A1E17),
    invBg = Color(0xFFF0E8DB),
    invInk = Color(0xFF1E1A15),
    invMuted = Color(0xFF5C5147),
    invTrack = Color(0xFFD6CBBB),
    invFill = Color(0xFFB0502A),
    invCtaInk = Color(0xFFFFFFFF),
    scrim = Color(0x99000000),          // rgba(0,0,0,.6)
)

val LocalCores = staticCompositionLocalOf { CoresClaras }

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

/** Os estilos de texto do redesign, pelo papel de cada um (sem cor). */
object Estilo {
    private fun lit(tam: Int, peso: FontWeight = FontWeight.Normal, italico: Boolean = false) =
        TextStyle(
            fontFamily = Literata, fontSize = tam.sp, fontWeight = peso,
            fontStyle = if (italico) FontStyle.Italic else FontStyle.Normal,
        )

    private fun sans(tam: Int, peso: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = InstrumentSans, fontSize = tam.sp, fontWeight = peso)

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
    val logotipo = lit(22, FontWeight.SemiBold, italico = true)
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

private val tipografia = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Literata),
        displayMedium = base.displayMedium.copy(fontFamily = Literata),
        displaySmall = base.displaySmall.copy(fontFamily = Literata),
        headlineLarge = base.headlineLarge.copy(fontFamily = Literata),
        headlineMedium = base.headlineMedium.copy(fontFamily = Literata),
        headlineSmall = base.headlineSmall.copy(fontFamily = Literata),
        titleLarge = base.titleLarge.copy(fontFamily = Literata),
        titleMedium = base.titleMedium.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Literata, fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = InstrumentSans),
        bodySmall = base.bodySmall.copy(fontFamily = InstrumentSans),
        labelLarge = base.labelLarge.copy(fontFamily = InstrumentSans),
        labelMedium = base.labelMedium.copy(fontFamily = InstrumentSans),
        labelSmall = base.labelSmall.copy(fontFamily = InstrumentSans),
    )
}

@Composable
fun TemaPalavrame(
    preferido: pt.jorges15.palavrame.data.Tema = pt.jorges15.palavrame.data.Tema.SISTEMA,
    content: @Composable () -> Unit,
) {
    val escuroAtivo = when (preferido) {
        pt.jorges15.palavrame.data.Tema.SISTEMA -> isSystemInDarkTheme()
        pt.jorges15.palavrame.data.Tema.CLARO -> false
        pt.jorges15.palavrame.data.Tema.ESCURO -> true
    }
    val cores = if (escuroAtivo) CoresEscuras else CoresClaras

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
        }
    }

    // Sem cores dinâmicas do sistema: o papel e a tinta são a identidade da app.
    CompositionLocalProvider(LocalCores provides cores) {
        MaterialTheme(
            colorScheme = esquema(cores, escuroAtivo),
            typography = tipografia,
            content = content,
        )
    }
}
