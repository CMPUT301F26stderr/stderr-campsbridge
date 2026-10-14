package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Owns key safety and deterministic identities; forwarding path methods need no mirrored tests. */
class PersistenceKeysTest {
    /** Compound identities distinguish component boundaries even with delimiters in IDs. */
    @Test fun compoundIdsDoNotCollide() {
        assertNotEquals(DocumentKeys.reservation("a_b", "c"), DocumentKeys.reservation("a", "b_c"))
        assertNotEquals(DocumentKeys.reservation("1:a", "b"), DocumentKeys.reservation("1", "a:b"))
    }

    /** Firestore IDs reject reserved names, paths and oversized UTF-8 values. */
    @Test fun validatesDocumentIdsInUtf8Bytes() {
        assertEquals("é".repeat(750), DocumentKeys.id("é".repeat(750)))
        for (id in listOf("", " ", ".", "..", "parent/child", "__reserved__", "é".repeat(751))) {
            assertThrows(IllegalArgumentException::class.java) { DocumentKeys.id(id) }
        }
    }

    /** Image slots retain the complete target ID while blob paths remain derived. */
    @Test fun attachmentsRetainTargetIdentity() {
        for (type in ImageTargetType.entries) {
            val target = ImageTarget(type, "user_with_underscores")
            assertEquals(target, DocumentKeys.target(DocumentKeys.attachment(target)))
        }
        assertThrows(IllegalArgumentException::class.java) { DocumentKeys.target("unknown_site") }
        assertEquals("images/blob/original", DocumentKeys.blob("blob"))
    }
}
