package io.github.CMPUT301F26stderr.campsbridge.ui.profile

import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.ui.preview.sampleAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Contracts for existing-profile presentation, without name/contact editing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Observed fields remain exact and update without profile writes. */
    @Test fun existingProfileUpdatesAndStopsOnClear() =
        runTest {
            val accounts = FakeAccountRepository(sampleAccount())
            val model = ProfileViewModel("account-1", accounts, FakeImageRepository())
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                assertEquals(ProfileUiState.Loading, model.state.value)
                runCurrent()
                assertEquals(sampleAccount(), (model.state.value as ProfileUiState.Content).account)
                accounts.account.value = sampleAccount().copy(name = "Updated name", email = null)
                runCurrent()
                val shown = (model.state.value as ProfileUiState.Content).account
                assertEquals("Updated name", shown.name)
                assertEquals(null, shown.email)
                assertEquals(0, accounts.writes)
            } finally {
                store.clear()
                runCurrent()
            }
            assertEquals(0, accounts.activeObservers)
        }

    /** Missing and removed profiles are unavailable, distinct from a failed read. */
    @Test fun missingRemovedAndFailedReadsHaveExplicitOutcomes() =
        runTest {
            val accounts = FakeAccountRepository()
            val model = ProfileViewModel("account-1", accounts, FakeImageRepository())
            val store = ViewModelStore().apply { put("profile", model) }
            try {
                runCurrent()
                assertEquals(ProfileUiState.Unavailable, model.state.value)
                accounts.account.value =
                    sampleAccount().copy(removal = Removal(Instant.EPOCH, "admin"))
                runCurrent()
                assertEquals(ProfileUiState.Unavailable, model.state.value)
                accounts.readFailure = IOException("offline")
                model.retry()
                runCurrent()
                assertEquals(ProfileUiState.Failure, model.state.value)
                accounts.readFailure = null
                accounts.account.value = sampleAccount()
                model.retry()
                runCurrent()
                assertTrue(model.state.value is ProfileUiState.Content)
                assertEquals(1, accounts.activeObservers)
            } finally {
                store.clear()
                runCurrent()
            }
        }
}
