package io.github.CMPUT301F26stderr.campsbridge.platform.images

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

/** Tests the real Android adapter around a controlled ContentResolver boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoContentReaderTest {
    /** Selection checks access but opens fresh bytes only when each upload attempt requests them. */
    @Test fun validSelectionIsLazyAndStreamsAreRepeatable() =
        runTest {
            val resolver = mock(ContentResolver::class.java)
            val descriptor = mock(AssetFileDescriptor::class.java)
            val uri = Uri.parse("content://fixtures/photo.png")
            `when`(resolver.getType(uri)).thenReturn("image/png")
            `when`(resolver.openAssetFileDescriptor(uri, "r")).thenReturn(descriptor)
            `when`(resolver.openInputStream(uri)).thenReturn(
                ByteArrayInputStream(byteArrayOf(1, 2)),
                ByteArrayInputStream(byteArrayOf(3, 4)),
            )
            val selected = AndroidPhotoContentReader(resolver).read(uri.toString())
            assertEquals(uri.toString(), selected.previewReference)
            assertEquals("image/png", selected.upload.contentType)
            verify(descriptor).close()
            verify(resolver, never()).openInputStream(uri)
            assertArrayEquals(
                byteArrayOf(1, 2),
                selected.upload.openStream().use { it.readBytes() },
            )
            assertArrayEquals(
                byteArrayOf(3, 4),
                selected.upload.openStream().use { it.readBytes() },
            )
            verify(resolver, times(2)).openInputStream(uri)
        }

    /** Missing, non-image, and incomplete MIME metadata fail before any upload stream opens. */
    @Test fun unusableMetadataIsASelectionFailure() =
        runTest {
            for (mime in listOf(null, "text/plain", "image/")) {
                val resolver = mock(ContentResolver::class.java)
                val uri = Uri.parse("content://fixtures/photo")
                `when`(resolver.getType(uri)).thenReturn(mime)
                `when`(resolver.openAssetFileDescriptor(uri, "r"))
                    .thenReturn(mock(AssetFileDescriptor::class.java))
                try {
                    AndroidPhotoContentReader(resolver).read(uri.toString())
                    fail("Expected invalid image metadata to fail")
                } catch (expected: IOException) {
                    assertNotNull(expected)
                }
                verify(resolver, never()).openInputStream(uri)
            }
        }

    /** Missing or denied content is recoverable and does not defer a known access failure to Save. */
    @Test fun unreadableContentIsASelectionFailure() =
        runTest {
            for (denied in listOf(false, true)) {
                val resolver = mock(ContentResolver::class.java)
                val uri = Uri.parse("content://fixtures/unavailable.png")
                `when`(resolver.getType(uri)).thenReturn("image/png")
                if (denied) {
                    `when`(
                        resolver.openAssetFileDescriptor(uri, "r"),
                    ).thenThrow(SecurityException("denied"))
                }
                try {
                    AndroidPhotoContentReader(resolver).read(uri.toString())
                    fail("Expected unavailable image to fail")
                } catch (expected: IOException) {
                    assertNotNull(expected)
                }
                verify(resolver, never()).openInputStream(uri)
            }
        }
}
