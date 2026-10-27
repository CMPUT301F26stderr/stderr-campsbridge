package io.github.CMPUT301F26stderr.campsbridge.ui.discovery

import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import java.time.LocalDate

/** Applied criteria; dates identify occupied nights before an exclusive checkout.
 * @param locationText Applied human-readable location query.
 * @param checkIn Inclusive first night, or null without a date filter.
 * @param checkOut Exclusive checkout, paired with checkIn.
 * @param requestedGuests Optional positive group size.
 */
data class DiscoveryFilters(
    val locationText: String = "",
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val requestedGuests: Long? = null,
)

/** Local edits that do not affect results before Apply.
 * @param locationText Raw location input.
 * @param checkIn Working arrival.
 * @param checkOut Working exclusive checkout.
 * @param guestText Raw optional whole-count input.
 */
data class FilterDraft(
    val locationText: String = "",
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val guestText: String = "",
)

/** Form feedback independent of repository outcomes.
 * @param dates Missing endpoint or nonpositive stay feedback.
 * @param guests Whether the supplied optional count is invalid.
 */
data class FilterErrors(
    val dates: StayFilterError? = null,
    val guests: Boolean = false,
)

/** Stay-field errors; an empty pair is an optional unfiltered stay. */
enum class StayFilterError { MISSING_ENDPOINT, INVALID_ORDER }

/** Parsed applied criteria or user-correctable feedback. */
sealed interface FilterInput {
    /** Valid criteria.
     * @param filters Typed applied values.
     */
    data class Valid(
        val filters: DiscoveryFilters,
    ) : FilterInput

    /** Invalid working fields.
     * @param errors All current field feedback.
     */
    data class Invalid(
        val errors: FilterErrors,
    ) : FilterInput
}

/** Validates working input before changing applied criteria.
 * @param draft Raw editable criteria.
 */
fun validateDiscoveryFilters(draft: FilterDraft): FilterInput {
    val dates =
        when {
            (draft.checkIn == null) != (draft.checkOut == null) -> {
                StayFilterError.MISSING_ENDPOINT
            }

            draft.checkIn != null && draft.checkOut != null && draft.checkOut <= draft.checkIn -> {
                StayFilterError.INVALID_ORDER
            }

            else -> {
                null
            }
        }
    val count =
        if (draft.guestText.isBlank()) {
            null
        } else {
            parsePositiveCount(
                draft.guestText,
                Long.MAX_VALUE,
            )
        }
    val invalidGuests = count != null && count !is PositiveCountInput.Value
    if (dates != null ||
        invalidGuests
    ) {
        return FilterInput.Invalid(FilterErrors(dates, invalidGuests))
    }
    return FilterInput.Valid(
        DiscoveryFilters(
            draft.locationText.trim(),
            draft.checkIn,
            draft.checkOut,
            (count as? PositiveCountInput.Value)?.count,
        ),
    )
}
