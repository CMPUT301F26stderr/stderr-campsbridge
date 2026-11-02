package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import io.github.CMPUT301F26stderr.campsbridge.domain.account.*
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.*
import io.github.CMPUT301F26stderr.campsbridge.domain.image.*
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
        if (closed || target.id.isBlank() || selection == AdminInspectionTarget.Image(target)) return
        retire()
        selection = AdminInspectionTarget.Image(target)
        mutableState.value = AdminInspectionUiState.Image(target)
        startPhoto(target)
        observeParent(target)
    }
    private fun valid(token: Int) = !closed && generation == token
    private fun observeProfile(id: String) {
        val token = generation
        val attempt = ++parentGeneration
        parentJob?.cancel()
        updateProfile { it.copy(account = AdminRead.Loading) }
        parentJob = scope.launch {
            try {
                accounts.observe(id).collect { account ->
                    if (valid(token) && parentGeneration == attempt) {
                        if (account == null || account.id != id) {
                            stopDependents()
                            updateProfile { it.copy(account = AdminRead.Unavailable, campsites = AdminRead.Loading, photo = ImageSlotState()) }
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
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (valid(token) && parentGeneration == attempt) {
                    stopDependents()
                    updateProfile { it.copy(account = AdminRead.Failure, campsites = AdminRead.Loading, photo = ImageSlotState()) }
                }
            }
        }
    }
