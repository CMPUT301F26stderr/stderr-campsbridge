package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Defines the observable contract for the F11 campsite ordering exercise. */
class CampsiteOrderingTest {
    /** Empty input has no listings to order. */
    @Test
    fun returnsNoListingsForEmptyInput() {
        assertEquals(emptyList<Campsite>(), sortCampsitesByName(emptyList()))
    }

    /** A single listing is returned intact. */
    @Test
    fun retainsASingleListing() {
        val listing = campsite("only", "River Camp")

        assertEquals(listOf(listing), sortCampsitesByName(listOf(listing)))
    }

    /** Names sort ascending without depending on their original capitalization. */
    @Test
    fun sortsMixedCaseNamesAscending() {
        val zulu = campsite("zulu", "zULU Camp")
        val alpha = campsite("alpha", "Alpha Camp")
        val bravo = campsite("bravo", "bravo Camp")

        assertEquals(listOf(alpha, bravo, zulu), sortCampsitesByName(listOf(zulu, alpha, bravo)))
    }

    /** Names with the same lowercase spelling use case-sensitive ascending IDs. */
    @Test
    fun usesExactIdForFoldedNameTies() {
        val lowercaseId = campsite("a-1", "Cedar Camp")
        val uppercaseId = campsite("A-1", "CEDAR CAMP")
        val laterId = campsite("z-1", "cedar camp")

        assertEquals(
            listOf(uppercaseId, lowercaseId, laterId),
            sortCampsitesByName(listOf(laterId, lowercaseId, uppercaseId)),
        )
    }

    /** Distinct records with equal names are all retained in ID order. */
    @Test
    fun retainsEveryRecordWithAnEqualName() {
        val first = campsite("site-a", "Pine Ridge")
        val second = campsite("site-b", "Pine Ridge")
        val third = campsite("site-c", "Pine Ridge")

        assertEquals(
            listOf(first, second, third),
            sortCampsitesByName(listOf(third, first, second)),
        )
    }

    /** Outside whitespace is part of the stored name, not a reason to use the ID tie-break. */
    @Test fun comparesNamesWithoutTrimming() {
        val leading = campsite("z-leading", " Pine")
        val plain = campsite("z-plain", "Pine")
        val trailing = campsite("a-trailing", "Pine ")
        assertEquals(
            listOf(leading, plain, trailing),
            sortCampsitesByName(listOf(trailing, plain, leading)),
        )
    }

    /** Repeated records remain repeated; ordering does not deduplicate input. */
    @Test
    fun retainsRepeatedRecords() {
        val first = campsite("site-a", "Pine Ridge")
        val second = campsite("site-b", "Pine Ridge")
        assertEquals(
            listOf(first, first, second),
            sortCampsitesByName(listOf(second, first, first)),
        )
    }

    /** Name folding stays the same when the device default locale is Turkish. */
    @Test
    fun usesRootLocaleInsteadOfTheDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val item = campsite("item", "item")
            val iris = campsite("iris", "Iris")

            assertEquals(listOf(iris, item), sortCampsitesByName(listOf(item, iris)))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    /** Sorting preserves the original Unicode spelling in each listing. */
    @Test
    fun preservesUnicodeNameSpelling() {
        val accented = campsite("accented", "Érable")
        val cafe = campsite("cafe", "Café 東京 🏕️")
        val japanese = campsite("japanese", "東京")
        val result = sortCampsitesByName(listOf(japanese, accented, cafe))
        assertEquals(listOf(cafe, accented, japanese), result)
    }

    /** Sorting returns ordered results without reordering the caller's mutable list. */
    @Test
    fun leavesMutableInputOrderUnchanged() {
        val zulu = campsite("zulu", "Zulu Camp")
        val alpha = campsite("alpha", "Alpha Camp")
        val input = mutableListOf(zulu, alpha)
        val originalOrder = input.toList()

        val result = sortCampsitesByName(input)

        assertEquals(listOf(alpha, zulu), result)
        assertEquals(originalOrder, input)
    }

    /** Builds a realistic listing fixture with a caller-selected name.
     * @param id Stable listing ID used for deterministic tie-break assertions.
     * @param name Display name to place in the fixture's details.
     * @return A sample campsite whose only changed fact is its display name.
     */
    private fun campsite(
        id: String,
        name: String,
    ): Campsite {
        val fixture = sampleCampsite(id = id)
        return fixture.copy(details = fixture.details.copy(name = name))
    }
}
