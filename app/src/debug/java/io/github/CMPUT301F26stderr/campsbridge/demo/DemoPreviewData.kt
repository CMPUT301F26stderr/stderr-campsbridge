/*
 * Coherent sample records shared by the offline workspace.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Announcement
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.SceneryPhoto
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Coherent sample records shared by the offline workspace.
 * @param scenario Identity and capabilities selected in Demo tools.
 */
class DemoPreviewData(
    scenario: DemoScenario,
) {
    val now: Instant = Instant.parse("2027-05-10T18:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val ownerId = "demo-host"
    val coOwnerId = "demo-cohost"
    val entrantId = "demo-account"
    val photoReferences =
        mapOf(
            ImageTarget(ImageTargetType.CAMPSITE, "demo-river") to SceneryPhoto.JASPER.reference,
            ImageTarget(ImageTargetType.CAMPSITE, "demo-alpine") to SceneryPhoto.BANFF.reference,
            ImageTarget(ImageTargetType.CAMPSITE, "demo-lake") to SceneryPhoto.KANANASKIS.reference,
            ImageTarget(ImageTargetType.CAMPSITE, "demo-cedar") to SceneryPhoto.JASPER.reference,
        )
    val accounts: List<Account> =
        listOf(
            account(ownerId, "Jordan Lee", setOf(Role.ENTRANT, Role.OWNER)),
            account(coOwnerId, "Sam Rivera", setOf(Role.ENTRANT, Role.OWNER)),
            account(entrantId, "Alex Chen", setOf(Role.ENTRANT)),
            account("demo-other", "Morgan Park", setOf(Role.ENTRANT)),
            account("demo-admin", "Taylor Brooks", Role.entries.toSet()),
        )
    val campsites: List<Campsite> =
        listOf(
            sampleCampsite("demo-river", ownerId).let {
                it.copy(
                    details =
                        it.details.copy(
                            description =
                                "A quiet riverside clearing beneath the pines, with " +
                                    "walking trails along the Athabasca River.",
                            amenities = setOf("water", "flush-toilets", "picnic-table"),
                            rules =
                                "Quiet hours begin at 10 pm. Keep food secured and " +
                                    "leave the clearing clean.",
                        ),
                )
            },
            sampleCampsite("demo-alpine", ownerId).let {
                it.copy(
                    details =
                        it.details.copy(
                            name = "Alpine Meadow",
                            description =
                                "Wake up to mountain views and trails through the " +
                                    "meadow.",
                            location = Coordinates(51.1784, -115.5708),
                            locationLabel = "Banff, Alberta",
                            maxGuests = 4,
                            nightlyPriceCents = 8000,
                            tags = setOf("national-parks"),
                        ),
                )
            },
            sampleCampsite("demo-lake", ownerId).let {
                it.copy(
                    details =
                        it.details.copy(
                            name = "Pine Lake Retreat",
                            description =
                                "A sheltered lakeside pitch for slow mornings and canoe " +
                                    "trips.",
                            location = Coordinates(52.7, -113.5),
                            locationLabel = "Pine Lake, Alberta",
                            maxGuests = 6,
                            nightlyPriceCents = 5500,
                            tags = setOf("lakeside"),
                        ),
                )
            },
            sampleCampsite("demo-cedar", coOwnerId).let {
                it.copy(
                    details =
                        it.details.copy(
                            name = "Cedar Ridge",
                            nightlyPriceCents = 9000,
                            description = "A sheltered forest pitch near Jasper's walking trails.",
                        ),
                )
            },
            sampleCampsite("demo-draft", ownerId, published = false).let {
                it.copy(details = it.details.copy(name = "Forest clearing"))
            },
        )
    val lotteries: List<Lottery> =
        listOf(
            lottery("demo-open", "June riverside getaway", open = true),
            lottery("demo-won", "Your spring escape"),
            lottery("demo-lost", "Long weekend draw"),
            lottery("demo-booked", "Riverside weekend"),
            lottery("demo-past", "Early spring camping", at = now.minusSeconds(1728000)),
            lottery("demo-shared", "Riverside and cedar weekend").copy(participantOwnerIds = setOf(ownerId, coOwnerId)),
            lottery(
                "demo-lottery-draft",
                "Summer under the pines",
            ).copy(publishedAt = null, drawnAt = null),
        )
    val offers: Map<String, List<LotteryOffer>> =
        lotteries.associate { draw ->
            draw.id to
                if (draw.id == "demo-shared") {
                    listOf(
                        LotteryOffer("demo-river", (20..21).map(::date)),
                        LotteryOffer("demo-cedar", (20..21).map(::date)),
                    )
                } else {
                    listOf(
                        LotteryOffer(
                            "demo-river",
                            if (draw.id == "demo-past") {
                                (5..6).map(::date)
                            } else if (draw.id ==
                                "demo-won"
                            ) {
                                (18..21).map(::date)
                            } else {
                                (14..21).map(::date)
                            },
                        ),
                    )
                }
        }
    val entries: List<LotteryEntry> =
        listOf(
            entry("demo-open", entrantId, EntryState.WAITING),
            entry("demo-open", "demo-other", EntryState.WAITING),
            entry("demo-won", entrantId, EntryState.OFFERED),
            entry("demo-won", "demo-other", EntryState.LOST),
            entry("demo-lost", entrantId, EntryState.LOST),
            entry("demo-booked", entrantId, EntryState.OFFERED),
            entry("demo-shared", entrantId, EntryState.OFFERED),
            entry("demo-shared", "demo-other", EntryState.LOST),
            entry("demo-past", entrantId, EntryState.OFFERED).copy(
                joinedAt = now.minusSeconds(1900800),
                stateChangedAt = now.minusSeconds(1814400),
            ),
        )
    val bookedReservationId =
        io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys.reservation(
            "demo-booked",
            entrantId,
        )
    val pastReservationId =
        io.github.CMPUT301F26stderr.campsbridge.data.firestore.DocumentKeys.reservation(
            "demo-past",
            entrantId,
        )
    val sharedReservationId = DocumentKeys.reservation("demo-shared", entrantId)
    val reservations: List<Reservation> =
        listOf(
            stay(bookedReservationId, "demo-booked", 15),
            stay(pastReservationId, "demo-past", 5),
            stay(sharedReservationId, "demo-shared", 20, "demo-cedar"),
        )
    val calendars: Map<String, List<CalendarNight>> =
        campsites.associate { site ->
            site.id to
                (1..31).map { day ->
                    val date = date(day)
                    val stay =
                        reservations.firstOrNull {
                            it.campsiteId == site.id && date >= it.checkInDate &&
                                date < it.checkOutDate
                        }
                    CalendarNight(
                        date,
                        day in 11..28 || stay != null,
                        stay?.id,
                    )
                }
        }
    val reviews: List<Review> =
        listOf(
            Review("demo-river", entrantId, 4, null, now.minusSeconds(3600), now.minusSeconds(3600)),
            Review(
                "demo-river",
                "demo-other",
                5,
                "Clear directions and plenty of shade.",
                now.minusSeconds(7200),
                now.minusSeconds(7200),
            ),
            Review(
                "demo-cedar",
                entrantId,
                null,
                "The host answered our arrival questions quickly.",
                now.minusSeconds(1800),
                now.minusSeconds(1800),
            ),
        )
    val messages: List<Message> =
        listOf(
            Message(
                "demo-arrival-question",
                sharedReservationId,
                entrantId,
                "Can we arrive around six?",
                now.minusSeconds(3600),
                now.minusSeconds(1900),
            ),
            Message(
                "demo-arrival-reply",
                sharedReservationId,
                coOwnerId,
                "Yes, I'll meet you by the gate.",
                now.minusSeconds(1800),
            ),
        )
    val events: List<Notification> =
        listOf(
            event("demo-notice-won", NotificationType.LOTTERY_WON, SubjectType.LOTTERY, "demo-won"),
            event(
                "demo-notice-stay",
                NotificationType.RESERVATION_CONFIRMED,
                SubjectType.RESERVATION,
                bookedReservationId,
            ),
            event(
                "demo-notice-lost",
                NotificationType.LOTTERY_LOST,
                SubjectType.LOTTERY,
                "demo-lost",
            ),
            Notification(
                "demo-notice-owner",
                ownerId,
                NotificationType.OWNER_ANNOUNCEMENT,
                NotificationSubject(SubjectType.LOTTERY, "demo-open"),
                Announcement(
                    "Getting ready for your stay",
                    "The riverside trail is open. Bring warm layers for the evenings.",
                ),
                now.minusSeconds(7200),
            ),
            Notification(
                "demo-shared-announcement",
                coOwnerId,
                NotificationType.OWNER_ANNOUNCEMENT,
                NotificationSubject(SubjectType.LOTTERY, "demo-shared"),
                Announcement("Arrival at Cedar Ridge", "Please use the marked entrance by the trail."),
                now.minusSeconds(5400),
            ),
        ) +
            messages.map { message ->
                Notification(
                    DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, message.id),
                    message.senderId,
                    NotificationType.NEW_MESSAGE,
                    NotificationSubject(SubjectType.MESSAGE, message.id),
                    null,
                    message.createdAt,
                )
            }
    val inboxes: Map<String, List<InboxReceipt>> =
        mapOf(
            entrantId to
                events.filter { it.type != NotificationType.NEW_MESSAGE || it.senderId != entrantId }.map {
                    InboxReceipt(
                        it.id,
                        if (it.id ==
                            "demo-notice-lost"
                        ) {
                            now.minusSeconds(1800)
                        } else {
                            null
                        },
                    )
                },
            ownerId to listOf(InboxReceipt("demo-notice-stay", null)),
            coOwnerId to
                messages.filter { it.senderId == entrantId }.map {
                    InboxReceipt(DocumentKeys.compound(NotificationType.NEW_MESSAGE.name, it.id), it.readAt)
                },
            "demo-other" to
                listOf(InboxReceipt("demo-notice-owner", null), InboxReceipt("demo-shared-announcement", null)),
        )

    /** Builds a fixture account with the same identity used by its sample records.
     * @param id Stable fixture identity.
     * @param name Display label.
     * @param roles Explicit capabilities.
     */
    private fun account(
        id: String,
        name: String,
        roles: Set<Role>,
    ): Account =
        Account(
            id,
            name,
            "camper@example.com",
            null,
            roles,
            Preferences(setOf("lakeside")),
            AccountSettings(),
            now.minusSeconds(864000),
        )

    /** Builds an open or drawn sample without waiting for real dates.
     * @param id Stable draw identity.
     * @param title Display label.
     * @param open Whether registration is currently open.
     * @param at Fixture window reference, moved back for historical draws.
     */
    private fun lottery(
        id: String,
        title: String,
        open: Boolean = false,
        at: Instant = now,
    ): Lottery =
        Lottery(
            id,
            ownerId,
            title,
            "A whole campsite for a quiet break in the outdoors.",
            LotteryConfiguration(
                at.minusSeconds(172800),
                if (open) at.plusSeconds(172800) else at.minusSeconds(86400),
                if (open) at.plusSeconds(172800) else at.minusSeconds(86400),
                at.plusSeconds(864000),
                1,
                null,
            ),
            if (open) 2 else 0,
            at.minusSeconds(259200),
            at.minusSeconds(172800),
            if (open) null else at.minusSeconds(86400),
        )

    /** Supplies a literal May day in the fixed demonstration month.
     * @param day Day of May 2027.
     */
    private fun date(day: Int): LocalDate =
        LocalDate.of(2027, 5, day)

    /** Records a known sample participation state.
     * @param lotteryId Associated fixture draw.
     * @param accountId Associated fixture account.
     * @param state Literal recorded outcome.
     */
    private fun entry(
        lotteryId: String,
        accountId: String,
        state: EntryState,
    ): LotteryEntry =
        LotteryEntry(lotteryId, accountId, state, now.minusSeconds(172800), now.minusSeconds(86400))

    /** Retains exact booking facts so detail, payment and terms use the same saved aggregate.
     * @param id Saved reservation identity.
     * @param lotteryId Consumed draw identity.
     * @param day Arrival day in the fixed month.
     * @param campsiteId Actual selected listing, including another participating owner's campsite.
     */
    private fun stay(
        id: String,
        lotteryId: String,
        day: Int,
        campsiteId: String = "demo-river",
    ): Reservation {
        val site = campsites.single { it.id == campsiteId }
        val bookedAt = now.minusSeconds(if (day < 10) 950400 else 86400)
        return Reservation(
            id,
            lotteryId,
            entrantId,
            site.id,
            date(day),
            date(day + 2),
            2,
            BookedCampsite(
                site.details.name,
                site.details.locationLabel,
                site.details.location,
                site.details.timeZone,
            ),
            AcceptedTerms(site.details.rules, BookingPolicy.VERSION, bookedAt),
            bookedAt,
            Payment(site.details.nightlyPriceCents * 2, bookedAt),
        )
    }

    /** Builds one history item with an actual saved navigation target.
     * @param id Event identity.
     * @param type Event presentation template.
     * @param subject Typed target category.
     * @param target Saved fixture identity.
     */
    private fun event(
        id: String,
        type: NotificationType,
        subject: SubjectType,
        target: String,
    ): Notification =
        Notification(
            id,
            null,
            type,
            NotificationSubject(subject, target),
            null,
            now.minusSeconds(3600),
        )
}
