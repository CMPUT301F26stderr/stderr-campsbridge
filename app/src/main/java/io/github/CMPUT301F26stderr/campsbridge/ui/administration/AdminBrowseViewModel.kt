package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.*
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.*
import io.github.CMPUT301F26stderr.campsbridge.domain.image.*
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.VisibleImageSlots
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

/** Parent graph owns browse inputs, the selected reader and visible thumbnails independently. */
class AdminBrowseViewModel(
    private val campsites: CampsiteRepository,
    private val accounts: AccountRepository,
    private val images: ImageRepository,
    private val saved: SavedStateHandle,
    private val matchesSite: ((Campsite, String) -> Boolean)? = null,
    private val filterProfiles: ((List<Account>, Role?) -> List<Account>)? = null,
    private val matchesProfile: ((Account, String) -> Boolean)? = null,
) : ViewModel() {
    private fun savedString(key: String) = saved.get<Any?>(key) as? String
    private var tab = AdminBrowseTab.entries.firstOrNull { it.name == savedString("adminBrowseTab") } ?: AdminBrowseTab.CAMPSITES
    private val queries = AdminBrowseTab.entries.associateWith { savedString("adminBrowseQuery$it").orEmpty() }.toMutableMap()
    private var role = Role.entries.firstOrNull { it.name == savedString("adminBrowseProfileRole") }
    private val mutableState = MutableStateFlow(AdminBrowseUiState(tab, queries[tab].orEmpty(), if (tab == AdminBrowseTab.PROFILES) role else null))
    val state = mutableState.asStateFlow()
    private val reader = AdminInspectionController(accounts, campsites, images, viewModelScope)
    val inspection = reader.state
    private val thumbnails = VisibleImageSlots(images, viewModelScope)
    val photos = thumbnails.state
    private var source: Job? = null
    private var generation = 0
    private var closed = false
    private var raw: AdminBrowseContent = AdminBrowseContent.Loading
    private var visible = emptySet<ImageTarget>()

    init { observe() }

    fun selectTab(value: AdminBrowseTab) {
        if (closed || tab == value) return
        tab = value
        saved["adminBrowseTab"] = value.name
        setVisibleTargets(emptySet())
        observe()
    }
    fun editQuery(value: String) {
        if (closed || queries[tab] == value) return
        queries[tab] = value
        saved["adminBrowseQuery$tab"] = value
        project()
    }
    fun selectRole(value: Role?) {
        if (closed || tab != AdminBrowseTab.PROFILES || role == value) return
        role = value
        saved["adminBrowseProfileRole"] = value?.name.orEmpty()
        project()
    }
    fun retry() { if (!closed && raw == AdminBrowseContent.Failure) observe() }
    private fun observe() {
        val token = ++generation
        source?.cancel()
        raw = AdminBrowseContent.Loading
        project()
        source = viewModelScope.launch {
            try {
                when (tab) {
                    AdminBrowseTab.CAMPSITES -> campsites.observeAll().collect { if (valid(token)) { raw = AdminBrowseContent.Campsites(it); project() } }
                    AdminBrowseTab.PROFILES -> accounts.observeAll().collect { if (valid(token)) { raw = AdminBrowseContent.Profiles(it); project() } }
                    AdminBrowseTab.IMAGES -> images.observeAll().collect { if (valid(token)) { raw = AdminBrowseContent.Images(it); project() } }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { if (valid(token)) { raw = AdminBrowseContent.Failure; project() } }
        }
    }
    private fun valid(token: Int) = !closed && token == generation
    private fun project() {
        val query = queries[tab].orEmpty()
        val content = when (val rows = raw) {
            is AdminBrowseContent.Campsites -> if (query.isBlank()) rows else matchesSite?.let { match -> AdminBrowseContent.Campsites(rows.rows.filter { match(it, query) }) } ?: AdminBrowseContent.Pending
            is AdminBrowseContent.Profiles -> {
                if ((role != null && filterProfiles == null) || (query.isNotBlank() && matchesProfile == null)) AdminBrowseContent.Pending
                else {
                    val selected = if (role == null) rows.rows else filterProfiles!!.invoke(rows.rows, role)
                    AdminBrowseContent.Profiles(if (query.isBlank()) selected else selected.filter { matchesProfile!!.invoke(it, query) })
                }
            }
            is AdminBrowseContent.Images -> {
                val needle = query.trim().lowercase(Locale.ROOT)
                AdminBrowseContent.Images(rows.rows.filter { it.target.id.lowercase(Locale.ROOT).contains(needle) || it.target.type.name.lowercase(Locale.ROOT).contains(needle) })
            }
            else -> rows
        }
        mutableState.value = AdminBrowseUiState(tab, query, if (tab == AdminBrowseTab.PROFILES) role else null, content)
        setVisibleTargets(visible)
    }
    fun setVisibleTargets(targets: Set<ImageTarget>) {
        if (closed) return
        visible = targets
        val allowed = when (val content = state.value.content) {
            is AdminBrowseContent.Campsites -> content.rows.map { ImageTarget(ImageTargetType.CAMPSITE, it.id) }
            is AdminBrowseContent.Profiles -> content.rows.map { ImageTarget(ImageTargetType.ACCOUNT, it.id) }
            is AdminBrowseContent.Images -> content.rows.map { it.target }
            else -> emptyList()
        }
        thumbnails.show(targets intersect allowed.toSet())
    }
    fun retryThumbnail(target: ImageTarget) { if (!closed) thumbnails.retry(target) }
    fun openProfile(id: String) = reader.openProfile(id)
    fun openImage(target: ImageTarget) = reader.openImage(target)
    fun closeSelection(target: AdminInspectionTarget) = reader.closeSelection(target)
    fun retryProfile() = reader.retryProfile()
    fun retryProfileCampsites() = reader.retryCampsites()
    fun retryInspectionPhoto() = reader.retryPhoto()
    fun retryImageParent() = reader.retryParent()
    override fun onCleared() {
        closed = true
        generation++
        source?.cancel()
        thumbnails.close()
        reader.close()
    }
}
