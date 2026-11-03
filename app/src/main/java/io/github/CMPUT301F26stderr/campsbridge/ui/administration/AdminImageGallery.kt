package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.domain.image.*
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage

/** Metadata membership belongs to the observed source, independently of thumbnail success. */
@Composable
fun AdminImageGallery(
    rows: List<ImageAttachment>,
    photos: Map<ImageTarget, ImageSlotState>,
    grid: LazyGridState,
    retry: (ImageTarget) -> Unit,
    open: (ImageTarget) -> Unit,
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), state = grid) {
        items(rows, key = { "${it.target.type}:${it.target.id}" }) { image ->
            Column(Modifier.padding(8.dp).clickable { open(image.target) }) {
                AttachmentImage(photos[image.target] ?: ImageSlotState(), image.target.id,
                    { retry(image.target) }, Modifier.fillMaxWidth().height(120.dp))
                Text("${image.target.type} | ${image.target.id}")
                Text(image.blobId)
                Text(image.uploadedAt.toString())
            }
        }
    }
}
