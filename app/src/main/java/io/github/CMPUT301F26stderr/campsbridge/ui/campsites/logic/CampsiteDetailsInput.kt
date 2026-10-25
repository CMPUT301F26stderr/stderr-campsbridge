package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDraft
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PositiveCountInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.PriceInput
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePositiveCount
import io.github.CMPUT301F26stderr.campsbridge.ui.common.input.parsePriceCents
import java.time.ZoneId

/** Complete validated details or user-correctable feedback; no repository receives a raw draft. */
sealed interface CampsiteDetailsInput {
    /** Valid listing facts suitable for explicit draft saving or publication.
     * @param details Domain details with exact price/capacity and selected coordinates.
     */
    data class Value(
        val details: CampsiteDetails,
    ) : CampsiteDetailsInput

    /** Independent field errors shown together without losing raw input.
     * @param nameRequired Whether no nonblank campsite name was supplied.
     * @param locationRequired Whether map coordinates have not been selected.
     * @param timeZoneInvalid Whether the zone is absent or cannot be resolved.
     * @param capacity Existing count-parser feedback, or null for valid capacity.
     * @param price Existing price-parser feedback, or null for a valid amount.
     */
    data class Errors(
        val nameRequired: Boolean = false,
        val locationRequired: Boolean = false,
        val timeZoneInvalid: Boolean = false,
        val capacity: PositiveCountInput.Error? = null,
        val price: PriceInput.Error? = null,
    ) : CampsiteDetailsInput
}

/** Validates local form input without saving or imposing restrictions on optional fields.
 * @param draft Raw owner-supplied listing input.
 * @return Complete domain facts or all affected field errors.
 */
fun validateCampsiteDetails(draft: CampsiteDraft): CampsiteDetailsInput {
    val capacity = parsePositiveCount(draft.maxGuestsText, Long.MAX_VALUE)
    val price = parsePriceCents(draft.priceText)
    val zone = draft.timeZone.trim()
    val validZone = zone in ZoneId.getAvailableZoneIds()
    val errors =
        CampsiteDetailsInput.Errors(
            nameRequired = draft.name.isBlank(),
            locationRequired = draft.location == null,
            timeZoneInvalid = !validZone,
            capacity = capacity as? PositiveCountInput.Error,
            price = price as? PriceInput.Error,
        )
    if (errors != CampsiteDetailsInput.Errors()) return errors
    return CampsiteDetailsInput.Value(
        CampsiteDetails(
            name = draft.name.trim(),
            description = draft.description,
            location = checkNotNull(draft.location),
            locationLabel = draft.locationLabel,
            timeZone = zone,
            maxGuests = (capacity as PositiveCountInput.Value).count,
            amenities = draft.amenities,
            tags = draft.tags,
            nightlyPriceCents = (price as PriceInput.Value).cents,
            rules = draft.rules,
        ),
    )
}
