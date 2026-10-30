package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoImageRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** Owns one task's actual controllers and sample repositories until reset or exit.
 * @param example Selected stable task identity.
 * @param bindings Real assigned functions supplied by the handoff.
 * @param directory Private local image-cache parent.
 */
class TaskExampleSession(
    val example: TaskExample,
    val bindings: TaskExampleBindings,
    directory: File,
) : ViewModelStoreOwner,
    AutoCloseable {
    override val viewModelStore = ViewModelStore()
    val accounts =
        DemoAccountRepository(
            listOf(
                TaskExampleData.account,
                TaskExampleData.owner,
                TaskExampleData.account.copy(id = "task-other-entrant", name = "Morgan Park"),
            ),
        )
    internal val images = DemoImageRepository(File(directory, UUID.randomUUID().toString()))
    internal val campsites = TaskCampsiteRepository()
    internal val lotteries = TaskLotteryRepository()
    internal val bookings = TaskBookingRepository()
    internal val notifications = TaskNotificationRepository()
    var isClosed = false
        private set

    /** Returns the real profile editor, with only this task's parsers connected. */
    fun profile(): ProfileViewModel =
        model(ProfileViewModel::class.java) {
            ProfileViewModel(
                TaskExampleData.ACCOUNT_ID,
                accounts,
                images,
                SavedStateHandle(),
                bindings.parseName,
                bindings.parseEmail,
                bindings.parsePhone,
            )
        }

    /** Isolates discovery's target helper with constant peer outcomes for supplied records only. */
    fun explore(): ExploreViewModel =
        model(ExploreViewModel::class.java) {
            ExploreViewModel(
                campsites,
                images,
                DemoReviewRepository(),
                SavedStateHandle(),
                matchesText =
                    if (example ==
                        TaskExample.TEXT_MATCH
                    ) {
                        bindings.matchesText
                    } else {
                        { _, _ -> true }
                    },
                matchesCapacity =
                    if (example ==
                        TaskExample.GUEST_MATCH
                    ) {
                        bindings.matchesGuests
                    } else {
                        { _, _ -> true }
                    },
                sortCampsites = { it },
                clock = TaskExampleData.clock,
            )
        }

    /** Supplies the real owner inventory without requiring onboarding or publication. */
    fun campsiteList(): CampsiteListViewModel =
        model(CampsiteListViewModel::class.java) {
            CampsiteListViewModel(
                TaskExampleData.OWNER_ID,
                campsites,
                images,
                SavedStateHandle(),
                bindings.orderCampsites,
            )
        }

    /** Supplies the real editor with local-only calendar writes and the assigned comparison. */
    fun availability(): AvailabilityViewModel =
        model(AvailabilityViewModel::class.java) {
            AvailabilityViewModel(
                TaskExampleData.OWNER_ID,
                TaskExampleData.sites[0].id,
                campsites,
                TaskExampleData.clock,
                SavedStateHandle(),
                bindings.remainingEdits,
            )
        }

    /** Supplies an already-completed owner draw so participant filtering needs no earlier setup. */
    fun participants(): LotteryDetailViewModel =
        model(LotteryDetailViewModel::class.java) {
            LotteryDetailViewModel(
                TaskExampleData.OWNER_ID,
                TaskExampleData.lottery.id,
                lotteries,
                campsites,
                TaskExampleData.clock,
                bookings,
                LotteryContext.OWNER,
                accounts,
                SavedStateHandle(),
                bindings.filterParticipants,
            )
        }

    /** Reads the supplied saved lottery selected by Inbox.
     * @param id Actual sample draw identity.
     */
    fun lottery(id: String): LotteryDetailViewModel =
        model(LotteryDetailViewModel::class.java, id) {
            LotteryDetailViewModel(
                TaskExampleData.ACCOUNT_ID,
                id,
                lotteries,
                campsites,
                TaskExampleData.clock,
                bookings,
                LotteryContext.ENTRANT,
                accounts,
            )
        }

    /** Retains each real owner and releases its coroutine/listener scope on reset.
     * @param type Requested ViewModel class.
     * @param key Optional record identity within the task.
     * @param create Constructor using only this session's dependencies.
     */
    internal fun <T : ViewModel> model(
        type: Class<T>,
        key: String = type.name,
        create: () -> T,
    ): T {
        check(!isClosed)
        val factory =
            object : ViewModelProvider.Factory {
                /** Constructs the requested real owner from this session's explicit dependencies.
                 * @param modelClass Requested ViewModel class.
                 */
                override fun <VM : ViewModel> create(modelClass: Class<VM>): VM =
                    modelClass.cast(create())!!
            }
        return ViewModelProvider(this, factory)[key, type]
    }

    /** Retires every task owner before another example is shown. */
    override fun close() {
        if (!isClosed) {
            isClosed = true
            viewModelStore.clear()
        }
    }
}

/** Retains the selected task across recreation and resets all mutable sample state explicitly.
 * @param directory Private cache parent.
 * @param bindings Handoff's actual helper references.
 */
class TaskExamplesHostViewModel(
    private val directory: File,
    private val bindings: TaskExampleBindings,
) : ViewModel() {
    private val mutableSession = MutableStateFlow<TaskExampleSession?>(null)
    val session = mutableSession.asStateFlow()

    /** Opens a new isolated task and retires the previous one.
     * @param example Selected catalogue entry, including future placeholders.
     */
    fun open(example: TaskExample) {
        mutableSession.value?.close()
        mutableSession.value = TaskExampleSession(example, bindings, directory)
    }

    /** Restores the selected task's original records and controllers. */
    fun reset() {
        session.value?.example?.let(::open)
    }

    /** Returns to the catalogue and releases all feature owners. */
    fun showCatalogue() {
        mutableSession.value?.close()
        mutableSession.value = null
    }

    /** Releases feature scopes when the host actually finishes. */
    override fun onCleared() {
        showCatalogue()
    }
}
