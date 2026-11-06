/*
 * Parent graph owns browse inputs, the selected reader and visible thumbnails independently.
 */

package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.ui.common.images.VisibleImageSlots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Parent graph owns browse inputs, the selected reader and visible thumbnails independently.
 * @param campsites Source for the Campsites tab.
 * @param accounts Source for the Profiles tab.
 * @param images Source for the Images tab and thumbnails.
 * @param saved Keeps the tab, each tab's search and the role filter through restoration.
 * @param matchesSite Campsite text search; null leaves a search pending.
 * @param filterProfiles Profile role filter; null leaves a selected role pending.
 * @param matchesProfile Profile text search; null leaves a search pending.
 */
class AdminBrowseViewModel(
    private val campsites: CampsiteRepository,
    private val accounts: AccountRepository,
    private val images: ImageRepository,
    private val saved: SavedStateHandle,
    private val matchesSite: ((Campsite, String) -> Boolean)? = null,
    private val filterProfiles: ((List<Account>, Role?) -> List<Account>)? = null,
    private val matchesProfile: ((Account, String) -> Boolean)? = null,
) : ViewModel() {
    /** Reads a saved string, ignoring a value of any other type.
     * @param key Saved-state key.
     */
    private fun savedString(key: String) =
        saved.get<Any?>(key) as? String

    private var tab =
        AdminBrowseTab.entries.firstOrNull { it.name == savedString("adminBrowseTab") }
            ?: AdminBrowseTab.CAMPSITES
    private val queries =
        AdminBrowseTab.entries
            .associateWith {
                savedString("adminBrowseQuery$it").orEmpty()
            }.toMutableMap()
    private var role = Role.entries.firstOrNull { it.name == savedString("adminBrowseProfileRole") }
    private val mutableState =
        MutableStateFlow(
            AdminBrowseUiState(
                tab,
                queries[tab].orEmpty(),
                if (tab ==
                    AdminBrowseTab.PROFILES
                ) {
                    role
                } else {
                    null
                },
            ),
        )
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

    init {
        observe()
    }

    /** Switches tabs, restoring that tab's search and observing its source.
     * @param value Tab to show.
     */
    fun selectTab(value: AdminBrowseTab) {
        if (closed || tab == value) return
        tab = value
        saved["adminBrowseTab"] = value.name
        setVisibleTargets(emptySet())
        observe()
    }

    /** Saves the selected tab's search text and filters the loaded rows.
     * @param value Raw text, kept as typed.
     */
    fun editQuery(value: String) {
        if (closed || queries[tab] == value) return
        queries[tab] = value
        saved["adminBrowseQuery$tab"] = value
        project()
    }

    /** Saves the profile role filter; ignored on other tabs.
     * @param value Role to show, or null for every role.
     */
    fun selectRole(value: Role?) {
        if (closed || tab != AdminBrowseTab.PROFILES || role == value) return
        role = value
        saved["adminBrowseProfileRole"] = value?.name.orEmpty()
        project()
    }

    /** Restarts the selected source after a failure. */
    fun retry() {
        if (!closed && raw == AdminBrowseContent.Failure) observe()
    }

    /** Replaces the source observation for the selected tab, ignoring late results from the old one. */
    private fun observe() {
        val token = ++generation
        source?.cancel()
        raw = AdminBrowseContent.Loading
        project()
        source =
            viewModelScope.launch {
                try {
                    when (tab) {
                        AdminBrowseTab.CAMPSITES -> {
                            campsites.observeAll().collect {
                                if (valid(token)) {
                                    raw =
                                        AdminBrowseContent.Campsites(it)
                                    project()
                                }
                            }
                        }

                        AdminBrowseTab.PROFILES -> {
                            accounts.observeAll().collect {
                                if (valid(token)) {
                                    raw =
                                        AdminBrowseContent.Profiles(it)
                                    project()
                                }
                            }
                        }

                        AdminBrowseTab.IMAGES -> {
                            images.observeAll().collect {
                                if (valid(token)) {
                                    raw =
                                        AdminBrowseContent.Images(it)
                                    project()
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    if (valid(token)) {
                        raw = AdminBrowseContent.Failure
                        project()
                    }
                }
            }
    }

    /** Reports whether a source result still belongs to the current observation.
     * @param token Generation the observation started with.
     */
    private fun valid(token: Int) =
        !closed && token == generation

    /** Publishes the loaded rows after applying the selected tab's search and filter. */
    private fun project() {
        val query = queries[tab].orEmpty()
        val content =
            when (val rows = raw) {
                is AdminBrowseContent.Campsites -> {
                    if (query.isBlank()) {
                        rows
                    } else {
                        matchesSite?.let { match ->
                            AdminBrowseContent.Campsites(rows.rows.filter { match(it, query) })
                        }
                            ?: AdminBrowseContent.Pending
                    }
                }

                is AdminBrowseContent.Profiles -> {
                    if ((role != null && filterProfiles == null) ||
                        (query.isNotBlank() && matchesProfile == null)
                    ) {
                        AdminBrowseContent.Pending
                    } else {
                        val selected =
                            if (role ==
                                null
                            ) {
                                rows.rows
                            } else {
                                filterProfiles!!.invoke(rows.rows, role)
                            }
                        AdminBrowseContent.Profiles(
                            if (query.isBlank()) {
                                selected
                            } else {
                                selected.filter {
                                    matchesProfile!!.invoke(it, query)
                                }
                            },
                        )
                    }
                }

                is AdminBrowseContent.Images -> {
                    val needle = query.trim().lowercase(Locale.ROOT)
                    AdminBrowseContent.Images(
                        rows.rows.filter {
                            it.target.id
                                .lowercase(Locale.ROOT)
                                .contains(needle) ||
                                it.target.type.name
                                    .lowercase(Locale.ROOT)
                                    .contains(needle)
                        },
                    )
                }

                else -> {
                    rows
                }
            }
        mutableState.value =
            AdminBrowseUiState(
                tab,
                query,
                if (tab ==
                    AdminBrowseTab.PROFILES
                ) {
                    role
                } else {
                    null
                },
                content,
            )
        setVisibleTargets(visible)
    }

    /** Loads thumbnails only for on-screen rows that are still in the results.
     * @param targets Image slots the route reports as visible.
     */
    fun setVisibleTargets(targets: Set<ImageTarget>) {
        if (closed) return
        visible = targets
        val allowed =
            when (val content = state.value.content) {
                is AdminBrowseContent.Campsites -> {
                    content.rows.map {
                        ImageTarget(
                            ImageTargetType.CAMPSITE,
                            it.id,
                        )
                    }
                }

                is AdminBrowseContent.Profiles -> {
                    content.rows.map {
                        ImageTarget(
                            ImageTargetType.ACCOUNT,
                            it.id,
                        )
                    }
                }

                is AdminBrowseContent.Images -> {
                    content.rows.map { it.target }
                }

                else -> {
                    emptyList()
                }
            }
        thumbnails.show(targets intersect allowed.toSet())
    }

    /** Retries one failed thumbnail.
     * @param target Image slot to reload.
     */
    fun retryThumbnail(target: ImageTarget) {
        if (!closed) thumbnails.retry(target)
    }

    /** Starts reading one profile for inspection.
     * @param id Account ID to inspect.
     */
    fun openProfile(id: String) =
        reader.openProfile(id)

    /** Starts reading one image slot and its parent record for inspection.
     * @param target Image slot to inspect.
     */
    fun openImage(target: ImageTarget) =
        reader.openImage(target)

    /** Stops an inspection when its screen leaves.
     * @param target Selection that screen owned; a newer selection is left alone.
     */
    fun closeSelection(target: AdminInspectionTarget) =
        reader.closeSelection(target)

    /** Retries the selected profile after a failed read. */
    fun retryProfile() =
        reader.retryProfile()

    /** Retries the selected profile's campsites. */
    fun retryProfileCampsites() =
        reader.retryCampsites()

    /** Retries the inspected photo. */
    fun retryInspectionPhoto() =
        reader.retryPhoto()

    /** Retries the inspected image's parent record. */
    fun retryImageParent() =
        reader.retryParent()

    /** Stops the source, thumbnails and inspection when the graph is removed. */
    override fun onCleared() {
        closed = true
        generation++
        source?.cancel()
        thumbnails.close()
        reader.close()
    }
}
