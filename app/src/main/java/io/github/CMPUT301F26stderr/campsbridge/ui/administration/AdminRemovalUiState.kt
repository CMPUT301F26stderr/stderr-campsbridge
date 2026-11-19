package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget

/** One original inspected target, independent of later navigation or source changes. */
sealed interface AdminRemovalTarget {
    data class Account(
        val id: String,
        val name: String,
    ) : AdminRemovalTarget

    data class Campsite(
        val id: String,
        val name: String,
    ) : AdminRemovalTarget

    data class Image(
        val target: ImageTarget,
        val blobId: String,
    ) : AdminRemovalTarget
}

/** Frozen confirmation identity; retries never replace its actor or target. */
data class AdminRemovalRequest(
    val actorId: String,
    val target: AdminRemovalTarget,
)

enum class AdminRemovalPhase {
    IDLE,
    CONFIRMING,
    REMOVING,
    CHECKING,
    FAILED,
    UNKNOWN,
    CLEANUP_PENDING,
    UNAVAILABLE,
    COMPLETE,
    ROOT_HANDOFF,
}

enum class AdminRemovalProblem { MISSING, CHANGED, DENIED, READ_FAILED, WRITE_FAILED }

/** Marking/detaching is distinct from the repository confirming all cleanup finished.
 * @param phase Current confirmation, operation or recovery state.
 * @param request Original administrator and inspected target.
 * @param committed Whether marking or detaching has already succeeded.
 * @param cleanupBlobId Original detached blob still awaiting cleanup.
 * @param problem Reason the operation cannot proceed.
 * @param dialogVisible Whether to show the retained request's dialog.
 */
data class AdminRemovalUiState(
    val phase: AdminRemovalPhase = AdminRemovalPhase.IDLE,
    val request: AdminRemovalRequest? = null,
    val committed: Boolean = false,
    val cleanupBlobId: String? = null,
    val problem: AdminRemovalProblem? = null,
    val dialogVisible: Boolean = true,
)
