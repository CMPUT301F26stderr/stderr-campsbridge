package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState

/** Route identity qualifies cleanup so outgoing screens cannot retire replacement readers. */
sealed interface AdminInspectionTarget {
    data class Profile(val id: String) : AdminInspectionTarget
    data class Image(val target: ImageTarget) : AdminInspectionTarget
}
sealed interface AdminImageParent {
    data class Profile(val account: Account) : AdminImageParent
    data class Site(val campsite: Campsite) : AdminImageParent
}
sealed interface AdminInspectionUiState {
    data object None : AdminInspectionUiState
    data class Profile(
        val id: String,
        val account: AdminRead<Account> = AdminRead.Loading,
        val campsites: AdminRead<List<Campsite>> = AdminRead.Loading,
        val photo: ImageSlotState = ImageSlotState(),
    ) : AdminInspectionUiState
    data class Image(
        val target: ImageTarget,
        val parent: AdminRead<AdminImageParent> = AdminRead.Loading,
        val photo: ImageSlotState = ImageSlotState(),
    ) : AdminInspectionUiState
}
