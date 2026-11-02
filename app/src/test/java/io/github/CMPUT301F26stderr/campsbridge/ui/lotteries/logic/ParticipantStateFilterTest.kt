package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Exact-state selection preserves supplied values and order without owning result derivation. */
class ParticipantStateFilterTest {
    private val waitingA = entry("A", EntryState.WAITING, 30)
    private val waitingB = entry("B", EntryState.WAITING, 10)
    private val withdrawn = entry("C", EntryState.WITHDRAWN, 20)
    private val selected = entry("D", EntryState.OFFERED, 40)
    private val lost = entry("E", EntryState.LOST, 50)
    private val declined = entry("F", EntryState.DECLINED, 60)
    private val entries = listOf(lost, waitingB, declined, waitingA, selected, withdrawn)

    /** Null retains every supplied entry, including historical states, in the supplied order. */
    @Test fun absentStateRetainsAllEntries() {
        assertEquals(entries, filterParticipantsByState(entries, null))
    }

    /** Waiting retains both matches in source order rather than sorting by account or time. */
    @Test fun waitingRetainsEveryExactMatchInSourceOrder() {
        assertEquals(
            listOf(waitingB, waitingA),
            filterParticipantsByState(entries, EntryState.WAITING),
        )
    }

    /** Withdrawn membership remains independently selectable. */
    @Test fun withdrawnSelectsOnlyWithdrawn() {
        assertEquals(listOf(withdrawn), filterParticipantsByState(entries, EntryState.WITHDRAWN))
    }

    /** Offered remains the exact input state; no booking or deadline classification occurs here. */
    @Test fun offeredSelectsOnlyOffered() {
        assertEquals(listOf(selected), filterParticipantsByState(entries, EntryState.OFFERED))
    }

    /** Lost selects its supplied outcome without drawing or consulting a parent. */
    @Test fun lostSelectsOnlyLost() {
        assertEquals(listOf(lost), filterParticipantsByState(entries, EntryState.LOST))
    }

    /** Declined history is distinct from a withdrawn or losing entry. */
    @Test fun declinedSelectsOnlyDeclined() {
        assertEquals(listOf(declined), filterParticipantsByState(entries, EntryState.DECLINED))
    }

    /** An empty source stays empty for All and every exact state. */
    @Test fun emptySourceAlwaysReturnsEmpty() {
        for (state in listOf(null) + EntryState.entries) {
            assertEquals(emptyList<LotteryEntry>(), filterParticipantsByState(emptyList(), state))
        }
    }

    /** No matching entry is a successful empty result. */
    @Test fun unmatchedStateReturnsEmpty() {
        assertEquals(
            emptyList<LotteryEntry>(),
            filterParticipantsByState(listOf(waitingA), EntryState.LOST),
        )
    }

    /** Even mutable inputs retain their contents, order and complete entry facts. */
    @Test fun mutableInputIsNotChangedByEitherSelection() {
        val supplied = entries.toMutableList()
        val original = supplied.toList()
        val selectedRows = filterParticipantsByState(supplied, EntryState.WAITING)
        assertEquals(original, supplied)
        assertEquals(listOf(waitingB, waitingA), selectedRows)
        assertEquals(original, filterParticipantsByState(supplied, null))
        assertEquals(original, supplied)
    }

    /** Supplies distinct fixed facts so rewriting or sorting them changes observable results.
     * @param id Canonical account identity.
     * @param state Exact persisted entry value.
     * @param seconds Distinct joined/state-change time offset.
     */
    private fun entry(
        id: String,
        state: EntryState,
        seconds: Long,
    ): LotteryEntry {
        val joined = Instant.parse("2027-05-01T12:00:00Z").plusSeconds(seconds)
        return LotteryEntry("draw", id, state, joined, joined.plusSeconds(5))
    }
}
