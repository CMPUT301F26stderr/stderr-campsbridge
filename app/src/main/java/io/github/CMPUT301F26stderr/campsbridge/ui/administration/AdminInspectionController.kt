package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Read-only selected record lifetime; section failures and retries never replace siblings. */
class AdminInspectionController(
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<AdminInspectionUiState>(AdminInspectionUiState.None)
    val state = mutableState.asStateFlow()
    private var selection: AdminInspectionTarget? = null
    private var generation = 0
    private var parentGeneration = 0
    private var dependentGeneration = 0
    private var closed = false
    private var parentJob: Job? = null
    private var sitesJob: Job? = null
    private var photoJob: Job? = null
    private var photo: ImageSlotController? = null
    private var dependents = false

    fun openProfile(id: String) {
        if (closed || id.isBlank() || selection == AdminInspectionTarget.Profile(id)) return
        retire()
        selection = AdminInspectionTarget.Profile(id)
        mutableState.value = AdminInspectionUiState.Profile(id)
        observeProfile(id)
    }

    fun openImage(target: ImageTarget) {
        if (closed || target.id.isBlank() ||
            selection == AdminInspectionTarget.Image(target)
        ) {
            return
        }
        retire()
        selection = AdminInspectionTarget.Image(target)
        mutableState.value = AdminInspectionUiState.Image(target)
        startPhoto(target)
        observeParent(target)
    }

    private fun valid(token: Int) =
        !closed && generation == token

    private fun observeProfile(id: String) {
        val token = generation
        val attempt = ++parentGeneration
        parentJob?.cancel()
        updateProfile { it.copy(account = AdminRead.Loading) }
        parentJob =
            scope.launch {
                try {
                    accounts.observe(id).collect { account ->
                        if (valid(token) && parentGeneration == attempt) {
                            if (account == null || account.id != id) {
                                stopDependents()
                                updateProfile {
                                    it.copy(
                                        account = AdminRead.Unavailable,
                                        campsites = AdminRead.Loading,
                                        photo = ImageSlotState(),
                                    )
                                }
                            } else {
                                updateProfile { it.copy(account = AdminRead.Content(account)) }
                                if (!dependents) {
                                    dependents = true
                                    observeSites(id)
                                    startPhoto(ImageTarget(ImageTargetType.ACCOUNT, id))
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (valid(token) && parentGeneration == attempt) {
                        stopDependents()
                        updateProfile {
                            it.copy(
                                account = AdminRead.Failure,
                                campsites = AdminRead.Loading,
                                photo = ImageSlotState(),
                            )
                        }
                    }
                }
            }
    }

    private fun observeSites(id: String) {
        val token = generation
        val attempt = ++dependentGeneration
        sitesJob?.cancel()
        updateProfile { it.copy(campsites = AdminRead.Loading) }
        sitesJob =
            scope.launch {
                try {
                    campsites.observeForOwner(id).collect { rows ->
                        if (valid(token) && dependentGeneration == attempt) {
                            updateProfile {
                                it.copy(
                                    campsites =
                                        if (rows.any { row ->
                                                row.ownerId != id
                                            }
                                        ) {
                                            AdminRead.Failure
                                        } else {
                                            AdminRead.Content(rows)
                                        },
                                )
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (valid(token) &&
                        dependentGeneration == attempt
                    ) {
                        updateProfile { it.copy(campsites = AdminRead.Failure) }
                    }
                }
            }
    }

    private fun observeParent(target: ImageTarget) {
        val token = generation
        val attempt = ++parentGeneration
        parentJob?.cancel()
        updateImage { it.copy(parent = AdminRead.Loading) }
        parentJob =
            scope.launch {
                try {
                    when (target.type) {
                        ImageTargetType.ACCOUNT -> {
                            accounts.observe(target.id).collect { account ->
                                if (valid(token) && parentGeneration == attempt) {
                                    updateImage {
                                        it.copy(
                                            parent =
                                                if (account == null ||
                                                    account.id != target.id
                                                ) {
                                                    AdminRead.Unavailable
                                                } else {
                                                    AdminRead.Content(
                                                        AdminImageParent.Profile(account),
                                                    )
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        ImageTargetType.CAMPSITE -> {
                            campsites.observe(target.id).collect { site ->
                                if (valid(token) && parentGeneration == attempt) {
                                    updateImage {
                                        it.copy(
                                            parent =
                                                if (site == null ||
                                                    site.id != target.id
                                                ) {
                                                    AdminRead.Unavailable
                                                } else {
                                                    AdminRead.Content(AdminImageParent.Site(site))
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (valid(token) &&
                        parentGeneration == attempt
                    ) {
                        updateImage { it.copy(parent = AdminRead.Failure) }
                    }
                }
            }
    }

    private fun startPhoto(target: ImageTarget) {
        val token = generation
        photoJob?.cancel()
        photo?.close()
        val slot = ImageSlotController(images, target, scope)
        photo = slot
        photoJob =
            scope.launch {
                slot.state.collect { value ->
                    if (valid(token) && photo === slot) {
                        updateProfile { it.copy(photo = value) }
                        updateImage { it.copy(photo = value) }
                    }
                }
            }
    }

    private fun updateProfile(
        change: (AdminInspectionUiState.Profile) -> AdminInspectionUiState.Profile,
    ) {
        val current = state.value as? AdminInspectionUiState.Profile ?: return
        mutableState.value = change(current)
    }

    private fun updateImage(
        change: (AdminInspectionUiState.Image) -> AdminInspectionUiState.Image,
    ) {
        val current = state.value as? AdminInspectionUiState.Image ?: return
        mutableState.value = change(current)
    }

    fun retryProfile() {
        if (closed) return
        val current = state.value as? AdminInspectionUiState.Profile ?: return
        if (current.account == AdminRead.Failure) observeProfile(current.id)
    }

    fun retryCampsites() {
        if (closed) return
        val current = state.value as? AdminInspectionUiState.Profile ?: return
        if (current.account is AdminRead.Content &&
            current.campsites == AdminRead.Failure
        ) {
            observeSites(current.id)
        }
    }

    fun retryParent() {
        if (closed) return
        val current = state.value as? AdminInspectionUiState.Image ?: return
        if (current.parent == AdminRead.Failure) observeParent(current.target)
    }

    fun retryPhoto() {
        if (!closed) photo?.retryDisplay()
    }

    fun closeSelection(target: AdminInspectionTarget) {
        if (!closed &&
            selection == target
        ) {
            retire()
        }
    }

    private fun stopDependents() {
        dependentGeneration++
        sitesJob?.cancel()
        sitesJob = null
        photoJob?.cancel()
        photoJob = null
        photo?.close()
        photo = null
        dependents = false
    }

    private fun retire() {
        generation++
        parentGeneration++
        parentJob?.cancel()
        parentJob = null
        stopDependents()
        selection = null
        mutableState.value = AdminInspectionUiState.None
    }

    fun close() {
        if (!closed) {
            retire()
            closed = true
        }
    }
}
