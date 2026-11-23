/*
 * Validated shared details or the existing field feedback for raw input.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.Lottery
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.ui.common.AppDisplayTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.common.resolveDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.validateLotteryWindows
import java.time.Instant

/** Validated shared details or the existing field feedback for raw input.
 * @param configuration Complete values, absent when any detail field needs correction.
 * @param errors Per-field feedback, without owner-allocation prerequisites.
 */
internal data class LotteryDetailsInput(
    val configuration: LotteryConfiguration?,
    val errors: LotterySetupErrors,
)

/** Validates the shared fields used by both authoring and the saved organizer editor.
 * @param draft Raw local details.
 * @param validateWindows Completed window validator, or explicit pending authoring dependency.
 * @param savedConfiguration Preserves exact saved instants when their displayed input is untouched.
 */
internal fun lotteryDetailsInput(
    draft: LotteryDraft,
    validateWindows: LotteryWindowValidator? = ::validateLotteryWindows,
    savedConfiguration: LotteryConfiguration? = null,
): LotteryDetailsInput {
    val saved = savedConfiguration?.let(::lotteryScheduleInstants).orEmpty()
    val schedule = lotteryScheduleErrors(draft, LotterySchedulePoint.entries, saved)
    val count = parsePositiveCount(draft.maximumWinnersText, Long.MAX_VALUE)
    val capacity = lotteryCapacityInput(draft)
    val instants =
        if (schedule.isEmpty()) {
            LotterySchedulePoint.entries.map {
                resolveLotterySchedule(draft.schedule.getValue(it), saved[it])!!
            }
        } else {
            emptyList()
        }
    val windows =
        if (instants.size ==
            4
        ) {
            validateWindows?.invoke(instants[0], instants[1], instants[2], instants[3])
        } else {
            null
        }
    val errors =
        LotterySetupErrors(
            titleRequired = draft.title.isBlank(),
            schedule = schedule,
            winnerCount = count as? PositiveCountInput.Error,
            capacity = capacity as? PositiveCountInput.Error,
            windows = windows?.takeUnless { it == LotteryWindowInput.Valid },
            windowsPending = instants.size == 4 && validateWindows == null,
        )
    val valid =
        !errors.titleRequired && schedule.isEmpty() && count is PositiveCountInput.Value &&
            capacity !is PositiveCountInput.Error && windows == LotteryWindowInput.Valid
    val configuration =
        if (valid) {
            LotteryConfiguration(
                instants[0],
                instants[1],
                instants[2],
                instants[3],
                count.count,
                (capacity as? PositiveCountInput.Value)?.count,
            )
        } else {
            null
        }
    return LotteryDetailsInput(configuration, errors)
}

/** Projects saved shared fields into the existing raw input shape in Edmonton time.
 * @param lottery Confirmed saved draft or published record.
 */
internal fun lotteryDetailsDraft(lottery: Lottery): LotteryDraft =
    withLotterySchedule(
        LotteryDraft(
            id = lottery.id,
            title = lottery.title,
            description = lottery.description,
            maximumWinnersText = lottery.configuration.winnerCount.toString(),
            waitlistCapacityText =
                lottery.configuration.waitlistCapacity
                    ?.toString()
                    .orEmpty(),
        ),
        lottery.configuration,
    )

/** Converts stored windows into local input in the shared display zone.
 * @param draft Input whose schedule has not yet been populated.
 * @param configuration Confirmed stored windows and count.
 */
internal fun withLotterySchedule(
    draft: LotteryDraft,
    configuration: LotteryConfiguration,
): LotteryDraft =
    draft.copy(
        schedule =
            lotteryScheduleInstants(configuration).mapValues { (_, instant) ->
                val local = instant.atZone(AppDisplayTime.zone)
                LotteryLocalDateTime(local.toLocalDate(), local.toLocalTime())
            },
    )

/** Keeps named schedule fields attached to their exact persisted timestamps.
 * @param configuration Confirmed saved windows.
 */
private fun lotteryScheduleInstants(configuration: LotteryConfiguration): Map<LotterySchedulePoint, Instant> =
    mapOf(
        LotterySchedulePoint.REGISTRATION_OPEN to configuration.registrationOpensAt,
        LotterySchedulePoint.REGISTRATION_CLOSE to configuration.registrationClosesAt,
        LotterySchedulePoint.BOOKING_OPEN to configuration.bookingOpensAt,
        LotterySchedulePoint.BOOKING_CLOSE to configuration.bookingClosesAt,
    )

/** Parses the optional registration limit through the existing whole-count contract.
 * @param draft Raw form input.
 */
internal fun lotteryCapacityInput(draft: LotteryDraft): PositiveCountInput? =
    if (draft.waitlistCapacityText.isBlank()) {
        null
    } else {
        parsePositiveCount(
            draft.waitlistCapacityText,
            Long.MAX_VALUE,
        )
    }

/** Returns direct missing/DST feedback for the requested fixed schedule fields.
 * @param draft Current raw input.
 * @param points Fields relevant to the current step or full review.
 * @param saved Exact saved timestamps for fields whose displayed spelling is unchanged.
 */
internal fun lotteryScheduleErrors(
    draft: LotteryDraft,
    points: List<LotterySchedulePoint>,
    saved: Map<LotterySchedulePoint, Instant> = emptyMap(),
): Map<LotterySchedulePoint, LotteryScheduleError> =
    buildMap {
        for (point in points) {
            val input = draft.schedule[point] ?: LotteryLocalDateTime()
            val error =
                when {
                    input.date == null -> {
                        LotteryScheduleError.DATE_REQUIRED
                    }

                    input.time == null -> {
                        LotteryScheduleError.TIME_REQUIRED
                    }

                    resolveLotterySchedule(input, saved[point]) == null -> {
                        LotteryScheduleError.AMBIGUOUS_TIME
                    }

                    else -> {
                        null
                    }
                }
            if (error != null) put(point, error)
        }
    }

/** Converts one complete local value through the existing unambiguous-zone boundary.
 * @param input Explicit date/time input.
 * @param saved Exact saved timestamp, retained when its displayed date/time has not changed.
 */
internal fun resolveLotterySchedule(
    input: LotteryLocalDateTime,
    saved: Instant? = null,
): Instant? {
    val date = input.date ?: return null
    val time = input.time ?: return null
    val original = saved?.atZone(AppDisplayTime.zone)
    if (original != null && original.toLocalDate() == date && original.toLocalTime() == time) return saved
    return resolveDateTime(date, time.hour, time.minute, AppDisplayTime.zone)
}
