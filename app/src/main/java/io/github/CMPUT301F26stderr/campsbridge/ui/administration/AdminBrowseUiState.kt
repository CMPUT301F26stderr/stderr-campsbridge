/*
 * Represents independent admin tabs, queries and source outcomes without treating failed reads as empty lists.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment

/** Tabs retain independent raw queries under their graph owner. */
enum class AdminBrowseTab { CAMPSITES, PROFILES, IMAGES }

/** Source outcomes remain separate from confirmed empty results and unbound filters. */
sealed interface AdminBrowseContent {
    /** The selected source hasn't answered yet. */
    data object Loading : AdminBrowseContent

    /** The selected source failed and can be retried. */
    data object Failure : AdminBrowseContent

    /** A search or filter was used before its helper was bound. */
    data object Pending : AdminBrowseContent

    /** Campsites left after filtering.
     * @param rows Matching records; empty means no results.
     */
    data class Campsites(
        val rows: List<Campsite>,
    ) : AdminBrowseContent

    /** Profiles left after filtering.
     * @param rows Matching records; empty means no results.
     */
    data class Profiles(
        val rows: List<Account>,
    ) : AdminBrowseContent

    /** Image attachments left after filtering.
     * @param rows Matching records; empty means no results.
     */
    data class Images(
        val rows: List<ImageAttachment>,
    ) : AdminBrowseContent
}

/** Everything the browse screen shows.
 * @param tab Selected record type.
 * @param query Selected tab's raw search text.
 * @param role Selected role on the Profiles tab; null otherwise or for every role.
 * @param content Source outcome for the selected tab.
 */
data class AdminBrowseUiState(
    val tab: AdminBrowseTab = AdminBrowseTab.CAMPSITES,
    val query: String = "",
    val role: Role? = null,
    val content: AdminBrowseContent = AdminBrowseContent.Loading,
)
