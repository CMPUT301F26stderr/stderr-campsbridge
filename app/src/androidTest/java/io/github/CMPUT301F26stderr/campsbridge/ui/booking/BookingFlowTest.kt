package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppDestination
import io.github.CMPUT301F26stderr.campsbridge.navigation.bookingGraph
import io.github.CMPUT301F26stderr.campsbridge.navigation.reservationGraph
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Actual ID-scoped graph verifies step content, policy/Back, restoration and saved-ID handoff. */
class BookingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val preview = previewBooking()
    private val draw = preview.lottery.copy(id = "draw/A ?1")
    private val site = preview.campsite.copy(id = "site/A ?1")
    private val offer = preview.offer.copy(campsiteId = site.id)
    private val start = requireNotNull(preview.draft.checkIn)
    private val end = requireNotNull(preview.draft.checkOut)
    private val clock = Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC)
    private val sites =
        FakeCampsiteRepository(listOf(site)).apply {
            calendarNights.value = offer.nights.map { CalendarNight(it, true) }
        }
    private val lotteries =
        FakeLotteryRepository(listOf(draw)).apply {
            liveOffers.value = mapOf(draw.id to listOf(offer))
            entries.value =
                listOf(
                    LotteryEntry(
                        draw.id,
                        "entrant",
                        EntryState.OFFERED,
                        clock.instant(),
                        clock.instant(),
                    ),
                )
        }
    private val bookings = FakeBookingRepository()
    private val store = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    private var creations = 0
    private var host: TestAppHost? = null

    /** Retires every actual graph/source from either independent host. */
    @After fun close() {
        compose.runOnIdle {
            store.clear()
            host?.close()
        }
    }

    /** Four real steps share one parent through encoded IDs, policy Back, edits and restoration. */
    @Test fun completeWizardAndPolicyReturnPreserveOneDraftAndParent() {
        val restoration = StateRestorationTester(compose)
        start(restoration)
        selectStay()
        next()
        compose.onNodeWithContentDescription("Increase Guests").performClick()
        next()
        open("Reservation & refund policy")
        compose.onNodeWithText("Simulated payments and refunds").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Rules and policy").assertIsDisplayed()
        open("I have read and accept these rules and the reservation policy.")
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        compose.onNodeWithText("Confirm reservation").assertIsEnabled()
        open("Edit guests")
        compose.onNodeWithTag("booking-guests").performTextReplacement("3")
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        open("Edit dates")
        compose.onNodeWithTag("calendar-date-$start").performScrollTo().assertIsDisplayed()
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(listOf(draw.id), lotteries.observedRecordIds)
            assertEquals(listOf(site.id), sites.observedIds)
            assertTrue(lotteries.events.isEmpty())
            assertTrue(bookings.observedRecordIds.isEmpty())
            assertTrue(bookings.reserveRequests.isEmpty())
        }
    }

    /** Missing F15 disables the calendar and explains the pending rule. */
    @Test fun absentEligibilityCannotAdvanceOrChooseDates() {
        start(useHelper = false)
        compose
            .onNodeWithText(
                "Date selection is pending the available-night rule.",
            ).assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-$start").performScrollTo().assertIsNotEnabled()
        next()
        compose.onNodeWithText("Choose an arrival date.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Number of guests").assertDoesNotExist()
    }

    /** Failure is retryable in place, and retained selected nights remain under the same owner. */
    @Test fun failedCalendarRetriesWithoutLeavingOrReplacingTheDraft() {
        sites.calendarFailure.value = IOException("inventory offline")
        start()
        compose.onNodeWithText("Could not load these nights.").assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-$start").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { sites.calendarFailure.value = null }
        open("Retry")
        selectStay()
        next()
        compose.onNodeWithText("Number of guests").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, creations) }
    }

    /** Rate and rules updates require explicit renewed approvals. */
    @Test fun changedQuoteAndRulesRemainExplicitlyUnapproved() {
        start()
        review()
        compose.runOnIdle {
            sites.records.value =
                listOf(
                    site.copy(
                        details =
                            site.details.copy(
                                nightlyPriceCents = 7000,
                                rules = "No fires after 10 PM.",
                            ),
                    ),
                )
        }
        open("Accept current total: CAD 140.00")
        compose
            .onNodeWithText(
                "The total has changed. Review the current amount before confirming.",
            ).assertDoesNotExist()
        open("Review rules and policy")
        compose.onNodeWithText("No fires after 10 PM.").assertIsDisplayed()
        next()
        compose
            .onNodeWithText("Review and accept the current rules and policy.")
            .performScrollTo()
            .assertIsDisplayed()
        open("I have read and accept these rules and the reservation policy.")
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        compose.onNodeWithText("Confirm reservation").assertIsEnabled()
    }

    /** A failed parent still permits confirmed abandonment to the actual original caller. */
    @Test fun failedSourceCanDiscardAndBackToItsActualCaller() {
        start()
        selectStay()
        compose.runOnIdle { lotteries.recordFailure.value = IOException("parent offline") }
        compose.onNodeWithText("Could not confirm this booking opportunity.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Discard this booking draft?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Could not confirm this booking opportunity.").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Discard draft").performClick()
        compose.onNodeWithText("Open booking").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, sites.activeCalendarObservers)
            assertEquals(0, lotteries.activeRecordObservers)
        }
    }

    /** Cancelled history opens the saved reader once and removes the wizard. */
    @Test fun consumedCancelledWinOpensItsActualSavedReaderWithoutReplayingWizard() {
        val now = clock.instant()
        val saved =
            previewReservation().let {
                it.copy(
                    lotteryId = draw.id,
                    entrantId = "entrant",
                    campsiteId = site.id,
                    cancellation =
                        ReservationCancellation(
                            now,
                            "owner",
                            Role.OWNER,
                            "Closed for maintenance",
                        ),
                    payment = it.payment.copy(refundedAt = now),
                )
            }
        bookings.records.value = listOf(saved)
        val restoration = StateRestorationTester(compose)
        start(restoration)
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        open("Payment & refund")
        compose.onNodeWithText("Full simulated refund").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        pressBack()
        pressBack()
        compose.onNodeWithText("Open booking").assertIsDisplayed()
        compose.onNodeWithText("Choose dates").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(saved.id), bookings.observedRecordIds)
            assertEquals(1, creations)
        }
    }

    /** Unknown consumption never looks like a usable win and retries only the lookup. */
    @Test fun failedOpportunityConfirmationRetriesInPlace() {
        bookings.opportunityHandler = { _, _ -> throw IOException("server unavailable") }
        start()
        compose.onNodeWithText("Could not confirm this booking opportunity.").assertIsDisplayed()
        compose.onNodeWithTag("calendar-date-$start").assertDoesNotExist()
        compose.runOnIdle { bookings.opportunityHandler = { _, _ -> null } }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("calendar-date-$start").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(1, creations) }
    }

    /** The shared cell can be occupied as inventory while still accepting excluded checkout. */
    @Test fun occupiedCheckoutRemainsSelectableAfterArrival() {
        sites.calendarNights.value =
            sites.calendarNights.value + CalendarNight(end, true, "another")
        start()
        compose.onNodeWithTag("calendar-date-$end").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("calendar-date-$start").performScrollTo().performClick()
        compose
            .onNodeWithTag(
                "calendar-date-$end",
            ).performScrollTo()
            .assertIsEnabled()
            .performClick()
        next()
        compose.onNodeWithText("Number of guests").assertIsDisplayed()
    }

    /** Enlarged text retains reachable controls and the same four-step flow. */
    @Test fun largeTextCanReachAllStepsAndPolicyActions() {
        start(fontScale = 2f)
        review()
        open("Review rules and policy")
        open("Reservation & refund policy")
        compose
            .onNodeWithText(
                "Simulated payments and refunds",
            ).performScrollTo()
            .assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Rules and policy").assertIsDisplayed()
    }

    /** The actual account root exposes the winning booking entry with F15 pending. */
    @Test fun realLotteryReaderOpensTheRegisteredWinnerGraph() {
        val actual = TestAppHost().also { host = it }
        actual.campsites.records.value = listOf(site)
        actual.lotteries.records.value = listOf(draw)
        actual.lotteries.liveOffers.value = mapOf(draw.id to listOf(offer))
        actual.lotteries.entries.value =
            listOf(
                lotteries.entries.value
                    .single()
                    .copy(accountId = "account-1"),
            )
        compose.setContent { actual.Content() }
        compose.onNodeWithText("Lotteries").performClick()
        compose.onNodeWithText("Action needed").performClick()
        compose.onNodeWithText(draw.title).performClick()
        open("Book your stay")
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Date selection is pending the available-night rule.",
            ).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(draw.title).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, actual.lotteryDetailCreations) }
    }

    /** Confirmation blocks Back/edits, survives recreation and opens its saved reader. */
    @Test fun heldConfirmationLocksTheWizardAndBackCannotReopenItAfterSuccess() {
        val held = CompletableDeferred<String>()
        val saved = savedBooking()
        bookings.reserveHandler = { held.await() }
        val restoration = StateRestorationTester(compose)
        start(restoration)
        review()
        compose.onNodeWithText("Confirm reservation").assertIsEnabled().performClick()
        compose.onNodeWithText("Confirming your reservation…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm reservation").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Edit dates").performScrollTo().assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        compose.onNodeWithText("Discard this booking draft?").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Confirm reservation").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, bookings.reserveRequests.size)
            assertEquals(1, creations)
            bookings.records.value = listOf(saved)
            held.complete(saved.id)
        }
        compose.onNodeWithText("Confirmed").assertIsDisplayed()
        open("Payment & refund")
        compose.onNodeWithText("CAD 130.00").assertIsDisplayed()
        pressBack()
        pressBack()
        compose.onNodeWithText("Open booking").assertIsDisplayed()
        compose.onNodeWithText("Review your stay").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, bookings.reserveRequests.size) }
    }

    /** Unknown results allow only recovery, including cancelled saved history. */
    @Test fun failedConfirmationRecoveryBlocksBackAndRetriesOnlyTheHistoryRead() {
        bookings.reserveHandler = { throw IOException("Lost acknowledgement") }
        val saved =
            savedBooking().let {
                it.copy(
                    cancellation =
                        ReservationCancellation(
                            clock.instant(),
                            "owner",
                            Role.OWNER,
                            null,
                        ),
                    payment = it.payment.copy(refundedAt = clock.instant()),
                )
            }
        start()
        review()
        compose.runOnIdle {
            bookings.opportunityHandler = { _, _ -> throw IOException("Unknown history") }
        }
        compose.onNodeWithText("Confirm reservation").performClick()
        compose
            .onNodeWithText("The result is not confirmed. Check before trying again.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Confirm reservation").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        pressBack()
        compose.onNodeWithText("Discard this booking draft?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, bookings.reserveRequests.size)
            bookings.records.value = listOf(saved)
            bookings.opportunityHandler = { _, _ -> saved }
        }
        open("Check reservation")
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, bookings.reserveRequests.size) }
        pressBack()
        compose.onNodeWithText("Open booking").assertIsDisplayed()
    }

    /** A conflict preserves input and returns to date correction. */
    @Test fun conflictingDatesRemainEditableAndCanBeExplicitlyConfirmedAfterReview() {
        bookings.reserveHandler = {
            throw PersistenceFailure(PersistenceFailure.Reason.CONFLICT, "Claimed")
        }
        start()
        review()
        compose.onNodeWithText("Confirm reservation").performClick()
        compose.onNodeWithText("Choose dates").assertIsDisplayed()
        compose
            .onNodeWithText("These nights changed. Review your dates and try again.")
            .performScrollTo()
            .assertIsDisplayed()
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
        val saved = savedBooking()
        compose.runOnIdle {
            bookings.reserveHandler = {
                bookings.records.value = listOf(saved)
                saved.id
            }
        }
        compose.onNodeWithText("Confirm reservation").performClick()
        compose.onNodeWithText("Confirmed").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, bookings.reserveRequests.size)
            assertEquals(bookings.reserveRequests.first(), bookings.reserveRequests.last())
        }
    }

    /** Creates the complete saved aggregate returned by this transaction boundary. */
    private fun savedBooking() =
        previewReservation().copy(
            id = "saved/A ?1",
            lotteryId = draw.id,
            entrantId = "entrant",
            campsiteId = site.id,
            guestCount = 1,
        )

    /** Hosts registered graphs with explicit sources and no network bootstrap.
     * @param restoration Optional actual navigation save/restore driver.
     * @param useHelper Whether the known F15 fixture result is supplied.
     * @param fontScale Explicit system-text scale to exercise.
     */
    private fun start(
        restoration: StateRestorationTester? = null,
        useHelper: Boolean = true,
        fontScale: Float = 1f,
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                CampsbridgeTheme {
                    val controller = rememberNavController()
                    NavHost(controller, "caller") {
                        composable("caller") {
                            Button(
                                { controller.navigate(AppDestination.booking(draw.id, site.id)) },
                            ) {
                                Text("Open booking")
                            }
                        }
                        bookingGraph(controller) { drawId, siteId ->
                            viewModelFactory {
                                initializer {
                                    creations++
                                    BookingViewModel(
                                        "entrant",
                                        drawId,
                                        siteId,
                                        lotteries,
                                        sites,
                                        bookings,
                                        FakeImageRepository(),
                                        clock,
                                        createSavedStateHandle(),
                                        if (useHelper) { _, _ -> offer.nights.toSet() } else null,
                                    )
                                }
                            }
                        }
                        reservationGraph(controller) { id, context ->
                            viewModelFactory {
                                initializer {
                                    ReservationDetailViewModel(
                                        "entrant",
                                        id,
                                        context,
                                        bookings,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithText("Open booking").performClick()
    }

    /** Chooses actual shared calendar cells rather than changing ViewModel state directly. */
    private fun selectStay() {
        // The calendar has horizontal scrolling inside the wizard's vertical scroll owner.
        compose.onNodeWithTag("booking-calendar").performScrollTo()
        compose
            .onNodeWithTag("calendar-date-$start")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithTag("calendar-date-$end")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    /** Clicks the current reachable bottom action. */
    private fun next() {
        compose.onNodeWithText("Next").performClick()
    }

    /** Walks the four real steps, explicitly acknowledging their displayed terms. */
    private fun review() {
        selectStay()
        next()
        compose.onNodeWithText("Number of guests").performScrollTo().assertIsDisplayed()
        next()
        compose.onNodeWithText("Rules and policy").assertIsDisplayed()
        open("I have read and accept these rules and the reservation policy.")
        next()
        compose.onNodeWithText("Review your stay").assertIsDisplayed()
    }

    /** Scrolls only the actual screen's container to a reachable action.
     * @param text Exact localized fixture action text.
     */
    private fun open(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
}
