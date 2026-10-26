package io.github.CMPUT301F26stderr.campsbridge.data.booking

import com.google.firebase.firestore.FirebaseFirestore
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.activeAccount
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.atomic
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.cancellation
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.observe
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.read
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.require
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationWrites
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.stayNights
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.ALREADY_USED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CAPACITY_REACHED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.STALE_QUOTE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * Owns reservation/payment, inventory and notification writes as coordinated transactions.
 * @param db Firestore client used by all participating records.
 * @param clock Injectable time source for booking and cancellation.
 */
internal class BookingWrites(
    private val db: FirebaseFirestore,
    private val clock: Clock,
) {
    /**
     * Validates an unused win, current quote/terms, and every night before atomically
     * creating the reservation/payment, claiming inventory, and queueing confirmation.
     * Returns the reservation ID.
     *
     * @param request Stay selection and expected price/terms to validate.
     */
    suspend fun reserve(request: BookingRequest): String {
        val id = DocumentKeys.reservation(request.lotteryId, request.entrantId)
        val reference = db.document(FirestorePaths.reservation(id))
        val nights = stayNights(request.checkInDate, request.checkOutDate)
        db.atomic { tx ->
            val now = clock.instant()
            tx.activeAccount(db, request.entrantId, Role.ENTRANT)
            val previous = tx.read(reference, ReservationDocument)
            requireOperation(
                previous == null,
                ALREADY_USED,
                "Winning opportunity has already been used",
            )
            val lottery =
                tx.require(db.document(FirestorePaths.lottery(request.lotteryId)), LotteryDocument)
            tx.activeAccount(db, lottery.ownerId, Role.OWNER)
            requireOperation(lottery.bookingIsOpen(now), WINDOW_CLOSED, "Booking window is closed")
            val entry =
                tx.require(
                    db.document(FirestorePaths.entry(request.lotteryId, request.entrantId)),
                    LotteryEntryDocument,
                )
            requireOperation(
                entry.state == EntryState.OFFERED,
                NOT_ALLOWED,
                "No unused winning offer",
            )
            val offer =
                tx.require(
                    db.document(FirestorePaths.offer(request.lotteryId, request.campsiteId)),
                    LotteryOfferDocument,
                )
            requireOperation(
                offer.nights.containsAll(nights),
                NOT_ALLOWED,
                "Dates are not offered by this lottery",
            )
            val site =
                tx.require(
                    db.document(FirestorePaths.campsite(request.campsiteId)),
                    CampsiteDocument,
                )
            requireOperation(
                site.isPublished && site.ownerId == lottery.ownerId,
                INACTIVE,
                "Campsite unavailable",
            )
            requireOperation(
                request.checkInDate >=
                    LocalDate.ofInstant(now, java.time.ZoneId.of(site.details.timeZone)),
                WINDOW_CLOSED,
                "Stay has already begun",
            )
            requireOperation(
                request.guestCount <= site.details.maxGuests,
                CAPACITY_REACHED,
                "Group exceeds campsite capacity",
            )
            val total = Math.multiplyExact(site.details.nightlyPriceCents, nights.size.toLong())
            requireOperation(
                total == request.expectedTotalCents &&
                    request.acceptedRules == site.details.rules &&
                    request.policyVersion == BookingPolicy.VERSION,
                STALE_QUOTE,
                "Price or accepted terms have changed",
            )
            val references = nights.map { db.document(FirestorePaths.night(site.id, it)) }
            val inventory = references.map { tx.read(it, CalendarNightDocument) }
            requireOperation(
                inventory.all { it?.isFree == true },
                CONFLICT,
                "Some nights are no longer free",
            )
            val reservation =
                Reservation(
                    id,
                    lottery.id,
                    request.entrantId,
                    site.id,
                    request.checkInDate,
                    request.checkOutDate,
                    request.guestCount,
                    BookedCampsite(
                        site.details.name,
                        site.details.locationLabel,
                        site.details.location,
                        site.details.timeZone,
                    ),
                    AcceptedTerms(request.acceptedRules, request.policyVersion, now),
                    now,
                    Payment(total, now),
                )
            tx.set(reference, ReservationDocument.encode(reservation))
            references.forEach { tx.update(it, "reservationId", id) }
            NotificationWrites.reservation(db, tx, reservation, false, now)
        }
        return id
    }

    /**
     * Authorizes the requested actor role and atomically cancels/refunds the stay, releases
     * inventory, and queues notification. Already cancelled stays are unchanged; the win
     * stays consumed.
     *
     * @param request Actor, role, and reason for cancellation.
     */
    suspend fun cancel(request: CancellationRequest) {
        db.atomic { tx ->
            val actor = tx.activeAccount(db, request.actorId, request.initiatedAs)
            val ref = db.document(FirestorePaths.reservation(request.reservationId))
            val reservation = tx.require(ref, ReservationDocument)
            val site =
                tx.require(
                    db.document(FirestorePaths.campsite(reservation.campsiteId)),
                    CampsiteDocument,
                )
            val allowed =
                when (request.initiatedAs) {
                    Role.ENTRANT -> actor.id == reservation.entrantId
                    Role.OWNER -> actor.id == site.ownerId
                    Role.ADMIN -> true
                }
            requireOperation(allowed, NOT_ALLOWED, "Not a participant or administrator")
            cancelReservationInTransaction(db, tx, reservation, request, clock.instant())
        }
    }
}

/**
 * Cancels/refunds a stay and releases its inventory in the caller’s transaction.
 * The caller authorizes the actor first; repeated cancellation is harmless.
 *
 * @param db Firestore client used for queries and coordinated writes.
 * @param tx Caller-owned Firestore transaction; reads must precede writes.
 * @param reservation Stored stay whose inventory, payment, or event is
 * addressed.
 * @param request Actor, role, and reason for cancellation.
 * @param now Instant used to evaluate the window or record the operation.
 */
internal fun cancelReservationInTransaction(
    db: FirebaseFirestore,
    tx: com.google.firebase.firestore.Transaction,
    reservation: Reservation,
    request: CancellationRequest,
    now: java.time.Instant,
) {
    if (reservation.cancellation != null) return
    val refs =
        stayNights(reservation.checkInDate, reservation.checkOutDate).map {
            db.document(FirestorePaths.night(reservation.campsiteId, it))
        }
    val nights = refs.map { tx.require(it, CalendarNightDocument) }
    requireOperation(
        nights.all { it.reservationId == reservation.id },
        INVALID_STATE,
        "Reservation/calendar mismatch",
    )
    val cancellation =
        ReservationCancellation(now, request.actorId, request.initiatedAs, request.reason)
    tx.set(
        db.document(FirestorePaths.reservation(reservation.id)),
        ReservationDocument.encode(
            reservation.copy(
                cancellation = cancellation,
                payment =
                    reservation.payment.copy(
                        refundedAt =
                            reservation.payment.refundedAt ?: now,
                    ),
            ),
        ),
    )
    refs.forEach { tx.update(it, "reservationId", null) }
    NotificationWrites.reservation(db, tx, reservation, true, now)
}
