package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment

/** Independent local searches and independently confirmed source results. */
enum class AdminBrowseTab { PROFILES, CAMPSITES, IMAGES }

data class AdminBrowseUiState(
    val tab: AdminBrowseTab = AdminBrowseTab.PROFILES,
    val queries: Map<AdminBrowseTab, String> = emptyMap(),
    val role: Role? = null,
    val profiles: AdminRead<List<Account>> = AdminRead.Loading,
    val campsites: AdminRead<List<Campsite>> = AdminRead.Loading,
    val images: AdminRead<List<ImageAttachment>> = AdminRead.Loading,
) {
    val query: String get() = queries[tab].orEmpty()
}
