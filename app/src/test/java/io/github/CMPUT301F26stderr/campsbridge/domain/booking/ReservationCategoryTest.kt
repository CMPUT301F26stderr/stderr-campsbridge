package io.github.CMPUT301F26stderr.campsbridge.domain.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.CampingFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** F17's fixed date-only contract; caller, timezone, source and UI behavior remain lead-owned. */
class ReservationCategoryTest {
    private val record = CampingFixtures.reservation()

    /** A future arrival is upcoming without consulting an application clock. */
    @Test fun beforeArrivalIsUpcoming() {
        assertEquals(
            ReservationCategory.UPCOMING,
            classifyReservation(record, record.checkInDate.minusDays(1)),
        )
    }

    /** The first occupied night starts In progress. */
    @Test fun exactArrivalIsInProgress() {
        assertEquals(
            ReservationCategory.IN_PROGRESS,
            classifyReservation(record, record.checkInDate),
        )
    }

    /** Dates strictly between the endpoints remain occupied. */
    @Test fun betweenArrivalAndCheckoutIsInProgress() {
        assertEquals(
            ReservationCategory.IN_PROGRESS,
            classifyReservation(record, record.checkInDate.plusDays(1)),
        )
    }

    /** Checkout is excluded from occupied nights and begins Past. */
    @Test fun exactCheckoutIsPast() {
        assertEquals(ReservationCategory.PAST, classifyReservation(record, record.checkOutDate))
    }

    /** Later dates remain Past, including a new calendar year. */
    @Test fun afterCheckoutIsPast() {
        assertEquals(
            ReservationCategory.PAST,
            classifyReservation(record, record.checkOutDate.plusYears(1)),
        )
    }

    /** Cancellation takes precedence before arrival and during/after the stay. */
    @Test fun cancelledAlwaysPrecedesDateClassification() {
        val cancelled =
            record.copy(
                cancellation = ReservationCancellation(record.createdAt, "owner", Role.OWNER, null),
            )
        for (today in listOf(
            record.checkInDate.minusDays(1),
            record.checkInDate,
            record.checkOutDate,
            record.checkOutDate.plusDays(1),
        )) {
            assertEquals(ReservationCategory.CANCELLED, classifyReservation(cancelled, today))
        }
    }

    /** Different actors/reasons and nested payment do not replace cancellation precedence. */
    @Test fun everyStoredCancellationRoleRemainsCancelled() {
        for (role in Role.entries) {
            val cancelled =
                record.copy(
                    cancellation =
                        ReservationCancellation(
                            record.createdAt,
                            "actor",
                            role,
                            "Closure",
                        ),
                )
            assertEquals(
                ReservationCategory.CANCELLED,
                classifyReservation(cancelled, record.checkOutDate),
            )
        }
    }

    /** Supplied civil dates alone handle a one-night year boundary. */
    @Test fun oneNightAcrossYearBoundaryUsesTheGivenDate() {
        val stay =
            record.copy(
                checkInDate = LocalDate.parse("2027-12-31"),
                checkOutDate = LocalDate.parse("2028-01-01"),
            )
        assertEquals(ReservationCategory.IN_PROGRESS, classifyReservation(stay, stay.checkInDate))
        assertEquals(ReservationCategory.PAST, classifyReservation(stay, stay.checkOutDate))
    }

    /** Historical timezone labels/payment/refund values cannot reinterpret the supplied day. */
    @Test fun helperDoesNotRecomputeTodayOrInferCancellationFromPayment() {
        val changed =
            record.copy(
                bookedCampsite = record.bookedCampsite.copy(timeZone = "Asia/Tokyo"),
                payment = record.payment.copy(refundedAt = record.createdAt),
            )
        assertEquals(
            ReservationCategory.IN_PROGRESS,
            classifyReservation(changed, record.checkInDate),
        )
    }
}
