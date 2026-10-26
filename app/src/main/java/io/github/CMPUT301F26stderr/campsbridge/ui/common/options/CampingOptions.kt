package io.github.CMPUT301F26stderr.campsbridge.ui.common.options

import androidx.annotation.StringRes
import io.github.CMPUT301F26stderr.campsbridge.R

/** A stable campsite option and the resource used to display its label.
 * @param id Persisted option identifier.
 * @param label Localized label resource for this option.
 */
data class CampingOption(
    val id: String,
    @param:StringRes val label: Int,
)

/** Shared tag and amenity choices used by campsite and profile controls. */
object CampingOptions {
    /** Camper interest tags, retaining the IDs already used by profile preferences. */
    val tags: List<CampingOption> =
        listOf(
            CampingOption("tent", R.string.onboarding_tent),
            CampingOption("national-parks", R.string.onboarding_national_parks),
            CampingOption("backcountry", R.string.onboarding_backcountry),
            CampingOption("rv", R.string.onboarding_rv),
            CampingOption("lakeside", R.string.onboarding_lakeside),
            CampingOption("family", R.string.onboarding_family),
            CampingOption("stargazing", R.string.onboarding_stargazing),
        )

    /** Campsite amenities, using the existing fixture ID for potable water. */
    val amenities: List<CampingOption> =
        listOf(
            CampingOption("flush-toilets", R.string.camping_amenity_flush_toilets),
            CampingOption("water", R.string.camping_amenity_potable_water),
            CampingOption("cookhouse", R.string.camping_amenity_cookhouse),
            CampingOption("fire-ring", R.string.camping_amenity_fire_ring),
            CampingOption("picnic-table", R.string.camping_amenity_picnic_table),
        )
}
