package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Presentation contracts supplement the existing controller lifetime/retry tests. */
class AdminProfileScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedSectionsRetryIndependentlyAndKeepAccountVisible() {
        var accountRetries = 0
        var siteRetries = 0
        var photoRetries = 0
        compose.setContent {
            CampsbridgeTheme {
                AdminProfileScreen(
                    AdminInspectionUiState.Profile(
                        "account-1",
                        AdminRead.Content(sampleAccount()),
                        AdminRead.Failure,
                        ImageSlotState(loadState = ImageLoadState.FAILED),
                    ),
                    {},
                    { accountRetries++ },
                    { siteRetries++ },
                    { photoRetries++ },
                    {},
                )
            }
        }
        compose.onNodeWithText("Alex Chen").assertIsDisplayed()
        compose
            .onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("admin_profile_photo")))
            .performScrollTo()
            .performClick()
        compose
            .onNode(hasText("Retry") and hasAnyAncestor(hasTestTag("admin_profile_campsites")))
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            assertEquals(0, accountRetries)
            assertEquals(1, photoRetries)
            assertEquals(1, siteRetries)
        }
    }

    @Test fun optionalFieldsAndConfirmedEmptySectionsAreExplicit() {
        compose.setContent {
            CampsbridgeTheme {
                AdminProfileScreen(
                    AdminInspectionUiState.Profile(
                        "account-1",
                        AdminRead.Content(sampleAccount().copy(email = null, phone = null)),
                        AdminRead.Content(emptyList()),
                        ImageSlotState(loadState = ImageLoadState.READY),
                    ),
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        assertEquals(2, compose.onAllNodesWithText("Not provided").fetchSemanticsNodes().size)
        compose.onNodeWithText("No profile photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No owned campsites").performScrollTo().assertIsDisplayed()
    }

    @Test fun unavailableAccountIsNotAnEmptyProfileOrIndefiniteDependentLoad() {
        compose.setContent {
            CampsbridgeTheme {
                AdminProfileScreen(
                    AdminInspectionUiState.Profile("missing/id", account = AdminRead.Unavailable),
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("missing/id").assertIsDisplayed()
        compose.onNodeWithText("This profile is missing or unavailable.").assertIsDisplayed()
        compose.onNodeWithText("No owned campsites").assertDoesNotExist()
        compose.onNodeWithText("Loading owned campsites").assertDoesNotExist()
    }

    @Test fun relatedCampsiteOpensByActualIdentityWithoutOwnerActions() {
        val site = sampleCampsite("site/one", "account-1")
        var selected: String? = null
        compose.setContent {
            CampsbridgeTheme {
                AdminProfileScreen(
                    AdminInspectionUiState.Profile(
                        "account-1",
                        AdminRead.Content(sampleAccount()),
                        AdminRead.Content(listOf(site)),
                        ImageSlotState(loadState = ImageLoadState.READY),
                    ),
                    {},
                    {},
                    {},
                    {},
                    { selected = it },
                )
            }
        }
        compose.onNodeWithText(site.details.name).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(site.id, selected) }
        compose.onNodeWithText("Save").assertDoesNotExist()
    }
}
