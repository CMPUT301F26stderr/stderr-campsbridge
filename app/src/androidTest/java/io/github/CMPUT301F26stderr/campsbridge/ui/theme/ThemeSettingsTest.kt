package io.github.CMPUT301F26stderr.campsbridge.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.ui.components.actions.AppActionButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies that application scaling composes with system scaling exactly once. */
class ThemeSettingsTest {
    @get:Rule val compose = createComposeRule()

    /** App scaling changes typography without rewriting the system font scale. */
    @Test fun applicationScalePreservesSystemScale() {
        val settings = mutableStateOf(AccountSettings())
        var textSize = 0f
        var systemScale = 0f
        var controlScale = 0f
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CampsbridgeTheme(settings = settings.value) {
                    val size = MaterialTheme.typography.titleLarge.fontSize.value
                    val system = LocalDensity.current.fontScale
                    val controls = LocalControlScale.current
                    SideEffect {
                        textSize = size
                        systemScale = system
                        controlScale = controls
                    }
                }
            }
        }
        var baseline = 0f
        compose.runOnIdle {
            baseline = textSize
            settings.value = AccountSettings(fontScale = 1.25, controlScale = 1.5)
        }
        compose.runOnIdle {
            assertEquals(baseline * 1.25f, textSize, 0.001f)
            assertEquals(2f, systemScale, 0.001f)
            assertEquals(1.5f, controlScale, 0.001f)
        }
    }

    /** Smaller visual preferences preserve usable targets for shared and Material controls. */
    @Test fun smallerControlsRetainMinimumTouchTargets() {
        compose.setContent {
            CampsbridgeTheme(settings = AccountSettings(controlScale = 0.5)) {
                Column {
                    AppActionButton("Save", {})
                    Checkbox(
                        checked = false,
                        onCheckedChange = {},
                        modifier = Modifier.semantics { contentDescription = "Notifications" },
                    )
                }
            }
        }
        compose.onNodeWithText("Save").assertHeightIsAtLeast(48.dp)
        compose
            .onNodeWithContentDescription("Notifications")
            .assertTouchHeightIsEqualTo(48.dp)
            .assertTouchWidthIsEqualTo(48.dp)
    }
}
