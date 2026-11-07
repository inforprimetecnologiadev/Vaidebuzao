package br.com.inforprimetecnologia.vaidebuzao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.inforprimetecnologia.vaidebuzao.R

/** Títulos: Montserrat (pesada, combina com o lettering do logo). Licença OFL, embutida no APK. */
val Montserrat = FontFamily(
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
    Font(R.font.montserrat_black, FontWeight.Black),
)

/** Textos: Inter (alta legibilidade em telas pequenas). Licença OFL, embutida no APK. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(
    fontFamily = Montserrat, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.01).em,
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em,
)

val VdbTypography = Typography(
    displayLarge = display(52, 60, FontWeight.Black),
    displayMedium = display(42, 50, FontWeight.Black),
    displaySmall = display(34, 42),
    headlineLarge = display(30, 38),
    headlineMedium = display(26, 34),
    headlineSmall = display(22, 30),
    titleLarge = display(20, 28, FontWeight.Bold),
    titleMedium = body(16, 24, FontWeight.SemiBold),
    titleSmall = body(14, 20, FontWeight.SemiBold),
    bodyLarge = body(16, 24),
    bodyMedium = body(14, 20),
    bodySmall = body(12, 16),
    labelLarge = body(14, 20, FontWeight.SemiBold, 0.01),
    labelMedium = body(12, 16, FontWeight.SemiBold, 0.02),
    labelSmall = body(11, 16, FontWeight.Medium, 0.03),
)
