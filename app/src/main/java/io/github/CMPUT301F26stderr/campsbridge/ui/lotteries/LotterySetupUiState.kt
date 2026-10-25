package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryConfiguration
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteInventoryUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowInput
import java.time.LocalDate
import java.time.YearMonth

/** Current owner-scoped published campsite choices, independent of local form input. */
sealed interface LotteryCampsiteChoices {
    /** No confirmed choices yet. */
    data object Loading : LotteryCampsiteChoices

    /** Choice observation failed; entered form input stays available. */
    data object Failure : LotteryCampsiteChoices

    /** Confirmed eligible choices; an empty list means there are no eligible sites.
     * @param campsites Actual active published records owned by the current account.
     */
    data class Content(
        val campsites: List<Campsite>,
    ) : LotteryCampsiteChoices
}

/** User-correctable local date/time feedback without implementing the reserved window order. */
enum class LotteryScheduleError { DATE_REQUIRED, TIME_REQUIRED, AMBIGUOUS_TIME }

/** Explicit field/dependency feedback from Next or review validation.
 * @param titleRequired Title is blank.
 * @param campsiteRequired Current selected identity is not an eligible observed choice.
 * @param allocationRequired At least one allocated night is required.
 * @param schedule Incomplete or DST-invalid local inputs, keyed by their actual field.
 * @param winnerCount Existing positive-count parser feedback.
 * @param windows Actual F13 failure, absent before successful conversion or when pending.
 * @param windowsPending No F13 dependency was supplied after all four values converted.
 * @param zoneConfirmationRequired Existing local times have not been accepted in this site's zone.
 */
data class LotterySetupErrors(
    val titleRequired: Boolean = false,
    val campsiteRequired: Boolean = false,
    val allocationRequired: Boolean = false,
    val schedule: Map<LotterySchedulePoint, LotteryScheduleError> = emptyMap(),
    val winnerCount: PositiveCountInput.Error? = null,
    val windows: LotteryWindowInput? = null,
    val windowsPending: Boolean = false,
    val zoneConfirmationRequired: Boolean = false,
)

/** Working allocation and one required range covering the month plus all selected nights.
 * @param month Visible month initialized from the campsite zone and injected clock.
 * @param selected Working nights; Cancel never copies these into the parent draft.
 * @param inventory Current required range outcome; unknown is never confirmed empty inventory.
 * @param invalidSelection Apply found empty, unavailable or no-longer-free selected nights.
 */
data class LotteryAllocationUiState(
    val month: YearMonth,
    val selected: Set<LocalDate>,
    val inventory: CampsiteInventoryUiState = CampsiteInventoryUiState.Loading,
    val invalidSelection: Boolean = false,
) {
    /** Confirmed nonempty selection; failed/unknown inventory cannot enable the Apply action. */
    val canApply: Boolean
        get() {
            val known = inventory as? CampsiteInventoryUiState.Content ?: return false
            val eligible =
                known.nights
                    .filter { it.isFree }
                    .map { it.date }
                    .toSet()
            return selected.isNotEmpty() && eligible.containsAll(selected)
        }
}

/** One local setup owner; incomplete inputs never create a persisted lottery.
 * @param draft Raw local form input.
 * @param choices Independent current published/owned source outcome.
 * @param allocation Working child state when allocation is open.
 * @param errors Explicit validation feedback.
 * @param configuration Validated no-cap configuration, absent until every requirement is satisfied.
 * @param discardPrompt Whether an explicit exit needs abandonment confirmation.
 */
data class LotterySetupUiState(
    val draft: LotteryDraft = LotteryDraft(),
    val choices: LotteryCampsiteChoices = LotteryCampsiteChoices.Loading,
    val allocation: LotteryAllocationUiState? = null,
    val errors: LotterySetupErrors = LotterySetupErrors(),
    val configuration: LotteryConfiguration? = null,
    val discardPrompt: Boolean = false,
)
