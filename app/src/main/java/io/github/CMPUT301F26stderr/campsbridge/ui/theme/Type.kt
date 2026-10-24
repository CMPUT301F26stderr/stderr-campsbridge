package io.github.CMPUT301F26stderr.campsbridge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.CMPUT301F26stderr.campsbridge.R

private val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/**
 * Applies application scaling to Material typography; Android still applies system scaling.
 * @param scale Account font-size multiplier.
 */
fun appTypography(scale: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = scaledStyle(base.displayLarge, scale),
        displayMedium = scaledStyle(base.displayMedium, scale),
        displaySmall = scaledStyle(base.displaySmall, scale),
        headlineLarge = scaledStyle(base.headlineLarge, scale),
        headlineMedium = scaledStyle(base.headlineMedium, scale),
        headlineSmall = scaledStyle(base.headlineSmall, scale),
        titleLarge = scaledStyle(base.titleLarge, scale),
        titleMedium = scaledStyle(base.titleMedium, scale),
        titleSmall = scaledStyle(base.titleSmall, scale),
        bodyLarge = scaledStyle(base.bodyLarge, scale),
        bodyMedium = scaledStyle(base.bodyMedium, scale),
        bodySmall = scaledStyle(base.bodySmall, scale),
        labelLarge = scaledStyle(base.labelLarge, scale),
        labelMedium = scaledStyle(base.labelMedium, scale),
        labelSmall = scaledStyle(base.labelSmall, scale),
    )
}

/** Uses the bundled font and scales a specified Material text style once.
 * @param style Material base style.
 * @param scale Application multiplier.
 */
private fun scaledStyle(
    style: TextStyle,
    scale: Float,
): TextStyle =
    style.copy(
        fontFamily = Inter,
        fontSize = style.fontSize * scale,
        lineHeight = style.lineHeight * scale,
    )
