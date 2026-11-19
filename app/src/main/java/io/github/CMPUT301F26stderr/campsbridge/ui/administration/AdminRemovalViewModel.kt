package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.moderation.ModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns one original confirmed admin request through uncertainty and explicit cleanup retries.
 * @param adminId Actor bound by the account host, never taken from the inspected record.
 * @param accounts Matching account reads for preflight and recovery.
 * @param campsites Matching campsite reads for preflight and recovery.
 * @param images Existing slot delete and obsolete-blob cleanup operations.
 * @param moderation Shared marking/cancellation/cleanup operation; a return confirms completion.
 * @param saved Original request primitives, without copying records, navigation or URLs.
 */
class AdminRemovalViewModel(
    private val adminId: String,
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val moderation: ModerationRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var session: SessionState = SessionState.Initializing
    private var closed = false
    private var generation = 0
    private var job: Job? = null
    private val restored = restoreRequest()
    private val mutableState =
        MutableStateFlow(
            AdminRemovalUiState(
                phase = if (restored == null) AdminRemovalPhase.IDLE else AdminRemovalPhase.CHECKING,
                request = restored,
                committed = saved.get<Any?>(COMMITTED) == true,
                cleanupBlobId = savedString(CLEANUP)?.takeIf { it.isNotBlank() },
            ),
        )
    val state = mutableState.asStateFlow()

    init {
        if (restored != null) {
            if (restored.actorId == adminId) {
                checkRecovery(restored)
            } else {
                unavailable(AdminRemovalProblem.DENIED)
            }
        }
    }

    /** Supplies the actual session while preserving every submitted original target. */
    fun updateSession(session: SessionState) {
        if (closed) return
        this.session = session
        if (state.value.phase == AdminRemovalPhase.CONFIRMING && !isAdmin()) dismiss()
    }

    /** Opens confirmation for an inspected account, or asks the existing root to own self-removal. */
    fun requestAccount(account: Account) {
        request(AdminRemovalTarget.Account(account.id, account.name))
    }

    /** Opens confirmation for one inspected campsite, without writing. */
    fun requestCampsite(campsite: Campsite) {
        request(AdminRemovalTarget.Campsite(campsite.id, campsite.details.name))
    }

    /** Captures the inspected slot and original blob before confirmation can suspend. */
    fun requestImage(attachment: ImageAttachment) {
        request(AdminRemovalTarget.Image(attachment.target, attachment.blobId))
    }

    private fun request(target: AdminRemovalTarget) {
        if (closed || state.value.phase != AdminRemovalPhase.IDLE || !isAdmin()) return
        val id =
            when (target) {
                is AdminRemovalTarget.Account -> target.id
                is AdminRemovalTarget.Campsite -> target.id
                is AdminRemovalTarget.Image -> target.target.id
            }
        if (id.isBlank() || (target is AdminRemovalTarget.Image && target.blobId.isBlank())) return
        mutableState.value =
            AdminRemovalUiState(
                phase =
                    if (target is AdminRemovalTarget.Account && target.id == adminId) {
                        AdminRemovalPhase.ROOT_HANDOFF
                    } else {
                        AdminRemovalPhase.CONFIRMING
                    },
                request = AdminRemovalRequest(adminId, target),
            )
    }

    /** The root host must call its existing request flow, then acknowledge this one-shot handoff. */
    fun acknowledgeRootHandoff() {
        if (!closed && state.value.phase == AdminRemovalPhase.ROOT_HANDOFF) {
            mutableState.value = AdminRemovalUiState()
        }
    }

    /** Freezes one original request before launching any preflight or write. */
    fun confirm() {
        if (closed || state.value.phase != AdminRemovalPhase.CONFIRMING || !isAdmin()) return
        val request = state.value.request ?: return
        retain()
        remove(request)
    }

    /** Pending byte cleanup never deletes the slot again, even if a replacement is now attached. */
    fun retry() {
        if (closed || !isAdmin()) return
        val request = state.value.request ?: return
        when (state.value.phase) {
            AdminRemovalPhase.UNKNOWN -> checkRecovery(request)
            AdminRemovalPhase.FAILED, AdminRemovalPhase.CLEANUP_PENDING -> remove(request)
            else -> Unit
        }
    }

    /** Closes the dialog after work stops; unresolved requests remain available for retry. */
    fun dismiss() {
        if (closed) return
        when (state.value.phase) {
            AdminRemovalPhase.FAILED, AdminRemovalPhase.UNKNOWN, AdminRemovalPhase.CLEANUP_PENDING -> {
                mutableState.value = state.value.copy(dialogVisible = false)
            }

            AdminRemovalPhase.CONFIRMING, AdminRemovalPhase.UNAVAILABLE, AdminRemovalPhase.COMPLETE -> {
                clearRetained()
                mutableState.value = AdminRemovalUiState()
            }

            else -> {
                return
            }
        }
    }

    /** Reopens the original pending request without reading or writing its target. */
    fun resume() {
        if (closed || !isAdmin() || state.value.dialogVisible) return
        mutableState.value = state.value.copy(dialogVisible = true)
    }

    private fun isAdmin(): Boolean {
        val ready = session as? SessionState.Ready ?: return false
        return ready.account.id == adminId && ready.account.isActive && Role.ADMIN in ready.account.roles
    }

    private fun remove(request: AdminRemovalRequest) {
        val committed = state.value.committed
        val cleanup = state.value.cleanupBlobId
        mutableState.value = state.value.copy(phase = AdminRemovalPhase.REMOVING, problem = null)
        launchOperation {
            // Self-removal is deliberately never executed in a navigation-scoped controller.
            if (!isAdmin() || request.actorId != adminId ||
                (request.target is AdminRemovalTarget.Account && request.target.id == adminId)
            ) {
                unavailable(AdminRemovalProblem.DENIED)
                return@launchOperation
            }
            if (request.target is AdminRemovalTarget.Image && committed && cleanup != null) {
                try {
                    images.retryCleanup(cleanup)
                    ensureCurrent()
                    complete()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ensureCurrent()
                    pending(cleanup)
                }
                return@launchOperation
            }
            var attempted = false
            try {
                when (val target = request.target) {
                    is AdminRemovalTarget.Account -> {
                        val account = accounts.get(target.id)
                        ensureCurrent()
                        if (account?.id !=
                            target.id
                        ) {
                            unavailable(AdminRemovalProblem.MISSING)
                            return@launchOperation
                        }
                        if (!preflightRemoval(account.removal, committed)) return@launchOperation
                        if (!isAdmin()) {
                            unavailable(AdminRemovalProblem.DENIED)
                            return@launchOperation
                        }
                        attempted = true
                        moderation.removeAccount(target.id, request.actorId)
                    }

                    is AdminRemovalTarget.Campsite -> {
                        val site = campsites.get(target.id)
                        ensureCurrent()
                        if (site?.id != target.id) {
                            unavailable(AdminRemovalProblem.MISSING)
                            return@launchOperation
                        }
                        if (!preflightRemoval(site.removal, committed)) return@launchOperation
                        if (!isAdmin()) {
                            unavailable(AdminRemovalProblem.DENIED)
                            return@launchOperation
                        }
                        attempted = true
                        moderation.removeCampsite(target.id, request.actorId)
                    }

                    is AdminRemovalTarget.Image -> {
                        val current = images.get(target.target)
                        ensureCurrent()
                        if (current == null) {
                            unavailable(AdminRemovalProblem.MISSING)
                            return@launchOperation
                        }
                        if (current.target != target.target || current.blobId != target.blobId) {
                            unavailable(AdminRemovalProblem.CHANGED)
                            return@launchOperation
                        }
                        if (!isAdmin()) {
                            unavailable(AdminRemovalProblem.DENIED)
                            return@launchOperation
                        }
                        attempted = true
                        val result = images.delete(target.target)
                        ensureCurrent()
                        if (result.attachment != null) {
                            mutableState.value =
                                state.value.copy(
                                    phase = AdminRemovalPhase.UNKNOWN,
                                    problem = AdminRemovalProblem.WRITE_FAILED,
                                )
                            retain()
                            return@launchOperation
                        }
                        if (result.obsoleteBlobId != null) {
                            pending(result.obsoleteBlobId)
                            return@launchOperation
                        }
                    }
                }
                ensureCurrent()
                complete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureCurrent()
                if (attempted) {
                    recover(request)
                } else {
                    mutableState.value =
                        state.value.copy(phase = AdminRemovalPhase.UNKNOWN, problem = AdminRemovalProblem.READ_FAILED)
                    retain()
                }
            }
        }
    }

    private fun preflightRemoval(
        removal: Removal?,
        committed: Boolean,
    ): Boolean {
        if (removal == null) return true
        if (committed && removal.actorId == adminId) return true
        unavailable(AdminRemovalProblem.CHANGED)
        return false
    }

    /** Read-only restoration/reconciliation; no marking or deletion is automatically replayed. */
    private fun checkRecovery(request: AdminRemovalRequest) {
        mutableState.value = state.value.copy(phase = AdminRemovalPhase.CHECKING, problem = null)
        launchOperation { recover(request) }
    }

    private suspend fun recover(request: AdminRemovalRequest) {
        try {
            when (val target = request.target) {
                is AdminRemovalTarget.Account -> {
                    val account = accounts.get(target.id)
                    ensureCurrent()
                    if (account?.id != target.id) {
                        unavailable(AdminRemovalProblem.MISSING)
                    } else {
                        recoveredRemoval(account.removal, request.actorId)
                    }
                }

                is AdminRemovalTarget.Campsite -> {
                    val site = campsites.get(target.id)
                    ensureCurrent()
                    if (site?.id != target.id) {
                        unavailable(AdminRemovalProblem.MISSING)
                    } else {
                        recoveredRemoval(site.removal, request.actorId)
                    }
                }

                is AdminRemovalTarget.Image -> {
                    // A returned cleanup ID is an exact saved obligation, independent of the slot now.
                    if (state.value.committed && state.value.cleanupBlobId != null) {
                        pending(state.value.cleanupBlobId)
                        return
                    }
                    val current = images.get(target.target)
                    ensureCurrent()
                    if (current != null && current.target != target.target) {
                        unavailable(AdminRemovalProblem.CHANGED)
                    } else if (current == null || current.blobId != target.blobId) {
                        pending(target.blobId)
                    } else {
                        failed()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ensureCurrent()
            mutableState.value =
                state.value.copy(phase = AdminRemovalPhase.UNKNOWN, problem = AdminRemovalProblem.READ_FAILED)
            retain()
        }
    }

    private fun recoveredRemoval(
        removal: Removal?,
        actor: String,
    ) {
        when {
            removal == null -> failed()
            removal.actorId == actor -> pending(null)
            else -> unavailable(AdminRemovalProblem.CHANGED)
        }
    }

    private fun failed() {
        mutableState.value =
            state.value.copy(
                phase = AdminRemovalPhase.FAILED,
                committed = false,
                problem = AdminRemovalProblem.WRITE_FAILED,
            )
        retain()
    }

    private fun pending(blob: String?) {
        mutableState.value =
            state.value.copy(
                phase = AdminRemovalPhase.CLEANUP_PENDING,
                committed = true,
                cleanupBlobId = blob,
                problem = null,
            )
        retain()
    }

    private fun unavailable(problem: AdminRemovalProblem) {
        mutableState.value = state.value.copy(phase = AdminRemovalPhase.UNAVAILABLE, problem = problem)
    }

    private fun complete() {
        mutableState.value =
            state.value.copy(phase = AdminRemovalPhase.COMPLETE, committed = true, cleanupBlobId = null, problem = null)
        clearRetained()
    }

    private fun launchOperation(operation: suspend () -> Unit) {
        val token = ++generation
        job?.cancel()
        job =
            viewModelScope.launch {
                if (!closed && token == generation) operation()
            }
    }

    private suspend fun ensureCurrent() {
        currentCoroutineContext().ensureActive()
        if (closed) throw CancellationException("Admin removal owner retired")
    }

    private fun savedString(key: String) =
        saved.get<Any?>(key) as? String

    private fun restoreRequest(): AdminRemovalRequest? {
        val actor = savedString(ACTOR)?.takeIf { it.isNotBlank() } ?: return null
        val id = savedString(TARGET)?.takeIf { it.isNotBlank() } ?: return null
        val label = savedString(LABEL).orEmpty()
        val target =
            when (savedString(KIND)) {
                "ACCOUNT" -> {
                    AdminRemovalTarget.Account(id, label)
                }

                "CAMPSITE" -> {
                    AdminRemovalTarget.Campsite(id, label)
                }

                "IMAGE" -> {
                    val type = ImageTargetType.entries.firstOrNull { it.name == savedString(IMAGE_TYPE) } ?: return null
                    val blob = savedString(BLOB)?.takeIf { it.isNotBlank() } ?: return null
                    AdminRemovalTarget.Image(ImageTarget(type, id), blob)
                }

                else -> {
                    return null
                }
            }
        return AdminRemovalRequest(actor, target)
    }

    private fun retain() {
        val request = state.value.request ?: return
        saved[ACTOR] = request.actorId
        when (val target = request.target) {
            is AdminRemovalTarget.Account -> {
                saved[KIND] = "ACCOUNT"
                saved[TARGET] = target.id
                saved[LABEL] =
                    target.name
            }

            is AdminRemovalTarget.Campsite -> {
                saved[KIND] = "CAMPSITE"
                saved[TARGET] = target.id
                saved[LABEL] =
                    target.name
            }

            is AdminRemovalTarget.Image -> {
                saved[KIND] = "IMAGE"
                saved[TARGET] = target.target.id
                saved[IMAGE_TYPE] = target.target.type.name
                saved[BLOB] =
                    target.blobId
            }
        }
        saved[COMMITTED] = state.value.committed
        saved[CLEANUP] = state.value.cleanupBlobId.orEmpty()
    }

    private fun clearRetained() {
        for (key in listOf(ACTOR, KIND, TARGET, LABEL, IMAGE_TYPE, BLOB, COMMITTED, CLEANUP)) saved.remove<Any?>(key)
    }

    override fun onCleared() {
        closed = true
        generation++
        job?.cancel()
    }

    private companion object {
        const val ACTOR = "adminRemovalActor"
        const val KIND = "adminRemovalKind"
        const val TARGET = "adminRemovalTarget"
        const val LABEL = "adminRemovalLabel"
        const val IMAGE_TYPE = "adminRemovalImageType"
        const val BLOB = "adminRemovalBlob"
        const val COMMITTED = "adminRemovalCommitted"
        const val CLEANUP = "adminRemovalCleanup"
    }
}
