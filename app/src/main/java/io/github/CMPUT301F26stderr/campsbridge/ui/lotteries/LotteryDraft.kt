/*
 * Defines lottery setup steps, local schedule input and a working campsite-night allocation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryOffer
import java.time.LocalDate
import java.time.LocalTime

/** Ordered setup content; allocation is a child of SITE, never a seventh step. */
enum class LotteryStep { BASICS, SITE, REGISTRATION, BOOKING, WINNERS, REVIEW }

/** Fixed schedule inputs, interpreted in the shared display zone. */
enum class LotterySchedulePoint {
    REGISTRATION_OPEN,
    REGISTRATION_CLOSE,
    BOOKING_OPEN,
    BOOKING_CLOSE,
}

/** Incomplete date/time input remains local and is never guessed from the device zone.
 * @param date Explicit local date, absent until selected.
 * @param time Explicit local time, absent until selected.
 */
data class LotteryLocalDateTime(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
)

/** Local form edits one selected allocation while retaining the owner's other saved offers.
 * @param id Stable saved target, allocated only when saving or supplied for reopening.
 * @param step Current position in the six-step flow.
 * @param title Exact editable title spelling.
 * @param description Optional multiline description.
 * @param campsiteId The single chosen saved campsite identity.
 * @param nights Applied allocation, separate from a child's working selection.
 * @param schedule Four explicit local date/time inputs.
 * @param maximumWinnersText Raw positive winner count; no registration cap exists here.
 * @param returnToReview Whether a valid edit returns directly to the review.
 * @param allocations Complete restored owned offers, including ones not selected in this form.
 */
data class LotteryDraft(
    val id: String? = null,
    val step: LotteryStep = LotteryStep.BASICS,
    val title: String = "",
    val description: String = "",
    val campsiteId: String? = null,
    val nights: Set<LocalDate> = emptySet(),
    val schedule: Map<LotterySchedulePoint, LotteryLocalDateTime> = emptyMap(),
    val maximumWinnersText: String = "",
    val returnToReview: Boolean = false,
    val allocations: List<LotteryOffer> = emptyList(),
)
