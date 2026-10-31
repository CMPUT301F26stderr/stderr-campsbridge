package io.github.CMPUT301F26stderr.campsbridge.ui.reservations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Saved children addressed beneath the same identity and actor context.
 * @param routeKey Stable child segment independent of localized page headings.
 */
enum class ReservationDetailPage(
    val routeKey: String,
) {
    OVERVIEW("overview"),
    PAYMENT("payment"),
    TERMS("terms"),
}

/** Connects a child to its parent's existing reader and explicit navigation callbacks.
 * @param model Single saved parent owner.
 * @param page Currently registered child.
 * @param onBack Returns to the overview or original caller.
 * @param onPayment Opens this saved identity's payment child.
 * @param onTerms Opens this saved identity's accepted-terms child.
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
    when (page) {
        ReservationDetailPage.OVERVIEW -> {
            ReservationDetailScreen(state, onBack, model::retry, onPayment, onTerms)
        }

        ReservationDetailPage.PAYMENT -> {
            PaymentDetailsScreen(state, onBack, model::retry)
        }

        ReservationDetailPage.TERMS -> {
            AcceptedTermsScreen(state, onBack, model::retry)
        }
    }
}
