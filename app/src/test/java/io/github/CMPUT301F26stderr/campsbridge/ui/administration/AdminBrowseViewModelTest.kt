package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.CMPUT301F26stderr.campsbridge.domain.account.*
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.*
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.administration.logic.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBrowseViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun queriesAndRoleRestoreIndependentlyAndFilteringStaysLocal() = runTest {
        val saved = SavedStateHandle()
        val account = sampleAccount().copy(name = "Alex", roles = setOf(Role.ENTRANT, Role.OWNER))
        val accounts = FakeAccountRepository(account)
        val campsites = FakeCampsiteRepository()
        val images = FakeImageRepository()
        val store = ViewModelStore()
        val model = AdminBrowseViewModel(accounts, campsites, images, saved)
        store.put("browse", model)
        runCurrent()
        model.setQuery(" alex ")
        model.setRole(Role.OWNER)
        assertEquals(listOf(account), (model.state.value.profiles as AdminRead.Content).value)
        model.selectTab(AdminBrowseTab.CAMPSITES)
        model.setQuery("lake")
        model.selectTab(AdminBrowseTab.IMAGES)
        model.setQuery("photo")
        model.selectTab(AdminBrowseTab.PROFILES)
        assertEquals(" alex ", model.state.value.query)
        accounts.account.value = account.copy(roles = setOf(Role.ENTRANT))
        runCurrent()
        assertEquals(emptyList<Account>(), (model.state.value.profiles as AdminRead.Content).value)
        val restored = AdminBrowseViewModel(accounts, campsites, images,
            SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        store.put("restored", restored)
        assertEquals(model.state.value.queries, restored.state.value.queries)
        assertEquals(Role.OWNER, restored.state.value.role)
        assertEquals(AdminBrowseTab.PROFILES, restored.state.value.tab)
        assertEquals(1, campsites.allObservations)
        store.clear()
        runCurrent()
        assertEquals(0, images.activeObservers)
    }

    @Test fun failureDoesNotBecomeEmptyOrBlockOtherSourcesAndRetryRecovers() = runTest {
        var failed = true
        val fake = FakeAccountRepository(sampleAccount())
        val accounts = object : AccountRepository by fake {
            override fun observeAll() = flow {
                if (failed) throw IOException("offline")
                emit(emptyList<Account>())
            }
        }
        val store = ViewModelStore()
        val model = AdminBrowseViewModel(accounts, FakeCampsiteRepository(), FakeImageRepository(), SavedStateHandle())
        store.put("browse", model)
        assertEquals(AdminRead.Loading, model.state.value.profiles)
        runCurrent()
        assertEquals(AdminRead.Failure, model.state.value.profiles)
        assertEquals(AdminRead.Content(emptyList<Any>()), model.state.value.campsites)
        failed = false
        model.retry(AdminBrowseTab.PROFILES)
        runCurrent()
        assertEquals(AdminRead.Content(emptyList<Account>()), model.state.value.profiles)
        store.clear()
    }

    @Test fun thumbnailFailureKeepsMetadataAndClosingVisibilityReleasesReader() = runTest {
        val account = sampleAccount()
        val images = FakeImageRepository().apply { observationFailure = IOException("thumbnail") }
        val store = ViewModelStore()
        val model = AdminBrowseViewModel(FakeAccountRepository(account), FakeCampsiteRepository(), images, SavedStateHandle())
        store.put("browse", model)
        runCurrent()
        val target = io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget(
            io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType.ACCOUNT, account.id)
        model.setVisibleTargets(setOf(target))
        runCurrent()
        assertEquals(listOf(account), (model.state.value.profiles as AdminRead.Content).value)
        assertEquals(io.github.CMPUT301F26stderr.campsbridge.ui.common.images.ImageLoadState.FAILED,
            model.inspection.photos.value[target]?.loadState)
        model.setVisibleTargets(emptySet())
        assertTrue(model.inspection.photos.value.isEmpty())
        store.clear()
    }

    @Test fun profileMatchingUsesCurrentContactFieldsAndCapabilityMembership() {
        val account = sampleAccount().copy(name = "Alex", email = "A@Example.com", phone = "7801234567",
            roles = setOf(Role.ENTRANT, Role.OWNER))
        assertTrue(matchesProfileRole(account, null))
        assertTrue(matchesProfileRole(account, Role.OWNER))
        assertFalse(matchesProfileRole(account, Role.ADMIN))
        listOf("", "   ", " aLEX ", "EXAMPLE", "1234", account.id).forEach { assertTrue(matchesProfileText(account, it)) }
        assertFalse(matchesProfileText(account, "missing"))
        assertFalse(matchesProfileText(account.copy(email = null, phone = null), "example"))
    }
}
