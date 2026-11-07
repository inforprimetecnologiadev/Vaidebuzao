package br.com.inforprimetecnologia.vaidebuzao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Preferência de tema do usuário. */
enum class ThemeMode(val label: String) { SYSTEM("Automático"), LIGHT("Claro"), DARK("Escuro") }

/** Escala de espaçamento (grade de 4 dp). */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Margem lateral padrão das telas. */
    val screen: Dp = 20.dp,
    /** Alvo de toque mínimo (acessibilidade). */
    val touch: Dp = 48.dp,
)

/** Durações de animação (ms) — respeitar "remover animações" do sistema. */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 400
}

val VdbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
private val LocalIsDark = staticCompositionLocalOf { false }

/** Acesso aos tokens do design system: VdbTheme.spacing, VdbTheme.colors... */
object VdbTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val colors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun VaiDeBuzaoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    // Cor dinâmica (Material You) desativada de propósito para preservar a identidade da marca.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VdbTypography,
            shapes = VdbShapes,
            content = content,
        )
    }
}
