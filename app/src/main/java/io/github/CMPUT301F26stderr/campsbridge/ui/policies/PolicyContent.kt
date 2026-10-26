package io.github.CMPUT301F26stderr.campsbridge.ui.policies

import androidx.annotation.StringRes
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.BookingPolicy

/** Packaged readers; only the refund version is recorded in accepted booking terms.
 * @param routeKey Stable navigation value, independent of localized display titles.
 * @param currentVersion Version of the packaged document.
 */
enum class PolicyId(
    val routeKey: String,
    val currentVersion: String,
) {
    GUIDELINES("guidelines", "guidelines-v1"),
    REFUND("refund", BookingPolicy.VERSION),
}

/** Required topics shared between the packaged readers, never copied into account records. */
enum class PolicySectionId {
    CAMPSITE_ETIQUETTE,
    LOTTERY_SELECTION,
    PAYMENTS_AND_REFUNDS,
    SINGLE_USE_WIN,
}

/** Localized policy section identified independently of its display wording.
 * @param id Required topic.
 * @param title Section heading resource.
 * @param body Full approved section text resource.
 */
data class PolicySection(
    val id: PolicySectionId,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/** One exact packaged version, suitable for offline reading without a repository.
 * @param title Page title resource.
 * @param version Exact document identity.
 * @param sections Ordered complete section set.
 */
data class PolicyContent(
    @StringRes val title: Int,
    val version: String,
    val sections: List<PolicySection>,
)

/** Resolves only an available packaged version; unknown history must not show current text.
 * @param id Requested document.
 * @param version Exact version, defaulting only for a current-policy entry point.
 * @return Matching content, or null when that version is not packaged.
 */
fun policyContent(
    id: PolicyId,
    version: String = id.currentVersion,
): PolicyContent? {
    if (version != id.currentVersion) return null
    val paymentsAndRefunds =
        PolicySection(
            PolicySectionId.PAYMENTS_AND_REFUNDS,
            R.string.policy_simulation_title,
            R.string.policy_simulation_body,
        )
    return when (id) {
        PolicyId.GUIDELINES -> {
            PolicyContent(
                R.string.guidelines_title,
                version,
                listOf(
                    PolicySection(
                        PolicySectionId.CAMPSITE_ETIQUETTE,
                        R.string.guidelines_etiquette_title,
                        R.string.guidelines_etiquette_body,
                    ),
                    PolicySection(
                        PolicySectionId.LOTTERY_SELECTION,
                        R.string.guidelines_lottery_title,
                        R.string.guidelines_lottery_body,
                    ),
                    paymentsAndRefunds,
                ),
            )
        }

        PolicyId.REFUND -> {
            PolicyContent(
                R.string.refund_policy_title,
                version,
                listOf(
                    paymentsAndRefunds,
                    PolicySection(
                        PolicySectionId.SINGLE_USE_WIN,
                        R.string.policy_win_title,
                        R.string.policy_win_body,
                    ),
                ),
            )
        }
    }
}
