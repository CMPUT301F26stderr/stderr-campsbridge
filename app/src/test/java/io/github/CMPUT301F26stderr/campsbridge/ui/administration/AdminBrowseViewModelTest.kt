package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Account
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.CampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageAttachment
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageRepository
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTarget
import io.github.CMPUT301F26stderr.campsbridge.domain.image.ImageTargetType
import io.github.CMPUT301F26stderr.campsbridge.domain.shared.Removal
import io.github.CMPUT301F26stderr.campsbridge.testsupport.MainDispatcherRule
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleCampsite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Seventeen fixed read-only browse contracts with independent sources and supplied predicates. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminBrowseViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Default unfiltered browsing retains published, draft and removal records in source order. */
    @Test fun defaultReadsOnlyAllCampsites() =
        scenario { f ->
            runCurrent()
            assertEquals(AdminBrowseTab.CAMPSITES, f.model.state.value.tab)
            assertEquals(AdminBrowseContent.Campsites(f.siteRows), f.model.state.value.content)
            assertEquals(1, f.sites.requests)
            assertEquals(1, f.sites.active)
            assertEquals(0, f.profiles.requests)
            assertEquals(0, f.attachments.requests)
            assertTrue(f.siteBase.observedCalendars.isEmpty())
            assertTrue(f.accountBase.readIds.isEmpty())
        }

    /** Restoration keeps tab-specific raw input and role using only primitive saved values. */
    @Test fun restoresTabQueriesAndProfileRole() =
        scenario { f ->
            f.model.editQuery("  site input  ")
            f.model.selectTab(AdminBrowseTab.PROFILES)
            f.model.editQuery("  profile input  ")
            f.model.selectRole(Role.OWNER)
            f.model.selectTab(AdminBrowseTab.IMAGES)
            f.model.editQuery("  image input  ")
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            val values = f.snapshot()
            assertTrue(values.isNotEmpty())
            assertTrue(values.values.all { it is String })
            f.recreate(values)
            runCurrent()
            assertEquals(AdminBrowseTab.PROFILES, f.model.state.value.tab)
            assertEquals("  profile input  ", f.model.state.value.query)
            assertEquals(Role.OWNER, f.model.state.value.role)
            f.model.selectTab(AdminBrowseTab.IMAGES)
            assertEquals("  image input  ", f.model.state.value.query)
            assertEquals(null, f.model.state.value.role)
            f.model.selectTab(AdminBrowseTab.CAMPSITES)
            assertEquals("  site input  ", f.model.state.value.query)
        }

    /** Unknown enums and wrong primitive types cannot crash restoration or select foreign data. */
    @Test fun malformedSavedValuesUseSafeDefaults() =
        scenario(
            saved =
                SavedStateHandle(
                    mapOf(
                        "adminBrowseTab" to 42,
                        "adminBrowseQueryCAMPSITES" to listOf("wrong type"),
                        "adminBrowseProfileRole" to "NOT_A_ROLE",
                    ),
                ),
        ) { f ->
            runCurrent()
            assertEquals(AdminBrowseTab.CAMPSITES, f.model.state.value.tab)
            assertEquals("", f.model.state.value.query)
            assertEquals(null, f.model.state.value.role)
            assertEquals(AdminBrowseContent.Campsites(f.siteRows), f.model.state.value.content)
            f.model.selectTab(AdminBrowseTab.PROFILES)
            assertEquals(null, f.model.state.value.role)
        }

    /** Switching retires the previous observer and brings back only the selected tab's inputs. */
    @Test fun switchingTabsReleasesOldSourceAndRetainsEachQuery() =
        scenario { f ->
            runCurrent()
            f.model.editQuery("campsite query")
            f.model.selectTab(AdminBrowseTab.PROFILES)
            assertEquals(AdminBrowseContent.Loading, f.model.state.value.content)
            runCurrent()
            assertEquals(0, f.sites.active)
            assertEquals(1, f.profiles.active)
            f.model.editQuery("profile query")
            f.model.selectRole(Role.ENTRANT)
            f.model.selectTab(AdminBrowseTab.IMAGES)
            runCurrent()
            assertEquals(0, f.profiles.active)
            assertEquals(1, f.attachments.active)
            f.model.editQuery("image query")
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            assertEquals("profile query", f.model.state.value.query)
            assertEquals(Role.ENTRANT, f.model.state.value.role)
            assertEquals(0, f.attachments.active)
            f.model.selectTab(AdminBrowseTab.CAMPSITES)
            assertEquals("campsite query", f.model.state.value.query)
        }

    /** Repeated selections/input and role actions outside Profiles do not acquire new sources. */
    @Test fun unchangedActionsDoNotResubscribe() =
        scenario { f ->
            runCurrent()
            repeat(3) {
                f.model.selectTab(AdminBrowseTab.CAMPSITES)
                f.model.editQuery("")
                f.model.selectRole(Role.ADMIN)
            }
            runCurrent()
            assertEquals(1, f.sites.requests)
            assertEquals(0, f.profiles.requests)
            assertEquals(null, f.model.state.value.role)
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            repeat(3) {
                f.model.selectTab(AdminBrowseTab.PROFILES)
                f.model.selectRole(null)
                f.model.editQuery("")
            }
            runCurrent()
            assertEquals(1, f.profiles.requests)
        }

    /** Held loading, confirmed emptiness and a later source exception remain distinct outcomes. */
    @Test fun loadingEmptyAndFailureRemainDistinct() =
        scenario { f ->
            val loaded = CompletableDeferred<Unit>()
            val failed = CompletableDeferred<Unit>()
            f.sites.handler = { collector ->
                loaded.await()
                collector.emit(emptyList())
                failed.await()
                throw IOException("all-record read failed")
            }
            try {
                runCurrent()
                assertEquals(AdminBrowseContent.Loading, f.model.state.value.content)
                assertEquals(1, f.sites.active)
                loaded.complete(Unit)
                runCurrent()
                assertEquals(AdminBrowseContent.Campsites(emptyList()), f.model.state.value.content)
                failed.complete(Unit)
                runCurrent()
                assertEquals(AdminBrowseContent.Failure, f.model.state.value.content)
            } finally {
                loaded.complete(Unit)
                failed.complete(Unit)
            }
        }

    /** Failed-source retry keeps input and sets its loading guard before starting another read. */
    @Test fun explicitRetryIsGuardedAndPreservesInput() =
        scenario(
            matchesSite = { _, _ -> true },
        ) { f ->
            f.sites.rows.value = Result.failure(IOException("first read"))
            f.model.editQuery("  retained  ")
            runCurrent()
            assertEquals(AdminBrowseContent.Failure, f.model.state.value.content)
            val gate = CompletableDeferred<Unit>()
            f.sites.handler = { collector ->
                gate.await()
                collector.emit(f.siteRows)
                awaitCancellation()
            }
