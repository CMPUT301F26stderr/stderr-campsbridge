package io.github.CMPUT301F26stderr.campsbridge.navigation

import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
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
    const val LOTTERY_SETUP = "owner/lottery-setup/new"
    const val LOTTERY_SETUP_SAVED = "owner/lottery-setup/saved/{lotteryId}"
    const val LOTTERY_DETAIL = "owner/lotteries/{lotteryId}/detail"
    const val OWNER_LOTTERIES = "owner/lotteries"
    const val ENTRANT_LOTTERIES = "entrant/lotteries"
    const val ENTRANT_LOTTERY_DETAIL = "entrant/lotteries/{lotteryId}/detail"
    const val CAMPSITE_LOTTERIES = "owner/campsites/{campsiteId}/lotteries"

    /** Addresses the owner's allocation-filtered list from actual campsite management.
     * @param campsiteId Saved listing identity; punctuation is encoded as one argument.
     */
    fun campsiteLotteries(campsiteId: String): String =
        "owner/campsites/${Uri.encode(campsiteId)}/lotteries"

    /** Addresses one new form or an existing draft without copying record state.
     * @param lotteryId Actual saved target, or null for a new form.
     */
    fun lotterySetupGraph(lotteryId: String? = null): String =
        if (lotteryId ==
            null
        ) {
            LOTTERY_SETUP
        } else {
            "owner/lottery-setup/saved/${Uri.encode(lotteryId)}"
        }

    /** Selects one registered saved-reader parent per workspace context.
     * @param context Explicit owner management or own entrant presentation.
     */
    fun lotteryDetailPattern(context: LotteryContext): String =
        when (context) {
            LotteryContext.OWNER -> LOTTERY_DETAIL
            LotteryContext.ENTRANT -> ENTRANT_LOTTERY_DETAIL
        }

    /** Addresses an actual saved overview in its caller's context.
     * @param lotteryId Saved identity returned by persistence or an owner list.
     * @param context Explicit presentation; the repository still owns authorization.
     */
    fun lotteryDetail(
        lotteryId: String,
        context: LotteryContext = LotteryContext.OWNER,
    ): String =
        lotteryDetailPattern(context).replace("{lotteryId}", Uri.encode(lotteryId))

    /** Opens the owner participant child under its actual saved parent.
     * @param lotteryId Saved parent identity.
     */
    fun lotteryParticipants(lotteryId: String): String =
        "${lotteryDetail(lotteryId)}/participants"

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
