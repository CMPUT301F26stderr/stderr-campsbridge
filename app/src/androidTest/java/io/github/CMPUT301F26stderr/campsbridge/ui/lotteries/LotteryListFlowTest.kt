package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** Real owner primary/filter lists use actual setup/detail targets and retained callers. */
class LotteryListFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val site = sampleCampsite("site/A")
    private val published =
        (
            LotteryDetailPreviewProvider()
                .values
                .filterIsInstance<LotteryDetailUiState.Content>()
                .first {
                    it.lottery.publishedAt !=
                        null
                }
        ).lottery.copy(id = "draw/A", ownerId = "account-1", title = "May riverside lottery")
    private val draft = published.copy(id = "draft/A", publishedAt = null, title = "Summer draft")

    /** Releases navigation models and every scoped subscription. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Published rows open saved detail; drafts open their same saved setup identity. */
    @Test fun categoriesOpenActualSavedTargetsAndReturnToTheirCaller() {
        start()
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(published.title).performClick()
        compose.onNodeWithText("Lottery").assertIsDisplayed()
        compose.onNodeWithText(published.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My lotteries").assertIsDisplayed()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText(draft.title).performClick()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithText(draft.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My lotteries").assertIsDisplayed()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.runOnIdle {
            assertTrue(host.lotteries.events.contains("get:${draft.id}"))
            assertTrue(
                host.lotteries.events.none {
                    it.startsWith("create:") ||
                        it.startsWith("update:") ||
                        it.startsWith("publish:")
                },
            )
            assertEquals(1, host.lotteryListCreations)
        }
    }

    /** Selected category survives restoration and switching to a registered peer destination. */
    @Test fun categoryRestoresAndPrimaryReturnKeepsTheSameOwner() {
        val restoration = StateRestorationTester(compose)
        start(restoration)
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText("Drafts").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText(draft.title).assertIsDisplayed()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText("Drafts").assertIsSelected()
        compose.onNodeWithText(draft.title).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.lotteryListCreations) }
    }

    /** Campsite management supplies an encoded filter and remains the actual Back target. */
    @Test fun managementFilterReadsActualAllocationsAndCanClear() {
        val other = published.copy(id = "draw/B", title = "Forest lottery")
        host.lotteries.records.value = listOf(published, other)
        host.lotteries.liveOffers.value =
            mapOf(published.id to listOf(LotteryOffer(site.id, listOf(LocalDate.of(2027, 5, 14)))))
        start(initial = listOf(published, other))
        compose.onNodeWithText(site.details.name).performClick()
        compose.onNodeWithText("Campsite lotteries").performScrollTo().performClick()
        compose.onNodeWithText(published.title).assertIsDisplayed()
        compose.onNodeWithText(other.title).assertDoesNotExist()
        compose.onNodeWithText("Showing campsite ${site.id}").assertIsDisplayed()
        compose.onNodeWithText("Show all lotteries").performClick()
        compose.onNodeWithText(other.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, host.lotteries.activeOfferObservers) }
    }

    /** Failed reads keep the frame; Retry can confirm empty content without fixture rows. */
    @Test fun sourceFailureKeepsNavigationAndExplicitRetry() {
        start()
        compose.onNodeWithText("Lotteries").performClick()
        compose.runOnIdle { host.lotteries.ownerFailure.value = IOException("offline") }
        compose.onNodeWithText("My lotteries").assertIsDisplayed()
        compose.onNodeWithText("Could not load your lotteries.").assertIsDisplayed()
        compose.onNodeWithText("Drafts").performClick()
        compose.onNodeWithText("Could not load your lotteries.").assertIsDisplayed()
        compose.runOnIdle {
            host.lotteries.ownerFailure.value = null
            host.lotteries.records.value = emptyList()
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No lotteries in Drafts.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, host.lotteries.activeOwnerObservers) }
    }

    /** Primary create opens the real new wizard; untouched Back returns without writes. */
    @Test fun createStartsANewLocalWizardAndBackReturnsToTheList() {
        start()
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText("Create lottery").performClick()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("My lotteries").assertIsDisplayed()
        compose.runOnIdle { assertTrue(host.lotteries.events.isEmpty()) }
    }

    /** Shared content remains scrollable/clickable with large text and long real-shaped titles. */
    @Test fun longRowsRemainReachableAtLargeText() {
        val long =
            published.copy(
                title =
                    "Riverside long weekend camping with family and friends " +
                        "in the mountain valley",
            )
        var opened: LotteryListItem? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                CampsbridgeTheme {
                    LotteryListScreen(
                        LotteryListUiState.Content(
                            listOf(LotteryListItem(long, LotteryLifecycle.UPCOMING)),
                        ),
                        LotteryListCategory.PUBLISHED,
                        listOf(LotteryListCategory.PUBLISHED, LotteryListCategory.DRAFTS),
                        {
                        },
                        {
                            opened =
                                it
                        },
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText(long.title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(long.id, opened?.lottery?.id) }
    }

    /** Starts real navigation with scoped fixtures and no Firebase/platform bootstrap.
     * @param restoration Optional saved-state tester.
     * @param initial Exact saved records offered by the controlled source.
     */
    private fun start(
        restoration: StateRestorationTester? = null,
        initial: List<Lottery> = listOf(published, draft),
    ) {
        host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
        host.preferences.progress.value =
            host.preferences.progress.value
                .copy(workspace = Role.OWNER)
        host.campsites.records.value = listOf(site)
        host.lotteries.records.value = initial
        if (restoration ==
            null
        ) {
            compose.setContent { host.Content() }
        } else {
            restoration.setContent { host.Content() }
        }
    }
}
