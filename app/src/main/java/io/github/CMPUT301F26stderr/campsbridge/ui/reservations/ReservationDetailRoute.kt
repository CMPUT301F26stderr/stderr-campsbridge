/*
 * Saved children beneath the same identity and actor context.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Saved children beneath the same identity and actor context.
 * @param routeKey Stable child segment independent of localized headings.
 */
enum class ReservationDetailPage(
    val routeKey: String,
) {
    OVERVIEW("overview"),
    PAYMENT("payment"),
    TERMS("terms"),
}

/** Connects every child to one retained reader, guarded command and explicit navigation.
 * @param model Single saved parent owner.
 * @param page Currently registered child.
 * @param onBack Returns to the overview or original caller.
 * @param onPayment Opens this identity's payment child.
 * @param onTerms Opens its accepted terms.
 */
@Composable
fun ReservationDetailRoute(
    model: ReservationDetailViewModel,
    page: ReservationDetailPage,
    onBack: () -> Unit,
    onPayment: () -> Unit,
    onTerms: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val cancellation by model.cancellation.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) model.refreshTime()
            }
        lifecycle.addObserver(observer)
        model.refreshTime()
        onDispose { lifecycle.removeObserver(observer) }
    }
    val back = { if (model.canLeave()) onBack() }
    val feedback: @Composable () -> Unit = {
        CancellationContent(cancellation, model.context, model::retryCancellationRead)
    }
    when (page) {
        ReservationDetailPage.OVERVIEW -> {
            ReservationDetailScreen(
                state,
                back,
                model::retry,
                onPayment = { if (model.canLeave()) onPayment() },
                onTerms = { if (model.canLeave()) onTerms() },
                context = model.context,
                cancellation = cancellation,
                onRequestCancel = model::requestCancel,
                onReason = model::editReason,
                onConfirmCancel = model::confirmCancel,
                onDismissCancel = model::dismissCancel,
                onCheckCancellation = model::retryCancellationRead,
            )
        }

        ReservationDetailPage.PAYMENT -> {
            PaymentDetailsScreen(
                state,
                back,
                model::retry,
                backEnabled = cancellation.phase == CancellationPhase.IDLE,
                feedback = feedback,
            )
        }

        ReservationDetailPage.TERMS -> {
            AcceptedTermsScreen(
                state,
                back,
                model::retry,
                backEnabled = cancellation.phase == CancellationPhase.IDLE,
                feedback = feedback,
            )
        }
    }
}
