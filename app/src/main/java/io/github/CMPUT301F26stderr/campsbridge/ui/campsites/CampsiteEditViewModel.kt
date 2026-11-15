package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.validateCampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val EDIT_KEY = "campsiteEdit."

/** Edits one published listing; never creates, publishes, or changes inventory or bookings.
 * @param ownerId Active owner identity.
 * @param campsiteId Immutable saved listing identity.
 * @param campsites Existing listing operations.
 * @param images Independent attachment operations.
 * @param savedState Primitive raw input retained across recreation.
 */
class CampsiteEditViewModel(
    private val ownerId: String,
    private val campsiteId: String,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CampsiteEditUiState>(CampsiteEditUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutablePhoto = MutableStateFlow(ImageSlotState())
    val photoState = mutablePhoto.asStateFlow()
    private val mutableDiscard = MutableStateFlow(false)
    val discardPrompt = mutableDiscard.asStateFlow()
    private val mutableExit = MutableStateFlow(false)
    val exitRequested = mutableExit.asStateFlow()
    private val mutableSaved = MutableStateFlow(false)
    val saveConfirmed = mutableSaved.asStateFlow()
    private var baseline: CampsiteDraft? = null
    private var loadJob: Job? = null
    private var photo: ImageSlotController? = null

    init {
        retry()
    }

    /** Retries only a required source failure, retaining restored raw input. */
    fun retry() {
        if (state.value is CampsiteEditUiState.Content || loadJob?.isActive == true) return
        mutableState.value = CampsiteEditUiState.Loading
        loadJob =
            viewModelScope.launch {
                try {
                    val site = campsites.get(campsiteId)
                    currentCoroutineContext().ensureActive()
                    if (!editable(site)) {
                        mutableState.value = CampsiteEditUiState.Unavailable
                        return@launch
                    }
                    val initial = checkNotNull(site).toDraft()
                    baseline = initial
                    val draft = restore(initial)
                    mutableState.value = CampsiteEditUiState.Content(draft, isDirty = draft != initial)
                    if (photo == null) {
                        val controller =
                            ImageSlotController(
                                images,
                                ImageTarget(ImageTargetType.CAMPSITE, campsiteId),
                                viewModelScope,
                            )
                        photo = controller
                        viewModelScope.launch { controller.state.collect { mutablePhoto.value = it } }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = CampsiteEditUiState.Failure
                }
            }
    }

    /** Uses exactly the setup validator and updates only the immutable saved target. */
    fun save() {
        val current = content()?.takeIf { !it.isSaving && it.isDirty && !discardPrompt.value } ?: return
        val validated = validateCampsiteDetails(current.draft)
        if (validated is CampsiteDetailsInput.Errors) {
            mutableState.value = current.copy(errors = validated)
            return
        }
        mutableSaved.value = false
        mutableState.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                val site = campsites.get(campsiteId)
                if (!editable(site)) {
                    mutableState.value = CampsiteEditUiState.Unavailable
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                campsites.update(campsiteId, ownerId, (validated as CampsiteDetailsInput.Value).details)
                currentCoroutineContext().ensureActive()
                val confirmed = checkNotNull(site).copy(details = validated.details).toDraft()
                baseline = confirmed
                mutableState.value = CampsiteEditUiState.Content(confirmed)
                mutableSaved.value = true
                clearDraft()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = current.copy(saveFailed = true)
            }
        }
    }

    fun acknowledgeSave() {
        mutableSaved.value = false
    }

    fun editName(value: String) =
        edit { copy(name = value) }

    fun editDescription(value: String) =
        edit { copy(description = value) }

    fun editLocationLabel(value: String) =
        edit { copy(locationLabel = value) }

    fun editTimeZone(value: String) =
        edit { copy(timeZone = value) }

    fun selectLocation(value: Coordinates) =
        edit { copy(location = value) }

    fun editCapacity(value: String) =
        edit { copy(maxGuestsText = value) }

    fun editPrice(value: String) =
        edit { copy(priceText = value) }

    fun editRules(value: String) =
        edit { copy(rules = value) }

    fun setAmenity(
        id: String,
        selected: Boolean,
    ) =
        edit {
            copy(amenities = if (selected) amenities + id else amenities - id)
        }

    fun setTag(
        id: String,
        selected: Boolean,
    ) =
        edit {
            copy(tags = if (selected) tags + id else tags - id)
        }

    fun selectPhoto(value: SelectedPhoto?) {
        if (canEdit()) photo?.select(value)
    }

    fun photoSelectionFailed() {
        if (canEdit()) photo?.selectionFailed()
    }

    fun savePhoto() {
        if (canEdit()) photo?.save()
    }

    fun removePhoto() {
        if (canEdit()) photo?.remove()
    }

    fun discardPhoto() {
        if (canEdit()) photo?.discard()
    }

    fun retryPhoto() {
        if (canEdit()) photo?.retry()
    }

    /** Back never abandons unsaved input or an unresolved image publication. */
    fun requestExit() {
        if (content()?.isSaving == true || !currentPhoto().canChoose) return
        if (content()?.isDirty == true || currentPhoto().candidate != null ||
            savedState.get<Boolean>("${EDIT_KEY}present") == true
        ) {
            mutableDiscard.value = true
        } else {
            exit()
        }
    }

    fun dismissDiscard() {
        mutableDiscard.value = false
    }

    fun confirmDiscard() {
        if (discardPrompt.value && currentPhoto().canChoose && content()?.isSaving != true) exit()
    }

    fun acknowledgeExit() {
        mutableExit.value = false
    }

    private fun editable(site: Campsite?): Boolean =
        site != null && site.id == campsiteId && site.ownerId == ownerId && site.isPublished

    private fun currentPhoto() =
        photo?.state?.value ?: photoState.value

    private fun content() =
        state.value as? CampsiteEditUiState.Content

    private fun canEdit() =
        content()?.isSaving == false && !discardPrompt.value && !exitRequested.value

    private fun edit(change: CampsiteDraft.() -> CampsiteDraft) {
        if (!canEdit()) return
        val current = checkNotNull(content())
        val draft = current.draft.change()
        persist(draft)
        mutableSaved.value = false
        mutableState.value =
            current.copy(
                draft = draft,
                errors = CampsiteDetailsInput.Errors(),
                isDirty = draft != baseline,
                saveFailed = false,
            )
    }

    /** Persist only small primitives; selected photo streams remain owned by the live controller. */
    private fun persist(draft: CampsiteDraft) {
        savedState["${EDIT_KEY}present"] = draft != baseline
        savedState["${EDIT_KEY}name"] = draft.name
        savedState["${EDIT_KEY}description"] = draft.description
        savedState["${EDIT_KEY}label"] = draft.locationLabel
        savedState["${EDIT_KEY}zone"] = draft.timeZone
        savedState["${EDIT_KEY}latitude"] = draft.location?.latitude
        savedState["${EDIT_KEY}longitude"] = draft.location?.longitude
        savedState["${EDIT_KEY}capacity"] = draft.maxGuestsText
        savedState["${EDIT_KEY}price"] = draft.priceText
        savedState["${EDIT_KEY}rules"] = draft.rules
        savedState["${EDIT_KEY}amenities"] = ArrayList(draft.amenities)
        savedState["${EDIT_KEY}tags"] = ArrayList(draft.tags)
    }

    private fun restore(initial: CampsiteDraft): CampsiteDraft {
        if (savedState.get<Boolean>("${EDIT_KEY}present") != true) return initial
        val lat = savedState.get<Double>("${EDIT_KEY}latitude")
        val lon = savedState.get<Double>("${EDIT_KEY}longitude")
        return initial.copy(
            name = savedState.get<String>("${EDIT_KEY}name").orEmpty(),
            description = savedState.get<String>("${EDIT_KEY}description").orEmpty(),
            locationLabel = savedState.get<String>("${EDIT_KEY}label").orEmpty(),
            timeZone = savedState.get<String>("${EDIT_KEY}zone").orEmpty(),
            location = if (lat != null && lon != null) Coordinates(lat, lon) else null,
            maxGuestsText = savedState.get<String>("${EDIT_KEY}capacity").orEmpty(),
            priceText = savedState.get<String>("${EDIT_KEY}price").orEmpty(),
            rules = savedState.get<String>("${EDIT_KEY}rules").orEmpty(),
            amenities = savedState.get<ArrayList<String>>("${EDIT_KEY}amenities").orEmpty().toSet(),
            tags = savedState.get<ArrayList<String>>("${EDIT_KEY}tags").orEmpty().toSet(),
        )
    }

    private fun clearDraft() {
        savedState.keys().filter { it.startsWith(EDIT_KEY) }.forEach { savedState.remove<Any?>(it) }
    }

    private fun exit() {
        loadJob?.cancel()
        photo?.discard()
        clearDraft()
        mutableDiscard.value = false
        mutableExit.value = true
    }

    override fun onCleared() {
        photo?.close()
    }
}
