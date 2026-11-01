package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry

/** Selects entries by exact state, preserving their original order without modifying the input.
 * @param entries Supplied display-safe entries in their existing order.
 * @param state Exact state to retain, or null to retain all entries.
 * @return Matching original values in their original relative order.
 */
fun filterParticipantsByState(
    entries: List<LotteryEntry>,
    state: EntryState?,
): List<LotteryEntry> =
    if (state == null) {
        entries
    } else {
        entries.filter { it.state == state }
    }
