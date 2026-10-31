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
            try {
                f.model.retry()
                assertEquals(AdminBrowseContent.Loading, f.model.state.value.content)
                f.model.retry()
                runCurrent()
                assertEquals(2, f.sites.requests)
                assertEquals("  retained  ", f.model.state.value.query)
                gate.complete(Unit)
                runCurrent()
                assertEquals(AdminBrowseContent.Campsites(f.siteRows), f.model.state.value.content)
                f.model.retry()
                runCurrent()
                assertEquals(2, f.sites.requests)
            } finally {
                gate.complete(Unit)
            }
        }

    /** Missing predicates cannot hide the fact that the selected repository read failed. */
    @Test fun sourceFailurePrecedesPendingFilter() =
        scenario { f ->
            f.profiles.rows.value = Result.failure(IOException("profiles unavailable"))
            f.model.selectTab(AdminBrowseTab.PROFILES)
            f.model.editQuery("requested text")
            f.model.selectRole(Role.OWNER)
            runCurrent()
            assertEquals(AdminBrowseContent.Failure, f.model.state.value.content)
            f.profiles.rows.value = Result.success(f.profileRows)
            f.model.retry()
            runCurrent()
            assertEquals(AdminBrowseContent.Pending, f.model.state.value.content)
        }

    /** Passes original sites and raw text to supplied search without another subscription. */
    @Test fun campsiteSearchUsesSuppliedPredicateOnly() {
        val calls = mutableListOf<Pair<Campsite, String>>()
        scenario(matchesSite = { site, raw ->
            calls.add(site to raw)
            when (raw) {
                "  first  " -> site.id == "site/one"
                "second" -> site.id == "site/two"
                else -> error("Unexpected fixed query")
            }
        }) { f ->
            runCurrent()
            assertTrue(calls.isEmpty())
            f.model.editQuery("  first  ")
            assertEquals(f.siteRows.map { it to "  first  " }, calls)
            assertEquals(
                AdminBrowseContent.Campsites(listOf(f.siteRows[0])),
                f.model.state.value.content,
            )
            f.model.editQuery("second")
            assertEquals(
                AdminBrowseContent.Campsites(listOf(f.siteRows[1])),
                f.model.state.value.content,
            )
            runCurrent()
            assertEquals(1, f.sites.requests)
        }
    }

    /** Missing site search stays pending only while requested, with no alternate matcher. */
    @Test fun missingCampsitePredicateIsExplicitUntilCleared() =
        scenario { f ->
            runCurrent()
            f.model.editQuery("River")
            assertEquals(AdminBrowseContent.Pending, f.model.state.value.content)
            f.model.editQuery("   ")
            assertEquals(AdminBrowseContent.Campsites(f.siteRows), f.model.state.value.content)
            assertEquals(1, f.sites.requests)
        }

    /** Profile role filtering uses its supplied result; missing requested capability is pending. */
    @Test fun profileRoleUsesSuppliedFilterOrExplicitPending() {
        val calls = mutableListOf<Pair<List<Account>, Role?>>()
        scenario(filterProfiles = { records, role ->
            calls.add(records to role)
            listOf(records[0], records[1])
        }) { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            assertTrue(calls.isEmpty())
            f.model.selectRole(Role.OWNER)
            assertEquals(listOf(f.profileRows to Role.OWNER), calls)
            assertEquals(
                AdminBrowseContent.Profiles(f.profileRows.take(2)),
                f.model.state.value.content,
            )
            f.model.selectRole(null)
            assertEquals(AdminBrowseContent.Profiles(f.profileRows), f.model.state.value.content)
            assertEquals(1, f.profiles.requests)
        }
        scenario { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            f.model.selectRole(Role.OWNER)
            assertEquals(AdminBrowseContent.Pending, f.model.state.value.content)
            f.model.selectRole(null)
            assertEquals(AdminBrowseContent.Profiles(f.profileRows), f.model.state.value.content)
        }
    }

    /** Passes complete profiles and raw text to supplied search; missing search stays pending. */
    @Test fun profileTextUsesSuppliedPredicateOrExplicitPending() {
        val calls = mutableListOf<Pair<Account, String>>()
        scenario(matchesProfile = { account, raw ->
            calls.add(account to raw)
            account.id == "dual"
        }) { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            f.model.editQuery("  raw query  ")
            assertEquals(f.profileRows.map { it to "  raw query  " }, calls)
            assertEquals(
                AdminBrowseContent.Profiles(listOf(f.profileRows[1])),
                f.model.state.value.content,
            )
            assertEquals(null, calls[1].first.email)
            assertEquals(1, f.profiles.requests)
        }
        scenario { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            runCurrent()
            f.model.editQuery("Alex")
            assertEquals(AdminBrowseContent.Pending, f.model.state.value.content)
            f.model.editQuery("")
            assertEquals(AdminBrowseContent.Profiles(f.profileRows), f.model.state.value.content)
        }
    }

    /** Composed predicates use latest saved records and preserve the supplied role-result order. */
    @Test fun profilePredicatesComposeAgainstLiveRecords() =
        scenario(
            filterProfiles = { records, _ -> listOf(records[0], records[1]) },
            matchesProfile = { account, _ -> account.id == "dual" },
        ) { f ->
            f.model.selectTab(AdminBrowseTab.PROFILES)
            f.model.selectRole(Role.OWNER)
            f.model.editQuery("fixed query")
            runCurrent()
            assertEquals(
                AdminBrowseContent.Profiles(listOf(f.profileRows[1])),
                f.model.state.value.content,
            )
            val latest = f.profileRows[1].copy(name = "Latest saved name")
            f.profiles.rows.value = Result.success(listOf(latest, f.profileRows[0]))
            runCurrent()
            assertEquals(AdminBrowseContent.Profiles(listOf(latest)), f.model.state.value.content)
            assertEquals(1, f.profiles.requests)
        }

