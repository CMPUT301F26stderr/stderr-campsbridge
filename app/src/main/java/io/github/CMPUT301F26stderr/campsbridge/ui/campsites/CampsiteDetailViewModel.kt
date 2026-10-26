package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads one saved campsite; management and preview have separate graph-owned instances.
 * @param accountId Active identity.
 * @param campsiteId Saved listing identity.
 * @param context Caller-validated presentation.
 * @param campsites Listing read dependency.
 * @param images Attachment read dependency.
 */
class CampsiteDetailViewModel(
    private val accountId: String,
    private val campsiteId: String,
    val context: CampsiteDetailContext,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<CampsiteDetailUiState>(CampsiteDetailUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutablePhoto = MutableStateFlow(ImageSlotState())
    val photoState = mutablePhoto.asStateFlow()
    private var observation: Job? = null
    private var photo: ImageSlotController? = null
    private var photoJob: Job? = null

    init {
        observe()
    }

    /** Retries the failed record observation. */
    fun retry() {
        if (state.value == CampsiteDetailUiState.Failure) observe()
    }

    /** Retries an attachment or URL read without changing the listing. */
    fun retryPhoto() {
        photo?.retry()
    }

    /** Observes the saved identity without a global scan or placeholder data. */
    private fun observe() {
        observation?.cancel()
        mutableState.value = CampsiteDetailUiState.Loading
        observation =
            viewModelScope.launch {
                try {
                    campsites.observe(campsiteId).collect { site ->
                        if (site == null || !canPresent(site)) {
                            stopPhoto()
                            mutableState.value = CampsiteDetailUiState.Unavailable
                        } else {
                            mutableState.value = CampsiteDetailUiState.Content(site)
                            startPhoto()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    stopPhoto()
                    mutableState.value = CampsiteDetailUiState.Failure
                }
            }
    }

    /** Checks presentation compatibility; route capability checks are separate.
     * @param site Actual observed record.
     */
    private fun canPresent(site: Campsite): Boolean =
        site.id == campsiteId &&
            when (context) {
                CampsiteDetailContext.MANAGE, CampsiteDetailContext.PREVIEW -> {
                    site.ownerId == accountId && site.removal == null
                }

                CampsiteDetailContext.VIEW -> {
                    site.isPublished
                }

                CampsiteDetailContext.INSPECT -> {
                    true
                }
            }

    /** Starts one attachment owner only after the parent is known to be presentable. */
    private fun startPhoto() {
        if (photo != null) return
        val controller =
            ImageSlotController(
                images,
                ImageTarget(ImageTargetType.CAMPSITE, campsiteId),
                viewModelScope,
            )
        photo = controller
        photoJob =
            viewModelScope.launch {
                controller.state.collect { mutablePhoto.value = it }
            }
    }

    /** Releases stale attachment reads and their transient display reference. */
    private fun stopPhoto() {
        photoJob?.cancel()
        photo?.close()
        photo = null
        mutablePhoto.value = ImageSlotState()
    }

    /** Cancels pending reads without replacing the final observable snapshot on clear. */
    override fun onCleared() {
        observation?.cancel()
        photoJob?.cancel()
        photo?.close()
        super.onCleared()
    }
}
