/*
 * Checks the entrant reservation map through the real history screen and the offline map surface.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.CMPUT301F26stderr.campsbridge.demo.DemoPinsMap
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.maps.LocalDemoPinsMap
import io.github.CMPUT301F26stderr.campsbridge.ui.common.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** WOW 1: an entrant switches to Map, picks a campsite pin and opens one of its saved stays. */
class ReservationMapScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Pins come from saved stays; choosing one shows its stays and opens the selected reservation. */
    @Test fun entrantPicksAPinAndOpensItsReservation() {
        val river = previewReservation()
        val lake =
            river.copy(
                id = "reservation-lake",
                campsiteId = "lake-site",
                bookedCampsite =
                    river.bookedCampsite.copy(
                        name = "Lake Louise Walk-in",
                        locationLabel = "Banff",
                        location = Coordinates(51.4254, -116.1773),
                    ),
            )
        val state =
            ReservationListUiState.Content(
                listOf(
                    ReservationListItem(river, ReservationCategory.UPCOMING),
                    ReservationListItem(lake, ReservationCategory.UPCOMING),
                ),
            )
        val opened = mutableListOf<String>()
        compose.setContent {
            var view by remember { mutableStateOf(ReservationListView.LIST) }
            CampsbridgeTheme {
                CompositionLocalProvider(
                    LocalDemoPinsMap provides { pins, selected, select, modifier ->
                        DemoPinsMap(pins, selected, select, modifier)
                    },
                ) {
                    ReservationListScreen(
                        state,
                        ReservationContext.ENTRANT,
                        null,
                        {},
                        { opened += it },
                        {},
                        {},
                        Modifier,
                        view = view,
                        onView = { view = it },
                    )
                }
            }
        }
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("Select a pin to see its stays.").assertIsDisplayed()
        compose.onNodeWithText("Lake Louise Walk-in").performClick()
        compose.onNodeWithTag("reservation:reservation-lake").performScrollTo().performClick()
        assertEquals(listOf("reservation-lake"), opened)
    }
}
