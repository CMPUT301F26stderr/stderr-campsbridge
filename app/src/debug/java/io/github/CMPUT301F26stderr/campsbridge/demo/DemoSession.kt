package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoImageRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoNotificationRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.di.AppRepositories
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices
import io.github.CMPUT301F26stderr.campsbridge.di.appViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.util.UUID

/** Starting scenarios offered by the debug launcher's visible tools menu. */
enum class DemoScenario { ENTRANT, OWNER, NEW_ACCOUNT }

/** Owns one isolated in-memory graph and its root/navigation ViewModels.
 * @param scenario Starting account and progress.
 * @param cacheDirectory Parent directory for scenario-local photo bytes.
 */
class DemoSession(
    val scenario: DemoScenario,
    cacheDirectory: File,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val services: AppServices
    val model: AppViewModel

    init {
        val id = "demo-account"
        val owner = scenario == DemoScenario.OWNER
        val account =
            if (scenario == DemoScenario.NEW_ACCOUNT) {
                null
            } else {
                Account(
                    id,
                    if (owner) "Jordan Lee" else "Alex Chen",
                    "camper@example.com",
                    null,
                    if (owner) setOf(Role.ENTRANT, Role.OWNER) else setOf(Role.ENTRANT),
                    Preferences(tags = setOf("lakeside")),
                    AccountSettings(),
                    Instant.parse("2026-10-01T12:00:00Z"),
                )
            }
        val local =
            if (account == null) {
                LocalAccountProgress()
            } else {
                LocalAccountProgress(
                    SetupProgress.COMPLETED,
                    if (owner) Role.OWNER else Role.ENTRANT,
                )
            }
        services =
            AppServices(
                DemoRepositories(account, File(cacheDirectory, UUID.randomUUID().toString())),
                DemoIdentity(id),
                DemoAppPreferences(mapOf(id to local)),
            )
        model = ViewModelProvider(this, appViewModelFactory(services))[AppViewModel::class.java]
    }
}

/** Retains a demo graph across activity recreation and discards it on explicit reset.
 * @param cacheDirectory Application cache location, never an Activity reference.
 */
class DemoHostViewModel(
    private val cacheDirectory: File,
) : ViewModel() {
    private val mutableSession = MutableStateFlow(DemoSession(DemoScenario.ENTRANT, cacheDirectory))
    val session = mutableSession.asStateFlow()

    /** Cancels old models before exposing a fresh isolated scenario.
     * @param scenario Requested starting state; defaults to resetting the selected scenario.
     */
    fun reset(scenario: DemoScenario = session.value.scenario) {
        session.value.viewModelStore.clear()
        mutableSession.value = DemoSession(scenario, cacheDirectory)
    }

    /** Releases root/navigation models when the host finishes. */
    override fun onCleared() {
        session.value.viewModelStore.clear()
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

/** Supplies only repositories required by implemented screens.
 * @param account Optional starting profile.
 * @param directory Private photo cache for this scenario.
 */
private class DemoRepositories(
    account: Account?,
    directory: File,
) : AppRepositories {
    override val accounts = DemoAccountRepository(listOfNotNull(account))
    override val images = DemoImageRepository(directory)
    override val lotteries = DemoLotteryRepository()
    override val bookings = DemoBookingRepository()
    override val notifications = DemoNotificationRepository()
    override val reviews = DemoReviewRepository()
    override val campsites =
        DemoCampsiteRepository(
            if (account != null && Role.OWNER in account.roles) {
                listOf(
                    sampleCampsite("demo-river", account.id),
                    sampleCampsite("demo-draft", account.id, published = false).let {
                        it.copy(details = it.details.copy(name = "Forest clearing"))
                    },
                )
            } else {
                emptyList()
            },
        )
    override val pendingWork = DemoPendingWork()
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
