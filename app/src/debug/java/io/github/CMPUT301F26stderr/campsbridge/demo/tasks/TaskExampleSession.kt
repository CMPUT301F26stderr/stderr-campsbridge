package io.github.CMPUT301F26stderr.campsbridge.demo.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoImageRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
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
    private val campsites = TaskCampsiteRepository()
    private val images = DemoImageRepository(File(directory, UUID.randomUUID().toString()))
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

    /** Retains each real owner and releases its coroutine/listener scope on reset.
     * @param type Requested ViewModel class.
     * @param key Optional record identity within the task.
     * @param create Constructor using only this session's dependencies.
     */
    private fun <T : ViewModel> model(
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
