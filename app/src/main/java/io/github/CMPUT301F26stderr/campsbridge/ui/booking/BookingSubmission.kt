/*
 * Represents reservation submission and recovery outcomes separately from ordinary source reads.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.PersistenceFailure

/** Independent command state so source failure cannot hide an unresolved reservation attempt. */
sealed interface BookingSubmission {
    /** No reservation command or unresolved attempt is pending. */
    data object Idle : BookingSubmission

    /** One immutable reservation request is awaiting acknowledgement. */
    data object Submitting : BookingSubmission

    /** A read-only lookup is resolving whether the previous attempt consumed this win. */
    data object Recovering : BookingSubmission

    /** Lookup failed; another save is blocked, but the user may retry the check or leave. */
    data object Unknown : BookingSubmission

    /** Known rejection permits correction of the retained draft.
     * @param reason Actual transaction rejection rather than transport ambiguity.
     */
    data class Rejected(
        val reason: PersistenceFailure.Reason,
    ) : BookingSubmission

    /** Server confirmed no consuming reservation; another reserve requires explicit confirmation. */
    data object Absent : BookingSubmission
}
