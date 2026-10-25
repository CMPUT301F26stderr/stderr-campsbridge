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
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val SETUP_KEY = "campsiteSetup."

/** Owns local setup input and saved-parent readers; explicit persistence stages are pending.
 * @param ownerId Active owner identity.
 * @param campsiteId Existing unpublished target, or null for a new local form.
 * @param campsites Saved-target and inventory reader.
 * @param images Saved attachment reader.
 * @param clock Deterministic initial calendar month.
 * @param savedState Small editable restoration state.
 */
class CampsiteSetupViewModel(
    private val ownerId: String,
    private val campsiteId: String?,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
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
    private var calendarJob: Job? = null
    private var photoController: ImageSlotController? = null

    init {
        if (completion.value == null && !exitRequested.value) load()
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
        val current = editable() ?: return
        if (month == current.month) return
        publish(current.copy(month = month))
        observeCalendar()
    }

    /** Toggles a known unoccupied date's desired offered state without writing it.
     * @param date Date the owner edited.
     */
    fun toggleDate(date: LocalDate) {
        val current = editable() ?: return
        val inventory = current.inventory as? CampsiteInventoryUiState.Content ?: return
        if (current.draft.id != null && YearMonth.from(date) != current.month) return
        val night = inventory.nights.find { it.date == date }
        if (night?.reservationId != null) return
        val offered = current.draft.requestedAvailability[date] ?: night?.isOffered ?: false
        edit { it.copy(requestedAvailability = it.requestedAvailability + (date to !offered)) }
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
        if (editable()?.inventory == CampsiteInventoryUiState.Failure) observeCalendar()
    }

    /** Retries saved attachment display without uploading staged content. */
    fun retryPhoto() {
        photoController?.retry()
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
        photoController?.close()
        super.onCleared()
    }

    /** Returns editable input only while no navigation result is awaiting consumption. */
    private fun editable(): CampsiteSetupUiState.Content? =
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
        mutableState.value = CampsiteSetupUiState.Loading
        readJob?.cancel()
        readJob =
            viewModelScope.launch {
                try {
                    val targetId = campsiteId ?: savedState.get<String>("${SETUP_KEY}id")
                    val site = targetId?.let { campsites.get(it) }
                    currentCoroutineContext().ensureActive()
                    if (targetId != null && (
                            site == null || site.id != targetId ||
                                site.ownerId != ownerId || site.removal != null
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
                    baseline = site?.let(::draftFor) ?: CampsiteDraft()
                    val draft = restoreDraft(baseline)
                    val zone =
                        draft.timeZone
                            .trim()
                            .takeIf { it in ZoneId.getAvailableZoneIds() }
                            ?.let(ZoneId::of) ?: clock.zone
                    val month =
                        savedState.get<String>("${SETUP_KEY}month")?.let(YearMonth::parse)
                            ?: YearMonth.now(clock.withZone(zone))
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
                            month = month,
                            photoIntent =
                                if (photo ==
                                    "remove"
                                ) {
                                    CampsitePhotoIntent.Remove
                                } else {
                                    CampsitePhotoIntent.Keep
                                },
                            photoReselectionRequired = photo == "replace",
                        ),
                    )
                    if (site != null) observePhoto(site.id)
                    observeCalendar()
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
        val controller =
            ImageSlotController(images, ImageTarget(ImageTargetType.CAMPSITE, id), viewModelScope)
        photoController = controller
        viewModelScope.launch {
            controller.state.collect { photo ->
                editable()?.let { mutableState.value = it.copy(savedPhoto = photo) }
            }
        }
    }

    /** Replaces one range reader; only successfully loaded absence means unoffered inventory. */
    private fun observeCalendar() {
        val current = editable() ?: return
        calendarJob?.cancel()
        if (current.draft.id == null) {
            mutableState.value =
                current.copy(inventory = CampsiteInventoryUiState.Content(emptyList()))
            return
        }
        val month = current.month
        mutableState.value = current.copy(inventory = CampsiteInventoryUiState.Loading)
        calendarJob =
            viewModelScope.launch {
                try {
                    campsites
                        .observeCalendar(
                            current.draft.id,
                            month.atDay(1),
                            month.plusMonths(1).atDay(1),
                        ).collect { nights ->
                            currentCoroutineContext().ensureActive()
                            editable()?.takeIf { it.month == month }?.let {
                                mutableState.value =
                                    it.copy(inventory = CampsiteInventoryUiState.Content(nights))
                            }
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    editable()?.takeIf { it.month == month }?.let {
                        mutableState.value = it.copy(inventory = CampsiteInventoryUiState.Failure)
                    }
                }
            }
    }

    /** Saves small primitive inputs; selected bytes/controllers and confirmed records stay transient.
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
        savedState["${SETUP_KEY}dates"] =
            ArrayList(draft.requestedAvailability.keys.map(LocalDate::toString))
        savedState["${SETUP_KEY}offered"] = draft.requestedAvailability.values.toBooleanArray()
        savedState["${SETUP_KEY}review"] = draft.returnToReview
        savedState["${SETUP_KEY}month"] = content.month.toString()
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
        val dates = savedState.get<ArrayList<String>>("${SETUP_KEY}dates").orEmpty()
        val offered = savedState.get<BooleanArray>("${SETUP_KEY}offered") ?: booleanArrayOf()
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
            requestedAvailability =
                dates.zip(offered.toList()).associate { (date, value) ->
                    LocalDate.parse(date) to
                        value
                },
            returnToReview = savedState.get<Boolean>("${SETUP_KEY}review") == true,
        )
    }

    /** Clears abandoned input and emits an acknowledged navigation result, without backend
     * writes.
     */
    private fun exit() {
        readJob?.cancel()
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
