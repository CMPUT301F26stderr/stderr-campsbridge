package io.github.CMPUT301F26stderr.campsbridge.demo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.PendingWork
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.WorkSession
import io.github.CMPUT301F26stderr.campsbridge.platform.identity.IdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.LocalAccountProgress
import io.github.CMPUT301F26stderr.campsbridge.platform.preferences.SetupProgress
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.app.NotificationEntryViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.common.notifications.NotificationContentLoader
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** Starting scenarios offered by the debug launcher's visible tools menu. */
enum class DemoScenario { ENTRANT, OWNER, NEW_ACCOUNT, ADMIN }

/** Owns one isolated in-memory graph and its root/navigation ViewModels.
 * @param scenario Starting account and progress.
 * @param cacheDirectory Parent directory for scenario-local photo bytes.
 */
class DemoSession(
    val scenario: DemoScenario,
    cacheDirectory: File,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    val preview = DemoPreviewData(scenario)
    val services: AppServices
    val model: AppViewModel
    val notificationEntries: NotificationEntryViewModel

    init {
        val id = "demo-account"
        val data = preview
        val owner = scenario == DemoScenario.OWNER
        val account =
            if (scenario ==
                DemoScenario.NEW_ACCOUNT
            ) {
                null
            } else {
                data.accounts.single { it.id == id }
            }
        val local =
            if (account == null) {
                LocalAccountProgress()
            } else {
                LocalAccountProgress(
                    SetupProgress.COMPLETED,
                    if (scenario ==
                        DemoScenario.ADMIN
                    ) {
                        Role.ADMIN
                    } else if (owner) {
                        Role.OWNER
                    } else {
                        Role.ENTRANT
                    },
                )
            }
        services =
            AppServices(
                DemoRepositories(account, data, File(cacheDirectory, UUID.randomUUID().toString())),
                DemoIdentity(id),
                DemoAppPreferences(mapOf(id to local)),
                data.clock,
            )
        model = ViewModelProvider(this, appViewModelFactory(services))[AppViewModel::class.java]
        val notificationFactory =
            viewModelFactory {
                initializer {
                    NotificationEntryViewModel(
                        NotificationContentLoader(
                            services.repositories.notifications,
                            services.repositories.lotteries,
                            services.repositories.bookings,
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
 * @param data Coherent saved preview records and fixed clock.
 * @param directory Private photo cache for this scenario.
 */
private class DemoRepositories(
    account: Account?,
    data: DemoPreviewData,
    directory: File,
) : AppRepositories {
    override val accounts =
        DemoAccountRepository(
            data.accounts.filterNot { it.id == "demo-account" } + listOfNotNull(account),
        )
    override val images =
        DemoImageRepository(
            directory,
            data.clock,
            data.photoReferences,
        )
    override val lotteries = DemoLotteryRepository(data.lotteries, data.offers, data.entries)
    override val bookings =
        DemoBookingRepository(
            data.reservations,
            data.campsites.associate {
                it.id to
                    it.ownerId
            },
        )
    override val notifications = DemoNotificationRepository(data.events, data.inboxes, data.clock)
    override val reviews = DemoReviewRepository()
    override val campsites = DemoCampsiteRepository(data.campsites, data.calendars)
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
