package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/** F18 orders supplied complete records only; category/filter membership belongs to its caller. */
class ReservationOrderingTest {
    private val record = CampingFixtures.reservation()

    /** Confirmed empty input remains empty. */
    @Test fun emptyInputStaysEmpty() {
        assertEquals(emptyList<Any>(), sortReservationsByCheckIn(emptyList()))
    }

    /** One complete historical record remains unchanged. */
    @Test fun oneRecordStaysIntact() {
        assertEquals(listOf(record), sortReservationsByCheckIn(listOf(record)))
    }

    /** Earlier check-in dates precede later ones regardless of incoming source order. */
    @Test fun checkInIsAscending() {
        val later =
            record.copy(
                id = "later",
                checkInDate = record.checkInDate.plusDays(5),
                checkOutDate = record.checkOutDate.plusDays(5),
            )
        assertEquals(listOf(record, later), sortReservationsByCheckIn(listOf(later, record)))
    }

    /** Equal check-in dates use the exact case-sensitive ID as the tie-break. */
    @Test fun sameDateUsesExactIdAscending() {
        val records = listOf("z", "a", "A").map { record.copy(id = it) }
        assertEquals(listOf("A", "a", "z"), sortReservationsByCheckIn(records).map { it.id })
    }

    /** Check-in takes precedence even when the later record has a lower ID. */
    @Test fun datePrecedesIdentity() {
        val later = record.copy(id = "A", checkInDate = record.checkInDate.plusDays(1))
        val earlier = record.copy(id = "Z")
        assertEquals(listOf(earlier, later), sortReservationsByCheckIn(listOf(later, earlier)))
    }

    /** Cancelled/refunded rows stay present; this helper does not choose categories. */
    @Test fun neverFiltersCancelledOrRefundedRecords() {
        val cancelled =
            record.copy(
                id = "a",
                cancellation = ReservationCancellation(record.createdAt, "owner", Role.OWNER, null),
                payment = record.payment.copy(refundedAt = record.createdAt),
            )
        val active = record.copy(id = "z")
        assertEquals(
            listOf(cancelled, active),
            sortReservationsByCheckIn(listOf(active, cancelled)),
        )
    }

    /** Checkout, names and creation time cannot replace the declared sort keys. */
    @Test fun unrelatedFieldsDoNotChangeOrdering() {
        val first =
            record.copy(
                id = "a",
                checkOutDate = record.checkOutDate.plusDays(8),
                createdAt = record.createdAt.plusSeconds(30),
                bookedCampsite = record.bookedCampsite.copy(name = "Zulu"),
            )
        val second =
            record.copy(
                id = "b",
                bookedCampsite = record.bookedCampsite.copy(name = "Alpha"),
            )
        assertEquals(listOf(first, second), sortReservationsByCheckIn(listOf(second, first)))
    }

    /** Sorting returns every original record and does not mutate the caller's mutable list. */
    @Test fun mutableInputRemainsUnchanged() {
        val input = mutableListOf(record.copy(id = "b"), record.copy(id = "a"))
        val original = input.toList()
        assertEquals(original.reversed(), sortReservationsByCheckIn(input))
        assertEquals(original, input)
    }
}
