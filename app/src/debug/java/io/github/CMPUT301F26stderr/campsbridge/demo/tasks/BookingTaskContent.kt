/*
 * Exercises the accepted availability helper through the real date-selection owner.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.CMPUT301F26stderr.campsbridge.R
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.BookingUiState
import io.github.CMPUT301F26stderr.campsbridge.ui.booking.steps.StayDatesStep
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.FailureContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.feedback.LoadingContent
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.Spacing

/** Exercises the accepted availability helper through the real date-selection owner.
 * @param session Current task's local graph; submission is outside this example.
 * @param onBack Returns to the task catalogue.
 */
@Composable
internal fun BookingDatesTaskExample(
    session: TaskExampleSession,
    onBack: () -> Unit,
) {
    val model = session.bookingDates()
    val state by model.state.collectAsStateWithLifecycle()
    DisposableEffect(model) {
        model.setDatesVisible(true)
        onDispose { model.setDatesVisible(false) }
    }
    AppScreen(stringResource(session.example.title), onBack = onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            when (val current = state) {
                is BookingUiState.Content -> {
                    StayDatesStep(
                        current,
                        model::changeMonth,
                        model::selectStayDates,
                        model::clearDates,
                        model::retryVisibleInventory,
                        model::retrySelectedInventory,
                        enabled = true,
                    )
                }

                is BookingUiState.Failure -> {
                    FailureContent(stringResource(R.string.booking_inventory_failed), { model.retry(current.source) })
                }

                else -> {
                    LoadingContent()
                }
            }
        }
    }
}
