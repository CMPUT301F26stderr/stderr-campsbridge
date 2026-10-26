package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observes an existing profile without normalizing or editing shared account fields.
 * @param accountId Established identity, never a placeholder ID.
 * @param accounts Account observation dependency.
 * @param images Image dependency shared with the profile-photo integration.
 */
class ProfileViewModel(
    private val accountId: String,
    private val accounts: AccountRepository,
    images: ImageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private val photoController =
        ImageSlotController(images, ImageTarget(ImageTargetType.ACCOUNT, accountId), viewModelScope)
    val photoState = photoController.state

    /** Forwards a local selection only while this profile is active.
     * @param photo Native selection, or null when the picker was cancelled.
     */
    fun selectPhoto(photo: SelectedPhoto?) {
        if (state.value is ProfileUiState.Content) photoController.select(photo)
    }

    /** Explicitly saves the active profile's selected photo. */
    fun savePhoto() {
        if (state.value is ProfileUiState.Content) photoController.save()
    }

    /** Explicitly removes the active profile's saved photo. */
    fun removePhoto() {
        if (state.value is ProfileUiState.Content) photoController.remove()
    }

    /** Discards local photo input without a persistence operation. */
    fun discardPhoto() {
        if (state.value is ProfileUiState.Content) photoController.discard()
    }

    /** Retries only the failed image stage. */
    fun retryPhoto() {
        if (state.value is ProfileUiState.Content) photoController.retry()
    }

    /** Keeps an existing candidate/image after the native result cannot be read. */
    fun photoSelectionFailed() {
        if (state.value is ProfileUiState.Content) photoController.selectionFailed()
    }

    init {
        retry()
    }

    /** Replaces a failed observation; prior subscription is cancelled before replacement. */
    fun retry() {
        observation?.cancel()
        mutableState.value = ProfileUiState.Loading
        observation =
            viewModelScope.launch {
                try {
                    accounts.observe(accountId).collect { account ->
                        mutableState.value =
                            if (account == null || !account.isActive) {
                                ProfileUiState.Unavailable
                            } else {
                                ProfileUiState.Content(account)
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = ProfileUiState.Failure
                }
            }
    }
}
