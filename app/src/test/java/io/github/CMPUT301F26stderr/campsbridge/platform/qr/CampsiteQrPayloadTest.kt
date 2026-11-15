package io.github.CMPUT301F26stderr.campsbridge.platform.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Promotional links preserve saved identities and reject unrelated QR content. */
class CampsiteQrPayloadTest {
    /** Encoding keeps separators, spaces and Unicode inside one path segment. */
    @Test fun encodesTheSavedIdentity() {
        assertEquals(
            "campsbridge://campsite/river%20bend%2F%2B%E9%9B%AA",
            CampsiteQrPayload.encode("river bend/+雪"),
        )
    }

    /** Reading a link restores its identity without treating a plus sign as a space. */
    @Test fun decodesTheSavedIdentity() {
        assertEquals("river-bend", CampsiteQrPayload.decode("campsbridge://campsite/river-bend"))
        assertEquals(
            "river bend/+雪",
            CampsiteQrPayload.decode("campsbridge://campsite/river%20bend%2F%2B%E9%9B%AA"),
        )
    }

    /** Empty identities cannot produce a promotional link. */
    @Test fun rejectsBlankIdentities() {
        for (id in listOf("", " ")) {
            assertThrows(IllegalArgumentException::class.java) { CampsiteQrPayload.encode(id) }
        }
    }

    /** A malformed link or a different destination never becomes a campsite navigation. */
    @Test fun unrelatedOrIncompleteLinksHaveNoIdentity() {
        for (text in listOf(
            "",
            "https://example.com/campsite/river-bend",
            "campsbridge://reservation/river-bend",
            "campsbridge://campsite/",
            "campsbridge://campsite/%20",
            "campsbridge://campsite/a/b",
            "campsbridge://campsite/a?other=b",
            "campsbridge://campsite/a#other",
            "campsbridge://campsite/%zz",
        )) {
            assertNull(text, CampsiteQrPayload.decode(text))
        }
    }
}
