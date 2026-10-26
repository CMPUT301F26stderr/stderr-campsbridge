package io.github.CMPUT301F26stderr.campsbridge.navigation

import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
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

    /** Addresses this workspace's implemented settings menu.
     * @param workspace Owning workspace.
     */
    fun settings(workspace: Role): String =
        "${profileGraph(workspace)}/settings"

    /** Addresses the offline policy menu.
     * @param workspace Owning workspace.
     */
    fun policies(workspace: Role): String =
        "${profileGraph(workspace)}/policies"

    /** Registers policy identity and exact version as small reader arguments.
     * @param workspace Owning workspace.
     */
    fun policyPattern(workspace: Role): String =
        "${policies(workspace)}/{policyKey}?version={version}"

    /** Builds a reader route without carrying text or records in navigation state.
     * @param workspace Owning workspace.
     * @param policy Requested packaged document.
     * @param version Exact version, including historical versions when supplied by booking.
     */
    fun policyReader(
        workspace: Role,
        policy: PolicyId,
        version: String = policy.currentVersion,
    ): String =
        "${policies(workspace)}/${policy.routeKey}?version=${Uri.encode(version)}"
}
