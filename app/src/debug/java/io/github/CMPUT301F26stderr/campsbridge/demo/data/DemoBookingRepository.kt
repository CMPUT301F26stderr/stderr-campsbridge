/*
 * Coordinates actual local booking, inventory, simulated payments and Inbox records.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.data

import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.CancellationRequest
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.stayNights
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.ALREADY_USED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CAPACITY_REACHED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.CONFLICT
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INACTIVE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.INVALID_STATE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.NOT_ALLOWED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.STALE_QUOTE
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure.Reason.WINDOW_CLOSED
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.requireOperation
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import io.github.CMPUT301F26stderr.campsbridge.data.firestore.FirestorePaths as Paths

/** Coordinates actual local booking, inventory, simulated payments and Inbox records.
 * @param initial Initial helper reservations.
 * @param campsites Initial helper listings.
 * @param clock Isolated helper time.
 * @param store Shared demo state and controlled time.
 */
class DemoBookingRepository(
    initial: List<Reservation> = emptyList(),
    campsites: List<Campsite> = emptyList(),
    clock: Clock = Clock.systemUTC(),
    private val store: DemoStore =
        DemoStore(
            buildMap {
                initial.forEach { put(Paths.reservation(it.id), ReservationDocument.encode(it)) }
                campsites.forEach { put(Paths.campsite(it.id), CampsiteDocument.encode(it)) }
            },
            start = clock.instant(),
        ),
) : BookingRepository {
    /** Reads a complete saved stay.
     * @param id Reservation identity.
     */
    override suspend fun get(id: String) =
        store.read { get(Paths.reservation(id), ReservationDocument) }

    /** Finds consumed opportunities, including historical fixture IDs and cancelled stays.
     * @param lotteryId Lottery identity.
     * @param entrantId Entrant identity.
     */
    override suspend fun getForOpportunity(
        lotteryId: String,
        entrantId: String,
    ) =
        store.read {
            list("reservations", ReservationDocument).firstOrNull {
                it.lotteryId == lotteryId &&
                    it.entrantId == entrantId
            }
        }

    /** Observes the complete saved stay.
     * @param id Reservation identity.
     */
    override fun observe(id: String) =
        store.observe { get(Paths.reservation(id), ReservationDocument) }

    /** Observes the person's saved stays.
     * @param entrantId Entrant identity.
     */
    override fun observeForEntrant(entrantId: String) =
        store.observe {
            list("reservations", ReservationDocument).filter {
                it.entrantId ==
                    entrantId
            }
        }

    /** Observes saved stays at one listing.
     * @param campsiteId Listing identity.
     */
    override fun observeForCampsite(campsiteId: String) =
        store.observe {
            list("reservations", ReservationDocument).filter {
                it.campsiteId ==
                    campsiteId
            }
        }

    /** Observes stays using current immutable listing ownership.
     * @param ownerId Owner identity.
     */
    override fun observeForOwner(ownerId: String) =
        store.observe {
            val owned = list("campsites", CampsiteDocument).filter { it.ownerId == ownerId }.map { it.id }.toSet()
            list("reservations", ReservationDocument).filter { it.campsiteId in owned }
        }

    /** Validates an unused win and commits the entire stay with its confirmation.
     * @param request Selected dates, group and accepted quote/terms.
     * @return Saved reservation identity.
     */
    override suspend fun reserve(request: BookingRequest): String =
        store.change {
            active(request.entrantId, Role.ENTRANT)
            requireOperation(
                list("reservations", ReservationDocument).none {
                    it.lotteryId == request.lotteryId &&
                        it.entrantId == request.entrantId
                },
                ALREADY_USED,
                "Winning offer already consumed",
            )
            val lottery = require(Paths.lottery(request.lotteryId), LotteryDocument)
            active(lottery.ownerId, Role.OWNER)
            val now = store.clock.instant()
            requireOperation(lottery.bookingIsOpen(now), WINDOW_CLOSED, "Booking window closed")
            val entry = require(Paths.entry(lottery.id, request.entrantId), LotteryEntryDocument)
            requireOperation(entry.state == EntryState.OFFERED, NOT_ALLOWED, "No unused winning offer")
            val offer = require(Paths.offer(lottery.id, request.campsiteId), LotteryOfferDocument)
            val nights = stayNights(request.checkInDate, request.checkOutDate)
            requireOperation(offer.nights.containsAll(nights), NOT_ALLOWED, "Dates not offered")
            val site = require(Paths.campsite(request.campsiteId), CampsiteDocument)
            requireOperation(site.isPublished && site.ownerId == lottery.ownerId, INACTIVE, "Campsite unavailable")
            requireOperation(
                request.checkInDate >= LocalDate.ofInstant(now, ZoneId.of(site.details.timeZone)),
                WINDOW_CLOSED,
                "Stay already begun",
            )
            requireOperation(request.guestCount <= site.details.maxGuests, CAPACITY_REACHED, "Group exceeds capacity")
            val total = Math.multiplyExact(site.details.nightlyPriceCents, nights.size.toLong())
            requireOperation(
                total == request.expectedTotalCents && request.acceptedRules == site.details.rules &&
                    request.policyVersion == BookingPolicy.VERSION,
                STALE_QUOTE,
                "Price or accepted terms changed",
            )
            val calendar = nights.map { get(Paths.night(site.id, it), CalendarNightDocument) }
            requireOperation(calendar.all { it?.isFree == true }, CONFLICT, "Some nights are no longer free")
            val id = DocumentKeys.reservation(lottery.id, request.entrantId)
            val stay =
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
            put(Paths.reservation(id), ReservationDocument, stay)
            calendar.filterNotNull().forEach {
                put(Paths.night(site.id, it.date), CalendarNightDocument, it.copy(reservationId = id))
            }
            reservationNotice(stay, false)
            id
        }

    /** Authorizes the participant, then refunds and releases only this stay's nights once.
     * @param request Actor and cancellation attribution.
     */
    override suspend fun cancel(request: CancellationRequest) {
        store.change {
            val actor = active(request.actorId, request.initiatedAs)
            val stay = require(Paths.reservation(request.reservationId), ReservationDocument)
            val site = require(Paths.campsite(stay.campsiteId), CampsiteDocument)
            requireOperation(
                when (request.initiatedAs) {
                    Role.ENTRANT -> actor.id == stay.entrantId
                    Role.OWNER -> actor.id == site.ownerId
                    Role.ADMIN -> true
                },
                NOT_ALLOWED,
                "Not a participant",
            )
            if (stay.cancellation != null) return@change
            val now = store.clock.instant()
            requireOperation(
                LocalDate.ofInstant(now, ZoneId.of(stay.bookedCampsite.timeZone)) < stay.checkOutDate,
                WINDOW_CLOSED,
                "Stay has ended",
            )
            val calendar =
                stayNights(stay.checkInDate, stay.checkOutDate).map {
                    require(Paths.night(stay.campsiteId, it), CalendarNightDocument)
                }
            requireOperation(
                calendar.all { it.reservationId == stay.id },
                INVALID_STATE,
                "Reservation/calendar mismatch",
            )
            val cancelled =
                stay.copy(
                    cancellation = ReservationCancellation(now, actor.id, request.initiatedAs, request.reason),
                    payment =
                        stay.payment.copy(
                            refundedAt =
                                stay.payment.refundedAt ?: now,
                        ),
                )
            put(Paths.reservation(stay.id), ReservationDocument, cancelled)
            calendar.forEach { night ->
                put(Paths.night(stay.campsiteId, night.date), CalendarNightDocument, night.copy(reservationId = null))
            }
            reservationNotice(cancelled, true)
        }
    }

    /** Creates one stable event and actual receipt inside the originating commit.
     * @param stay Saved aggregate.
     * @param cancelled Whether this is a cancellation event.
     */
    private fun DemoDocuments.reservationNotice(
        stay: Reservation,
        cancelled: Boolean,
    ) {
        val type = if (cancelled) NotificationType.RESERVATION_CANCELLED else NotificationType.RESERVATION_CONFIRMED
        notify(
            Notification(
                DocumentKeys.compound(type.name, stay.id),
                null,
                type,
                NotificationSubject(SubjectType.RESERVATION, stay.id),
                null,
                if (cancelled) stay.cancellation!!.at else stay.createdAt,
            ),
            listOf(stay.entrantId),
        )
    }
}
