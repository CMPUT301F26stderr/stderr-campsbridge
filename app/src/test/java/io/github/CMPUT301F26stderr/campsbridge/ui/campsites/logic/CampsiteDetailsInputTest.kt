package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PriceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-owned form validation; reserved profile/count/availability exercises are not reimplemented. */
class CampsiteDetailsInputTest {
    /** Incomplete form input reports independent errors instead of constructing invalid details. */
    @Test fun emptyDraftReportsEveryRequiredField() {
        assertEquals(
            CampsiteDetailsInput.Errors(
                nameRequired = true,
                locationRequired = true,
                timeZoneInvalid = true,
                capacity = PositiveCountInput.Error.REQUIRED,
                price = PriceInput.Error.REQUIRED,
            ),
            validateCampsiteDetails(CampsiteDraft()),
        )
    }

    /** Blank optional fields and a free listing are valid; only the name is normalized. */
    @Test fun validDraftBuildsExactDomainDetails() {
        val draft =
            validDraft().copy(
                name = "  River Camp  ",
                locationLabel = "",
                timeZone = " America/Edmonton ",
                maxGuestsText = " 8 ",
                priceText = "0.00",
            )
        val details = validatedDetails(draft)
        assertEquals("River Camp", details.name)
        assertEquals(Coordinates(53.0, -118.0), details.location)
        assertEquals("", details.locationLabel)
        assertEquals("America/Edmonton", details.timeZone)
        assertEquals(8L, details.maxGuests)
        assertEquals(0L, details.nightlyPriceCents)
        assertEquals("", details.description)
        assertEquals("", details.rules)
        assertEquals(draft.amenities, details.amenities)
        assertEquals(draft.tags, details.tags)
    }

    /** Exact cents and Unicode/multiline fields survive without caps or formatting policies. */
    @Test fun preservesOptionalTextAndUnlistedOptions() {
        val draft =
            validDraft().copy(
                name = "李's " + "A".repeat(300),
                description = " First line\nSecond line  ",
                rules = "Quiet after 10.\n  Keep fires small. ",
                locationLabel = " Jasper area ",
                priceText = " 12.34 ",
                amenities = setOf("water", "unlisted-amenity"),
                tags = setOf("unlisted-tag"),
            )
        val details = validatedDetails(draft)
        assertEquals(1234L, details.nightlyPriceCents)
        assertEquals(draft.name, details.name)
        assertEquals(draft.description, details.description)
        assertEquals(draft.rules, details.rules)
        assertEquals(draft.locationLabel, details.locationLabel)
        assertEquals(draft.amenities, details.amenities)
        assertEquals(draft.tags, details.tags)
    }

    /** Location/zone mistakes stay field feedback; unrelated valid values are not marked invalid. */
    @Test fun missingCoordinateAndUnknownZoneAreExplicit() {
        assertEquals(
            CampsiteDetailsInput.Errors(locationRequired = true, timeZoneInvalid = true),
            validateCampsiteDetails(validDraft().copy(location = null, timeZone = "Mars/Base")),
        )
        assertEquals(
            CampsiteDetailsInput.Errors(nameRequired = true),
            validateCampsiteDetails(validDraft().copy(name = " \t\n ")),
        )
    }

    /** Registered IANA names/aliases are accepted; raw offsets cannot substitute for a zone. */
    @Test fun timeZonesUseRegisteredIanaNames() {
        for (zone in listOf("America/Edmonton", "UTC", "Canada/Mountain", "Etc/GMT+5")) {
            assertEquals(zone, validatedDetails(validDraft().copy(timeZone = zone)).timeZone)
        }
        for (zone in listOf("+05:30", "GMT+5", "UTC+1", "")) {
            assertEquals(
                CampsiteDetailsInput.Errors(timeZoneInvalid = true),
                validateCampsiteDetails(validDraft().copy(timeZone = zone)),
            )
        }
    }

    /** Existing parsers own their boundary/error wording, including overflow and fractions. */
    @Test fun capacityAndPriceKeepExistingParserOutcomes() {
        val cases =
            listOf(
                Triple(
                    "0",
                    "-1",
                    CampsiteDetailsInput.Errors(
                        capacity = PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                        price = PriceInput.Error.INVALID_FORMAT,
                    ),
                ),
                Triple(
                    "1.5",
                    "1.234",
                    CampsiteDetailsInput.Errors(
                        capacity = PositiveCountInput.Error.INVALID_WHOLE_NUMBER,
                        price = PriceInput.Error.INVALID_FORMAT,
                    ),
                ),
                Triple(
                    "9".repeat(30),
                    "9".repeat(30),
                    CampsiteDetailsInput.Errors(
                        capacity = PositiveCountInput.Error.EXCEEDS_MAXIMUM,
                        price = PriceInput.Error.TOO_LARGE,
                    ),
                ),
            )
        for ((capacity, price, errors) in cases) {
            assertEquals(
                errors,
                validateCampsiteDetails(
                    validDraft().copy(
                        maxGuestsText = capacity,
                        priceText = price,
                    ),
                ),
            )
        }
        assertTrue(
            validateCampsiteDetails(
                validDraft().copy(
                    maxGuestsText = Long.MAX_VALUE.toString(),
                    priceText = "92233720368547758.07",
                ),
            ) is CampsiteDetailsInput.Value,
        )
    }

    /** Provides only the facts required for a valid listing, with optional fields left empty. */
    private fun validDraft(): CampsiteDraft =
        CampsiteDraft(
            name = "River Camp",
            location = Coordinates(53.0, -118.0),
            timeZone = "America/Edmonton",
            maxGuestsText = "8",
            priceText = "65.00",
        )

    /** Reports a useful assertion when otherwise-valid input unexpectedly produces field errors.
     * @param draft Expected-valid input supplied by the current case.
     */
    private fun validatedDetails(draft: CampsiteDraft): CampsiteDetails {
        val result = validateCampsiteDetails(draft)
        assertTrue(
            "Expected complete details, received $result",
            result is CampsiteDetailsInput.Value,
        )
        return (result as CampsiteDetailsInput.Value).details
    }
}
