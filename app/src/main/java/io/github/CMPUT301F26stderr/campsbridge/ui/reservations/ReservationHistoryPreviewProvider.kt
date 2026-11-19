package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCancellation
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.ReservationCategory
import io.github.CMPUT301F26stderr.campsbridge.ui.common.preview.previewReservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Supplies saved past/cancelled records and category-specific source recovery without writes. */
class ReservationHistoryPreviewProvider :
    PreviewParameterProvider<Pair<ReservationListCategory, ReservationListUiState>> {
    override val values: Sequence<Pair<ReservationListCategory, ReservationListUiState>> =
        sequence {
            val saved =
                previewReservation().let {
                    it.copy(
                        bookedCampsite =
                            it.bookedCampsite.copy(
                                name = "River Camp north meadow beside the mountain trail",
                            ),
                    )
                }
            val choices = listOf(ReservationCampsiteChoice(saved.campsiteId, saved.bookedCampsite.name))
            yield(
                ReservationListCategory.PAST to
                    ReservationListUiState.Content(
                        listOf(
                            ReservationListItem(saved, ReservationCategory.PAST, ReservationEntrantName.Unavailable),
                        ),
                        choices,
                    ),
            )
            val cancelled =
                saved.copy(
                    cancellation = ReservationCancellation(saved.createdAt, "owner", Role.OWNER, "Seasonal closure"),
                    payment = saved.payment.copy(refundedAt = saved.createdAt),
                )
            yield(
                ReservationListCategory.CANCELLED to
                    ReservationListUiState.Content(
                        listOf(
                            ReservationListItem(
                                cancelled,
                                ReservationCategory.CANCELLED,
                                ReservationEntrantName.Available("Jamie Lee with a long display name"),
                            ),
                        ),
                        choices,
                    ),
            )
            yield(ReservationListCategory.PAST to ReservationListUiState.Content(emptyList()))
            yield(ReservationListCategory.CANCELLED to ReservationListUiState.Content(emptyList()))
            yield(ReservationListCategory.PAST to ReservationListUiState.Loading)
            yield(ReservationListCategory.PAST to ReservationListUiState.Failure)
        }
}

/** Checks Material history selection and reachable saved rows at ordinary and doubled scale.
 * @param sample Passive selected category and source result.
 */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Large history", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ReservationHistoryPreview(
    @PreviewParameter(ReservationHistoryPreviewProvider::class) sample:
        Pair<ReservationListCategory, ReservationListUiState>,
) {
    CampsbridgeTheme(AccountSettings(controlScale = if (LocalDensity.current.fontScale >= 2f) 2.0 else 1.0)) {
        AppScreen(stringResource(R.string.my_reservations)) {
            ReservationListScreen(
                sample.second,
                ReservationContext.OWNER,
                null,
                {},
                {},
                {},
                {},
                category = sample.first,
            )
        }
    }
}
