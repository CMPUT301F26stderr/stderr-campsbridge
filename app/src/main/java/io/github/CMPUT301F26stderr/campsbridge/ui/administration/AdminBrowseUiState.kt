package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.*
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment

/** Tabs retain independent raw queries under their graph owner. */
enum class AdminBrowseTab { CAMPSITES, PROFILES, IMAGES }

/** Source outcomes remain separate from confirmed empty results and unbound filters. */
sealed interface AdminBrowseContent {
    data object Loading : AdminBrowseContent
    data object Failure : AdminBrowseContent
    data object Pending : AdminBrowseContent
    data class Campsites(val rows: List<Campsite>) : AdminBrowseContent
    data class Profiles(val rows: List<Account>) : AdminBrowseContent
    data class Images(val rows: List<ImageAttachment>) : AdminBrowseContent
}

data class AdminBrowseUiState(
    val tab: AdminBrowseTab = AdminBrowseTab.CAMPSITES,
    val query: String = "",
    val role: Role? = null,
    val content: AdminBrowseContent = AdminBrowseContent.Loading,
)
