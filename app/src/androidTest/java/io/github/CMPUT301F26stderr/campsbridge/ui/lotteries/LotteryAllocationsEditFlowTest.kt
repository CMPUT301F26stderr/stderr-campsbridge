/*
 * Co-owner allocation editing through the registered saved graph and actual calendar.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MutableClock
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.addCalendarNights
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Co-owner allocation editing through the registered saved graph and actual calendar. */
class LotteryAllocationsEditFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val anchor = Instant.parse("2027-05-10T18:00:00Z")
    private val day = LocalDate.of(2027, 5, 18)
    private val site = sampleCampsite("site-b")
    private val organizerSite =
        sampleCampsite("site-a", "owner-a").let {
            it.copy(details = it.details.copy(name = "Forest Camp"))
        }
    private val lottery =
        Lottery(
            "lottery-a",
            organizerSite.ownerId,
            "Shared May stays",
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
            participantOwnerIds = setOf(organizerSite.ownerId, site.ownerId),
        )
    private val otherOffer = LotteryOffer(organizerSite.id, listOf(day))
    private val replacements = mutableListOf<List<LotteryOffer>>()

    /** Closes graph-owned subscriptions after each declared save vector. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** LA-04: empty complete-set Save clears this owner's rows and retains the organizer's offer. */
    @Test fun clearsTheOwnedSetAfterRestoringAndCancellingWorkingDates() {
        editOwnedSet(empty = true)
    }

    /** LA-04: nonempty complete-set Save applies this owner's row and retains the organizer's offer. */
    @Test fun savesTheOwnedSetAfterRestoringAndCancellingWorkingDates() {
        editOwnedSet(empty = false)
    }

    /** Adds, edits, restores, cancels and removes local offers before one explicit save.
     * @param empty Whether the complete desired set is cleared or replaced with one applied offer.
     */
    private fun editOwnedSet(empty: Boolean) {
        compose.runOnIdle {
            host.clock = MutableClock(anchor)
            host.accounts.account.value = sampleAccount(setOf(Role.OWNER))
            host.campsites.records.value = listOf(organizerSite, site)
            host.campsites.calendarNights.value = listOf(0L, 1L, 4L).map { CalendarNight(day.plusDays(it), true) }
            host.lotteries.records.value = listOf(lottery)
            host.lotteries.serverOffers[lottery.id] = listOf(otherOffer)
            host.lotteries.liveOffers.value = mapOf(lottery.id to listOf(otherOffer))
            host.lotteries.replaceOwnerOffersHandler = { id, actor, offers ->
                assertEquals(site.ownerId, actor)
                replacements += offers
                host.lotteries.serverOffers[id] = listOf(otherOffer) + offers
                host.lotteries.liveOffers.value = mapOf(id to checkNotNull(host.lotteries.serverOffers[id]))
            }
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { host.Content() }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText(lottery.title).performClick()
        compose.onNodeWithText("Manage my campsites").performScrollTo().performClick()
        compose.onNodeWithText("Add campsite").performScrollTo().performClick()
        compose.onNodeWithText(site.details.name).performClick()
        compose.addCalendarNights(day.plusDays(1))
        compose.onNodeWithText("Apply nights").performClick()
        compose.onNodeWithContentDescription("Edit nights for ${site.details.name}").performScrollTo().performClick()
        compose.addCalendarNights(day.plusDays(4))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Remove May 22, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("1 offered night").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove campsite ${site.details.name}").performScrollTo().performClick()
        if (!empty) {
            compose.onNodeWithText("Add campsite").performScrollTo().performClick()
            compose.onNodeWithText(site.details.name).performClick()
            compose.addCalendarNights(day.plusDays(4))
            compose.onNodeWithText("Apply nights").performClick()
        }
        compose.runOnIdle { assertEquals(emptyList<List<LotteryOffer>>(), replacements) }
        compose.onNodeWithText("Save campsites").performClick()
        compose.onNodeWithText("Included campsites").performScrollTo().performClick()
        compose.onNodeWithText(organizerSite.details.name).assertIsDisplayed()
        compose.runOnIdle {
            val expected = if (empty) emptyList() else listOf(LotteryOffer(site.id, listOf(day.plusDays(4))))
            assertEquals(listOf(expected), replacements)
            assertEquals(listOf(otherOffer) + expected, host.lotteries.serverOffers[lottery.id])
        }
    }
}
