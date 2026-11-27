/*
 * QR entry/display through the actual root, native result contracts and registered saved graph.
 */

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
    private val scans = ControlledQrResults()
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

    /** QR-01: scan navigation preserves query, applied filters and the actual list viewport without joining. */
    @Test fun decodedCampsiteReturnsToTheRetainedExploreState() {
        startExplore()
        compose.onNodeWithText("Search campsites").performTextReplacement("River")
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Location").performTextReplacement("Jasper")
        compose.onNodeWithContentDescription("Increase Guests").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(target.details.name))
        scans.result = QrScanResult.Decoded(CampsiteQrPayload.encode(target.id))
        compose.onNodeWithText("Scan QR").performClick()
        compose.onNodeWithText(target.id).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("River").assertIsDisplayed()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.onNodeWithText("Guests: 2").assertIsDisplayed()
        compose.onNodeWithText(target.details.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, scans.scans)
            assertEquals(listOf(target.id), host.campsites.observedIds)
            assertTrue(host.lotteries.events.none { it.startsWith("join:") })
        }
    }

    /** QR-02: cancellation is unchanged, denial/settings, startup failure and foreign text have explicit recovery. */
    @Test fun scanOutcomesKeepExploreAndLaunchRecoveryOnlyFromActions() {
        startExplore()
        compose.onNodeWithText("Search campsites").performTextReplacement("River")
        scans.result = QrScanResult.Cancelled
        compose.onNodeWithText("Scan QR").performClick()
        compose.onNodeWithText("River").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, scans.scans) }
        scans.result = QrScanResult.PermissionDenied
        compose.onNodeWithText("Scan QR").performClick()
        compose.onNodeWithText("Camera permission needed").assertIsDisplayed()
        compose.onNodeWithText("Open app settings").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val settings = scans.settings.single()
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
            assertEquals(Uri.fromParts("package", context.packageName, null), settings.data)
            assertEquals(2, scans.scans)
        }
        scans.result = QrScanResult.Failed
        compose.onNodeWithText("Try scanning again").performClick()
        compose.onNodeWithText("Could not start scanner").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, scans.scans) }
        scans.result = QrScanResult.Decoded("https://example.org/another-app")
        compose.onNodeWithText("Try scanning again").performClick()
        compose.onNodeWithText("Unrecognized QR code").assertIsDisplayed()
        compose.onNodeWithText("River").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(4, scans.scans)
            assertTrue(host.campsites.observedIds.isEmpty())
        }
    }

    /** QR-03: a valid saved identity with confirmed absence reaches the existing unavailable reader. */
    @Test fun scannedMissingCampsiteUsesTheRegisteredReader() {
        unavailableTarget("missing")
    }

    /** QR-03: a removed saved listing stays unavailable instead of showing fixture content. */
    @Test fun scannedRemovedCampsiteUsesTheRegisteredReader() {
        unavailableTarget("removed")
    }

    /** QR-03: a failed saved source retains reader Retry instead of reporting confirmed absence. */
    @Test fun scannedFailedCampsiteUsesTheRegisteredReader() {
        unavailableTarget("failed")
    }

    /** QR-04: generation, explicit retry, recomposition, recreation and both Back paths share one saved manager. */
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

    /** Opens discovery through its real root with scanner outcomes supplied only at the platform boundary. */
    private fun startExplore() {
        host.campsites.records.value = sites
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides scans,
            ) { host.Content() }
        }
    }

    /** Drives one fixed valid-ID unavailable-source vector through the actual scan route.
     * @param source Missing, removed or failed saved-record source.
     */
    private fun unavailableTarget(source: String) {
        if (source ==
            "removed"
        ) {
            host.campsites.records.value =
                listOf(target.copy(removal = Removal(Instant.parse("2027-05-10T18:00:00Z"), "admin")))
        }
        if (source == "failed") host.campsites.streamFailure.value = IOException("saved read unavailable")
        host.matchesText = { _, _ -> true }
        host.matchesCapacity = { _, _ -> true }
        scans.result = QrScanResult.Decoded(CampsiteQrPayload.encode(target.id))
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides scans,
            ) { host.Content() }
        }
        compose.onNodeWithText("Scan QR").performClick()
        compose
            .onNodeWithText(
                if (source ==
                    "failed"
                ) {
                    "Could not load this campsite."
                } else {
                    "Campsite unavailable"
                },
            ).assertIsDisplayed()
        if (source == "failed") compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(target.id), host.campsites.observedIds) }
    }
}

/** Captures real scanner/settings launch contracts and returns fixed native-boundary outcomes. */
private class ControlledQrResults : ActivityResultRegistryOwner {
    var result: QrScanResult = QrScanResult.Cancelled
    var scans = 0
    val settings = mutableListOf<Intent>()
    override val activityResultRegistry =
        object : ActivityResultRegistry() {
            /** Delivers a controlled result without opening a camera or Android settings surface.
             * @param requestCode Registered route launch identity.
             * @param contract Actual route contract creating the Android Intent.
             * @param input Route input for the explicit click.
             * @param options Optional platform launch options.
             */
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val intent = contract.createIntent(context, input)
                if (contract is QrScanContract) {
                    scans++
                    dispatchResult(requestCode, result)
                } else {
                    settings += intent
                    dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
                }
            }
        }
}
