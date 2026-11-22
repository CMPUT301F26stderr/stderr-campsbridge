package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Explicit actions own recovery; browse reads and failed passes cannot imply completion. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminMaintenanceTest {
    @get:Rule val main = MainDispatcherRule()

    /** Read-only hosts omit recovery and construction never executes a bound operation. */
    @Test fun recoveryWaitsForAnExplicitAction() =
        runTest {
            val owner = Owner()
            var calls = 0
            try {
                assertNull(owner.model().maintenance.value)
                val model = owner.model("bound") { calls++ }
                runCurrent()
                assertEquals(0, calls)
                assertEquals(AdminMaintenanceState.READY, model.maintenance.value)
                model.resumePendingWork()
                runCurrent()
                assertEquals(1, calls)
                assertEquals(AdminMaintenanceState.COMPLETE, model.maintenance.value)
            } finally {
                owner.viewModelStore.clear()
            }
        }

    /** Duplicate taps and tab changes preserve a single in-flight pass until acknowledgement. */
    @Test fun onePassSurvivesTabChangesAndWaitsForItsResult() =
        runTest {
            val owner = Owner()
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            try {
                val model =
                    owner.model {
                        calls++
                        gate.await()
                    }
                model.resumePendingWork()
                model.resumePendingWork()
                model.selectTab(AdminBrowseTab.PROFILES)
                runCurrent()
                assertEquals(1, calls)
                assertEquals(AdminMaintenanceState.RUNNING, model.maintenance.value)
                gate.complete(Unit)
                runCurrent()
                assertEquals(AdminMaintenanceState.COMPLETE, model.maintenance.value)
            } finally {
                owner.viewModelStore.clear()
            }
        }

    /** A failed pass remains explicitly retryable and never reruns because Browse changes. */
    @Test fun failedWorkRetriesOnlyWhenRequested() =
        runTest {
            val owner = Owner()
            var calls = 0
            try {
                val model = owner.model { if (++calls == 1) throw IOException("offline") }
                model.resumePendingWork()
                runCurrent()
                assertEquals(AdminMaintenanceState.FAILED, model.maintenance.value)
                model.selectTab(AdminBrowseTab.IMAGES)
                model.retry()
                runCurrent()
                assertEquals(1, calls)
                model.resumePendingWork()
                runCurrent()
                assertEquals(2, calls)
                assertEquals(AdminMaintenanceState.COMPLETE, model.maintenance.value)
            } finally {
                owner.viewModelStore.clear()
            }
        }

    /** A retired graph cannot accept late completion or launch another recovery pass. */
    @Test fun clearingTheOwnerCancelsWorkWithoutPublishingSuccess() =
        runTest {
            val owner = Owner()
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            val model =
                owner.model {
                    calls++
                    withContext(NonCancellable) { gate.await() }
                }
            model.resumePendingWork()
            runCurrent()
            owner.viewModelStore.clear()
            gate.complete(Unit)
            runCurrent()
            model.resumePendingWork()
            runCurrent()
            assertEquals(1, calls)
            assertEquals(AdminMaintenanceState.RUNNING, model.maintenance.value)
        }

    /** Supplies a cancellable graph owner and controlled repositories. */
    private class Owner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()

        /** Creates a real Browse owner with an optional recovery boundary.
         * @param key Distinguishes independent owners within this fixture.
         * @param resume Controlled acknowledgement, failure or suspension.
         */
        fun model(
            key: String = "browse",
            resume: (suspend () -> Unit)? = null,
        ): AdminBrowseViewModel =
            ViewModelProvider(
                this,
                viewModelFactory {
                    initializer {
                        AdminBrowseViewModel(
                            FakeCampsiteRepository(),
                            FakeAccountRepository(),
                            FakeImageRepository(),
                            SavedStateHandle(),
                            resumeWork = resume,
                        )
                    }
                },
            )[key, AdminBrowseViewModel::class.java]
    }
}
