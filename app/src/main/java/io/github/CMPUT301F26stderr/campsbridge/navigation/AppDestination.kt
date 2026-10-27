package io.github.CMPUT301F26stderr.campsbridge.navigation

import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.credits.BundledLicense
import io.github.CMPUT301F26stderr.campsbridge.ui.policies.PolicyId
import java.util.Locale

/** Registered destinations only; add feature routes when their implementations exist. */
object AppDestination {
    const val AVAILABILITY_EDITOR = "owner/campsites/{campsiteId}/availability/edit"
    const val EXPLORE = "entrant/explore"
    const val ONBOARDING = "onboarding"
    const val PROFILE = "profile"
    const val CAMPSITE_SETUP_NEW = "owner/campsite-setup/new"
    const val CAMPSITE_SETUP_SAVED = "owner/campsite-setup/saved/{campsiteId}"

    /** Addresses a distinct local/new or saved-ID setup parent.
     * @param campsiteId Existing unpublished target, or null for a new form.
     */
    fun campsiteSetupGraph(campsiteId: String? = null): String =
        if (campsiteId == null) {
            CAMPSITE_SETUP_NEW
        } else {
            "owner/campsite-setup/saved/${Uri.encode(campsiteId)}"
        }

    /** Addresses the registered owner inventory primary. */
    fun ownerCampsites(): String =
        "owner/campsites"

    /** Registers a context-qualified saved detail parent.
     * @param workspace Owning workspace.
     * @param context Fixed presentation registered by the caller.
     */
    fun campsitePattern(
        workspace: Role,
        context: CampsiteDetailContext,
    ): String =
        "${workspace.name.lowercase(Locale.ROOT)}/campsites/{campsiteId}/detail/${context.routeKey}"

    /** Opens a saved identity without copying a record into navigation state.
     * @param workspace Owning workspace.
     * @param campsiteId Saved listing identity.
     * @param context Fixed presentation permitted by the caller.
     */
    fun campsiteGraph(
        workspace: Role,
        campsiteId: String,
        context: CampsiteDetailContext,
    ): String =
        "${workspace.name.lowercase(
            Locale.ROOT,
        )}/campsites/${Uri.encode(campsiteId)}/detail/${context.routeKey}"

    /** Opens a separate inventory editor without sharing a read-only calendar route.
     * @param campsiteId Actual saved listing identity.
     */
    fun availabilityEditor(campsiteId: String): String =
        "owner/campsites/${Uri.encode(campsiteId)}/availability/edit"

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

    /** Addresses existing-account hosting without restarting onboarding.
     * @param workspace Actual caller's retained profile graph.
     */
    fun ownerIntroduction(workspace: Role): String =
        "${profileGraph(workspace)}/start-hosting"

    /** Addresses this workspace's implemented settings menu.
     * @param workspace Owning workspace.
     */
    fun settings(workspace: Role): String =
        "${profileGraph(workspace)}/settings"

    /** Addresses shared image/font credits and dependency notices.
     * @param workspace Profile graph owning the caller's Back stack.
     */
    fun credits(workspace: Role): String =
        "${settings(workspace)}/credits"

    /** Registers the complete bundled license reader under credits.
     * @param workspace Profile graph owning the caller's Back stack.
     */
    fun creditLicensePattern(workspace: Role): String =
        "${credits(workspace)}/licenses/{license}"

    /** Opens one exact bundled font or Java API license.
     * @param workspace Profile graph owning the caller's Back stack.
     * @param license Fixed packaged document identity.
     */
    fun creditLicense(
        workspace: Role,
        license: BundledLicense,
    ): String =
        "${credits(workspace)}/licenses/${license.routeKey}"

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
