package io.github.CMPUT301F26stderr.campsbridge.ui.reviews

import androidx.lifecycle.SavedStateHandle
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.review.Review
import io.github.CMPUT301F26stderr.campsbridge.domain.review.ReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ReviewModerationComponent { RATING, TEXT }

enum class ReviewModerationPhase { IDLE, CONFIRMING, REMOVING, FAILED, UNKNOWN, UNAVAILABLE, COMPLETE }

/** Immutable identity and selected component; the other component is never submitted. */
data class ReviewModerationTarget(
    val campsiteId: String,
    val campsiteName: String,
    val authorId: String,
    val authorName: String?,
    val component: ReviewModerationComponent,
    val rating: Long?,
    val text: String?,
)

data class ReviewModerationState(
    val phase: ReviewModerationPhase = ReviewModerationPhase.IDLE,
    val target: ReviewModerationTarget? = null,
    val dialogVisible: Boolean = true,
)

/** One feedback-entry owner for independently confirmed rating and written-text moderation. */
class ReviewModerationController(
    private val actorId: String,
    private val campsiteId: String,
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val reviews: ReviewRepository,
    private val moderation: ModerationRepository,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val canModerate: () -> Boolean,
    private val onRemoved: () -> Unit,
) {
    private var closed = false
    private var job: Job? = null
    private var submitted = saved.get<Any?>("moderationSubmitted") == true
    private val restored = restore()
    private val mutableState =
        MutableStateFlow(
            ReviewModerationState(
                phase =
                    if (restored ==
                        null
                    ) {
                        ReviewModerationPhase.IDLE
                    } else if (submitted) {
                        ReviewModerationPhase.UNKNOWN
                    } else {
                        ReviewModerationPhase.CONFIRMING
                    },
                target = restored,
            ),
        )
    val state = mutableState.asStateFlow()

}
