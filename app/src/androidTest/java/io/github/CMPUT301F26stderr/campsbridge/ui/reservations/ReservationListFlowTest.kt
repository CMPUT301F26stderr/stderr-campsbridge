package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.testsupport.TestAppHost
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Seven fixed interactions through actual account navigation and the shared saved reader. */
class ReservationListFlowTest {
    @get:Rule val compose = createComposeRule()
    private val host = TestAppHost()
    private val record =
        previewReservation().copy(
            id = "stay/A ?1",
            entrantId = "account-1",
            campsiteId = "site/A",
        )
    private val other =
        record.copy(
            id = "stay/B",
            campsiteId = "site/B",
            bookedCampsite = record.bookedCampsite.copy(name = "Pine Camp"),
        )
    private val categories =
        mutableMapOf(
            record.id to ReservationCategory.UPCOMING,
            other.id to ReservationCategory.IN_PROGRESS,
        )

    /** Releases every real root/feature model after an independent interaction. */
    @After fun close() {
        compose.runOnIdle { host.close() }
    }

    /** Saved identity/children retain one parent through restoration and actual list Back. */
    @Test fun entrantPrimaryOpensSavedChildrenAndReturnsToTheSameList() {
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithTag("reservation:${record.id}").performClick()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.onNodeWithText("Payment & refund").performScrollTo().performClick()
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Accepted rules & policy").performScrollTo().performClick()
        compose.onNodeWithText(record.acceptedTerms.rules).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Reservations").assertIsSelected()
        compose.onNodeWithText(record.bookedCampsite.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.reservationListCreations)
            assertEquals(1, host.reservationDetailCreations)
            assertEquals(
                listOf(record.id to ReservationContext.ENTRANT),
                host.reservationDetailRequests,
            )
            assertEquals(1, host.bookings.activeEntrantObservers)
            assertEquals(0, host.bookings.activeRecordObservers)
            assertTrue(host.bookings.reserveRequests.isEmpty())
        }
    }

    /** Owner filters are local/restorable and failed names retry without replacing reservations. */
    @Test fun ownerFilterAndIndependentNameRetrySurvivePrimaryReturnAndRestore() {
        val restoration = StateRestorationTester(compose)
        host.reservationAccounts.getHandler = { throw IOException("profile offline") }
        start(owner = true, records = listOf(record, other), restoration = restoration)
        compose.onAllNodesWithText("Could not load camper.").onFirst().assertIsDisplayed()
        compose.runOnIdle {
            host.reservationAccounts.getHandler =
                { sampleAccount().copy(name = "Robin") }
        }
        compose.onNodeWithText("Retry camper names").performClick()
        select("Pine Camp")
        compose.onNodeWithText(record.bookedCampsite.name).assertDoesNotExist()
        compose.onNodeWithText("Robin").assertIsDisplayed()
        compose.onNodeWithText("Inbox").performClick()
        compose.onNodeWithText("Reservations").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Robin").assertIsDisplayed()
        compose.onNodeWithText(record.bookedCampsite.name).assertDoesNotExist()
        select("All campsites")
        compose.onNodeWithText(record.bookedCampsite.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, host.reservationListCreations)
            assertEquals(listOf("account-1"), host.bookings.ownerRequests)
            assertEquals(2, host.reservationAccounts.readIds.size)
        }
    }

    /** The saved campsite's filtered child returns to that exact management caller. */
    @Test fun campsiteManagementOpensItsFilteredListAndBackKeepsTheCaller() {
        start(owner = true, records = listOf(record, other), openPrimary = false)
        compose.onNodeWithText("Owner site").performClick()
        compose.onNodeWithText("View reservations").performScrollTo().performClick()
        compose.onNodeWithTag("reservation:${record.id}").performClick()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Active").assertIsDisplayed()
        compose.onNodeWithText("Pine Camp").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage campsite").assertIsDisplayed()
        compose.onNodeWithText("Owner site").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(ReservationContext.OWNER to record.campsiteId),
                host.reservationListRequests,
            )
            assertEquals(
                listOf(record.id to ReservationContext.OWNER),
                host.reservationDetailRequests,
            )
            assertEquals(0, host.bookings.activeOwnerObservers)
        }
    }

    /** Loading, failure, confirmed empty and arriving saved data remain distinct. */
    @Test fun heldSourceCanFailAndRetryToConfirmedEmptyBeforeASavedRowArrives() {
        val held = CompletableDeferred<Unit>()
        host.bookings.entrantHandler =
            {
                flow {
                    held.await()
                    throw IOException("aggregate offline")
                }
            }
        try {
            start()
            compose.onNodeWithText("Loading reservations…").assertIsDisplayed()
            compose.onNodeWithText("No active reservations.").assertDoesNotExist()
            compose.runOnIdle { held.complete(Unit) }
            compose.onNodeWithText("Could not load reservations.").assertIsDisplayed()
            compose.runOnIdle {
                host.bookings.entrantHandler = null
                host.bookings.records.value = emptyList()
            }
            compose.onNodeWithText("Retry").performClick()
            compose.onNodeWithText("No active reservations.").assertIsDisplayed()
            compose.runOnIdle { host.bookings.records.value = listOf(record) }
            compose.onNodeWithText(record.bookedCampsite.name).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(2, host.bookings.entrantRequests.size)
                assertEquals(1, host.bookings.activeEntrantObservers)
                assertEquals(1, host.reservationListCreations)
            }
        } finally {
            held.complete(Unit)
        }
    }

    /** Observed cancellation changes Active membership without a client write or history UI. */
    @Test fun confirmedCancellationLeavesActiveWithoutInventingHistoryActions() {
        start()
        compose.onNodeWithText(record.bookedCampsite.name).assertIsDisplayed()
        compose.runOnIdle {
            categories[record.id] = ReservationCategory.CANCELLED
            host.bookings.records.value =
                listOf(
                    record.copy(
                        cancellation =
                            ReservationCancellation(
                                Instant.parse("2027-05-01T12:00:00Z"),
                                "account-1",
                                Role.ENTRANT,
                                "Plans changed",
                            ),
                    ),
                )
        }
        compose.onNodeWithText("No active reservations.").assertIsDisplayed()
        compose.onNodeWithText(record.bookedCampsite.name).assertDoesNotExist()
        compose.onNodeWithText("Past").assertDoesNotExist()
        compose.onNodeWithText("Cancelled").assertDoesNotExist()
        compose.onNodeWithText("Map").assertDoesNotExist()
        compose.runOnIdle { assertTrue(host.bookings.reserveRequests.isEmpty()) }
    }

    /** Large system text and multiline saved names retain a reachable actual-ID card action. */
    @Test fun enlargedMultilineRowsRemainReachableAndOpenTheirSavedIdentity() {
        val long =
            record.copy(
                bookedCampsite =
                    record.bookedCampsite.copy(
                        name = "River Camp\nNorth meadow\nA quiet place beside the water",
                    ),
            )
        start(records = listOf(long), fontScale = 2f)
        compose
            .onNodeWithText(
                long.bookedCampsite.name,
            ).performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText(long.id).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(long.id to ReservationContext.ENTRANT),
                host.reservationDetailRequests,
            )
        }
    }

    /** Missing reserved helpers stay visible and cannot become a false known-empty Active list. */
    @Test fun defaultDependenciesRemainExplicitAndDoNotClassifyRows() {
        start(helpers = false)
        compose
            .onNodeWithText(
                "Reservation categories are awaiting implementation.",
            ).assertIsDisplayed()
        compose
            .onNodeWithText(
                "Reservation ordering is awaiting implementation.",
            ).assertIsDisplayed()
        compose.onNodeWithText("No active reservations.").assertDoesNotExist()
        compose.onNodeWithText(record.bookedCampsite.name).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, host.reservationListCreations) }
    }

    /** Starts actual root navigation with explicit source fixtures and supplied helper outcomes.
     * @param owner Whether the actual selected workspace is owner.
     * @param records Exact complete saved aggregates.
     * @param restoration Optional actual composition restoration host.
     * @param openPrimary Whether to enter Reservations immediately.
     * @param helpers Whether category/order dependencies are supplied.
     * @param fontScale System text scale for content, independent of optional app settings.
     */
    private fun start(
        owner: Boolean = false,
        records: List<Reservation> = listOf(record),
        restoration: StateRestorationTester? = null,
        openPrimary: Boolean = true,
        helpers: Boolean = true,
        fontScale: Float = 1f,
    ) {
        host.bookings.records.value = records
        if (helpers) {
            host.classifyReservation = { saved, _ -> categories.getValue(saved.id) }
            host.orderReservations = { it }
        }
        if (owner) {
            host.accounts.account.value = sampleAccount(setOf(Role.ENTRANT, Role.OWNER))
            host.preferences.progress.value =
                host.preferences.progress.value
                    .copy(workspace = Role.OWNER)
            host.campsites.records.value =
                listOf(
                    sampleCampsite(record.campsiteId, "account-1").let {
                        it.copy(details = it.details.copy(name = "Owner site"))
                    },
                )
            host.bookings.ownerHandler = { host.bookings.records.map { it } }
        }
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                host.Content()
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        if (openPrimary) compose.onNodeWithText("Reservations").performClick()
    }

    /** Selects a visible Material popup option rather than a similarly named saved row.
     * @param label Exact saved campsite name or All campsites.
     */
    private fun select(label: String) {
        compose.onNodeWithText("Campsite").performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }
}
