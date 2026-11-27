/*
 * Projects saved listing facts into editable spelling while retaining exact cents.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.math.BigDecimal

/** Projects saved listing facts into editable spelling while retaining exact cents.
 * @param site Saved draft or published listing.
 */
internal fun campsiteDraft(site: Campsite): CampsiteDraft =
    with(site.details) {
        CampsiteDraft(
            id = site.id,
            name = name,
            description = description,
            location = location,
            locationLabel = locationLabel,
            timeZone = timeZone,
            maxGuestsText = maxGuests.toString(),
            priceText = BigDecimal.valueOf(nightlyPriceCents, 2).toPlainString(),
            amenities = amenities,
            tags = tags,
            rules = rules,
        )
    }

/** Retains raw details as primitives without copying a saved record or photo bytes.
 * @param state This graph's saved state.
 * @param prefix Keys owned by the calling editor.
 * @param draft Current raw fields.
 */
internal fun retainCampsiteDetails(
    state: SavedStateHandle,
    prefix: String,
    draft: CampsiteDraft,
) {
    state["${prefix}name"] = draft.name
    state["${prefix}description"] = draft.description
    state["${prefix}latitude"] = draft.location?.latitude
    state["${prefix}longitude"] = draft.location?.longitude
    state["${prefix}label"] = draft.locationLabel
    state["${prefix}zone"] = draft.timeZone
    state["${prefix}capacity"] = draft.maxGuestsText
    state["${prefix}price"] = draft.priceText
    state["${prefix}amenities"] = ArrayList(draft.amenities)
    state["${prefix}tags"] = ArrayList(draft.tags)
    state["${prefix}rules"] = draft.rules
}

/** Restores raw fields over this target's confirmed identity and editor-specific metadata.
 * @param state This graph's saved state.
 * @param prefix Keys owned by the calling editor.
 * @param initial Confirmed fields and identity used when no input was retained.
 */
internal fun restoreCampsiteDetails(
    state: SavedStateHandle,
    prefix: String,
    initial: CampsiteDraft,
): CampsiteDraft {
    if (state.get<Boolean>("${prefix}present") != true) return initial
    val latitude = state.get<Double>("${prefix}latitude")
    val longitude = state.get<Double>("${prefix}longitude")
    return initial.copy(
        name = state.get<String>("${prefix}name").orEmpty(),
        description = state.get<String>("${prefix}description").orEmpty(),
        location = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null,
        locationLabel = state.get<String>("${prefix}label").orEmpty(),
        timeZone = state.get<String>("${prefix}zone").orEmpty(),
        maxGuestsText = state.get<String>("${prefix}capacity").orEmpty(),
        priceText = state.get<String>("${prefix}price").orEmpty(),
        amenities = state.get<ArrayList<String>>("${prefix}amenities").orEmpty().toSet(),
        tags = state.get<ArrayList<String>>("${prefix}tags").orEmpty().toSet(),
        rules = state.get<String>("${prefix}rules").orEmpty(),
    )
}
