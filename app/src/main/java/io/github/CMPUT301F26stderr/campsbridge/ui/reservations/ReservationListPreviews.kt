/*
 * Covers source outcomes and independent owner names with passive saved fixtures.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.previewCampsitePhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components.ReservationCard
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Covers source outcomes and independent owner names with passive saved fixtures. */
class ReservationListPreviewProvider : PreviewParameterProvider<ReservationListUiState> {
    override val values: Sequence<ReservationListUiState> =
        sequence {
            yield(ReservationListUiState.Loading)
            yield(ReservationListUiState.Failure)
            yield(ReservationListUiState.Content(emptyList()))
            val saved = previewReservation()
            for (name in listOf(
                ReservationEntrantName.Available("Alex Chen"),
                ReservationEntrantName.Loading,
                ReservationEntrantName.Unavailable,
                ReservationEntrantName.Failure,
            )) {
                yield(
                    ReservationListUiState.Content(
                        listOf(
                            ReservationListItem(saved, ReservationCategory.UPCOMING, name),
                        ),
                        listOf(
                            ReservationCampsiteChoice(saved.campsiteId, saved.bookedCampsite.name),
                        ),
                    ),
                )
            }
            yield(
                ReservationListUiState.Content(
                    emptyList(),
                    classificationPending = true,
                    orderingPending = true,
                ),
            )
        }
}

/** Shows the owner list with inert callbacks and text scaling.
 * @param state Passive source and label outcome.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ReservationListPreview(
    @PreviewParameter(ReservationListPreviewProvider::class) state: ReservationListUiState,
) {
    CampsbridgeTheme {
        AppScreen(stringResource(R.string.my_reservations)) {
            ReservationListScreen(
                state,
                ReservationContext.OWNER,
                null,
                {},
                {},
                {},
                {},
                photos =
                    mapOf(
                        previewReservation().campsiteId to
                            previewCampsitePhoto(previewReservation().campsiteId),
                    ),
            )
        }
    }
}

/** Varies supplemental photo outcomes without loading a repository. */
class ReservationPhotoPreviewProvider : PreviewParameterProvider<ImageSlotState> {
    override val values =
        sequenceOf(
            previewCampsitePhoto(previewReservation().campsiteId),
            ImageSlotState(),
            ImageSlotState(loadState = ImageLoadState.READY),
            ImageSlotState(loadState = ImageLoadState.FAILED),
            ImageSlotState(loadState = ImageLoadState.READY, displayFailed = true),
        )
}

/** Keeps long saved text readable across independent photo outcomes.
 * @param photo Passive image result.
 */
@Preview(showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ReservationPhotoPreview(
    @PreviewParameter(ReservationPhotoPreviewProvider::class) photo: ImageSlotState,
) {
    val record =
        previewReservation().let {
            it.copy(
                bookedCampsite =
                    it.bookedCampsite.copy(
                        name = "River Camp\nNorth meadow\nBeside the water",
                    ),
            )
        }
    CampsbridgeTheme {
        ReservationCard(record, stringResource(R.string.reservation_in_progress), {}, photo = photo)
    }
}
