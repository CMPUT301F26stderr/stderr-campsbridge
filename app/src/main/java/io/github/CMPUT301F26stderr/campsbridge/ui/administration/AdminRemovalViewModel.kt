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

    /** Cancelling an unsubmitted prompt changes nothing; unresolved cleanup cannot be forgotten. */
    fun dismiss() {
        if (closed || state.value.phase !in
            setOf(
                AdminRemovalPhase.CONFIRMING,
                AdminRemovalPhase.UNAVAILABLE,
                AdminRemovalPhase.COMPLETE,
            )
        ) {
            return
        }
        clearRetained()
        mutableState.value = AdminRemovalUiState()
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

