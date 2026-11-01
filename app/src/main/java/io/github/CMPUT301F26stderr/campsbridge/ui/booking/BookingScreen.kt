package io.github.CMPUT301F26stderr.campsbridge.ui.booking

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import java.time.LocalDate
import java.time.YearMonth

/** BK3 presentation contract; four-step content is pending its interaction checks.
 * @param state Parent-supplied draft and source outcome.
 * @param onBack Requests Back from the parent.
 * @param onNext Reports the displayed step.
 * @param onCancel Requests draft abandonment.
 * @param onMonth Requests a displayed calendar month.
 * @param onDate Reports a selectable date.
 * @param onClearDates Requests clearing endpoints.
 * @param onGuests Reports raw guest input.
 * @param onAccept Reports acceptance and the exact displayed rules/policy.
 * @param onEdit Requests one review section.
 * @param onQuote Reports the displayed amount for approval.
 * @param onPolicy Requests the packaged policy reader.
 * @param onRetry Reports the failed source to retry.
 * @param onRetryVisible Requests the visible inventory retry.
 * @param onRetrySelected Requests the selected inventory retry.
 * @param onReservation Reports an existing saved reservation identity.
 * @param onConfirm Requests confirmation from the parent.
 * @param modifier Caller layout.
 * @param photo Independently supplied campsite photo state.
 * @param onRetryPhoto Requests photo-source retry.
 * @param isSubmitting Whether draft actions and exits are blocked.
 * @param discardPrompt Whether abandonment awaits confirmation.
 * @param onDismissDiscard Keeps the parent-owned draft.
 * @param onConfirmDiscard Confirms abandoning the parent-owned draft.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BookingScreen(
    state: BookingUiState,
    onBack: () -> Unit,
    onNext: (BookingStep) -> Unit,
    onCancel: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onClearDates: () -> Unit,
    onGuests: (String) -> Unit,
    onAccept: (Boolean, String, String) -> Unit,
    onEdit: (BookingStep) -> Unit,
    onQuote: (Long) -> Unit,
    onPolicy: () -> Unit,
    onRetry: (BookingReadSource) -> Unit,
    onRetryVisible: () -> Unit,
    onRetrySelected: () -> Unit,
    onReservation: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    photo: ImageSlotState = ImageSlotState(loadState = ImageLoadState.READY),
    onRetryPhoto: () -> Unit = {},
    isSubmitting: Boolean = false,
    discardPrompt: Boolean = false,
    onDismissDiscard: () -> Unit = {},
    onConfirmDiscard: () -> Unit = {},
) {
    Text(stringResource(R.string.booking_title), modifier)
}
