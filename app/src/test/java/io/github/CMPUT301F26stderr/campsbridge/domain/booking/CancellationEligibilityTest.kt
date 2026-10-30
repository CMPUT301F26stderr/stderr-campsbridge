package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public date eligibility; authorization and atomic side effects remain repository-owned. */
class CancellationEligibilityTest {
    private val record = CampingFixtures.reservation()

    /** An upcoming stay can be cancelled without waiting for check-in. */
    @Test fun beforeArrivalPermitsCancellation() {
        assertTrue(canCancelBeforeCheckout(record, record.checkInDate.minusDays(1)))
    }

    /** Check-in and the last occupied night remain before the excluded checkout cutoff. */
    @Test fun occupiedDaysPermitCancellation() {
        assertTrue(canCancelBeforeCheckout(record, record.checkInDate))
        assertTrue(canCancelBeforeCheckout(record, record.checkOutDate.minusDays(1)))
    }

    /** Exact checkout is no longer eligible for a new public cancellation. */
    @Test fun exactCheckoutRejectsCancellation() {
        assertFalse(canCancelBeforeCheckout(record, record.checkOutDate))
    }

    /** Later civil dates remain outside the cancellation window. */
    @Test fun afterCheckoutRejectsCancellation() {
        assertFalse(canCancelBeforeCheckout(record, record.checkOutDate.plusDays(1)))
    }

    /** A saved cancellation never offers another presentation action even before checkout. */
    @Test fun cancelledStayHasNoNewCancellationAction() {
        val cancelled =
            record.copy(
                cancellation = ReservationCancellation(record.createdAt, "owner", Role.OWNER, null),
            )
        assertFalse(canCancelBeforeCheckout(cancelled, record.checkInDate.minusDays(1)))
        assertFalse(canCancelBeforeCheckout(cancelled, record.checkOutDate))
    }

    /** The caller's supplied local date is authoritative; this helper does not convert zones. */
    @Test fun suppliedDayDoesNotDependOnTheStoredZone() {
        val other =
            record.copy(
                bookedCampsite = record.bookedCampsite.copy(timeZone = "Asia/Tokyo"),
            )
        assertTrue(canCancelBeforeCheckout(other, record.checkOutDate.minusDays(1)))
        assertFalse(canCancelBeforeCheckout(other, record.checkOutDate))
    }

    /** Nested payment facts cannot create a cancellation or replace its explicit cutoff. */
    @Test fun paymentDoesNotReplaceTheCancellationAndDateFacts() {
        val other = record.copy(payment = record.payment.copy(refundedAt = record.createdAt))
        assertTrue(canCancelBeforeCheckout(other, record.checkOutDate.minusDays(1)))
        assertFalse(canCancelBeforeCheckout(other, record.checkOutDate))
    }
}
