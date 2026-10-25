package io.github.CMPUT301F26stderr.campsbridge.navigation

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import java.util.Locale

/** Registered destinations only; add feature routes when their implementations exist. */
object AppDestination {
    const val ONBOARDING = "onboarding"
    const val PROFILE = "profile"

    /** Separates profile graph state by workspace within the current account.
     * @param workspace Granted workspace selected by the root session.
     */
    fun profileGraph(workspace: Role): String =
        "${workspace.name.lowercase(Locale.ROOT)}/$PROFILE"

    /** Addresses the primary profile menu.
     * @param workspace Owning workspace.
     */
    fun profileMenu(workspace: Role): String =
        "${profileGraph(workspace)}/menu"

    /** Addresses the personal child editor.
     * @param workspace Owning workspace.
     */
    fun personalDetails(workspace: Role): String =
        "${profileGraph(workspace)}/personal"

    /** Addresses optional contacts within the same profile owner.
     * @param workspace Owning workspace.
     */
    fun contactDetails(workspace: Role): String =
        "${profileGraph(workspace)}/contact"
}
