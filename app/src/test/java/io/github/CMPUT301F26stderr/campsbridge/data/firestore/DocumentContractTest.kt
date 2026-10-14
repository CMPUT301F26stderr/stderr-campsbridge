package io.github.CMPUT301F26stderr.campsbridge.data.firestore

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.DocumentFixture
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.PersistenceFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Pins the schema independently in both directions, including native types and field ownership.
 *
 * @param T Model type represented by this fixture.
 * @param document Handwritten document and corresponding model.
 */
@RunWith(Parameterized::class)
class DocumentContractTest<T>(
    private val document: DocumentFixture<T>,
) {
    /** The encoder must produce exactly the agreed stored fields, including historical facts. */
    @Test fun writesTheAgreedSchema() {
        assertEquals(document.fields, document.codec.encode(document.value))
    }

    /** Recovers the model and path-owned identity from independent stored fields. */
    @Test fun readsTheAgreedSchema() {
        assertEquals(document.value, document.codec.decode(document.path, document.fields))
    }

    /** Supplies named cases without mixing repository behavior into schema assertions. */
    companion object {
        /** Returns one independently authored document example per parameterized case. */
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun documents(): List<Array<Any>> =
            PersistenceFixtures.documents().map { arrayOf(it) }
    }
}
