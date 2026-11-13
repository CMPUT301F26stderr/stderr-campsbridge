/*
 * Owns separate demo people and their screen models over one shared local dataset.
 */

package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoImageRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoMaintenance
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoMessageRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoModerationRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.documents
import io.github.CMPUT301F26stderr.campsbridge.di.AppRepositories
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices
import io.github.CMPUT301F26stderr.campsbridge.di.appViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.NotificationEntryViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.SessionState
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Separate people in one local dataset.
 * @param accountId Stable person identity.
 * @param workspace Initial permitted workspace.
 */
enum class DemoScenario(
    val accountId: String,
    val workspace: Role,
) {
    ENTRANT("demo-account", Role.ENTRANT),
    OWNER("demo-host", Role.OWNER),
    CO_OWNER("demo-cohost", Role.OWNER),
    NEW_ACCOUNT("demo-new", Role.ENTRANT),
    ADMIN("demo-admin", Role.ADMIN),
}

/** Owns one person's screen models over the shared local dataset.
 * @param scenario Starting account and progress.
 * @param cacheDirectory Parent directory for local photo bytes.
 * @param store Shared data retained when another area opens.
 * @param repositories Same repository instances for every person.
 */
class DemoSession(
    val scenario: DemoScenario,
    cacheDirectory: File,
    val store: DemoStore = DemoStore(DemoPreviewData(scenario).documents()),
    private val repositories: DemoRepositories =
        DemoRepositories(store, DemoPreviewData(scenario), File(cacheDirectory, UUID.randomUUID().toString())),
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val preview = DemoPreviewData(scenario)
    val services: AppServices
    val model: AppViewModel
    val notificationEntries: NotificationEntryViewModel

    init {
        val id = scenario.accountId
        services =
            AppServices(repositories, DemoIdentity(id), DemoAppPreferences(emptyMap(), store, scenario), store.clock)
        model = ViewModelProvider(this, appViewModelFactory(services))[AppViewModel::class.java]
        val notificationFactory =
            viewModelFactory {
                initializer {
                    NotificationEntryViewModel(
                        NotificationContentLoader(
                            services.repositories.notifications,
                            services.repositories.lotteries,
                            services.repositories.bookings,
                            services.repositories.messages,
                        ),
                        SavedStateHandle(),
                    )
                }
            }
        notificationEntries =
            ViewModelProvider(this, notificationFactory)[
                NotificationEntryViewModel::class.java,
            ]
    }

    /** Releases this person's models without deleting shared records or photos. */
    fun close() {
        viewModelStore.clear()
    }
}

/** Retains each person's navigation and one durable dataset across area changes.
 * @param cacheDirectory Private dataset directory, never an Activity reference.
 */
class DemoHostViewModel(
    private val cacheDirectory: File,
) : ViewModel() {
    private val mutableSession = MutableStateFlow<DemoSession?>(null)
    val session = mutableSession.asStateFlow()
    private val mutableDestination = MutableStateFlow<String?>(null)
    val destination = mutableDestination.asStateFlow()
    private val mutableFailure = MutableStateFlow(false)
    val failure = mutableFailure.asStateFlow()
    private val sessions = mutableMapOf<DemoScenario, DemoSession>()
    private val commands = Mutex()
    private var repositories: DemoRepositories? = null
    private var store: DemoStore? = null

    init {
        command { restore() }
    }

    /** Serializes host actions so a later area selection or reset wins over earlier work.
     * @param operation Explicit user action or initial load.
     */
    private fun command(operation: suspend () -> Unit) {
        viewModelScope.launch {
            commands.withLock {
                try {
                    operation()
                    mutableFailure.value = false
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableFailure.value = true
                }
            }
        }
    }

    /** Restores committed records before constructing any screen controllers.
     * @param preferred Optional area following an explicit reset.
     */
    private suspend fun restore(preferred: DemoScenario? = null) {
        val data = DemoPreviewData(DemoScenario.ENTRANT)
        val restored =
            withContext(Dispatchers.IO) { DemoStore(data.documents(), File(cacheDirectory, "records.json"), data.now) }
        store = restored
        repositories = DemoRepositories(restored, data, File(cacheDirectory, "photos"))
        val selected =
            preferred ?: restored
                .read { raw["_demo/area"]?.get("name") as? String }
                ?.let { name -> DemoScenario.entries.firstOrNull { it.name == name } } ?: DemoScenario.ENTRANT
        activate(selected)
    }

    /** Selects the area's actual person and confirmed workspace before exposing its graph.
     * @param scenario Requested person/area.
     */
    private suspend fun activate(scenario: DemoScenario) {
        val shared = checkNotNull(store)
        DemoAppPreferences(emptyMap(), shared, scenario).setWorkspace(scenario.accountId, scenario.workspace)
        shared.change { raw["_demo/area"] = mapOf("name" to scenario.name) }
        val target =
            sessions.getOrPut(
                scenario,
            ) { DemoSession(scenario, cacheDirectory, shared, checkNotNull(repositories)) }
        target.model.state.first { state ->
            state != SessionState.Initializing &&
                (state !is SessionState.Ready || state.workspace == scenario.workspace)
        }
        mutableDestination.value = null
        mutableSession.value = target
    }

    /** Opens a retained area without rebuilding records or discarding its navigation.
     * @param scenario Person whose view should open.
     */
    fun select(scenario: DemoScenario) {
        command { if (store != null) activate(scenario) }
    }

    /** Opens an actual saved target, advancing demonstration time only on explicit request.
     * @param action Current contextual handoff.
     */
    fun follow(action: DemoJourneyAction) {
        command {
            val shared = checkNotNull(store)
            action.at?.let { shared.advanceTo(maxOf(it, shared.clock.instant())) }
            action.drawId?.let { checkNotNull(repositories).lotteries.draw(it) }
            activate(action.area)
            mutableDestination.value = action.route
        }
    }

    /** Acknowledges a handoff only after its destination has opened. */
    fun consumeDestination() {
        mutableDestination.value = null
    }

    /** Explicitly restores baseline records for every person after pending commits finish.
     * @param scenario Area to open after reset.
     */
    fun reset(scenario: DemoScenario = session.value?.scenario ?: DemoScenario.ENTRANT) {
        command {
            mutableSession.value = null
            mutableDestination.value = null
            sessions.values.forEach { it.close() }
            sessions.clear()
            repositories?.images?.close()
            store?.change { Unit }
            withContext(Dispatchers.IO) { check(!cacheDirectory.exists() || cacheDirectory.deleteRecursively()) }
            store = null
            repositories = null
            restore(scenario)
        }
    }

    /** Retires models while retaining committed records and photos for the next launch. */
    override fun onCleared() {
        sessions.values.forEach { it.close() }
        super.onCleared()
    }
}

/** Fixed installation identity belonging only to the current demo graph.
 * @param id Scenario identity.
 */
private class DemoIdentity(
    private val id: String,
) : IdentityProvider {
    /** Returns the scenario identity without authentication. */
    override suspend fun establishIdentity(): String =
        id
}

/** Supplies the same local repositories to each person's real screen factories.
 * @param store Shared committed documents and clock.
 * @param data Baseline packaged photo references.
 * @param directory Private photo files retained with the dataset.
 */
class DemoRepositories(
    store: DemoStore,
    data: DemoPreviewData,
    directory: File,
) : AppRepositories {
    override val accounts = DemoAccountRepository(emptyList(), store.clock, store)
    override val images = DemoImageRepository(directory, store.clock, data.photoReferences, store)
    override val lotteries = DemoLotteryRepository(store = store)
    override val bookings = DemoBookingRepository(store = store)
    override val notifications = DemoNotificationRepository(clock = store.clock, store = store)
    override val messages = DemoMessageRepository(store)
    override val reviews = DemoReviewRepository(store)
    override val moderation = DemoModerationRepository(store, images, bookings, lotteries)
    override val campsites = DemoCampsiteRepository(store = store)
    override val pendingWork: PendingWork = DemoPendingWork()
    override val maintenance = DemoMaintenance(store, moderation, images)
}

/** The offline demo has no distributed lottery or notification work to process. */
private class DemoPendingWork : PendingWork {
    /** Waits for cancellation without timers, network requests or simulated delivery.
     * @param session Current demo account.
     * @param onFailure Unused because the demo has no distributed work.
     */
    override suspend fun run(
        session: WorkSession,
        onFailure: (Exception) -> Unit,
    ) {
        awaitCancellation()
    }
}
