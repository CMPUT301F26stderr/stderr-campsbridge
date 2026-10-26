package io.github.CMPUT301F26stderr.campsbridge.ui.components.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent

/**
 * Decodes/caches supplied local or remote references without looking up repository data.
 * @param reference Transient display URL or local preview reference.
 * @param description Localized image description.
 * @param modifier Image size/shape; failure feedback remains outside its clipping boundary.
 * @param compactFailure Uses a compact placeholder instead of a retry row for card images.
 */
@Composable
fun RemoteImage(
    reference: String?,
    description: String,
    modifier: Modifier = Modifier,
    compactFailure: Boolean = false,
) {
    if (reference == null) {
        MissingPhoto(description, modifier)
        return
    }
    val context = LocalContext.current
    val size = rememberConstraintsSizeResolver()
    val request =
        remember(context, reference, size) {
            ImageRequest
                .Builder(context)
                .data(reference)
                .size(size)
                .build()
        }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    Column {
        Box {
            if (compactFailure && state is AsyncImagePainter.State.Error) {
                MissingPhoto(description, modifier)
            } else {
                Image(painter, description, modifier.then(size), contentScale = ContentScale.Crop)
            }
            if (state is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        if (!compactFailure && state is AsyncImagePainter.State.Error) {
            FailureContent(stringResource(R.string.photo_display_failure), painter::restart)
        }
    }
}

/** Neutral photo fallback using Material content and an accessible missing-photo description.
 * @param description Parent-provided image identity.
 * @param modifier Parent-provided geometry.
 */
@Composable
private fun MissingPhoto(
    description: String,
    modifier: Modifier,
) {
    val missing = stringResource(R.string.photo_missing)
    Surface(
        modifier.semantics { contentDescription = "$description. $missing" },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Place, contentDescription = null)
        }
    }
}
