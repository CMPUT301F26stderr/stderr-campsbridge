/*
 * Shares saved-page feedback and scrolling without acquiring a reader or owning navigation.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.domain.booking.Reservation
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.EmptyContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.reservations.ReservationDetailUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Shares saved-page feedback and scrolling without acquiring a reader or owning navigation.
 * @param title Localized child page heading.
 * @param state Outcome from the saved parent's single observation.
 * @param onBack Returns to the actual caller.
 * @param onRetry Restarts only the failed saved source.
 * @param modifier Caller layout.
 * @param backEnabled Whether an unresolved command allows leaving.
 * @param feedback Command feedback independent of ordinary read failures.
 * @param content Child facts rendered only for a matching saved reservation.
 */
@Composable
internal fun ReservationRecordScreen(
    @StringRes title: Int,
    state: ReservationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    feedback: @Composable () -> Unit = {},
    content: @Composable ColumnScope.(Reservation) -> Unit,
) {
    val scrollState = rememberScrollState()
    val cancellation = (state as? ReservationDetailUiState.Content)?.reservation?.cancellation
    var hadCancellation by remember { mutableStateOf(cancellation != null) }
    LaunchedEffect(cancellation) {
        if (cancellation != null && !hadCancellation) scrollState.scrollTo(0)
        hadCancellation = cancellation != null
    }
    AppScreen(stringResource(title), modifier, onBack, backEnabled = backEnabled) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            feedback()
            when (state) {
                ReservationDetailUiState.Loading -> {
                    LoadingContent()
                }

                ReservationDetailUiState.Failure -> {
                    FailureContent(stringResource(R.string.reservation_load_failed), onRetry)
                }

                ReservationDetailUiState.Unavailable -> {
                    EmptyContent(stringResource(R.string.reservation_unavailable))
                }

                is ReservationDetailUiState.Content -> {
                    content(state.reservation)
                }
            }
        }
    }
}
