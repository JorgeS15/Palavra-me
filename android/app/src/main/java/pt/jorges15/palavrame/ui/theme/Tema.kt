package pt.jorges15.palavrame.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Uma app de leitura devia parecer papel, não um painel de controlo.
private val Tinta = Color(0xFF2B2B2B)
private val Papel = Color(0xFFFBF8F3)
private val Marcador = Color(0xFF8C5A3C)

// A paleta define TODOS os papéis que os componentes usam. Os que ficavam por
// definir caíam no lilás de origem do Material 3: o botão "Registar" (tonal),
// os botões segmentados dos temas, o fundo dos diálogos e o contorno dos
// cartões apareciam arroxeados ao lado do papel e da tinta.
private val claro = lightColorScheme(
    primary = Marcador,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2DFD2),
    onPrimaryContainer = Color(0xFF3B2414),
    secondary = Color(0xFF7A6A5C),
    onSecondary = Papel,
    secondaryContainer = Color(0xFFEFE3D6),
    onSecondaryContainer = Tinta,
    tertiary = Marcador,
    onTertiary = Color(0xFFFFFFFF),
    background = Papel,
    surface = Papel,
    surfaceVariant = Color(0xFFF0EAE0),
    onSurfaceVariant = Color(0xFF4F463E),
    surfaceContainerLowest = Color(0xFFFFFCF8),
    surfaceContainerLow = Color(0xFFF8F3EC),
    surfaceContainer = Color(0xFFF4EEE6),
    surfaceContainerHigh = Color(0xFFF0EAE0),
    surfaceContainerHighest = Color(0xFFEAE3D8),
    onBackground = Tinta,
    onSurface = Tinta,
    outline = Color(0xFF8A8178),
    outlineVariant = Color(0xFFDDD3C7),
)

private val escuro = darkColorScheme(
    primary = Color(0xFFD8A98C),
    onPrimary = Color(0xFF2B1A10),
    primaryContainer = Color(0xFF5A3A26),
    onPrimaryContainer = Color(0xFFF5DCCB),
    secondary = Color(0xFFCDBBA9),
    onSecondary = Color(0xFF2B2219),
    secondaryContainer = Color(0xFF3A2F26),
    onSecondaryContainer = Color(0xFFEDE6DC),
    tertiary = Color(0xFFD8A98C),
    onTertiary = Color(0xFF2B1A10),
    background = Color(0xFF16130F),
    surface = Color(0xFF16130F),
    surfaceVariant = Color(0xFF2A251F),
    onSurfaceVariant = Color(0xFFD3C8BC),
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1C1814),
    surfaceContainer = Color(0xFF201B17),
    surfaceContainerHigh = Color(0xFF2A251F),
    surfaceContainerHighest = Color(0xFF353029),
    onBackground = Color(0xFFEDE6DC),
    onSurface = Color(0xFFEDE6DC),
    outline = Color(0xFF9C9187),
    outlineVariant = Color(0xFF4A4038),
)

/**
 * Serifa para a palavra e para as definições; sem serifa para os controlos.
 *
 * É a decisão de desenho mais consequente da app: o conteúdo é texto de
 * dicionário, e um dicionário lê-se com serifas. Os botões e as etiquetas
 * ficam na tipografia do sistema, que é o que os faz parecer botões.
 * Não se usa fonte externa — a serifa do próprio Android chega e não
 * acrescenta um único byte ao APK.
 */
private val serifa = FontFamily.Serif

private val tipografia = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = serifa),
        displayMedium = base.displayMedium.copy(fontFamily = serifa),
        displaySmall = base.displaySmall.copy(fontFamily = serifa),
        headlineLarge = base.headlineLarge.copy(fontFamily = serifa),
        headlineMedium = base.headlineMedium.copy(fontFamily = serifa),
        headlineSmall = base.headlineSmall.copy(fontFamily = serifa),
        // O corpo das definições. Entrelinha maior do que o padrão: são
        // textos densos, muitas vezes de 1913.
        bodyLarge = TextStyle(
            fontFamily = serifa,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontFamily = serifa,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
        ),
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
    /*
     * As barras do sistema seguem o tema da app, não o do telemóvel.
     *
     * Sem isto, quem punha a app em escuro com o telemóvel em claro ficava
     * com a barra de navegação de baixo clara por cima de um ecrã escuro —
     * defeito visível num Galaxy S22 Ultra e invisível no emulador, que
     * normalmente tem o sistema e a app no mesmo tema.
     *
     * O que se controla aqui é a *aparência* das barras: `isAppearanceLight`
     * a verdadeiro pede ícones escuros, e o sistema desenha por baixo deles
     * um véu claro. É esse véu que se via. Como o tema da app pode divergir
     * do tema do sistema, tem de ser a app a dizê-lo.
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

    // Sem cores dinâmicas do sistema: o papel e a tinta são a identidade da
    // app, e deixá-los mudar com o fundo do telemóvel tirava-lhe o carácter.
    MaterialTheme(
        colorScheme = if (escuroAtivo) escuro else claro,
        typography = tipografia,
        content = content,
    )
}
