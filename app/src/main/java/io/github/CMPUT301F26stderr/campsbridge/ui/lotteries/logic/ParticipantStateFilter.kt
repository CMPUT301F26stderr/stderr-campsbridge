package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry

/** Reserved exact-state selection; the compiling placeholder intentionally fails its contracts.
 * @param entries Supplied display-safe entries in their existing order; do not mutate this input.
 * @param state Exact state to retain, or null to retain all entries.
 * @return Matching original values in their original relative order.
 */
fun filterParticipantsByState(
    entries: List<LotteryEntry>,
    state: EntryState?,
): List<LotteryEntry> =
    emptyList()
