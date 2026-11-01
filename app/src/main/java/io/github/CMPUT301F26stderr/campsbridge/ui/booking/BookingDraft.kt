package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import java.time.LocalDate

/** Actual single-site booking steps; a policy reader is a child, not an extra step. */
enum class BookingStep { DATES, GUESTS, TERMS, REVIEW }

/** Exact displayed terms acknowledged before submitting; acceptedAt remains transaction-owned.
 * @param rules Displayed current campsite rules, including intentional empty text.
 * @param policyVersion Exact displayed packaged cancellation policy version.
 */
data class BookingAgreement(
    val rules: String,
    val policyVersion: String,
)

/** Unsubmitted input; incomplete dates and raw guest text stay separate from domain requests.
 * @param checkIn Inclusive local arrival, or null before selection.
 * @param checkOut Exclusive local departure, or null before selection.
 * @param guestInput Raw positive-count input retained through read failure and recreation.
 * @param step Current wizard page.
 * @param returnToReview Whether Next returns from an explicit review edit.
 * @param agreement Exact terms acknowledged, or null before acceptance/after invalidation.
 * @param reviewedTotalCents Current displayed amount reviewed, or null after price/date changes.
 */
data class BookingDraft(
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val guestInput: String = "1",
    val step: BookingStep = BookingStep.DATES,
    val returnToReview: Boolean = false,
    val agreement: BookingAgreement? = null,
    val reviewedTotalCents: Long? = null,
)
