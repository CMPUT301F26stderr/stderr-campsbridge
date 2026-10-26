package io.github.CMPUT301F26stderr.campsbridge.ui.campsites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageSlotController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns the owner's inventory tab and reads, separately from setup or write operations.
 * @param ownerId Active owner.
 * @param campsites Listing read dependency.
 * @param images Attachment dependency.
 * @param savedState Small tab restoration state.
 * @param sortCampsites Reserved F11 helper; absent until reviewed.
 */
class CampsiteListViewModel(
    private val ownerId: String,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val sortCampsites: ((List<Campsite>) -> List<Campsite>)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<CampsiteListUiState>(CampsiteListUiState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableTab =
        MutableStateFlow(
            CampsiteListTab.entries.find { it.name == savedState.get<String>("campsiteTab") }
                ?: CampsiteListTab.PUBLISHED,
        )
    val tab = mutableTab.asStateFlow()
    private var records: List<Campsite>? = null
    private var failed = false
    private var observation: Job? = null
    private var visibleIds = emptySet<String>()
    private val photos = mutableMapOf<String, ImageSlotController>()
    private val photoJobs = mutableMapOf<String, Job>()

    init {
        observe()
    }

    /** Selects an inventory category.
     * @param tab Requested category.
     */
    fun selectTab(tab: CampsiteListTab) {
        if (tab == this.tab.value) return
        mutableTab.value = tab
        savedState["campsiteTab"] = tab.name
        visibleIds = emptySet()
        publish()
    }

    /** Retries only the failed listing source. */
    fun retry() {
        if (failed) observe()
    }

    /** Reports rows requiring thumbnail reads.
     * @param ids Visible stable listing identities.
     */
    fun setVisibleCampsites(ids: Set<String>) {
        if (ids == visibleIds) return
        visibleIds = ids.toSet()
        publish()
    }

    /** Replaces the owner source; failure is not a confirmed empty inventory. */
    private fun observe() {
        observation?.cancel()
        records = null
        failed = false
        publish()
        observation =
            viewModelScope.launch {
                try {
                    campsites.observeForOwner(ownerId).collect { rows ->
                        records = rows.filter { it.ownerId == ownerId && it.removal == null }
                        publish()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                    records = null
                    publish()
                }
            }
    }

    /** Assembles rows without replacing the reserved ordering implementation. */
    private fun publish() {
        val rows =
            records?.filter {
                (it.publishedAt != null) == (tab.value == CampsiteListTab.PUBLISHED)
            }
        reconcilePhotos(rows.orEmpty().map { it.id }.toSet() intersect visibleIds)
        mutableState.value =
            when {
                failed -> {
                    CampsiteListUiState.Failure
                }

                rows == null -> {
                    CampsiteListUiState.Loading
                }

                else -> {
                    CampsiteListUiState.Content(
                        sortCampsites?.invoke(rows) ?: rows,
                        orderingPending = sortCampsites == null,
                        photos = photos.mapValues { it.value.state.value },
                    )
                }
            }
    }

    /** Keeps thumbnail reads bounded to the visible selected category.
     * @param ids Currently permitted visible records.
     */
    private fun reconcilePhotos(ids: Set<String>) {
        for (id in photos.keys.toList()) {
            if (id !in ids) {
                photoJobs.remove(id)?.cancel()
                photos.remove(id)?.close()
            }
        }
        for (id in ids) {
            if (id !in photos) {
                val photo =
                    ImageSlotController(
                        images,
                        ImageTarget(ImageTargetType.CAMPSITE, id),
                        viewModelScope,
                    )
                photos[id] = photo
                photoJobs[id] =
                    viewModelScope.launch {
                        photo.state.collect { publish() }
                    }
            }
        }
    }

    /** Releases all feature-owned subscriptions, including the image controllers. */
    override fun onCleared() {
        observation?.cancel()
        photoJobs.values.forEach { it.cancel() }
        photos.values.forEach { it.close() }
        super.onCleared()
    }
}
