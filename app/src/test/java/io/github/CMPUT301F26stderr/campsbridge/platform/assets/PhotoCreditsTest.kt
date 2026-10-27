package io.github.CMPUT301F26stderr.campsbridge.platform.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URI

/** Verifies the shared credit catalog and the actual Android asset packaging. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoCreditsTest {
    /** Each bundled region has a real JPEG, a named creator and original source/license links. */
    @Test fun packagedPhotosHaveCompleteCreditsAndMatchingFiles() {
        val assets = RuntimeEnvironment.getApplication().assets
        val catalog = photoCredits(assets)
        assertEquals("Unsplash License", catalog.licenseName)
        assertEquals("https://unsplash.com/license", catalog.licenseUrl)
        assertEquals(
            SceneryPhoto.entries.map { it.id }.toSet(),
            catalog.photos.map { it.id }.toSet(),
        )
        for (photo in catalog.photos) {
            assertTrue(photo.title.isNotBlank())
            assertTrue(photo.photographer.isNotBlank())
            assertEquals("unsplash.com", URI(photo.sourceUrl).host)
            assertTrue(URI(photo.sourceUrl).path.startsWith("/photos/"))
            assertEquals("unsplash.com", URI(photo.photographerUrl).host)
            assertTrue(URI(photo.photographerUrl).path.startsWith("/@"))
            assets.open("photos/${photo.id}.jpg").use {
                assertEquals(0xff, it.read())
                assertEquals(0xd8, it.read())
            }
        }
    }
}
