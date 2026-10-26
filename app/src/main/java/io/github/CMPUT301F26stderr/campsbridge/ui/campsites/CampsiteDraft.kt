package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import java.time.LocalDate

/** Ordered setup content; allocation precedes review despite the prototype frame numbering. */
enum class CampsiteStep {
    BASICS,
    PHOTO,
    LOCATION,
    CAPACITY_PRICE,
    OPTIONS,
    RULES,
    AVAILABILITY,
    REVIEW,
}

/** Editable listing input; incomplete values remain local until details validate.
 * @param id Stable draft identity retained before the first persistence attempt.
 * @param step Current position in the eight-step setup flow.
 * @param name Exact editable campsite name.
 * @param description Optional multiline description, retained without reformatting.
 * @param location Selected map coordinate, absent before selection.
 * @param locationLabel Human-readable location supplied by the owner.
 * @param timeZone Editable IANA zone used to interpret campsite nights.
 * @param maxGuestsText Raw positive capacity input.
 * @param priceText Raw nonnegative CAD amount, parsed into exact cents on validation.
 * @param amenities Stable selected amenity IDs, including unlisted saved choices.
 * @param tags Stable selected campsite tag IDs.
 * @param rules Optional multiline rules, retained without reformatting.
 * @param requestedAvailability Explicit desired offered states for edited local dates.
 * @param returnToReview Whether the next valid step should return directly to review.
 */
data class CampsiteDraft(
    val id: String? = null,
    val step: CampsiteStep = CampsiteStep.BASICS,
    val name: String = "",
    val description: String = "",
    val location: Coordinates? = null,
    val locationLabel: String = "",
    val timeZone: String = "",
    val maxGuestsText: String = "",
    val priceText: String = "",
    val amenities: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val rules: String = "",
    val requestedAvailability: Map<LocalDate, Boolean> = emptyMap(),
    val returnToReview: Boolean = false,
)
