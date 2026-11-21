package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Saved organizer editing through actual management, navigation and lifecycle boundaries. */
class LotteryDetailsEditFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val clock = MutableClock(anchor)
    private val campsite = sampleCampsite("site-a")
    private val lottery =
        Lottery(
            "lottery-a",
            campsite.ownerId,
            "May family stays",
            "Quiet stays.",
            LotteryConfiguration(
                anchor.plusSeconds(3600),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(86400),
                anchor.plusSeconds(172800),
                5,
                null,
            ),
            0,
            anchor,
            anchor,
        )
    private lateinit var owner: RouteOwner

    /** Releases the real root graph and its record listeners. */
    @After fun close() {
        compose.runOnIdle {
            host.close()
            if (::owner.isInitialized) owner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    /** LD-05: failure/discard retain fields, confirmed save preserves allocations, and resume freezes configuration. */
    @Test fun savedDetailsUseTheExistingLotteryAndFreezeOnResume() {
        compose.runOnIdle {
            owner = RouteOwner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            host.clock = clock
            host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
            host.campsites.records.value = listOf(campsite)
            host.lotteries.records.value = listOf(lottery)
            val offer = LotteryOffer(campsite.id, listOf(LocalDate.of(2027, 5, 18), LocalDate.of(2027, 5, 19)))
            host.lotteries.serverOffers[lottery.id] = listOf(offer)
            host.lotteries.liveOffers.value = mapOf(lottery.id to listOf(offer))
            host.lotteries.updateHandler = { throw IOException("offline") }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { host.Content() }
        }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(lottery.title).performClick()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.onNodeWithText("Title & description").performClick()
        compose.onNodeWithText("Description (optional)").performTextReplacement("Please arrive before sunset.")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard lottery changes?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Could not save lottery details.").assertIsDisplayed()
        compose.onNodeWithText("Title & description").performClick()
        compose.onNodeWithText("Please arrive before sunset.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            host.lotteries.updateHandler = { request ->
                host.lotteries.records.value =
                    host.lotteries.records.value.map {
                        if (it.id ==
                            request.id
                        ) {
                            it.copy(
                                title = request.title,
                                description = request.description,
                                configuration = request.configuration,
                            )
                        } else {
                            it
                        }
                    }
            }
        }
        compose.onNodeWithText("Save details").performClick()
        compose.onNodeWithText("Included campsites").performScrollTo().performClick()
        compose.onNodeWithText(campsite.details.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Edit details").performScrollTo().performClick()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitForIdle()
        compose.runOnIdle {
            clock.advance(Duration.ofHours(1))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Registration window").assertIsNotEnabled()
        compose.onNodeWithText("Booking window").assertIsNotEnabled()
        compose.onNodeWithText("Winners & waitlist capacity").assertIsNotEnabled()
        compose.onNodeWithText("Title & description").assertIsEnabled().performClick()
        compose.onNodeWithText("Description (optional)").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(listOf(lottery.id, lottery.id), host.lotteries.detailsRequests.map { it.id })
            assertTrue(host.lotteries.offerRequests.isEmpty())
        }
    }

    /** Controls the real navigation host's parent lifecycle without substituting route behavior. */
    private class RouteOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
