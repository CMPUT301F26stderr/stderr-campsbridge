package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput

/** Real B1 parser ports; each handoff connects only its own functions.
 * @param parseName F01 display-name implementation.
 * @param parseEmail F02 email implementation.
 * @param parsePhone F02 phone implementation.
 * @param remainingEdits F08 comparison of requested and confirmed offered values.
 * @param orderCampsites F11 stable campsite ordering.
 */
data class TaskExampleBindings(
    val parseName: ((String) -> DisplayNameInput)? = null,
    val parseEmail: ((String) -> OptionalContactInput)? = null,
    val parsePhone: ((String) -> OptionalContactInput)? = null,
    val remainingEdits: AvailabilityDifference? = null,
    val orderCampsites: ((List<Campsite>) -> List<Campsite>)? = null,
) {
    /** Reports whether the selected task's actual functions are supplied.
     * @param example Selected task identity.
     */
    fun isConnected(example: TaskExample): Boolean =
        when (example) {
            TaskExample.DISPLAY_NAME -> parseName != null
            TaskExample.CONTACT -> parseEmail != null && parsePhone != null
            TaskExample.AVAILABILITY -> remainingEdits != null
            TaskExample.CAMPSITE_ORDER -> orderCampsites != null
            else -> false
        }
}

/** Connects reviewed task functions in isolated handoffs; shared defaults remain pending. */
fun taskExampleBindings(): TaskExampleBindings =
    TaskExampleBindings()
