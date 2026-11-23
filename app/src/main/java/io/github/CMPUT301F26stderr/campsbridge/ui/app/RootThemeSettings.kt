/*
 * Retains only display preferences while account marking disposes the signed-in profile.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ColorMode

/** Retains only display preferences while account marking disposes the signed-in profile.
 * @param session Actual current account outcome, never the removed profile's scrubbed settings.
 * @param removal Original root cleanup request and its unresolved operation state.
 */
@Composable
internal fun rememberRootThemeSettings(
    session: SessionState,
    removal: AccountRemovalUiState,
): AccountSettings {
    val account =
        when (session) {
            is SessionState.Ready -> session.account
            is SessionState.SetupRequired -> session.account
            else -> null
        }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var colorMode by rememberSaveable { mutableStateOf(ColorMode.STANDARD.name) }
    var fontScale by rememberSaveable { mutableStateOf(1.0) }
    var controlScale by rememberSaveable { mutableStateOf(1.0) }
    LaunchedEffect(account?.id, account?.settings) {
        if (account?.isActive == true) {
            accountId = account.id
            colorMode = account.settings.colorMode.name
            fontScale = account.settings.fontScale
            controlScale = account.settings.controlScale
        }
    }
    return account?.settings ?: if (removal.blocksPersonChange && removal.request?.accountId == accountId) {
        AccountSettings(colorMode = ColorMode.valueOf(colorMode), fontScale = fontScale, controlScale = controlScale)
    } else {
        AccountSettings()
    }
}
