package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.assets.SceneryPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import java.time.Instant

/** Stable ordinary domain values used only by themed content previews. */
internal fun previewCampsite(): Campsite =
    Campsite(
        "preview-campsite",
        "preview-owner",
        CampsiteDetails(
            "River Camp",
            "A quiet riverside campsite.",
            Coordinates(53.0, -118.0),
            "Jasper, Alberta",
            "America/Edmonton",
            8,
            setOf("water"),
            setOf("lakeside"),
            6500,
            "Keep the campsite clean.",
        ),
        Instant.EPOCH,
        Instant.EPOCH,
    )

/** Saved-image fixture using a bundled credited photograph, without repository calls.
 * @param campsiteId Actual fixture identity represented by the attachment.
 */
internal fun previewCampsitePhoto(campsiteId: String = previewCampsite().id): ImageSlotState =
    ImageSlotState(
        loadState = ImageLoadState.READY,
        attachment =
            ImageAttachment(
                ImageTarget(ImageTargetType.CAMPSITE, campsiteId),
                "preview-jasper",
                Instant.EPOCH,
            ),
        displayUrl = SceneryPhoto.JASPER.reference,
    )
