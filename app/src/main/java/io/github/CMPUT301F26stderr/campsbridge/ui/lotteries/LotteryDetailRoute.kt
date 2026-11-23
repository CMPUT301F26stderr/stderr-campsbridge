/*
 * Connects one saved parent to its registered overview, schedule and allocation children.
 */

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
 * @param onEditDetails Opens the organizer's saved details editor.
 * @param onEditAllocations Opens the current participant's owned allocation editor.
 * @param onAnnounce Opens the saved lottery announcement form.
 * @param announcementSent Whether the child confirmed announcement storage.
 * @param onDismissAnnouncement Clears the local saved-result feedback.
 * @param onEditOwners Opens saved member context and the organizer's membership picker.
 * @param onChooseCampsite Opens the saved multiple-offer child.
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
    onEditDetails: (() -> Unit)? = null,
    onEditAllocations: (() -> Unit)? = null,
    onAnnounce: (() -> Unit)? = null,
    announcementSent: Boolean = false,
    onDismissAnnouncement: () -> Unit = {},
    onEditOwners: (() -> Unit)? = null,
    onChooseCampsite: (() -> Unit)? = null,
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
                onEditDetails = onEditDetails,
                onEditAllocations = onEditAllocations,
                onAnnounce = onAnnounce,
                announcementSent = announcementSent,
                onDismissAnnouncement = onDismissAnnouncement,
                onEditOwners = onEditOwners,
                onChooseCampsite = onChooseCampsite,
            )
        }

        LotteryDetailSection.SCHEDULE -> {
            LotteryScheduleScreen(state, onBack, model::retry)
        }

        LotteryDetailSection.CAMPSITES -> {
            LotteryOffersScreen(state, onBack, model::retry, model::retryOffers, onCampsite)
        }
    }
}
