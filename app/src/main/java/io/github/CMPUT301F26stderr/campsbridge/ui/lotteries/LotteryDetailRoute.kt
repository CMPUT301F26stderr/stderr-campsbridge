package io.github.CMPUT301F26stderr.campsbridge.ui.lotteries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Connects one saved parent to its registered overview, schedule and allocation children.
 * @param model Existing context-qualified saved-record owner.
 * @param onBack Returns to the actual caller or retained overview.
 * @param onContinue Opens setup for this saved identity.
 * @param onParticipants Registered owner-only participant child.
 * @param onReservation Registered consuming reservation destination.
 * @param onBooking Registered single-site winner flow; current facts still gate entry.
 * @param section Registered read-only child of the same saved parent.
 * @param onOpenSection Registered overview menu actions, absent in isolated examples.
 * @param onCampsite Registered saved campsite calendar, absent in isolated examples.
 */
@Composable
fun LotteryDetailRoute(
    model: LotteryDetailViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onParticipants: (() -> Unit)? = null,
    onReservation: ((String) -> Unit)? = null,
    onBooking: ((String, String) -> Unit)? = null,
    section: LotteryDetailSection = LotteryDetailSection.OVERVIEW,
    onOpenSection: ((LotteryDetailSection) -> Unit)? = null,
    onCampsite: ((String) -> Unit)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) model.refreshTime()
    }
    when (section) {
        LotteryDetailSection.OVERVIEW -> {
            LotteryDetailScreen(
                state,
                onBack,
                model::retry,
                model::retryOffers,
                onContinue,
                model::retryMembership,
                model::retryOpportunity,
                model::join,
                model::requestLeave,
                model::dismissLeave,
                model::confirmLeave,
                model::draw,
                onParticipants = onParticipants,
                onReservation = onReservation,
                onBooking = onBooking,
                onOpenSection = onOpenSection,
            )
        }

        LotteryDetailSection.SCHEDULE -> {
            LotteryScheduleScreen(state, onBack, model::retry, model::retryOffers)
        }

        LotteryDetailSection.CAMPSITES -> {
            LotteryOffersScreen(state, onBack, model::retry, model::retryOffers, onCampsite)
        }
    }
}
