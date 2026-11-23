/*
 * Owns raw saved-listing details and independent photo actions within the edit graph.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.validateCampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns raw saved-listing details and independent photo actions within the edit graph.
 * @param campsiteId Existing listing identity, retained for every update attempt.
 * @param ownerId Acting owner of the saved listing.
 * @param campsites Saved listing reads and explicit updates.
 * @param images Independent attachment operations.
 * @param savedState Primitive raw input for this editor graph.
 */
class CampsiteEditViewModel(
    val campsiteId: String,
    private val ownerId: String,
    private val campsites: CampsiteRepository,
    images: ImageRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CampsiteEditUiState>(CampsiteEditUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableCompletion = MutableStateFlow<String?>(null)
    val completion = mutableCompletion.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    val photo = ImageSlotController(images, ImageTarget(ImageTargetType.CAMPSITE, campsiteId), viewModelScope)
    private var reading: Job? = null
    private var baseline = CampsiteDraft(id = campsiteId)
    private var editor: CampsiteEditUiState.Content? = null

    init {
        retry()
    }

    /** Retries the required saved-listing source without creating a target. */
    fun retry() {
        reading?.cancel()
        mutableState.value = CampsiteEditUiState.Loading
        reading =
            viewModelScope.launch {
                try {
                    campsites.observe(campsiteId).collect { site ->
                        if (site == null || site.id != campsiteId || site.ownerId != ownerId || !site.isPublished) {
                            mutableState.value = CampsiteEditUiState.Unavailable
                        } else {
                            val confirmed = campsiteDraft(site)
                            val previous = editor
                            if (previous?.isDirty != true) baseline = confirmed
                            val draft =
                                when {
                                    previous?.isDirty == true -> previous.draft

                                    savedState.get<Boolean>(
                                        "${KEY}dirty",
                                    ) == true -> restoreCampsiteDetails(savedState, KEY, confirmed)

                                    else -> confirmed
                                }
                            publish((previous ?: CampsiteEditUiState.Content(draft)).copy(draft = draft))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = CampsiteEditUiState.Failure
                }
            }
    }

    /** Retains the exact local name.
     * @param value Raw name input.
     */
    fun editName(value: String) =
        change { it.copy(name = value) }

    /** Retains multiline description input.
     * @param value Raw description.
     */
    fun editDescription(value: String) =
        change { it.copy(description = value) }

    /** Retains raw capacity until explicit validation.
     * @param value Raw positive-count input.
     */
    fun editMaxGuests(value: String) =
        change { it.copy(maxGuestsText = value) }

    /** Retains the campsite-local timezone metadata.
     * @param value Raw IANA zone input.
     */
    fun editTimeZone(value: String) =
        change { it.copy(timeZone = value) }

    /** Retains a display label independently of the map pin.
     * @param value Raw location label.
     */
    fun editLocationLabel(value: String) =
        change { it.copy(locationLabel = value) }

    /** Applies only an explicitly confirmed working map pin.
     * @param value Selected geographic location.
     */
    fun applyLocation(value: Coordinates) =
        change { it.copy(location = value) }

    /** Retains the exact price spelling until validation.
     * @param value Raw CAD price.
     */
    fun editPrice(value: String) =
        change { it.copy(priceText = value) }

    /** Sets one local amenity without dropping unlisted saved choices.
     * @param id Stable amenity identity.
     * @param checked Requested checked state.
     */
    fun setAmenity(
        id: String,
        checked: Boolean,
    ) =
        change {
            it.copy(
                amenities =
                    if (checked) {
                        it.amenities + id
                    } else {
                        it.amenities -
                            id
                    },
            )
        }

    /** Sets one local tag without dropping unlisted saved choices.
     * @param id Stable tag identity.
     * @param checked Requested checked state.
     */
    fun setTag(
        id: String,
        checked: Boolean,
    ) =
        change { it.copy(tags = if (checked) it.tags + id else it.tags - id) }

    /** Retains multiline rules without publishing them before Save.
     * @param value Raw campsite rules.
     */
    fun editRules(value: String) =
        change { it.copy(rules = value) }

    /** Validates and updates this saved target only after an explicit action. */
    fun save() {
        val current = mutableState.value as? CampsiteEditUiState.Content ?: return
        if (current.isSaving) return
        val checked = validateCampsiteDetails(current.draft)
        if (checked is CampsiteDetailsInput.Errors) {
            publish(current.copy(errors = checked, saveFailed = false))
            return
        }
        mutableDiscard.value = false
        publish(current.copy(isSaving = true, saveFailed = false))
        viewModelScope.launch {
            try {
                campsites.update(campsiteId, ownerId, (checked as CampsiteDetailsInput.Value).details)
                currentCoroutineContext().ensureActive()
                baseline = current.draft
                val latest = editor ?: current
                val completed = latest.copy(isSaving = false, saveFailed = false)
                if (mutableState.value is CampsiteEditUiState.Content) publish(completed) else editor = completed
                mutableCompletion.value = campsiteId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val failed = (editor ?: current).copy(isSaving = false, saveFailed = true)
                if (mutableState.value is CampsiteEditUiState.Content) publish(failed) else editor = failed
            } finally {
                if (viewModelScope.isActive && editor?.isSaving == true) {
                    val idle = checkNotNull(editor).copy(isSaving = false)
                    if (mutableState.value is CampsiteEditUiState.Content) publish(idle) else editor = idle
                }
            }
        }
    }

    /** Consumes a confirmed navigation result once. */
    fun acknowledgeCompletion() {
        mutableCompletion.value = null
    }

    /** Leaves immediately only when no unsaved details or pending update would be abandoned. */
    fun requestExit() {
        if (editor?.isSaving == true || !photo.state.value.canChoose) return
        if (editor?.isDirty == true || photo.state.value.candidate != null) {
            mutableDiscard.value = true
        } else {
            mutableExit.value = true
        }
    }

    /** Keeps the retained form when discard confirmation is dismissed. */
    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    /** Confirms abandoning local details without changing the saved listing. */
    fun confirmDiscard() {
        if (editor?.isSaving == true || !photo.state.value.canChoose) return
        photo.discard()
        mutableDiscard.value = false
        mutableExit.value = true
    }

    /** Consumes an explicit exit after the navigation owner has returned. */
    fun acknowledgeExit() {
        mutableExit.value = false
    }

    /** Releases the independent image controller with its parent editor. */
    override fun onCleared() {
        photo.close()
        super.onCleared()
    }

    /** Applies raw input only while this saved target is editable and idle.
     * @param transform Local field change.
     */
    private fun change(transform: (CampsiteDraft) -> CampsiteDraft) {
        val current = mutableState.value as? CampsiteEditUiState.Content ?: return
        if (current.isSaving) return
        publish(
            current.copy(draft = transform(current.draft), errors = CampsiteDetailsInput.Errors(), saveFailed = false),
        )
    }

    /** Retains primitive input and publishes one immutable editing state.
     * @param content Latest local details and operation outcome.
     */
    private fun publish(content: CampsiteEditUiState.Content) {
        val next = content.copy(isDirty = content.draft != baseline)
        savedState["${KEY}present"] = true
        savedState["${KEY}dirty"] = next.isDirty
        retainCampsiteDetails(savedState, KEY, next.draft)
        editor = next
        mutableState.value = next
    }

    /** Keys owned by this saved-listing editor. */
    private companion object {
        const val KEY = "campsite-edit:"
    }
}
