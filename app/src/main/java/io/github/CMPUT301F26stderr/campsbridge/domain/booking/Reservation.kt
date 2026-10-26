package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Versioned standard full-refund policy used by simulated booking and cancellation.
 */
object BookingPolicy {
    const val VERSION = "full-refund-v1"
    const val TEXT = "Cancelling or rejecting a reservation returns the full simulated payment."
}

/**
 * Location and display snapshot retained when a reservation is created.
 *
 * @param name Display name shown in the application.
 * @param locationLabel Human-readable campsite location.
 * @param location Campsite coordinates used for map display.
 * @param timeZone IANA timezone used to interpret local stay dates.
 */
data class BookedCampsite(
    val name: String,
    val locationLabel: String,
    val location: Coordinates,
    val timeZone: String,
)

/**
 * Rules and policy version acknowledged at booking, preserved for historical display.
 *
 * @param rules Campsite rules presented to entrants.
 * @param cancellationPolicyVersion Version identifying the policy accepted for this booking.
 * @param acceptedAt Time the booking terms were acknowledged.
 */
data class AcceptedTerms(
    val rules: String,
    val cancellationPolicyVersion: String,
    val acceptedAt: Instant,
)

/**
 * Audit record identifying when, why, and in which role a stay was cancelled.
 *
 * @param at Time the removal, cancellation, or event occurred.
 * @param actorId Account ID responsible for the requested operation.
 * @param initiatedAs Role in which the cancellation actor is acting.
 * @param reason Optional explanation recorded with the operation.
 */
data class ReservationCancellation(
    val at: Instant,
    val actorId: String,
    val initiatedAs: Role,
    val reason: String?,
)

/**
 * One winning opportunity consumed by a consecutive-night stay at one campsite.
 *
 * @param id Reservation document ID.
 * @param lotteryId Lottery document ID.
 * @param entrantId Account ID of the entrant using the winning opportunity.
 * @param campsiteId Campsite document ID.
 * @param checkInDate First occupied local campsite date.
 * @param checkOutDate Checkout date, excluded from occupied nights.
 * @param guestCount Number of guests occupying the entire campsite.
 * @param bookedCampsite Historical campsite display/location snapshot.
 * @param acceptedTerms Historical rules and policy acknowledgement.
 * @param createdAt Original record creation time.
 * @param payment Simulated charge and full-refund facts owned by this reservation.
 * @param cancellation Cancellation metadata, or null while not cancelled.
 */
data class Reservation(
    val id: String,
    val lotteryId: String,
    val entrantId: String,
    val campsiteId: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val guestCount: Long,
    val bookedCampsite: BookedCampsite,
    val acceptedTerms: AcceptedTerms,
    val createdAt: Instant,
    val payment: Payment,
    val cancellation: ReservationCancellation? = null,
) {
    init {
        require(checkOutDate > checkInDate)
        require(guestCount > 0)
    }

    val nightCount: Long
        get() = ChronoUnit.DAYS.between(checkInDate, checkOutDate)
}

/**
 * Simulated charge and optional full-refund timestamp for a reservation.
 *
 * @param amountCents Nonnegative total simulated CAD charge in integer cents.
 * @param paidAt Time the simulated charge was recorded.
 * @param refundedAt Full-refund time, or null if not refunded.
 */
data class Payment(
    val amountCents: Long,
    val paidAt: Instant,
    val refundedAt: Instant? = null,
) {
    init {
        require(amountCents >= 0)
    }
}

/**
 * Stay selection and accepted quote that must still match current data at confirmation.
 *
 * @param lotteryId Lottery document ID.
 * @param entrantId Account ID of the entrant using the winning opportunity.
 * @param campsiteId Campsite document ID.
 * @param checkInDate First occupied local campsite date.
 * @param checkOutDate Checkout date, excluded from occupied nights.
 * @param guestCount Number of guests occupying the entire campsite.
 * @param expectedTotalCents Displayed CAD total in cents that confirmation must still match.
 * @param acceptedRules Campsite rules the entrant acknowledged.
 * @param policyVersion Cancellation-policy version acknowledged by the entrant.
 */
data class BookingRequest(
    val lotteryId: String,
    val entrantId: String,
    val campsiteId: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val guestCount: Long,
    val expectedTotalCents: Long,
    val acceptedRules: String,
    val policyVersion: String,
) {
    init {
        require(checkOutDate > checkInDate)
        require(guestCount > 0)
        require(expectedTotalCents >= 0)
    }
}

/**
 * Actor and role requesting cancellation of an existing reservation.
 *
 * @param reservationId Reservation document ID.
 * @param actorId Account ID responsible for the requested operation.
 * @param initiatedAs Role in which the cancellation actor is acting.
 * @param reason Optional explanation recorded with the operation.
 */
data class CancellationRequest(
    val reservationId: String,
    val actorId: String,
    val initiatedAs: Role,
    val reason: String? = null,
)

/**
 * Lists occupied calendar dates, including check-in and excluding checkout.
 *
 * @param start Inclusive first local date in the range.
 * @param end Exclusive checkout date; must follow the first date.
 */
fun stayNights(
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> {
    require(end > start)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
}
