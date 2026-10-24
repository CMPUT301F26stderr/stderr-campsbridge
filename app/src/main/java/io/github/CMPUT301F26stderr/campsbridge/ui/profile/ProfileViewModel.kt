package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
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
