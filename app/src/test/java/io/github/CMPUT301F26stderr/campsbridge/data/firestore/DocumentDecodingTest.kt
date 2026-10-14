package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.data.account.AccountDocument
import io.github.CMPUT301F26stderr.campsbridge.data.booking.ReservationDocument
import io.github.CMPUT301F26stderr.campsbridge.data.campsite.CalendarNightDocument
import io.github.CMPUT301F26stderr.campsbridge.data.lottery.LotteryEntryDocument
import io.github.CMPUT301F26stderr.campsbridge.data.review.ReactionDocument
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.PersistenceFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owns malformed-data diagnostics and compatibility; domain validation matrices live elsewhere. */
class DocumentDecodingTest {
    /** Required fields cannot silently default or change their Firestore storage type. */
    @Test fun rejectsMissingAndWronglyTypedRequiredFields() {
        val path = "reservations/4:draw7:entrant"
        val fields = PersistenceFixtures.reservationFields
        for (data in listOf(
            fields - "guestCount",
            fields + ("guestCount" to 4.0),
            fields + ("createdAt" to "2027-05-01"),
        )) {
            val failure =
                assertThrows(
                    DocumentDecodingException::class.java,
                ) { ReservationDocument.decode(path, data) }
            assertEquals(path, failure.documentPath)
        }
    }

    /** Optional fields remain nullable when older documents omit them. */
    @Test fun readsOmittedOptionalFields() {
        assertEquals(
            CampingFixtures.account().copy(email = null, phone = null),
            AccountDocument.decode(
                "accounts/entrant",
                PersistenceFixtures.accountFields - setOf("email", "phone", "removal"),
            ),
        )
        assertEquals(
            null,
            CalendarNightDocument
                .decode(
                    "campsites/site/calendar/2027-05-15",
                    mapOf("isOffered" to true),
                ).reservationId,
        )
    }

    /** Nested malformed values identify the document and the failing section. */
    @Test fun identifiesNestedDecodingFailures() {
        val path = "accounts/broken"
        val invalid =
            PersistenceFixtures.accountFields +
                ("settings" to mapOf("notificationsEnabled" to "yes"))
        val failure =
            assertThrows(
                DocumentDecodingException::class.java,
            ) { AccountDocument.decode(path, invalid) }
        assertEquals("$path.settings", failure.documentPath)
        assertTrue(failure.message.orEmpty().contains("notificationsEnabled"))
    }

    /** Unknown enums and repeated set members cannot silently lose stored information. */
    @Test fun rejectsUnknownEnumsAndDuplicateSetMembers() {
        assertThrows(DocumentDecodingException::class.java) {
            ReactionDocument.decode(
                "campsites/site/reviews/entrant/reactions/owner",
                mapOf("type" to "UNKNOWN"),
            )
        }
        for (roles in listOf(listOf("ENTRANT", "ENTRANT"), listOf("UNKNOWN"))) {
            assertThrows(DocumentDecodingException::class.java) {
                AccountDocument.decode(
                    "accounts/entrant",
                    PersistenceFixtures.accountFields + ("roles" to roles),
                )
            }
        }
    }

    /** Nested identities cannot disagree with the stored query copy or reservation identity. */
    @Test fun rejectsContradictoryIdentity() {
        val entry = mapOf("accountId" to "someone-else", "state" to "WAITING")
        assertThrows(DocumentDecodingException::class.java) {
            LotteryEntryDocument.decode("lotteries/draw/entries/entrant", entry)
        }
        assertThrows(DocumentDecodingException::class.java) {
            ReservationDocument.decode(
                "reservations/unrelated",
                PersistenceFixtures.reservationFields,
            )
        }
    }

    /** Invalid dates and domain values retain their cause and document context. */
    @Test fun wrapsInvalidDatesAndDomainValues() {
        for (date in listOf("2027-2-01", "2027-02-30", "+10000-01-01")) {
            val path = "campsites/site/calendar/$date"
            val failure =
                assertThrows(DocumentDecodingException::class.java) {
                    CalendarNightDocument.decode(path, mapOf("isOffered" to true))
                }
            assertEquals(path, failure.documentPath)
            assertNotNull(failure.cause)
        }
        val failure =
            assertThrows(DocumentDecodingException::class.java) {
                ReservationDocument.decode(
                    "reservations/4:draw7:entrant",
                    PersistenceFixtures.reservationFields + ("guestCount" to 0L),
                )
            }
        assertTrue(failure.cause is IllegalArgumentException)
    }
}
