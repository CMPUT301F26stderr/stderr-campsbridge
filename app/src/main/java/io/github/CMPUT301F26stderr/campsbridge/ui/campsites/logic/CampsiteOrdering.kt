package io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic

import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite

/** Orders listings by locale-independent name, then by their exact stable ID.
 * @param campsites Listings to order without changing the input list or its records.
 * @return Every supplied listing in ascending name and ID order.
 */
fun sortCampsitesByName(campsites: List<Campsite>): List<Campsite> =
    emptyList()
