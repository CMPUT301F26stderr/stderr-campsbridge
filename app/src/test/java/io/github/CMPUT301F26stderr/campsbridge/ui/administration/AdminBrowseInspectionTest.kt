package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Two fixed parent-binding contracts; the plain reader owns its detailed source cases. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminBrowseInspectionTest {
    @get:Rule val main = MainDispatcherRule()

    /** Actual forwarding/retries leave Browse inputs and its source independently owned. */
    @Test fun selectedReadsAndRetriesPreserveBrowseAndSaveNoNavigationCopy() =
        scenario { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            f.model.selectRole(Role.OWNER)
            f.model.editQuery(" raw query ")
            runCurrent()
            val browse = f.model.state.value
            f.model.openProfile("account-1")
            runCurrent()
            assertTrue(f.model.inspection.value is AdminInspectionUiState.Profile)
            f.accounts.streamFailure.value = IOException("account offline")
            runCurrent()
            assertEquals(
                AdminRead.Failure,
                (f.model.inspection.value as AdminInspectionUiState.Profile).account,
            )
            f.accounts.streamFailure.value = null
            f.model.retryProfile()
            runCurrent()
            f.sites.streamFailure.value = IOException("owned offline")
            runCurrent()
            f.sites.streamFailure.value = null
            f.model.retryProfileCampsites()
            runCurrent()
            val profile = f.model.inspection.value as AdminInspectionUiState.Profile
            assertTrue(profile.account is AdminRead.Content)
            assertTrue(profile.campsites is AdminRead.Content)
            val target = ImageTarget(ImageTargetType.ACCOUNT, "account-1")
            f.model.openImage(target)
            runCurrent()
            f.accounts.streamFailure.value = IOException("parent offline")
            runCurrent()
            f.accounts.streamFailure.value = null
            f.model.retryImageParent()
            f.images.streamFailure.value = IOException("image offline")
            runCurrent()
            f.images.streamFailure.value = null
            f.model.retryInspectionPhoto()
            runCurrent()
            val image = f.model.inspection.value as AdminInspectionUiState.Image
            assertTrue(image.parent is AdminRead.Content)
            assertEquals(browse, f.model.state.value)
            assertEquals(
                setOf("adminBrowseTab", "adminBrowseProfileRole", "adminBrowseQueryPROFILES"),
                f.saved.keys(),
            )
            f.model.closeSelection(AdminInspectionTarget.Image(target))
            runCurrent()
            assertEquals(AdminInspectionUiState.None, f.model.inspection.value)
            assertEquals(0, f.accounts.writes + f.sites.writeCalls)
        }

    /** Clearing the actual parent store retires selected and Browse reads together. */
    @Test fun clearedBrowseParentRejectsLaterSelectedActions() =
        scenario { f ->
            f.model.openProfile("account-1")
            runCurrent()
            assertTrue(f.model.inspection.value is AdminInspectionUiState.Profile)
            assertEquals(1, f.accounts.activeObservers)
            f.store.clear()
            runCurrent()
            f.model.openProfile("account-1")
            f.model.openImage(ImageTarget(ImageTargetType.ACCOUNT, "account-1"))
            f.model.retryProfile()
            f.model.retryProfileCampsites()
            f.model.retryInspectionPhoto()
            f.model.retryImageParent()
            runCurrent()
            assertEquals(AdminInspectionUiState.None, f.model.inspection.value)
            assertEquals(
                0,
                f.accounts.activeObservers + f.sites.activeObservers + f.images.activeObservers,
            )
            assertEquals(1, f.accounts.observations)
            assertEquals(0, f.images.replaceCalls + f.images.deleteCalls)
        }

    /** Releases the actual parent model and its readers after one fixed scenario.
     * @param block Parent ownership interaction under test.
     */
    private fun scenario(block: suspend TestScope.(Fixture) -> Unit) =
        runTest(main.dispatcher) {
            val f = Fixture()
            try {
                block(f)
            } finally {
                f.store.clear()
                runCurrent()
            }
        }

    /** Supplies real parent creation and existing controlled repository boundaries. */
    private class Fixture {
        val accounts = FakeAccountRepository(sampleAccount())
        val sites = FakeCampsiteRepository(listOf(sampleCampsite()))
        val images = FakeImageRepository()
        val saved = SavedStateHandle()
        val store = ViewModelStore()
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        val model =
            ViewModelProvider(
                owner,
                viewModelFactory {
                    initializer { AdminBrowseViewModel(sites, accounts, images, saved) }
                },
            )[AdminBrowseViewModel::class.java]
    }
}
