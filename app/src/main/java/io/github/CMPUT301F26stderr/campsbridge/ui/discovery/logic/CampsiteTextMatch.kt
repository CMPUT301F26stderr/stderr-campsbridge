package io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import java.util.Locale

/** Matches a trimmed query as a substring of the name, location label or description.
 * Blank input matches every campsite; case folding uses [Locale.ROOT]. Inputs remain unchanged.
 * @param campsite Listing facts to search, without repository reads.
 * @param query Raw search text with optional surrounding whitespace.
 * @return Whether any supported field contains the normalized query.
 */
fun matchesCampsiteText(
    campsite: Campsite,
    query: String,
): Boolean {
    if (query.isBlank()) return true

    val normalized = query.trim().lowercase(Locale.ROOT)

    return campsite.details.name
        .lowercase(Locale.ROOT)
        .contains(normalized) ||
        campsite.details.locationLabel
            .lowercase(Locale.ROOT)
            .contains(normalized) ||
        campsite.details.description
            .lowercase(Locale.ROOT)
            .contains(normalized)
}
