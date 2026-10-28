package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic

import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** F09 contracts for supported fields, exact substring behavior and locale independence. */
class CampsiteTextMatchTest {
    private val campsite = sampleCampsite(id = "private-id", ownerId = "private-owner")

    /** Empty and whitespace-only queries leave every listing eligible. */
    @Test fun blankQueryMatches() {
        for (query in listOf("", " ", "\t\n", "\u2003")) {
            assertTrue(matchesCampsiteText(campsite, query))
        }
    }

    /** Name matching ignores case without requiring the whole name. */
    @Test fun nameIsSearchable() {
        assertTrue(matchesCampsiteText(campsite, "RIVER"))
        val nameOnly =
            campsite.copy(
                details = campsite.details.copy(locationLabel = "", description = ""),
            )
        assertTrue(matchesCampsiteText(nameOnly, "river"))
    }

    /** Location labels are searched even when the name and description do not match. */
    @Test fun locationLabelIsSearchable() {
        assertTrue(matchesCampsiteText(campsite, "ALBERTA"))
    }

    /** Descriptions are searched independently of names and location labels. */
    @Test fun descriptionIsSearchable() {
        assertTrue(matchesCampsiteText(campsite, "QUIET"))
    }

    /** Only outside whitespace is removed; a substring need not start on a word boundary. */
    @Test fun trimsOutsideWhitespaceAndRetainsLiteralInternalSpacing() {
        assertTrue(matchesCampsiteText(campsite, "  iver  "))
        assertFalse(matchesCampsiteText(campsite, "River  Camp"))
        assertFalse(matchesCampsiteText(campsite, "riv camp"))
        assertFalse(matchesCampsiteText(campsite, "Camp Jasper"))
    }

    /** IDs, ownership, timezone, rules, amenities and tags are outside this predicate's fields. */
    @Test fun unsupportedFieldsAndUnmatchedTextDoNotMatch() {
        val site =
            campsite.copy(
                details =
                    campsite.details.copy(
                        rules = "private-rule",
                        amenities = setOf("private-amenity"),
                        tags = setOf("private-tag"),
                    ),
            )
        for (query in listOf(
            "private-id",
            "private-owner",
            "private-rule",
            "private-amenity",
            "private-tag",
            "America/Edmonton",
            "Banff",
        )) {
            assertFalse(matchesCampsiteText(site, query))
        }
    }

    /** Device locale cannot change ROOT case-folded results, including capital I. */
    @Test fun usesRootLocaleRatherThanTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val site = campsite.copy(details = campsite.details.copy(name = "PINE"))
            assertTrue(matchesCampsiteText(site, "pine"))
            assertTrue(matchesCampsiteText(site, " I "))
            assertFalse(matchesCampsiteText(site, "ı"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** Non-ASCII spelling remains searchable without transliteration or a fuzzy matcher. */
    @Test fun supportsUnicodeSubstrings() {
        val site = campsite.copy(details = campsite.details.copy(name = "Café 東京 🏕️"))
        assertTrue(matchesCampsiteText(site, "CAFÉ"))
        assertTrue(matchesCampsiteText(site, "東京"))
        assertTrue(matchesCampsiteText(site, "🏕️"))
        assertFalse(matchesCampsiteText(site, "cafe"))
    }

    /** Searching does not rewrite the supplied facts or mutable option collections. */
    @Test fun leavesInputUnchanged() {
        val site =
            campsite.copy(
                details =
                    campsite.details.copy(
                        name = " River Camp ",
                        amenities = mutableSetOf("wifi"),
                        tags = mutableSetOf("forest"),
                    ),
            )
        val before =
            site.copy(
                details =
                    site.details.copy(
                        amenities = site.details.amenities.toSet(),
                        tags = site.details.tags.toSet(),
                    ),
            )
        val query = " RIVER "
        assertTrue(matchesCampsiteText(site, query))
        assertEquals(before, site)
        assertEquals(" RIVER ", query)
    }
}
