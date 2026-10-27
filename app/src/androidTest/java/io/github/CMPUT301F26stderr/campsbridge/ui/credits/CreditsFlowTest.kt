package io.github.CMPUT301F26stderr.campsbridge.ui.credits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.photoCredits
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Three predeclared interactions for packaged credits and the dependency-notices entry point. */
class CreditsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()

    /** Releases the account-scoped graph after each screen interaction. */
    @After fun closeHost() {
        compose.runOnIdle { host.close() }
    }

    /** Settings and the offline font reader preserve their actual caller without account writes. */
    @Test fun settingsCreditsAndFontLicenseReturnToTheirCaller() {
        openCredits()
        compose.onNodeWithText("Inter font license").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Copyright (c) 2016 The Inter Project Authors",
                substring = true,
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Photography").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.accounts.writes) }
    }

    /** Every photo/source/creator action forwards the exact shared catalog URL. */
    @Test fun photoAndLicenseActionsExposeTheCatalogLinks() {
        val catalog =
            photoCredits(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        val opened = mutableListOf<String>()
        compose.setContent {
            CampsbridgeTheme { CreditsScreen(catalog, {}, {}, {}, opened::add) }
        }
        for ((index, photo) in catalog.photos.withIndex()) {
            compose.onNodeWithText("View photo: ${photo.title}").performScrollTo().performClick()
            compose
                .onAllNodesWithText(
                    "Photographer profile",
                )[index]
                .performScrollTo()
                .performClick()
        }
        compose.onNodeWithText("Unsplash License").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                catalog.photos.flatMap { listOf(it.sourceUrl, it.photographerUrl) } +
                    catalog.licenseUrl,
                opened,
            )
        }
    }

    /** The real Android intent opens the registered notices activity and Back returns to credits. */
    @Test fun librariesActionLaunchesRegisteredNoticesActivity() {
        openCredits()
        compose.onNodeWithText("Library licenses").performScrollTo().performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertTrue(
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(
                        Stage.RESUMED,
                    ).any {
                        it is OssLicensesMenuActivity
                    },
            )
        }
        Espresso.pressBack()
        compose.onNodeWithText("Credits and licenses").assertIsDisplayed()
    }

    /** Reaches the new child through the actual profile/settings graph. */
    private fun openCredits() {
        compose.setContent { host.Content() }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Credits and licenses").performClick()
    }
}
