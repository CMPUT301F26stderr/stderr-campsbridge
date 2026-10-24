package io.github.CMPUT301F26stderr.campsbridge.ui.theme

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ColorMode

val LocalControlScale = compositionLocalOf { 1f }

/**
 * Applies the approved white/green Material theme and account accessibility preferences.
 * @param settings Account preferences, or defaults before startup resolves.
 * @param content Content rendered without altering Android's system font scale.
 */
@Composable
fun CampsbridgeTheme(
    settings: AccountSettings = AccountSettings(),
    content: @Composable () -> Unit,
) {
    val typography = remember(settings.fontScale) { appTypography(settings.fontScale.toFloat()) }
    CompositionLocalProvider(
        LocalControlScale provides settings.controlScale.toFloat(),
        LocalMinimumInteractiveComponentSize provides
            (48.dp * settings.controlScale.toFloat().coerceAtLeast(1f)),
    ) {
        MaterialTheme(
            colorScheme =
                if (settings.colorMode ==
                    ColorMode.COLOR_BLIND
                ) {
                    ColorBlindColors
                } else {
                    StandardColors
                },
            typography = typography,
            content = content,
        )
    }
}
