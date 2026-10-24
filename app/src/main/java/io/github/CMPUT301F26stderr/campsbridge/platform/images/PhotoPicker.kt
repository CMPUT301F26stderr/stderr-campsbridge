package io.github.CMPUT301F26stderr.campsbridge.platform.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Owns a native single-image launch and reports cancellation without modifying a previous choice.
 * @param onResult Local content reference, or null when the picker is cancelled.
 * @return Explicit launch action, invoked only by a user action.
 */
@Composable
fun rememberPhotoPicker(onResult: (String?) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            onResult(uri?.toString())
        }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/** Remembers the platform adapter with an application resolver rather than an Activity reference. */
@Composable
fun rememberPhotoContentReader(): PhotoContentReader {
    val resolver = LocalContext.current.applicationContext.contentResolver
    return remember(resolver) { AndroidPhotoContentReader(resolver) }
}
