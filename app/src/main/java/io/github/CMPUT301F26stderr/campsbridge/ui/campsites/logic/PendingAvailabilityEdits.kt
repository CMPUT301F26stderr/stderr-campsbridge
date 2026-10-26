package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import java.time.LocalDate

/** Keeps requested offered values that differ from a successfully known baseline.
 * The caller owns unknown ranges, write acknowledgements and retries; this function has no IO.
 * @param requested Desired offered values for edited dates.
 * @param confirmed Known offered values; an absent key means false.
 * @return Only differing requested entries, preserving dates and requested Boolean values.
 */
fun remainingAvailabilityEdits(
    requested: Map<LocalDate, Boolean>,
    confirmed: Map<LocalDate, Boolean>,
): Map<LocalDate, Boolean> =
    throw NotImplementedError("F08: implement the pure comparison")
