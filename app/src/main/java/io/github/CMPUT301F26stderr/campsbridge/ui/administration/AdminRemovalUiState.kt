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

/** Marking/detaching is distinct from the repository confirming all cleanup finished. */
data class AdminRemovalUiState(
    val phase: AdminRemovalPhase = AdminRemovalPhase.IDLE,
    val request: AdminRemovalRequest? = null,
    val committed: Boolean = false,
    val cleanupBlobId: String? = null,
    val problem: AdminRemovalProblem? = null,
) {
    val blocksNavigation: Boolean get() =
        phase in
            setOf(
                AdminRemovalPhase.REMOVING,
                AdminRemovalPhase.CHECKING,
                AdminRemovalPhase.FAILED,
                AdminRemovalPhase.UNKNOWN,
                AdminRemovalPhase.CLEANUP_PENDING,
            )
}
