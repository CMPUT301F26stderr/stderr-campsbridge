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
