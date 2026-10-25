package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import java.time.LocalDate
import java.time.LocalTime

/** Ordered setup content; allocation is a child of SITE, never a seventh step. */
enum class LotteryStep { BASICS, SITE, REGISTRATION, BOOKING, WINNERS, REVIEW }

/** Fixed schedule inputs, interpreted in the selected campsite's IANA zone. */
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

/** Local single-site form; changes require an explicit later save operation.
 * @param step Current position in the six-step flow.
 * @param title Exact editable title spelling.
 * @param description Optional multiline description.
 * @param campsiteId The single chosen saved campsite identity.
 * @param nights Applied allocation, separate from a child's working selection.
 * @param schedule Four explicit local date/time inputs.
 * @param scheduleZone Last explicitly accepted zone for those local inputs.
 * @param maximumWinnersText Raw positive winner count; no registration cap exists here.
 * @param returnToReview Whether a valid edit returns directly to the review.
 */
data class LotteryDraft(
    val step: LotteryStep = LotteryStep.BASICS,
    val title: String = "",
    val description: String = "",
    val campsiteId: String? = null,
    val nights: Set<LocalDate> = emptySet(),
    val schedule: Map<LotterySchedulePoint, LotteryLocalDateTime> = emptyMap(),
    val scheduleZone: String? = null,
    val maximumWinnersText: String = "",
    val returnToReview: Boolean = false,
)
