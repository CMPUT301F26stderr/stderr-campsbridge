/*
 * Represents selected profile and image reads, including independent section failures and retry targets.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState

/** Route identity qualifies cleanup so outgoing screens cannot retire replacement readers. */
sealed interface AdminInspectionTarget {
    /** A selected profile.
     * @param id Account ID.
     */
    data class Profile(
        val id: String,
    ) : AdminInspectionTarget

    /** A selected image slot.
     * @param target Account or campsite slot.
     */
    data class Image(
        val target: ImageTarget,
    ) : AdminInspectionTarget
}

/** The record that owns an inspected image. */
sealed interface AdminImageParent {
    /** The image belongs to an account.
     * @param account Owning account.
     */
    data class Profile(
        val account: Account,
    ) : AdminImageParent

    /** The image belongs to a campsite.
     * @param campsite Owning campsite.
     */
    data class Site(
        val campsite: Campsite,
    ) : AdminImageParent
}

/** Current inspection, with each section loading and failing on its own. */
sealed interface AdminInspectionUiState {
    /** Nothing is selected. */
    data object None : AdminInspectionUiState

    /** A selected profile.
     * @param id Account ID.
     * @param account Account read.
     * @param campsites Campsites owned by this account.
     * @param photo Profile photo.
     */
    data class Profile(
        val id: String,
        val account: AdminRead<Account> = AdminRead.Loading,
        val campsites: AdminRead<List<Campsite>> = AdminRead.Loading,
        val photo: ImageSlotState = ImageSlotState(),
    ) : AdminInspectionUiState

    /** A selected image slot.
     * @param target Account or campsite slot.
     * @param parent Record that owns the slot.
     * @param photo Image in the slot.
     */
    data class Image(
        val target: ImageTarget,
        val parent: AdminRead<AdminImageParent> = AdminRead.Loading,
        val photo: ImageSlotState = ImageSlotState(),
    ) : AdminInspectionUiState
}
