package io.github.CMPUT301F26stderr.campsbridge.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ForestGreen = Color(0xFF00874A)
val Ink = Color(0xFF18231D)

val StandardColors =
    lightColorScheme(
        primary = ForestGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8F2DF),
        onPrimaryContainer = Color(0xFF00391D),
        secondary = Color(0xFF006E3B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0EFE4),
        onSecondaryContainer = Ink,
        tertiary = Color(0xFF376957),
        onTertiary = Color.White,
        background = Color.White,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF1F5F1),
        onSurfaceVariant = Color(0xFF47564D),
        surfaceContainer = Color(0xFFF1F5F1),
        surfaceContainerLow = Color(0xFFF7F9F7),
        surfaceContainerHigh = Color(0xFFE9EFEA),
        surfaceContainerHighest = Color(0xFFE1E8E2),
        outline = Color(0xFF727D75),
        outlineVariant = Color(0xFFC3CDC5),
        error = Color(0xFFA83836),
        onError = Color.White,
    )
val ColorBlindColors =
    StandardColors.copy(
        primary = Color(0xFF00677E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3EDF5),
        onPrimaryContainer = Color(0xFF003542),
        secondary = Color(0xFF00677E),
        error = Color(0xFF9B3B00),
    )
