package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.CampsiteQrPayload
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.QrScanContract
import io.github.CMPUT301F26stderr.campsbridge.platform.qr.QrScanResult
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** QR entry/display through the actual root, native result contracts and registered saved graph. */
class CampsiteQrFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val host = TestAppHost()
    private val sites =
        (1..12).map { index ->
            sampleCampsite("river/$index").let {
                it.copy(details = it.details.copy(name = "River camp $index", locationLabel = "Jasper"))
            }
        }
    private val target = sites[9]
    private val redraw = mutableIntStateOf(0)
    private val rendering = staticCompositionLocalOf { 0 }

    /** Disposes actual graph-owned listeners and pending image generation. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** QR-01: generation, explicit retry, recomposition, recreation and both Back paths share one saved manager. */
    @Test fun ownerQrChildUsesSavedIdentityAndRegeneratesOnlyWhenNeeded() {
        val generated = CopyOnWriteArrayList<String>()
        val fail =
            java.util.concurrent.atomic
                .AtomicBoolean(true)
        compose.runOnIdle {
            host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
            host.campsites.records.value = listOf(target)
            host.createQrBitmap = { id ->
                generated += id
                if (fail.get()) throw IOException("native encoder unavailable")
                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            }
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(rendering provides redraw.intValue) { host.Content() }
        }
        compose.onNodeWithText(target.details.name).performClick()
        compose.onNodeWithText("Show QR").performScrollTo().performClick()
        compose.onNodeWithText(target.details.name).assertIsDisplayed()
        compose.waitUntil { generated.size == 1 }
        compose.onNodeWithText("Could not create QR code.").assertIsDisplayed()
        compose.runOnIdle { fail.set(false) }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil { generated.size == 2 }
        compose.waitUntil { compose.onAllNodes(hasText(target.details.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("QR code for ${target.details.name}").assertIsDisplayed()
        compose.runOnIdle { redraw.intValue++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(target.id, target.id), generated.toList()) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("Show QR").performScrollTo().performClick()
        compose.waitUntil { generated.size == 3 }
        compose.onNodeWithContentDescription("QR code for ${target.details.name}").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil { generated.size == 4 }
        compose.onNodeWithContentDescription("QR code for ${target.details.name}").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT),
            )
        }
        compose.waitForIdle()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(0f, 0f, 0.7f, BackEventCompat.EDGE_RIGHT),
            )
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { assertTrue(generated.all { it == target.id }) }
    }

}

