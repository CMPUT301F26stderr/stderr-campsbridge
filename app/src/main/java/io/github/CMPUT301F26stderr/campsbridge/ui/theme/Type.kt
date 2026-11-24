/*
 * Applies application scaling to Material typography; Android still applies system scaling.
 * Headings use JetBrains Mono for the team's terminal voice; reading text stays in Inter.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.CMPUT301F26stderr.campsbridge.R

private val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private val JetBrainsMono =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

/**
 * Applies application scaling to Material typography; Android still applies system scaling.
 * @param scale Account font-size multiplier.
 */
fun appTypography(scale: Float): Typography {
    val defaults = Typography()
    val base =
        defaults.copy(
            headlineLarge = defaults.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall =
                defaults.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            titleLarge =
                defaults.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = defaults.labelSmall.copy(letterSpacing = 0.sp),
        )

    /** Scales a heading style in the terminal face.
     * @param style Material base style.
     */
    fun heading(style: TextStyle): TextStyle =
        scaledStyle(style, scale, JetBrainsMono)

    return Typography(
        displayLarge = heading(base.displayLarge),
        displayMedium = heading(base.displayMedium),
        displaySmall = heading(base.displaySmall),
        headlineLarge = heading(base.headlineLarge),
        headlineMedium = heading(base.headlineMedium),
        headlineSmall = heading(base.headlineSmall),
        titleLarge = heading(base.titleLarge),
        titleMedium = scaledStyle(base.titleMedium, scale),
        titleSmall = scaledStyle(base.titleSmall, scale),
        bodyLarge = scaledStyle(base.bodyLarge, scale),
        bodyMedium = scaledStyle(base.bodyMedium, scale),
        bodySmall = scaledStyle(base.bodySmall, scale),
        labelLarge = scaledStyle(base.labelLarge, scale),
        labelMedium = scaledStyle(base.labelMedium, scale),
        labelSmall = scaledStyle(base.labelSmall, scale),
        displayLargeEmphasized = heading(base.displayLarge.copy(fontWeight = FontWeight.Bold)),
        displayMediumEmphasized = heading(base.displayMedium.copy(fontWeight = FontWeight.Bold)),
        displaySmallEmphasized = heading(base.displaySmall.copy(fontWeight = FontWeight.Bold)),
        headlineLargeEmphasized = heading(base.headlineLarge.copy(fontWeight = FontWeight.Bold)),
        headlineMediumEmphasized = heading(base.headlineMedium.copy(fontWeight = FontWeight.Bold)),
        headlineSmallEmphasized = heading(base.headlineSmall.copy(fontWeight = FontWeight.Bold)),
        titleLargeEmphasized = heading(base.titleLarge.copy(fontWeight = FontWeight.Bold)),
        titleMediumEmphasized = scaledStyle(base.titleMedium.copy(fontWeight = FontWeight.Bold), scale),
        titleSmallEmphasized = scaledStyle(base.titleSmall.copy(fontWeight = FontWeight.Bold), scale),
        bodyLargeEmphasized = scaledStyle(base.bodyLarge.copy(fontWeight = FontWeight.Medium), scale),
        bodyMediumEmphasized = scaledStyle(base.bodyMedium.copy(fontWeight = FontWeight.Medium), scale),
        bodySmallEmphasized = scaledStyle(base.bodySmall.copy(fontWeight = FontWeight.Medium), scale),
        labelLargeEmphasized = scaledStyle(base.labelLarge.copy(fontWeight = FontWeight.Bold), scale),
        labelMediumEmphasized = scaledStyle(base.labelMedium.copy(fontWeight = FontWeight.SemiBold), scale),
        labelSmallEmphasized = scaledStyle(base.labelSmall.copy(fontWeight = FontWeight.SemiBold), scale),
    )
}

/** Uses a bundled font and scales a specified Material text style once.
 * @param style Material base style.
 * @param scale Application multiplier.
 * @param family Bundled family; Inter except for headings.
 */
private fun scaledStyle(
    style: TextStyle,
    scale: Float,
    family: FontFamily = Inter,
): TextStyle =
    style.copy(
        fontFamily = family,
        letterSpacing = 0.sp,
        fontSize = style.fontSize * scale,
        lineHeight = style.lineHeight * scale,
    )
