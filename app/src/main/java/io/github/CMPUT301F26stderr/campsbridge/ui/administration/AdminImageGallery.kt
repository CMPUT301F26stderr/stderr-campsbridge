package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.format.formatDateTime
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.components.images.AttachmentImage
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing
import java.time.ZoneId

/** Metadata membership belongs to the observed source, independently of thumbnail success. */
@Composable
fun AdminImageGallery(
    rows: List<ImageAttachment>,
    photos: Map<ImageTarget, ImageSlotState>,
    grid: LazyGridState,
    retry: (ImageTarget) -> Unit,
    open: (ImageTarget) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), state = grid) {
        items(rows, key = { "${it.target.type}:${it.target.id}" }) { image ->
            Column(Modifier.padding(Spacing.small).clickable { open(image.target) }) {
                AttachmentImage(
                    photos[image.target] ?: ImageSlotState(),
                    image.target.id,
                    { retry(image.target) },
                    Modifier.fillMaxWidth().height(120.dp),
                )
                Text(
                    stringResource(
                        R.string.admin_image_target,
                        stringResource(
                            when (image.target.type) {
                                ImageTargetType.ACCOUNT -> R.string.admin_target_account
                                ImageTargetType.CAMPSITE -> R.string.admin_target_campsite
                            },
                        ),
                        image.target.id,
                    ),
                )
                Text(formatDateTime(image.uploadedAt, ZoneId.systemDefault(), locale))
            }
        }
    }
}
