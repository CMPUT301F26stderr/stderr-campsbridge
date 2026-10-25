package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.ProfileUpdate
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns profile drafts and explicit field saves; the image controller owns photo persistence.
 * @param accountId Established identity, never a placeholder ID.
 * @param accounts Account observation dependency.
 * @param images Image dependency shared with the profile-photo integration.
 * @param savedState Navigation-owned small editor inputs.
 * @param parseName Reserved display-name parser, absent until reviewed.
 * @param parseEmail Reserved optional-email parser, absent until reviewed.
 * @param parsePhone Reserved optional-phone parser, absent until reviewed.
 */
class ProfileViewModel(
    private val accountId: String,
    private val accounts: AccountRepository,
    images: ImageRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val parseName: ((String) -> DisplayNameInput)? = null,
    private val parseEmail: ((String) -> OptionalContactInput)? = null,
    private val parsePhone: ((String) -> OptionalContactInput)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private val photoController =
        ImageSlotController(images, ImageTarget(ImageTargetType.ACCOUNT, accountId), viewModelScope)
    val photoState = photoController.state
    private var account: Account? = null
    private var loading = true
    private var loadFailed = false
    private var personalDraft = savedState.get<String>("profileName")?.let(::PersonalDetailsDraft)
    private var contactDraft =
        savedState.get<String>("profileEmail")?.let {
            ContactDetailsDraft(it, savedState["profilePhone"] ?: "")
        }
    private var operation = ProfileOperation.IDLE
    private var nameError = false
    private var emailError = false
    private var phoneError = false
    private var needsConfirmedRead = savedState["profileWritePending"] ?: false
    private var saveFailed = needsConfirmedRead
    private val mutableCompletion =
        MutableStateFlow(
            savedState.get<String>("profileCompletedEditor")?.let(ProfileEditor::valueOf),
        )
    val completion = mutableCompletion.asStateFlow()

    /** Starts name editing from confirmed values without resetting a dirty draft. */
    fun beginPersonalEdit() {
        if (!canEdit() || personalDraft != null) return
        personalDraft = PersonalDetailsDraft(checkNotNull(account).name)
        saveDrafts()
        publish()
    }

    /** Retains exact unsaved name input.
     * @param text New editable text.
     */
    fun editName(text: String) {
        if (!canEdit() || personalDraft == null) return
        personalDraft = PersonalDetailsDraft(text)
        nameError = false
        saveFailed = false
        saveDrafts()
        publish()
    }

    /** Starts optional-contact editing from confirmed values. */
    fun beginContactEdit() {
        if (!canEdit() || contactDraft != null) return
        val current = checkNotNull(account)
        contactDraft = ContactDetailsDraft(current.email.orEmpty(), current.phone.orEmpty())
        saveDrafts()
        publish()
    }

    /** Retains exact optional-email input.
     * @param text New editable text.
     */
    fun editEmail(text: String) {
        if (!canEdit()) return
        contactDraft = contactDraft?.copy(emailText = text) ?: return
        emailError = false
        saveFailed = false
        saveDrafts()
        publish()
    }

    /** Retains exact optional-phone input.
     * @param text New editable text.
     */
    fun editPhone(text: String) {
        if (!canEdit()) return
        contactDraft = contactDraft?.copy(phoneText = text) ?: return
        phoneError = false
        saveFailed = false
        saveDrafts()
        publish()
    }

    /** Explicitly validates and saves only the name while retaining current contacts. */
    fun saveName() {
        if (!canEdit() || photoState.value.operation != ImageOperation.IDLE) return
        val draft = personalDraft ?: return
        val parsed = parseName?.invoke(draft.nameText) ?: return
        nameError = parsed == DisplayNameInput.Required
        if (parsed is DisplayNameInput.Valid) {
            val current = checkNotNull(account)
            save(ProfileEditor.PERSONAL, ProfileUpdate(parsed.name, current.email, current.phone))
        } else {
            publish()
        }
    }

    /** Explicitly validates and saves only contacts while retaining the current name. */
    fun saveContacts() {
        if (!canEdit() || photoState.value.operation != ImageOperation.IDLE) return
        val draft = contactDraft ?: return
        val emailParser = parseEmail ?: return
        val phoneParser = parsePhone ?: return
        val email = emailParser(draft.emailText)
        val phone = phoneParser(draft.phoneText)
        emailError = email == OptionalContactInput.Invalid
        phoneError = phone == OptionalContactInput.Invalid
        if (email is OptionalContactInput.Valid && phone is OptionalContactInput.Valid) {
            save(
                ProfileEditor.CONTACT,
                ProfileUpdate(checkNotNull(account).name, email.value, phone.value),
            )
        } else {
            publish()
        }
    }

    /** Discards unsaved personal input when no mutation or image reconciliation is active. */
    fun cancelPersonalEdit(): Boolean {
        if (operation != ProfileOperation.IDLE || completion.value != null ||
            photoState.value.operation != ImageOperation.IDLE
        ) {
            return false
        }
        photoController.discard()
        personalDraft = null
        nameError = false
        saveFailed = false
        saveDrafts()
        publish()
        return true
    }

    /** Discards unsaved contacts when no field write is active. */
    fun cancelContactEdit(): Boolean {
        if (operation != ProfileOperation.IDLE || completion.value != null ||
            photoState.value.operation != ImageOperation.IDLE
        ) {
            return false
        }
        contactDraft = null
        emailError = false
        phoneError = false
        saveFailed = false
        saveDrafts()
        publish()
        return true
    }

    /** Acknowledges only the matching editor's confirmed completion.
     * @param editor Active editor that handled navigation.
     */
    fun acknowledgeCompletion(editor: ProfileEditor) {
        if (completion.value != editor) return
        setCompletion(null)
        publish()
    }

    /** Forwards a local selection only while this profile is active.
     * @param photo Native selection, or null when the picker was cancelled.
     */
    fun selectPhoto(photo: SelectedPhoto?) {
        if (canEdit()) photoController.select(photo)
    }

    /** Explicitly saves the active profile's selected photo. */
    fun savePhoto() {
        if (canEdit()) photoController.save()
    }

    /** Explicitly removes the active profile's saved photo. */
    fun removePhoto() {
        if (canEdit()) photoController.remove()
    }

    /** Discards local photo input without a persistence operation. */
    fun discardPhoto() {
        if (canEdit()) photoController.discard()
    }

    /** Retries only the failed image stage. */
    fun retryPhoto() {
        if (canEdit()) photoController.retry()
    }

    /** Keeps an existing candidate/image after the native result cannot be read. */
    fun photoSelectionFailed() {
        if (canEdit()) photoController.selectionFailed()
    }

    init {
        retry()
    }

    /** Replaces a failed observation; prior subscription is cancelled before replacement. */
    fun retry() {
        if (operation != ProfileOperation.IDLE) return
        observation?.cancel()
        loading = true
        loadFailed = false
        publish()
        observation =
            viewModelScope.launch {
                try {
                    if (needsConfirmedRead) {
                        account = accounts.get(accountId)
                        needsConfirmedRead = false
                        savedState["profileWritePending"] = false
                    }
                    accounts.observe(accountId).collect { record ->
                        account = record
                        loading = false
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    loading = false
                    loadFailed = true
                    publish()
                }
            }
    }

    /** Checks synchronous guards before launching any user-triggered work. */
    private fun canEdit(): Boolean =
        state.value is ProfileUiState.Content &&
            operation == ProfileOperation.IDLE && completion.value == null

    /** Saves one editor's fields with confirmed unrelated values, retaining input on failure.
     * @param editor Editor whose completion may navigate after acknowledgement.
     * @param update Exact validated replacement profile fields.
     */
    private fun save(
        editor: ProfileEditor,
        update: ProfileUpdate,
    ) {
        operation =
            if (editor ==
                ProfileEditor.PERSONAL
            ) {
                ProfileOperation.SAVING_NAME
            } else {
                ProfileOperation.SAVING_CONTACTS
            }
        savedState["profileWritePending"] = true
        saveFailed = false
        publish()
        viewModelScope.launch {
            try {
                accounts.updateProfile(accountId, update)
                when (editor) {
                    ProfileEditor.PERSONAL -> personalDraft = null
                    ProfileEditor.CONTACT -> contactDraft = null
                }
                saveDrafts()
                setCompletion(editor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveFailed = true
            }
            operation = ProfileOperation.IDLE
            savedState["profileWritePending"] = false
            publish()
        }
    }

    /** Retains a confirmed completion across read failures and process recreation.
     * @param editor Confirmed editor, or null after active-route acknowledgement.
     */
    private fun setCompletion(editor: ProfileEditor?) {
        savedState["profileCompletedEditor"] = editor?.name
        mutableCompletion.value = editor
    }

    /** Persists only small unsaved strings; null removes that editor's saved input. */
    private fun saveDrafts() {
        savedState["profileName"] = personalDraft?.nameText
        savedState["profileEmail"] = contactDraft?.emailText
        savedState["profilePhone"] = contactDraft?.phoneText
    }

    /** Renders authoritative read outcome and retained editor state without performing writes. */
    private fun publish() {
        val current = account
        mutableState.value =
            when {
                loading -> {
                    ProfileUiState.Loading
                }

                loadFailed -> {
                    ProfileUiState.Failure
                }

                current == null || !current.isActive -> {
                    ProfileUiState.Unavailable
                }

                else -> {
                    ProfileUiState.Content(
                        account = current,
                        personalDraft = personalDraft,
                        contactDraft = contactDraft,
                        operation = operation,
                        nameError = nameError,
                        emailError = emailError,
                        phoneError = phoneError,
                        saveFailed = saveFailed,
                        nameValidationPending = parseName == null,
                        contactValidationPending = parseEmail == null || parsePhone == null,
                        completedEditor = completion.value,
                    )
                }
            }
    }
}
