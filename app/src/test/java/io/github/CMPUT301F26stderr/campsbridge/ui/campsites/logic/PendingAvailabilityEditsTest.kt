package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Executable F08 contracts; no Firebase, coroutines, clock or UI setup is needed. */
class PendingAvailabilityEditsTest {
    private val first = LocalDate.of(2027, 5, 14)
    private val second = first.plusDays(1)
    private val third = LocalDate.of(2027, 6, 1)
    private val fourth = third.plusDays(1)

    /** No requested dates means no changes, including for an entirely empty baseline. */
    @Test fun emptyInputsHaveNoChanges() {
        assertEquals(
            emptyMap<LocalDate, Boolean>(),
            remainingAvailabilityEdits(emptyMap(), emptyMap()),
        )
    }

    /** Confirmed dates never create work that the caller did not request. */
    @Test fun emptyRequestIgnoresExistingOfferedDates() {
        assertEquals(
            emptyMap<LocalDate, Boolean>(),
            remainingAvailabilityEdits(
                emptyMap(),
                mapOf(first to true),
            ),
        )
    }

    /** A missing known date is not offered, so an explicit offer remains a change. */
    @Test fun offerOfAnAbsentDateRemainsPending() {
        assertEquals(
            mapOf(first to true),
            remainingAvailabilityEdits(mapOf(first to true), emptyMap()),
        )
    }

    /** Withdrawing an already absent date requires no change. */
    @Test fun withdrawalOfAnAbsentDateIsAlreadySatisfied() {
        assertEquals(
            emptyMap<LocalDate, Boolean>(),
            remainingAvailabilityEdits(mapOf(first to false), emptyMap()),
        )
    }

    /** Both true and false equalities are excluded without changing their source maps. */
    @Test fun equalKnownValuesAreExcluded() {
        val values = mapOf(first to true, second to false)
        assertEquals(emptyMap<LocalDate, Boolean>(), remainingAvailabilityEdits(values, values))
    }

    /** An offer and a withdrawal both retain the exact requested values when the baseline differs. */
    @Test fun changedOfferAndWithdrawalRemainPending() {
        val requested = mapOf(first to true, second to false)
        assertEquals(
            requested,
            remainingAvailabilityEdits(
                requested,
                mapOf(
                    first to false,
                    second to true,
                ),
            ),
        )
    }

    /** A mixed set includes only differences and treats each missing key as false. */
    @Test fun mixedKnownAndMissingDatesReturnOnlyDifferences() {
        val requested = mapOf(first to true, second to false, third to true, fourth to false)
        val confirmed = mapOf(first to true, second to true)
        assertEquals(
            mapOf(second to false, third to true),
            remainingAvailabilityEdits(requested, confirmed),
        )
    }

    /** The result never introduces unrelated confirmed entries or alters the requested dates. */
    @Test fun unrelatedConfirmedDatesAreNotReturned() {
        val requested = mapOf(first to true)
        val confirmed = mapOf(second to true, third to false)
        assertEquals(requested, remainingAvailabilityEdits(requested, confirmed))
    }

    /** Mutable inputs remain unchanged; callers may reuse their original editing/baseline maps. */
    @Test fun inputMapsAreNotMutated() {
        val requested = linkedMapOf(first to true, second to false, third to true)
        val confirmed = linkedMapOf(first to true, second to true)
        val expectedRequest = requested.toMap()
        val expectedConfirmed = confirmed.toMap()
        assertEquals(
            mapOf(second to false, third to true),
            remainingAvailabilityEdits(requested, confirmed),
        )
        assertEquals(expectedRequest, requested)
        assertEquals(expectedConfirmed, confirmed)
    }
}
