package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.navigation.AppDestination
import io.github.CMPUT301F26stderr.campsbridge.navigation.reservationGraph
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Real saved-reader graph verifies children, historical content and caller/observer lifetime. */
class ReservationDetailTest {
    @get:Rule val compose = createComposeRule()
    private val record = previewReservation().copy(id = "reservation/A ?1")
    private val bookings = FakeBookingRepository(listOf(record))
    private val store = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    private var creations = 0

    /** Clears actual graph-owned readers after each independent case. */
    @After fun close() {
        compose.runOnIdle { store.clear() }
    }

    /**
     * Encoded identity, child Back and restoration all retain one parent and one aggregate
     * read.
     */
    @Test fun paymentAndTermsShareTheSavedParentAcrossRestoration() {
        val restoration = StateRestorationTester(compose)
        start(restoration = restoration)
        compose.onNodeWithText(record.id).assertIsDisplayed()
        open("Payment & refund")
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        compose.onNodeWithText("CAD 130.00").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        open("Accepted rules & policy")
        compose.onNodeWithText(record.acceptedTerms.rules).assertIsDisplayed()
        compose.onNodeWithText(record.acceptedTerms.cancellationPolicyVersion).assertIsDisplayed()
        compose
            .onNodeWithText(
                "Simulated payments and refunds",
            ).performScrollTo()
            .assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(record.id).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
            assertEquals(1, bookings.activeRecordObservers)
            assertTrue(bookings.getRequests.isEmpty())
        }
        pressBack()
        compose.onNodeWithText("Open saved reservation").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, bookings.activeRecordObservers) }
    }

    /** Unknown accepted policy keeps saved rules/version and never substitutes current text. */
    @Test fun unknownPolicyShowsUnavailableVersionWithoutLosingSavedRules() {
        bookings.records.value =
            listOf(
                record.copy(
                    acceptedTerms =
                        record.acceptedTerms.copy(
                            cancellationPolicyVersion = "retired-refund-v0",
                        ),
                ),
            )
        start()
        open("Accepted rules & policy")
        compose.onNodeWithText("retired-refund-v0").assertIsDisplayed()
        compose.onNodeWithText(record.acceptedTerms.rules).assertIsDisplayed()
        compose.onNodeWithText("Simulated payments and refunds").assertDoesNotExist()
        compose
            .onNodeWithText(
                "This policy version is not included in this build.",
            ).performScrollTo()
            .assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, creations) }
    }

    /** Missing data is not a failure; same source can restore the real identity in place. */
    @Test fun missingReservationRetainsBackAndCanReappear() {
        bookings.records.value = emptyList()
        start()
        compose.onNodeWithText("Reservation unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Payment & refund").assertDoesNotExist()
        compose.runOnIdle { bookings.records.value = listOf(record) }
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(record.id), bookings.observedRecordIds) }
        pressBack()
        compose.onNodeWithText("Open saved reservation").assertIsDisplayed()
    }

    /**
     * Failure while on a child retries the same aggregate without replacing its parent or
     * route.
     */
    @Test fun paymentFailureRetriesWithoutLeavingTheChild() {
        start()
        open("Payment & refund")
        compose.runOnIdle { bookings.recordFailure.value = IOException("offline") }
        compose.onNodeWithText("Could not load this reservation.").assertIsDisplayed()
        compose.onNodeWithText("Total paid").assertDoesNotExist()
        compose.runOnIdle { bookings.recordFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(listOf(record.id, record.id), bookings.observedRecordIds)
            assertEquals(1, bookings.activeRecordObservers)
        }
        pressBack()
        compose.onNodeWithText(record.id).assertIsDisplayed()
    }

    /** Refund facts update the existing payment/overview views, without another payment source. */
    @Test fun observedCancellationAndRefundUpdateTheSameSavedChildren() {
        start()
        open("Payment & refund")
        compose.onNodeWithText("Full simulated refund").assertDoesNotExist()
        compose.runOnIdle {
            bookings.records.value =
                listOf(
                    record.copy(
                        cancellation =
                            ReservationCancellation(
                                record.createdAt.plusSeconds(30),
                                "owner",
                                Role.OWNER,
                                "Campsite closure",
                            ),
                        payment =
                            record.payment.copy(
                                refundedAt = record.createdAt.plusSeconds(30),
                            ),
                    ),
                )
        }
        compose.onNodeWithText("Full simulated refund").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amount refunded").performScrollTo().assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Cancelled by owner").assertIsDisplayed()
        compose.onNodeWithText("Campsite closure").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(listOf(record.id), bookings.observedRecordIds)
        }
    }

    /**
     * Owner presentation uses the same historical content without premature actions/extra
     * tools.
     */
    @Test fun ownerCanReadHistoryWithoutMutationOrFinalControls() {
        bookings.records.value =
            listOf(
                record.copy(
                    cancellation =
                        ReservationCancellation(
                            record.createdAt.plusSeconds(30),
                            "administrator",
                            Role.ADMIN,
                            "Campsite removed",
                        ),
                    payment =
                        record.payment.copy(
                            refundedAt = record.createdAt.plusSeconds(30),
                        ),
                ),
            )
        start(context = ReservationContext.OWNER)
        compose.onNodeWithText("Cancelled by administrator").assertIsDisplayed()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.onNodeWithText("Cancel reservation").assertDoesNotExist()
        compose.onNodeWithText("Reject reservation").assertDoesNotExist()
        compose.onNodeWithText("Message owner").assertDoesNotExist()
        compose.onNodeWithText("Download receipt (CSV)").assertDoesNotExist()
        open("Accepted rules & policy")
        compose.onNodeWithText(record.acceptedTerms.rules).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertTrue(bookings.getRequests.isEmpty())
        }
    }

    /** Large text and long saved rules scroll inside the same child, keeping Back reachable. */
    @Test fun enlargedTextKeepsHistoricalRulesAndBackUsable() {
        val rules = "Keep the campsite clean.\n".repeat(30) + "Final saved rule."
        bookings.records.value =
            listOf(record.copy(acceptedTerms = record.acceptedTerms.copy(rules = rules)))
        start(fontScale = 2f)
        open("Accepted rules & policy")
        compose.onNodeWithText(rules).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(record.id).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, creations) }
    }

    /** An initial failure retries the encoded saved identity without any booking mutation. */
    @Test fun initialFailureRetriesTheSameReservationWithoutWriting() {
        bookings.recordFailure.value = IOException("offline")
        start()
        compose.onNodeWithText("Could not load this reservation.").assertIsDisplayed()
        compose.onNodeWithText("Payment & refund").assertDoesNotExist()
        compose.runOnIdle { bookings.recordFailure.value = null }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText(record.id).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(listOf(record.id, record.id), bookings.observedRecordIds)
            assertTrue(bookings.reserveRequests.isEmpty())
            assertTrue(bookings.cancellationRequests.isEmpty())
        }
        pressBack()
        compose.onNodeWithText("Open saved reservation").assertIsDisplayed()
    }

    /** Starts the actual saved-reader graph with a real caller and controlled repository.
     * @param context Actual caller presentation.
     * @param restoration Optional composition restoration host.
     * @param fontScale System font scale supplied to content.
     */
    private fun start(
        context: ReservationContext = ReservationContext.ENTRANT,
        restoration: StateRestorationTester? = null,
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
                            Button({
                                controller.navigate(
                                    AppDestination.reservationDetail(record.id, context),
                                )
                            }) { Text("Open saved reservation") }
                        }
                        reservationGraph(controller) { id, actualContext ->
                            viewModelFactory {
                                initializer {
                                    creations++
                                    ReservationDetailViewModel(
                                        if (actualContext ==
                                            ReservationContext.OWNER
                                        ) {
                                            "owner"
                                        } else {
                                            record.entrantId
                                        },
                                        id,
                                        actualContext,
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
        compose.onNodeWithText("Open saved reservation").performClick()
    }

    /** Opens an actual overview child after scrolling its row into view.
     * @param title Visible real destination label.
     */
    private fun open(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
    }
}
