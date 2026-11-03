package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.BookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.StaySelectionInput
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.logic.bookingQuote
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import java.time.LocalDate
import java.time.YearMonth

/** Retains user input and exact approvals independently of reads and reservation commands.
 * @param savedState Primitive values belonging to the wizard's navigation entry.
 */
internal class BookingDraftState(
    private val savedState: SavedStateHandle,
) {
    var value =
        BookingDraft(
            checkIn = restoredDate("bookingCheckIn"),
            checkOut = restoredDate("bookingCheckOut"),
            guestInput = savedState.get<String>("bookingGuests") ?: "1",
            step =
                BookingStep.entries.firstOrNull { it.name == savedState.get<String>("bookingStep") }
                    ?: BookingStep.DATES,
            returnToReview = savedState.get<Boolean>("bookingReviewEdit") ?: false,
            agreement = restoredAgreement(),
            reviewedTotalCents = savedState.get<Long>("bookingReviewedTotal")?.takeIf { it >= 0 },
        )

    // Distinguishes first presentation from returning after a date/price invalidated approval.
    private var hasReviewed =
        savedState.get<Boolean>("bookingHasReviewed")
            ?: (
                value.step == BookingStep.REVIEW || value.returnToReview ||
                    value.reviewedTotalCents != null
            )
    var month =
        savedState.get<String>("bookingMonth")?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        }
    var error: BookingStepError? = null

    /** Validates the requested step using current source-backed facts and existing parsers.
     * @param current Confirmed editable opportunity at this action boundary.
     * @param step Step whose explicit Next was requested.
     */
    fun stepError(
        current: BookingUiState.Content,
        step: BookingStep,
    ): BookingStepError? =
        when (step) {
            BookingStep.DATES -> {
                when {
                    current.stayValidation != null &&
                        current.stayValidation != StaySelectionInput.VALID -> {
                        BookingStepError.Dates(current.stayValidation)
                    }

                    current.eligibilityPending -> {
                        BookingStepError.EligibilityPending
                    }

                    current.stayValidation == null -> {
                        BookingStepError.InventoryUnknown
                    }

                    else -> {
                        null
                    }
                }
            }

            BookingStep.GUESTS -> {
                (
                    parsePositiveCount(
                        value.guestInput,
                        current.campsite.details.maxGuests,
                    ) as? PositiveCountInput.Error
                )?.let { BookingStepError.Guests(it) }
            }

            BookingStep.TERMS -> {
                if (value.agreement ==
                    BookingAgreement(current.campsite.details.rules, BookingPolicy.VERSION)
                ) {
                    null
                } else {
                    BookingStepError.AgreementRequired
                }
            }

            BookingStep.REVIEW -> {
                null
            }
        }

    /** Rechecks all form facts before retaining the amount about to be displayed on review.
     * @param current Current confirmed opportunity, inventory and checked quote.
     */
    fun enterReview(current: BookingUiState.Content) {
        for (step in listOf(BookingStep.DATES, BookingStep.GUESTS, BookingStep.TERMS)) {
            val invalid = stepError(current, step) ?: continue
            value = value.copy(step = step)
            error = invalid
            save()
            return
        }
        val quote = current.quote as? BookingQuote.Value
        if (quote == null) {
            error = BookingStepError.QuoteOverflow
            return
        }
        value =
            value.copy(
                step = BookingStep.REVIEW,
                returnToReview = false,
                reviewedTotalCents =
                    if (hasReviewed) value.reviewedTotalCents else quote.totalCents,
            )
        hasReviewed = true
        save()
    }

    /** Invalidates accepted/reviewed snapshots only against confirmed current site facts.
     * @param site Current site after its source has confirmed it.
     */
    fun reconcileApproval(site: Campsite) {
        val agreement =
            value.agreement?.takeIf {
                it.rules == site.details.rules && it.policyVersion == BookingPolicy.VERSION
            }
        val start = value.checkIn
        val end = value.checkOut
        val quote =
            if (start != null && end != null && end > start) {
                bookingQuote(start, end, site.details.nightlyPriceCents) as? BookingQuote.Value
            } else {
                null
            }
        val reviewed = value.reviewedTotalCents?.takeIf { it == quote?.totalCents }
        if (agreement != value.agreement || reviewed != value.reviewedTotalCents) {
            value = value.copy(agreement = agreement, reviewedTotalCents = reviewed)
            save()
        }
    }

    /** Stores value input and approval primitives independently of source outcomes. */
    fun save() {
        savedState["bookingCheckIn"] = value.checkIn?.toString()
        savedState["bookingCheckOut"] = value.checkOut?.toString()
        savedState["bookingGuests"] = value.guestInput
        savedState["bookingStep"] = value.step.name
        savedState["bookingReviewEdit"] = value.returnToReview
        savedState["bookingRules"] = value.agreement?.rules
        savedState["bookingPolicy"] = value.agreement?.policyVersion
        savedState["bookingReviewedTotal"] = value.reviewedTotalCents
        savedState["bookingHasReviewed"] = hasReviewed
    }

    /** Restores an exact pair without accepting a partial saved agreement. */
    private fun restoredAgreement(): BookingAgreement? {
        val rules = savedState.get<String>("bookingRules") ?: return null
        val policy = savedState.get<String>("bookingPolicy") ?: return null
        return BookingAgreement(rules, policy)
    }

    /** Parses a primitive restoration value safely; malformed input does not reach a query.
     * @param key Saved local-date key.
     */
    private fun restoredDate(key: String): LocalDate? =
        savedState.get<String>(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
