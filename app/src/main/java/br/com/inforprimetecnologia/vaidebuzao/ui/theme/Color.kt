package br.com.inforprimetecnologia.vaidebuzao.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta da marca Vai de Buzão, amostrada do logo oficial (branding/logo-vai-de-buzao.png).
 * Ver docs/design-system.md para papéis e contrastes.
 */
object Brand {
    val Navy900 = Color(0xFF06166A)
    val Navy800 = Color(0xFF0B2287)
    val Royal700 = Color(0xFF0F37A6)
    val Royal600 = Color(0xFF1446C8)
    val Royal500 = Color(0xFF1E5BF0)
    val Sky400 = Color(0xFF3FA9F5)
    val Yellow400 = Color(0xFFFFC800)
    val Yellow300 = Color(0xFFFFD84D)
    val Orange500 = Color(0xFFFF8A00)
    val Orange700 = Color(0xFFC25600)
    val White = Color(0xFFFFFFFF)
}

internal val LightColors = lightColorScheme(
    primary = Brand.Royal600,
    onPrimary = Brand.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Brand.Navy900,
    inversePrimary = Color(0xFF8FB0FF),
    secondary = Brand.Yellow400,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = Color(0xFFFFF1BF),
    onSecondaryContainer = Color(0xFF3D2E00),
    tertiary = Brand.Orange700,
    onTertiary = Brand.White,
    tertiaryContainer = Color(0xFFFFE0C7),
    onTertiaryContainer = Color(0xFF3A1600),
    background = Color(0xFFF6F8FD),
    onBackground = Color(0xFF0E1530),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1530),
    surfaceVariant = Color(0xFFE6EBF5),
    onSurfaceVariant = Color(0xFF4A5372),
    surfaceTint = Brand.Royal600,
    inverseSurface = Color(0xFF1B2343),
    inverseOnSurface = Color(0xFFEEF1FA),
    error = Color(0xFFBA1A1A),
    onError = Brand.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF737C9B),
    outlineVariant = Color(0xFFC9D0E2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE1EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5FB),
    surfaceContainer = Color(0xFFEDF1F9),
    surfaceContainerHigh = Color(0xFFE7ECF6),
    surfaceContainerHighest = Color(0xFFE1E7F3),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF00287A),
    primaryContainer = Color(0xFF1B3FA8),
    onPrimaryContainer = Color(0xFFDCE6FF),
    inversePrimary = Brand.Royal600,
    secondary = Brand.Yellow400,
    onSecondary = Color(0xFF261A00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFFFFB77A),
    onTertiary = Color(0xFF4A2000),
    tertiaryContainer = Color(0xFF6B3200),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF070D24),
    onBackground = Color(0xFFE3E7F5),
    surface = Color(0xFF0B1230),
    onSurface = Color(0xFFE3E7F5),
    surfaceVariant = Color(0xFF2A3354),
    onSurfaceVariant = Color(0xFFBCC3DC),
    surfaceTint = Color(0xFF9DB8FF),
    inverseSurface = Color(0xFFE3E7F5),
    inverseOnSurface = Color(0xFF1B2343),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8790AE),
    outlineVariant = Color(0xFF3A4366),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF262F55),
    surfaceDim = Color(0xFF070D24),
    surfaceContainerLowest = Color(0xFF050A1C),
    surfaceContainerLow = Color(0xFF0E1636),
    surfaceContainer = Color(0xFF121B3D),
    surfaceContainerHigh = Color(0xFF18224A),
    surfaceContainerHighest = Color(0xFF1F2A55),
)

/** Cores semânticas extras (além do Material 3). */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val walk: Color,
    val stop: Color,
    val stopStroke: Color,
    val userLocation: Color,
    val lineDefault: Color,
    val heroGradientTop: Color,
    val heroGradientBottom: Color,
)

internal val LightExtended = ExtendedColors(
    success = Color(0xFF1B7A3D),
    onSuccess = Color.White,
    successContainer = Color(0xFFD5F5DD),
    warning = Color(0xFF8A5A00),
    warningContainer = Color(0xFFFFEFC7),
    onWarningContainer = Color(0xFF3D2800),
    info = Brand.Royal600,
    walk = Color(0xFF5B6585),
    stop = Brand.Orange500,
    stopStroke = Color.White,
    userLocation = Brand.Royal500,
    lineDefault = Brand.Royal600,
    heroGradientTop = Brand.Royal500,
    heroGradientBottom = Brand.Navy900,
)

internal val DarkExtended = ExtendedColors(
    success = Color(0xFF7DDC98),
    onSuccess = Color(0xFF00391A),
    successContainer = Color(0xFF0E4A26),
    warning = Color(0xFFFFCF6B),
    warningContainer = Color(0xFF4A3500),
    onWarningContainer = Color(0xFFFFEFC7),
    info = Color(0xFF9DB8FF),
    walk = Color(0xFFAEB6D2),
    stop = Brand.Orange500,
    stopStroke = Color(0xFF0B1230),
    userLocation = Brand.Sky400,
    lineDefault = Color(0xFF6F95FF),
    heroGradientTop = Brand.Royal700,
    heroGradientBottom = Color(0xFF040A26),
)
