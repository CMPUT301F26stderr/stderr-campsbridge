package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.account.DeviceRegistrationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.PaymentDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CampsiteDocument
import io.github.CMPUT301F26stderr.campsbridge.data.image.ImageAttachmentDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryOfferDocument
import io.github.CMPUT301F26stderr.campsbridge.data.messaging.MessageDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.InboxReceiptDocument
import io.github.CMPUT301F26stderr.campsbridge.data.notification.NotificationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReactionDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReplyDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReviewDocument
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.DeviceRegistration
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.AcceptedTerms
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookedCampsite
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Payment
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.stayNights
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CalendarNight
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import io.github.CMPUT301F26stderr.campsbridge.domain.messaging.Message
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.InboxReceipt
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.Notification
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationSubject
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.NotificationType
import io.github.CMPUT301F26stderr.campsbridge.domain.notification.SubjectType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reaction
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReactionType
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Reply
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Small executable convention; emulator transaction/race tests remain a separate test task. */
class PersistenceConventionTest {
    private val now = Instant.parse("2027-05-01T12:00:00Z")
    private val start = LocalDate.parse("2027-05-15")
    private val point = Coordinates(53.0, -118.0)

    /**
     * Asserts that encoding and decoding preserve an application value at its document path.
     *
     * @param T Application model represented by the codec or operation.
     * @param path Firestore document path carrying identity and decoding context.
     * @param codec Codec converting stored fields into the application model.
     * @param value Expected application value after codec round-trip.
     */
    private fun <T> roundTrip(
        path: String,
        codec: DocumentCodec<T>,
        value: T,
    ) {
        assertEquals(value, codec.decode(path, codec.encode(value)))
    }

    /**
     * Verifies all document codecs preserve models without duplicating path IDs or derived
     * night counts.
     */
    @Test
    fun documentsRoundTripWithoutPersistingIdentityOrDerivedFields() {
        val account =
            Account(
                "entrant",
                "Camper",
                null,
                null,
                setOf(Role.ENTRANT, Role.OWNER),
                Preferences(),
                AccountSettings(),
                now,
            )
        roundTrip("accounts/entrant", AccountDocument, account)
        assertFalse(AccountDocument.encode(account).containsKey("id"))
        roundTrip(
            "accounts/entrant/devices/device",
            DeviceRegistrationDocument,
            DeviceRegistration("device", "token", now),
        )
        val site =
            Campsite(
                "site",
                "owner",
                CampsiteDetails(
                    "Camp",
                    "Quiet",
                    point,
                    "Jasper",
                    "America/Edmonton",
                    8,
                    setOf("water"),
                    setOf("quiet"),
                    6500,
                    "Be kind",
                ),
                now,
                now,
            )
        roundTrip("campsites/site", CampsiteDocument, site)
        roundTrip(
            "campsites/site/calendar/$start",
            CalendarNightDocument,
            CalendarNight(start, true),
        )
        val config =
            LotteryConfiguration(
                now,
                now.plusSeconds(100),
                now.plusSeconds(100),
                now.plusSeconds(200),
                5,
                null,
            )
        roundTrip(
            "lotteries/draw",
            LotteryDocument,
            Lottery("draw", "owner", "May", "Weekend", config, 1, now, now),
        )
        roundTrip(
            "lotteries/draw/offers/site",
            LotteryOfferDocument,
            LotteryOffer("site", listOf(start)),
        )
        roundTrip(
            "lotteries/draw/entries/entrant",
            LotteryEntryDocument,
            LotteryEntry("draw", "entrant", EntryState.OFFERED, now, now),
        )
        val id = DocumentKeys.reservation("draw", "entrant")
        val reservation =
            Reservation(
                id,
                "draw",
                "entrant",
                "site",
                start,
                start.plusDays(2),
                4,
                BookedCampsite("Camp", "Jasper", point, "America/Edmonton"),
                AcceptedTerms("Be kind", BookingPolicy.VERSION, now),
                now,
            )
        roundTrip("reservations/$id", ReservationDocument, reservation)
        assertFalse(ReservationDocument.encode(reservation).containsKey("nightCount"))
        roundTrip("payments/$id", PaymentDocument, Payment(id, 13000, now, now))
        val target = ImageTarget(ImageTargetType.CAMPSITE, "site")
        roundTrip(
            "images/${DocumentKeys.attachment(target)}",
            ImageAttachmentDocument,
            ImageAttachment(target, "blob", now),
        )
        roundTrip(
            "campsites/site/reviews/entrant",
            ReviewDocument,
            Review("site", "entrant", 5, null, now, now),
        )
        roundTrip(
            "campsites/site/reviews/entrant/replies/reply",
            ReplyDocument,
            Reply("reply", "owner", null, "Thanks", now),
        )
        roundTrip(
            "campsites/site/reviews/entrant/reactions/owner",
            ReactionDocument,
            Reaction("owner", ReactionType.HELPFUL),
        )
        roundTrip(
            "notifications/event",
            NotificationDocument,
            Notification(
                "event",
                null,
                NotificationType.LOTTERY_WON,
                NotificationSubject(SubjectType.LOTTERY, "draw"),
                null,
                now,
            ),
        )
        roundTrip("accounts/entrant/inbox/event", InboxReceiptDocument, InboxReceipt("event", null))
        roundTrip(
            "messages/message",
            MessageDocument,
            Message("message", id, "owner", "Welcome", now),
        )
    }

    /**
     * Verifies compound keys do not collide and image targets/Storage paths follow the
     * shared convention.
     */
    @Test
    fun compositeKeysAreUnambiguousAndAttachmentTargetsRoundTrip() {
        assertNotEquals(DocumentKeys.reservation("a_b", "c"), DocumentKeys.reservation("a", "b_c"))
        val target = ImageTarget(ImageTargetType.ACCOUNT, "user_with_underscores")
        assertEquals(target, DocumentKeys.target(DocumentKeys.attachment(target)))
        assertEquals("images/blob/original", DocumentKeys.blob("blob"))
    }

    /**
     * Verifies occupied-night enumeration uses local dates and excludes checkout across a
     * DST boundary.
     */
    @Test
    fun nightsUseCalendarDaysAndExcludeCheckoutAcrossDaylightSaving() {
        val arrival = LocalDate.parse("2027-03-13")
        assertEquals(listOf(arrival, arrival.plusDays(1)), stayNights(arrival, arrival.plusDays(2)))
    }

    /**
     * Verifies missing fields, unknown enum names, and malformed date identities fail as
     * decoding errors.
     */
    @Test
    fun missingRequiredFieldsAndUnknownEnumsFailClearly() {
        assertThrows(DocumentDecodingException::class.java) {
            AccountDocument.decode("accounts/broken", emptyMap())
        }
        assertThrows(DocumentDecodingException::class.java) {
            ReactionDocument.decode(
                "campsites/site/reviews/a/reactions/b",
                mapOf("type" to "UNKNOWN"),
            )
        }
        assertThrows(DocumentDecodingException::class.java) {
            CalendarNightDocument.decode(
                "campsites/site/calendar/2027-2-1",
                mapOf("isOffered" to true),
            )
        }
    }

    /**
     * Verifies nonfinite settings, invalid time windows, and duplicate offered dates are
     * rejected.
     */
    @Test
    fun invalidConfigurationAndNonFiniteSettingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountSettings(fontScale = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LotteryConfiguration(now, now, now, now, 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LotteryOffer("site", listOf(start, start))
        }
    }
}
