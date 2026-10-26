package io.github.CMPUT301F26stderr.campsbridge.platform.images

import android.content.ContentResolver
import android.net.Uri
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Resolves selected local content without uploading or eagerly buffering its bytes. */
interface PhotoContentReader {
    /**
     * Supplies a local preview and repeatable lazy upload stream.
     * @param reference Native picker's local content reference.
     * @throws IOException When content is unavailable or image metadata is unusable.
     */
    suspend fun read(reference: String): SelectedPhoto
}

/**
 * Checks metadata/access on IO and retains only an application resolver in the stream factory.
 * @param resolver Application-scoped resolver, never an Activity.
 */
class AndroidPhotoContentReader(
    private val resolver: ContentResolver,
) : PhotoContentReader {
    /**
     * Probes readability without buffering bytes; each upload attempt opens a fresh stream.
     * @param reference Native picker's local content reference.
     * @throws IOException When MIME/access information cannot describe a usable image.
     */
    override suspend fun read(reference: String): SelectedPhoto =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(reference)
            try {
                val type = resolver.getType(uri)
                if (type == null || !type.startsWith("image/") || type.length <= 6) {
                    throw IOException("Selected content has no usable image MIME type")
                }
                val descriptor =
                    resolver.openAssetFileDescriptor(uri, "r")
                        ?: throw IOException("Selected image is unavailable")
                descriptor.use { /* Verify access without reading or retaining its bytes. */ }
                SelectedPhoto(
                    reference,
                    ImageUpload(type) {
                        resolver.openInputStream(uri)
                            ?: throw IOException("Selected image is no longer available")
                    },
                )
            } catch (failure: SecurityException) {
                throw IOException("Selected image cannot be read", failure)
            }
        }
}
