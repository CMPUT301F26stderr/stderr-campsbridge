package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Coordinates
import io.github.CMPUT301F26stderr.campsbridge.platform.images.SelectedPhoto
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.CampsiteDetailsInput
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.logic.validateCampsiteDetails
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageOperation
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

private const val SETUP_KEY = "campsiteSetup."

/** Owns setup input, saved-parent readers and explicit details/photo/inventory draft saves.
 * @param ownerId Active owner identity.
 * @param campsiteId Existing unpublished target, or null for a new local form.
 * @param campsites Saved-target, details and inventory operations.
 * @param images Saved attachment display and explicit publication/removal.
 * @param clock Deterministic initial calendar month.
 * @param savedState Small editable restoration state.
 * @param newId Allocates one stable identity only after the first valid save action.
 * @param remainingAvailabilityEdits Reserved F08 comparison; null blocks requested inventory saves.
 */
class CampsiteSetupViewModel(
    private val ownerId: String,
    private val campsiteId: String?,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val remainingAvailabilityEdits: AvailabilityDifference? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CampsiteSetupUiState>(CampsiteSetupUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableCompletion =
        MutableStateFlow(savedState.get<String>("${SETUP_KEY}completion"))
    val completion = mutableCompletion.asStateFlow()
    private val mutableExit = MutableStateFlow(savedState.get<Boolean>("${SETUP_KEY}exit") == true)
    val exitRequested = mutableExit.asStateFlow()
    private var baseline = CampsiteDraft()
    private var readJob: Job? = null
    private var availability: CampsiteAvailabilityController? = null
    private var availabilityObservation: Job? = null
    private var photoController: ImageSlotController? = null
    private var photoObservation: Job? = null
    private var confirmedSite: Campsite? = null
    private var attemptedPhoto: CampsitePhotoIntent? = null

    init {
        if (completion.value == null && !exitRequested.value) load()
    }

    /** Saves a valid review using the retained identity; no write occurs from composition. */
    fun saveDraft() {
        beginSave(retrying = false)
    }

    /** Reconciles the same target before retrying an explicitly failed save. */
    fun retrySave() {
        if (content()?.saveFailure != null) beginSave(retrying = true)
    }

    /** Freezes valid review input and its identity before starting the named save stages.
     * @param retrying Whether the action is reconciling an earlier failed attempt.
     */
    private fun beginSave(retrying: Boolean) {
        val current = content()?.takeIf { it.savePhase == CampsiteSavePhase.IDLE } ?: return
        if (current.draft.step != CampsiteStep.REVIEW || current.discardPrompt) return
        val validated = validateCampsiteDetails(current.draft)
        if (validated is CampsiteDetailsInput.Errors) {
            publish(current.copy(errors = validated))
            return
        }
        val blocked =
            when {
                current.photoReselectionRequired -> {
                    CampsiteSaveFailure.PHOTO_RESELECTION
                }

                current.draft.requestedAvailability.isNotEmpty() &&
                    current.availabilityComparisonPending -> {
                    CampsiteSaveFailure.AVAILABILITY_PENDING
                }

                else -> {
                    null
                }
            }
        if (blocked != null) {
            publish(current.copy(saveFailure = blocked))
            return
        }
        val details = (validated as CampsiteDetailsInput.Value).details
        val id = current.draft.id ?: newId()
        savedState["${SETUP_KEY}attempt"] = true
        publish(
            current.copy(
                draft = current.draft.copy(id = id),
                savePhase =
                    if (retrying) {
                        CampsiteSavePhase.RECOVERING
                    } else {
                        CampsiteSavePhase.SAVING_DETAILS
                    },
                saveFailure = null,
            ),
        )
        viewModelScope.launch { saveDraftStages(id, details, current.photoIntent) }
    }

    /** Reconciles the same server target, saves details/photo, then applies requested inventory work.
     * @param id Retained identity, allocated before any suspension.
     * @param details Frozen valid listing values.
     * @param photo Frozen local attachment intent.
     */
    private suspend fun saveDraftStages(
        id: String,
        details: CampsiteDetails,
        photo: CampsitePhotoIntent,
    ) {
        var failedStage = CampsiteSaveFailure.RECOVERY
        try {
            val existing = campsites.get(id)
            currentCoroutineContext().ensureActive()
            check(existing != null || (campsiteId == null && confirmedSite == null)) {
                "Saved target no longer exists"
            }
            failedStage = CampsiteSaveFailure.DETAILS
            content()?.let { publish(it.copy(savePhase = CampsiteSavePhase.SAVING_DETAILS)) }
            val saved = existing ?: campsites.createDraft(id, ownerId, details)
            currentCoroutineContext().ensureActive()
            check(saved.id == id && saved.ownerId == ownerId && saved.removal == null) {
                "Target is unavailable for this owner"
            }
            if (saved.isPublished) {
                completeSave(saved.id)
                return
            }
            confirmedSite = saved
            if (saved.details != details) {
                campsites.update(id, ownerId, details)
                currentCoroutineContext().ensureActive()
                confirmedSite = saved.copy(details = details)
            }
            availability?.bindSavedTarget(id)
            if (photoController == null) observePhoto(id)
            failedStage = CampsiteSaveFailure.PHOTO
            if (photo != CampsitePhotoIntent.Keep ||
                photoController?.state?.value?.unconfirmedBlobId != null
            ) {
                content()?.let { publish(it.copy(savePhase = CampsiteSavePhase.SAVING_PHOTO)) }
                savePhoto(photo)
            }
            val calendar = checkNotNull(availability)
            if (calendar.state.value.requested
                    .isNotEmpty()
            ) {
                failedStage = CampsiteSaveFailure.AVAILABILITY
                content()?.let {
                    publish(
                        it.copy(savePhase = CampsiteSavePhase.SAVING_AVAILABILITY),
                    )
                }
                check(calendar.save()) { "Requested inventory needs explicit retry" }
                reflectAvailability(calendar.state.value)
            }
            currentCoroutineContext().ensureActive()
            completeSave(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            content()?.let {
                publish(
                    it.copy(savePhase = CampsiteSavePhase.IDLE, saveFailure = failedStage),
                )
            }
        }
    }

    /** Uses the shared slot owner for explicit publication/removal and its recovery state.
     * @param intent Frozen attachment choice; Keep can still require resolving a prior publication.
     */
    private suspend fun savePhoto(intent: CampsitePhotoIntent) {
        val controller = checkNotNull(photoController)
        if (controller.state.value.loadState == ImageLoadState.FAILED) controller.retryDisplay()
        val ready = controller.state.first { it.loadState != ImageLoadState.LOADING }
        check(ready.loadState == ImageLoadState.READY) { "Attachment read failed" }
        if (ready.unconfirmedBlobId != null) {
            controller.retry()
        } else if (attemptedPhoto == intent && ready.candidate == null && ready.failure == null) {
            return
        } else {
            when (intent) {
                CampsitePhotoIntent.Keep -> {
                    controller.discard()
                }

                CampsitePhotoIntent.Remove -> {
                    controller.discard()
                    attemptedPhoto = intent
                    content()?.let { publish(it.copy(photoRemovalPending = true)) }
                    controller.clearAttachment()
                }

                is CampsitePhotoIntent.Replace -> {
                    if (attemptedPhoto != intent) controller.select(intent.photo)
                    attemptedPhoto = intent
                    controller.save()
                }
            }
        }
        val result = controller.state.first { it.operation == ImageOperation.IDLE }
        check(
            result.unconfirmedBlobId == null && result.failure == null && result.candidate == null,
        ) {
            "Attachment work requires explicit retry"
        }
        content()?.let {
            publish(it.copy(photoIntent = CampsitePhotoIntent.Keep, photoRemovalPending = false))
        }
    }

    /** Publishes only the confirmed saved identity, independently of later source failures.
     * @param id Actual completed target.
     */
    private fun completeSave(id: String) {
        savedState.remove<Boolean>("${SETUP_KEY}attempt")
        content()?.let { publish(it.copy(savePhase = CampsiteSavePhase.IDLE, saveFailure = null)) }
        savedState["${SETUP_KEY}completion"] = id
        mutableCompletion.value = id
    }

    /** Changes local name input.
     * @param value Raw field input.
     */
    fun editName(value: String) =
        edit { it.copy(name = value) }

    /** Changes optional description.
     * @param value Raw multiline input.
     */
    fun editDescription(value: String) =
        edit { it.copy(description = value) }

    /** Changes optional location label.
     * @param value Raw field input.
     */
    fun editLocationLabel(value: String) =
        edit { it.copy(locationLabel = value) }

    /** Changes the IANA zone input without silently converting local dates.
     * @param value Raw field input.
     */
    fun editTimeZone(value: String) =
        edit { it.copy(timeZone = value) }

    /** Applies an explicitly confirmed map choice.
     * @param value Actual selected coordinate.
     */
    fun selectLocation(value: Coordinates) =
        edit { it.copy(location = value) }

    /** Changes raw guest capacity.
     * @param value Raw count input.
     */
    fun editCapacity(value: String) =
        edit { it.copy(maxGuestsText = value) }

    /** Changes raw nightly CAD amount.
     * @param value Exact decimal input.
     */
    fun editPrice(value: String) =
        edit { it.copy(priceText = value) }

    /** Changes optional multiline rules.
     * @param value Raw field input.
     */
    fun editRules(value: String) =
        edit { it.copy(rules = value) }

    /** Changes one amenity without replacing unknown saved IDs.
     * @param id Stable option ID.
     * @param selected Desired selection.
     */
    fun setAmenity(
        id: String,
        selected: Boolean,
    ) =
        edit {
            it.copy(amenities = if (selected) it.amenities + id else it.amenities - id)
        }

    /** Changes one tag without replacing unknown saved IDs.
     * @param id Stable option ID.
     * @param selected Desired selection.
     */
    fun setTag(
        id: String,
        selected: Boolean,
    ) =
        edit {
            it.copy(tags = if (selected) it.tags + id else it.tags - id)
        }

    /** Advances only after validating this step's required fields through the shared validator. */
    fun next() {
        val current = editable() ?: return
        val allErrors =
            validateCampsiteDetails(current.draft) as? CampsiteDetailsInput.Errors
                ?: CampsiteDetailsInput.Errors()
        val errors =
            when (current.draft.step) {
                CampsiteStep.BASICS -> {
                    CampsiteDetailsInput.Errors(nameRequired = allErrors.nameRequired)
                }

                CampsiteStep.LOCATION -> {
                    CampsiteDetailsInput.Errors(
                        locationRequired = allErrors.locationRequired,
                        timeZoneInvalid = allErrors.timeZoneInvalid,
                    )
                }

                CampsiteStep.CAPACITY_PRICE -> {
                    CampsiteDetailsInput.Errors(
                        capacity = allErrors.capacity,
                        price = allErrors.price,
                    )
                }

                else -> {
                    CampsiteDetailsInput.Errors()
                }
            }
        if (errors != CampsiteDetailsInput.Errors()) {
            publish(current.copy(errors = errors))
            return
        }
        if (current.draft.step == CampsiteStep.REVIEW) return
        val step =
            if (current.draft.returnToReview) {
                CampsiteStep.REVIEW
            } else {
                CampsiteStep.entries[current.draft.step.ordinal + 1]
            }
        edit { it.copy(step = step, returnToReview = false) }
    }

    /** Returns within the draft, requesting explicit abandonment from its first step. */
    fun back() {
        val current = editable() ?: return
        if (current.draft.step == CampsiteStep.BASICS && !current.draft.returnToReview) {
            requestExit()
        } else {
            edit {
                it.copy(
                    step =
                        if (it.returnToReview) {
                            CampsiteStep.REVIEW
                        } else {
                            CampsiteStep.entries[it.step.ordinal - 1]
                        },
                    returnToReview = false,
                )
            }
        }
    }

    /** Opens a section for direct return to review after valid advancement.
     * @param step Section to edit.
     */
    fun editStep(step: CampsiteStep) {
        if (step == CampsiteStep.REVIEW) return
        val current = editable() ?: return
        if (current.draft.step != CampsiteStep.REVIEW) return
        edit { it.copy(step = step, returnToReview = true) }
    }

    /** Requests exit, asking before abandoning changed input or staged photo intent. */
    fun requestExit() {
        if ((state.value as? CampsiteSetupUiState.Content)?.let {
                it.savePhase != CampsiteSavePhase.IDLE || it.photoNeedsRecovery
            } == true
        ) {
            return
        }
        if (completion.value != null || exitRequested.value) return
        if (state.value is CampsiteSetupUiState.ConfirmDiscard) return
        val current = editable()
        if (current == null) {
            if (savedState.get<Boolean>("${SETUP_KEY}changed") == true) {
                readJob?.cancel()
                mutableState.value = CampsiteSetupUiState.ConfirmDiscard(state.value)
            } else {
                exit()
            }
            return
        }
        if (hasChanges(current)) publish(current.copy(discardPrompt = true)) else exit()
    }

    /** Keeps the current input after dismissing the discard confirmation. */
    fun dismissDiscard() {
        val pending = state.value as? CampsiteSetupUiState.ConfirmDiscard
        if (pending != null) {
            mutableState.value = pending.previous
            if (pending.previous == CampsiteSetupUiState.Loading) load()
            return
        }
        editable()?.let { publish(it.copy(discardPrompt = false)) }
    }

    /** Discards local saved input only after the visible confirmation is accepted. */
    fun confirmDiscard() {
        if (editable()?.discardPrompt == true ||
            state.value is CampsiteSetupUiState.ConfirmDiscard
        ) {
            exit()
        }
    }

    /** Changes the visible month while preserving all local date intentions.
     * @param month Requested month.
     */
    fun changeMonth(month: YearMonth) {
        if (editable() == null) return
        availability?.let {
            it.changeMonth(month)
            reflectAvailability(it.state.value)
        }
    }

    /** Toggles a known unoccupied date's desired value through the shared availability owner.
     * @param date Date the owner edited.
     */
    fun toggleDate(date: LocalDate) {
        if (editable() == null) return
        availability?.let {
            it.toggleDate(date)
            reflectAvailability(it.state.value)
        }
    }

    /** Stages replacement; picker cancellation retains the previous choice.
     * @param photo Selected local content, or null for cancellation.
     */
    fun selectPhoto(photo: SelectedPhoto?) {
        if (photo == null) return
        editable()?.takeIf { it.draft.step == CampsiteStep.PHOTO && !it.discardPrompt }?.let {
            publish(
                it.copy(
                    photoIntent = CampsitePhotoIntent.Replace(photo),
                    photoReselectionRequired = false,
                    photoSelectionFailed = false,
                ),
            )
        }
    }

    /** Retains the saved attachment instead of any unsaved replacement/removal. */
    fun discardPhoto() {
        editable()?.let {
            publish(
                it.copy(
                    photoIntent = CampsitePhotoIntent.Keep,
                    photoReselectionRequired = false,
                    photoSelectionFailed = false,
                ),
            )
        }
    }

    /** Stages explicit removal without deleting Storage bytes or attachment metadata. */
    fun removePhoto() {
        editable()?.let {
            publish(
                it.copy(
                    photoIntent = CampsitePhotoIntent.Remove,
                    photoReselectionRequired = false,
                    photoSelectionFailed = false,
                ),
            )
        }
    }

    /** Reports unreadable picker content without discarding earlier valid choices. */
    fun photoSelectionFailed() {
        editable()?.takeIf { it.draft.step == CampsiteStep.PHOTO && !it.discardPrompt }?.let {
            publish(it.copy(photoSelectionFailed = true))
        }
    }

    /** Retries only a failed existing-target lookup. */
    fun retry() {
        if (state.value == CampsiteSetupUiState.Failure) load()
    }

    /** Retries only failed visible-month inventory, retaining form input. */
    fun retryCalendar() {
        if (editable() == null) return
        availability?.let {
            it.retryCalendar()
            reflectAvailability(it.state.value)
        }
    }

    /** Retries saved attachment display without uploading staged content. */
    fun retryPhoto() {
        photoController?.retryDisplay()
    }

    /** Consumes a saved-ID redirect after actual navigation. */
    fun acknowledgeCompletion() {
        mutableCompletion.value = null
        savedState.remove<String>("${SETUP_KEY}completion")
    }

    /** Consumes confirmed exit after actual navigation. */
    fun acknowledgeExit() {
        mutableExit.value = false
        savedState.remove<Boolean>("${SETUP_KEY}exit")
    }

    /** Cancels readers when the graph is retired. */
    override fun onCleared() {
        availability?.close()
        photoController?.close()
        super.onCleared()
    }

    /** Returns editable input only while no navigation result is awaiting consumption. */
    private fun editable(): CampsiteSetupUiState.Content? =
        content()?.takeIf { it.savePhase == CampsiteSavePhase.IDLE && !it.photoNeedsRecovery }

    /** Returns displayable content, including a guarded in-flight operation. */
    private fun content(): CampsiteSetupUiState.Content? =
        if (completion.value == null && !exitRequested.value) {
            state.value as? CampsiteSetupUiState.Content
        } else {
            null
        }

    /** Replaces only local draft values and resets obsolete step feedback.
     * @param transform Field/step edit to apply to current input.
     */
    private fun edit(transform: (CampsiteDraft) -> CampsiteDraft) {
        editable()?.let {
            publish(it.copy(draft = transform(it.draft), errors = CampsiteDetailsInput.Errors()))
        }
    }

    /** Loads a saved target before restoring edits; a new local form needs no parent read. */
    private fun load() {
        availabilityObservation?.cancel()
        availability?.close()
        availability = null
        mutableState.value = CampsiteSetupUiState.Loading
        readJob?.cancel()
        readJob =
            viewModelScope.launch {
                try {
                    val targetId = campsiteId ?: savedState.get<String>("${SETUP_KEY}id")
                    val site = targetId?.let { campsites.get(it) }
                    currentCoroutineContext().ensureActive()
                    val unfinishedNew =
                        campsiteId == null &&
                            savedState.get<Boolean>("${SETUP_KEY}attempt") == true
                    if ((targetId != null && site == null && !unfinishedNew) || (
                            site != null && (
                                site.id != targetId ||
                                    site.ownerId != ownerId || site.removal != null
                            )
                        )
                    ) {
                        mutableState.value = CampsiteSetupUiState.Unavailable
                        return@launch
                    }
                    if (site?.isPublished == true) {
                        savedState["${SETUP_KEY}completion"] = site.id
                        mutableCompletion.value = site.id
                        mutableState.value = CampsiteSetupUiState.Unavailable
                        return@launch
                    }
                    confirmedSite = site
                    baseline = site?.let(::draftFor) ?: CampsiteDraft(id = targetId)
                    val restoredDraft = restoreDraft(baseline)
                    val zone =
                        restoredDraft.timeZone
                            .trim()
                            .takeIf { it in ZoneId.getAvailableZoneIds() }
                            ?.let(ZoneId::of) ?: clock.zone
                    val controller =
                        CampsiteAvailabilityController(
                            ownerId,
                            site?.id,
                            campsites,
                            YearMonth.now(clock.withZone(zone)),
                            savedState,
                            "${SETUP_KEY}availability.",
                            viewModelScope,
                            remainingAvailabilityEdits,
                        )
                    availability = controller
                    val calendar = controller.state.value
                    val draft = restoredDraft.copy(requestedAvailability = calendar.requested)
                    val photo = savedState.get<String>("${SETUP_KEY}photo")
                    publish(
                        CampsiteSetupUiState.Content(
                            draft = draft,
                            savedPhoto =
                                ImageSlotState(
                                    loadState =
                                        if (site ==
                                            null
                                        ) {
                                            ImageLoadState.READY
                                        } else {
                                            ImageLoadState.LOADING
                                        },
                                ),
                            month = calendar.month,
                            inventory = calendar.inventory,
                            availabilityComparisonPending = calendar.pendingEdits == null,
                            photoIntent =
                                if (photo ==
                                    "remove"
                                ) {
                                    CampsitePhotoIntent.Remove
                                } else {
                                    CampsitePhotoIntent.Keep
                                },
                            photoReselectionRequired = photo == "replace",
                            saveFailure =
                                if (savedState.get<Boolean>("${SETUP_KEY}attempt") ==
                                    true
                                ) {
                                    CampsiteSaveFailure.RECOVERY
                                } else {
                                    null
                                },
                            photoRemovalPending =
                                savedState.get<Boolean>("${SETUP_KEY}removingPhoto") == true,
                        ),
                    )
                    if (site != null) observePhoto(site.id)
                    observeAvailability(controller)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = CampsiteSetupUiState.Failure
                }
            }
    }

    /** Projects confirmed fields into initial editable spelling, retaining exact integer cents.
     * @param site Confirmed unpublished target.
     */
    private fun draftFor(site: Campsite): CampsiteDraft =
        with(site.details) {
            CampsiteDraft(
                id = site.id,
                name = name,
                description = description,
                location = location,
                locationLabel = locationLabel,
                timeZone = timeZone,
                maxGuestsText = maxGuests.toString(),
                priceText = BigDecimal.valueOf(nightlyPriceCents, 2).toPlainString(),
                amenities = amenities,
                tags = tags,
                rules = rules,
            )
        }

    /** Observes exactly one actual saved attachment without applying staged photo intent.
     * @param id Saved parent identity.
     */
    private fun observePhoto(id: String) {
        photoObservation?.cancel()
        photoController?.close()
        val controller =
            ImageSlotController(images, ImageTarget(ImageTargetType.CAMPSITE, id), viewModelScope)
        photoController = controller
        photoObservation =
            viewModelScope.launch {
                controller.state.collect { photo ->
                    content()?.let { mutableState.value = it.copy(savedPhoto = photo) }
                }
            }
    }

    /** Observes the shared range/edit owner without introducing another repository listener.
     * @param controller This setup graph's availability owner.
     */
    private fun observeAvailability(controller: CampsiteAvailabilityController) {
        availabilityObservation?.cancel()
        availabilityObservation =
            viewModelScope.launch {
                controller.state.collect { reflectAvailability(it) }
            }
    }

    /** Projects date state into the draft for display, retaining one authoritative persistence owner.
     * @param value Current availability state, including confirmed progress during a held save.
     */
    private fun reflectAvailability(value: CampsiteAvailabilityState) {
        content()?.let {
            publish(
                it.copy(
                    draft = it.draft.copy(requestedAvailability = value.requested),
                    month = value.month,
                    inventory = value.inventory,
                    availabilityComparisonPending = value.pendingEdits == null,
                ),
            )
        }
    }

    /** Saves primitive inputs; selected bytes/controllers and confirmed records stay transient.
     * @param content Latest local state.
     */
    private fun publish(content: CampsiteSetupUiState.Content) {
        val draft = content.draft
        savedState["${SETUP_KEY}present"] = true
        savedState["${SETUP_KEY}changed"] = hasChanges(content)
        savedState["${SETUP_KEY}id"] = draft.id
        savedState["${SETUP_KEY}step"] = draft.step.name
        savedState["${SETUP_KEY}name"] = draft.name
        savedState["${SETUP_KEY}description"] = draft.description
        savedState["${SETUP_KEY}latitude"] = draft.location?.latitude
        savedState["${SETUP_KEY}longitude"] = draft.location?.longitude
        savedState["${SETUP_KEY}label"] = draft.locationLabel
        savedState["${SETUP_KEY}zone"] = draft.timeZone
        savedState["${SETUP_KEY}capacity"] = draft.maxGuestsText
        savedState["${SETUP_KEY}price"] = draft.priceText
        savedState["${SETUP_KEY}amenities"] = ArrayList(draft.amenities)
        savedState["${SETUP_KEY}tags"] = ArrayList(draft.tags)
        savedState["${SETUP_KEY}rules"] = draft.rules
        savedState["${SETUP_KEY}review"] = draft.returnToReview
        savedState["${SETUP_KEY}removingPhoto"] = content.photoRemovalPending
        savedState["${SETUP_KEY}photo"] =
            when {
                content.photoIntent is CampsitePhotoIntent.Replace ||
                    content.photoReselectionRequired -> "replace"

                content.photoIntent == CampsitePhotoIntent.Remove -> "remove"

                else -> "keep"
            }
        mutableState.value = content
    }

    /** Ignores visual position when checking for abandoned input.
     * @param content Current local values and staged photo choice.
     */
    private fun hasChanges(content: CampsiteSetupUiState.Content): Boolean =
        content.draft.copy(step = baseline.step, returnToReview = false) != baseline ||
            content.photoIntent != CampsitePhotoIntent.Keep || content.photoReselectionRequired

    /** Restores only this graph's primitive editing values over the confirmed initial target.
     * @param initial Current saved target/default local input.
     */
    private fun restoreDraft(initial: CampsiteDraft): CampsiteDraft {
        if (savedState.get<Boolean>("${SETUP_KEY}present") != true) return initial
        val latitude = savedState.get<Double>("${SETUP_KEY}latitude")
        val longitude = savedState.get<Double>("${SETUP_KEY}longitude")
        return initial.copy(
            step =
                CampsiteStep.entries.find { it.name == savedState.get<String>("${SETUP_KEY}step") }
                    ?: CampsiteStep.BASICS,
            name = savedState.get<String>("${SETUP_KEY}name").orEmpty(),
            description = savedState.get<String>("${SETUP_KEY}description").orEmpty(),
            location =
                if (latitude != null &&
                    longitude != null
                ) {
                    Coordinates(latitude, longitude)
                } else {
                    null
                },
            locationLabel = savedState.get<String>("${SETUP_KEY}label").orEmpty(),
            timeZone = savedState.get<String>("${SETUP_KEY}zone").orEmpty(),
            maxGuestsText = savedState.get<String>("${SETUP_KEY}capacity").orEmpty(),
            priceText = savedState.get<String>("${SETUP_KEY}price").orEmpty(),
            amenities =
                savedState
                    .get<ArrayList<String>>(
                        "${SETUP_KEY}amenities",
                    ).orEmpty()
                    .toSet(),
            tags = savedState.get<ArrayList<String>>("${SETUP_KEY}tags").orEmpty().toSet(),
            rules = savedState.get<String>("${SETUP_KEY}rules").orEmpty(),
            returnToReview = savedState.get<Boolean>("${SETUP_KEY}review") == true,
        )
    }

    /** Clears abandoned input and emits an acknowledged navigation result, without backend
     * writes.
     */
    private fun exit() {
        readJob?.cancel()
        availabilityObservation?.cancel()
        availability?.close()
        savedState
            .keys()
            .filter {
                it.startsWith(
                    SETUP_KEY,
                )
            }.forEach { savedState.remove<Any?>(it) }
        savedState["${SETUP_KEY}exit"] = true
        mutableExit.value = true
    }
}
