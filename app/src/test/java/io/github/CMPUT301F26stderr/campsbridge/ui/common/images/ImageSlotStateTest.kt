package io.github.CMPUT301F26stderr.campsbridge.ui.common.images

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

/** Pure availability contracts for shared image controls and controller action guards. */
class ImageSlotStateTest {
    /** Known data, unsaved input, pending writes, and unconfirmed publication constrain actions. */
    @Test fun onlyMeaningfulActionsAreAvailable() {
        val attachment =
            ImageAttachment(ImageTarget(ImageTargetType.ACCOUNT, "one"), "blob", Instant.EPOCH)
        val candidate =
            SelectedPhoto(
                "content://selected",
                ImageUpload("image/png") {
                    ByteArrayInputStream(byteArrayOf(1))
                },
            )
        val empty = ImageSlotState(loadState = ImageLoadState.READY)
        val saved = empty.copy(attachment = attachment)
        val selected = saved.copy(candidate = candidate)
        val cases =
            listOf(
                ImageSlotState() to listOf(true, false, false),
                ImageSlotState(loadState = ImageLoadState.FAILED) to listOf(true, false, false),
                empty to listOf(true, false, false),
                saved to listOf(true, false, true),
                empty.copy(candidate = candidate) to listOf(true, true, false),
                selected to listOf(true, true, false),
                selected.copy(loadState = ImageLoadState.FAILED) to listOf(true, false, false),
                selected.copy(loadState = ImageLoadState.LOADING) to listOf(true, false, false),
                saved.copy(loadState = ImageLoadState.FAILED) to listOf(true, false, false),
                saved.copy(loadState = ImageLoadState.LOADING) to listOf(true, false, false),
                empty.copy(unconfirmedBlobId = "unknown") to listOf(false, false, false),
                saved.copy(unconfirmedBlobId = "unknown") to listOf(false, false, false),
                selected.copy(unconfirmedBlobId = "unknown") to listOf(false, false, false),
            ) +
                ImageOperation.entries.filter { it != ImageOperation.IDLE }.flatMap { operation ->
                    listOf(
                        saved.copy(operation = operation),
                        selected.copy(operation = operation),
                    ).map {
                        it to listOf(false, false, false)
                    }
                }
        for ((state, expected) in cases) {
            assertEquals(
                state.toString(),
                expected,
                listOf(state.canChoose, state.canSave, state.canRemove),
            )
        }
    }
}
