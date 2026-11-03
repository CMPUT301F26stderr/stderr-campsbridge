package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.*
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.*
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.logic.matchesCampsiteText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Graph-scoped browse owner. Only scalar input is saved; records and URLs are reobserved. */
class AdminBrowseViewModel(
    private val accounts: AccountRepository,
    private val campsites: CampsiteRepository,
    private val images: ImageRepository,
    private val saved: SavedStateHandle,
    private val roleMatch: (Account, Role?) -> Boolean = ::matchesProfileRole,
    private val textMatch: (Account, String) -> Boolean = ::matchesProfileText,
) : ViewModel() {
    val inspection = AdminInspectionController(images, viewModelScope)
    private val mutableState = MutableStateFlow(AdminBrowseUiState(
        tab = AdminBrowseTab.entries.firstOrNull { it.name == saved.get<String>("admin.tab") } ?: AdminBrowseTab.PROFILES,
        queries = AdminBrowseTab.entries.associateWith { saved.get<String>("admin.query.$it").orEmpty() },
        role = Role.entries.firstOrNull { it.name == saved.get<String>("admin.role") },
    ))
    val state = mutableState.asStateFlow()
    private var profiles: AdminRead<List<Account>> = AdminRead.Loading
    private var listings: AdminRead<List<Campsite>> = AdminRead.Loading
    private var attachments: AdminRead<List<ImageAttachment>> = AdminRead.Loading
    private val jobs = mutableMapOf<AdminBrowseTab, Job>()
    private var visible = emptySet<ImageTarget>()

    init { AdminBrowseTab.entries.forEach(::retry) }

    fun selectTab(tab: AdminBrowseTab) {
        saved["admin.tab"] = tab.name
        mutableState.update { it.copy(tab = tab) }
        setVisibleTargets(emptySet())
    }
    fun setQuery(query: String) {
        val tab = state.value.tab
        saved["admin.query.$tab"] = query
        mutableState.update { it.copy(queries = it.queries + (tab to query)) }
        project()
    }
    fun setRole(role: Role?) {
        saved["admin.role"] = role?.name
        mutableState.update { it.copy(role = role) }
        project()
    }
    fun retry(tab: AdminBrowseTab = state.value.tab) {
        jobs.remove(tab)?.cancel()
        jobs[tab] = when (tab) {
            AdminBrowseTab.PROFILES -> observe(accounts::observeAll) { profiles = it }
            AdminBrowseTab.CAMPSITES -> observe(campsites::observeAll) { listings = it }
            AdminBrowseTab.IMAGES -> observe(images::observeAll) { attachments = it }
        }
    }
    private fun <T> observe(source: () -> Flow<List<T>>, assign: (AdminRead<List<T>>) -> Unit): Job =
        viewModelScope.launch {
            assign(AdminRead.Loading)
            project()
            flow { emitAll(source()) }.catch { assign(AdminRead.Failure); project() }
                .collect { assign(AdminRead.Content(it)); project() }
        }
    private fun <T> AdminRead<List<T>>.filterRows(predicate: (T) -> Boolean): AdminRead<List<T>> =
        when (this) {
            is AdminRead.Content -> AdminRead.Content(value.filter(predicate))
            else -> this
        }
    private fun project() {
        mutableState.update { input ->
            input.copy(
                profiles = profiles.filterRows { roleMatch(it, input.role) && textMatch(it, input.queries[AdminBrowseTab.PROFILES].orEmpty()) },
                campsites = listings.filterRows { matchesCampsiteText(it, input.queries[AdminBrowseTab.CAMPSITES].orEmpty()) },
                images = attachments.filterRows {
                    val query = input.queries[AdminBrowseTab.IMAGES].orEmpty().trim()
                    listOf(it.target.id, it.target.type.name, it.blobId).any { field -> field.contains(query, ignoreCase = true) }
                },
            )
        }
        setVisibleTargets(visible)
    }
    fun setVisibleTargets(targets: Set<ImageTarget>) {
        visible = targets
        val current = state.value
        val allowed = when (current.tab) {
            AdminBrowseTab.PROFILES -> (current.profiles as? AdminRead.Content)?.value?.map { ImageTarget(ImageTargetType.ACCOUNT, it.id) }
            AdminBrowseTab.CAMPSITES -> (current.campsites as? AdminRead.Content)?.value?.map { ImageTarget(ImageTargetType.CAMPSITE, it.id) }
            AdminBrowseTab.IMAGES -> (current.images as? AdminRead.Content)?.value?.map { it.target }
        }.orEmpty().toSet()
        inspection.show(targets intersect allowed)
    }
    override fun onCleared() { inspection.close() }
}
