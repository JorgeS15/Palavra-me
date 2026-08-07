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

private val claro = lightColorScheme(
    primary = Marcador,
    background = Papel,
    surface = Papel,
    surfaceVariant = Color(0xFFF0EAE0),
    onBackground = Tinta,
    onSurface = Tinta,
    outline = Color(0xFF8A8178),
)

private val escuro = darkColorScheme(
    primary = Color(0xFFD8A98C),
    background = Color(0xFF16130F),
    surface = Color(0xFF16130F),
    surfaceVariant = Color(0xFF2A251F),
    onBackground = Color(0xFFEDE6DC),
    onSurface = Color(0xFFEDE6DC),
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
