package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.campsite.Campsite
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.EntryState
import io.github.CMPUT301F26stderr.campsbridge.domain.lottery.LotteryEntry
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeBookingRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLotteryRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeReviewRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityDifference
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.AvailabilityViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteSetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.discovery.ExploreViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryContext
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotteryListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.LotterySetupViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.lotteries.logic.LotteryWindowValidator
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Hosts real root/feature models with isolated dependencies and an explicitly closed lifetime. */
class TestAppHost : AutoCloseable {
    val accounts = FakeAccountRepository(sampleAccount())
    val identity = FakeIdentityProvider()
    val preferences = FakeLocalAppPreferences()
    val images = FakeImageRepository()
    val campsites = FakeCampsiteRepository()

    @Volatile var keyboardVisible = false
        private set
    var participantFilter: ((List<LotteryEntry>, EntryState?) -> List<LotteryEntry>)? = null
    val lotteryAccounts = FakeAccountRepository()
    val lotteries = FakeLotteryRepository()
    val bookings = FakeBookingRepository()
    var lotteryListCreations = 0
    var lotteryDetailCreations = 0
    val reviews = FakeReviewRepository()
    val campsiteOwners = FakeAccountRepository(sampleAccount())
    var newLotteryId: () -> String = {
        java.util.UUID
            .randomUUID()
            .toString()
    }
    var lotterySetupCreations = 0
    var validateLotteryWindows: LotteryWindowValidator? = null
    var exploreCreations = 0
    var matchesText: ((Campsite, String) -> Boolean)? = null
    var matchesCapacity: ((Long, Long?) -> Boolean)? = null
    var availabilityCreations = 0
    var campsiteSetupCreations = 0
    var remainingAvailabilityEdits: AvailabilityDifference? = null
    var campsiteListCreations = 0
    var campsiteDetailCreations = 0
    var profileCreations = 0
    var parseEmail: ((String) -> OptionalContactInput)? = null
    var parsePhone: ((String) -> OptionalContactInput)? = null
    var parseName: ((String) -> DisplayNameInput)? = { input ->
        when (input) {
            "Alex" -> DisplayNameInput.Valid("Alex")
            "Host" -> DisplayNameInput.Valid("Host")
            "" -> DisplayNameInput.Required
            else -> error("Add explicit name fixture: $input")
        }
    }
    private val store = ViewModelStore()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    private val app by lazy {
        AppViewModel(identity, accounts, preferences).also { store.put("app", it) }
    }

    /** Composes production navigation without production bootstrap or network access. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Content() {
        val visible = WindowInsets.isImeVisible
        SideEffect { keyboardVisible = visible }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            AppRoot(app, { id ->
                viewModelFactory {
                    initializer {
                        OnboardingViewModel(
                            id,
                            accounts,
                            preferences,
                            createSavedStateHandle(),
                            parseName,
                        )
                    }
                }
            }, { id ->
                viewModelFactory {
                    initializer {
                        profileCreations++
                        ProfileViewModel(
                            id,
                            accounts,
                            images,
                            createSavedStateHandle(),
                            parseName,
                            parseEmail,
                            parsePhone,
                        )
                    }
                }
            }, { id ->
                viewModelFactory {
                    initializer {
                        campsiteListCreations++
                        CampsiteListViewModel(id, campsites, images, createSavedStateHandle())
                    }
                }
            }, { accountId, id, context ->
                viewModelFactory {
                    initializer {
                        campsiteDetailCreations++
                        CampsiteDetailViewModel(
                            accountId,
                            id,
                            context,
                            campsites,
                            images,
                            campsiteOwners,
                            reviews,
                            createSavedStateHandle(),
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                        )
                    }
                }
            }, { accountId, id ->
                viewModelFactory {
                    initializer {
                        campsiteSetupCreations++
                        CampsiteSetupViewModel(
                            accountId,
                            id,
                            campsites,
                            images,
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                            createSavedStateHandle(),
                            remainingAvailabilityEdits = remainingAvailabilityEdits,
                        )
                    }
                }
            }, { accountId, id ->
                viewModelFactory {
                    initializer {
                        availabilityCreations++
                        AvailabilityViewModel(
                            accountId,
                            id,
                            campsites,
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                            createSavedStateHandle(),
                            remainingAvailabilityEdits,
                        )
                    }
                }
            }, {
                viewModelFactory {
                    initializer {
                        exploreCreations++
                        ExploreViewModel(
                            campsites,
                            images,
                            reviews,
                            createSavedStateHandle(),
                            matchesText,
                            matchesCapacity,
                            clock =
                                Clock.fixed(
                                    Instant.parse("2027-05-01T12:00:00Z"),
                                    ZoneOffset.UTC,
                                ),
                        )
                    }
                }
            }, { ownerId, id ->
                viewModelFactory {
                    initializer {
                        lotterySetupCreations++
                        LotterySetupViewModel(
                            ownerId,
                            campsites,
                            lotteries,
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                            createSavedStateHandle(),
                            validateLotteryWindows,
                            lotteryId = id,
                            newId = newLotteryId,
                        )
                    }
                }
            }, { accountId, id, context ->
                viewModelFactory {
                    initializer {
                        lotteryDetailCreations++
                        LotteryDetailViewModel(
                            accountId,
                            id,
                            lotteries,
                            campsites,
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                            bookings,
                            context,
                            lotteryAccounts,
                            createSavedStateHandle(),
                            participantFilter,
                        )
                    }
                }
            }, { accountId, context, campsiteId ->
                viewModelFactory {
                    initializer {
                        lotteryListCreations++
                        LotteryListViewModel(
                            accountId,
                            context,
                            lotteries,
                            bookings,
                            Clock.fixed(Instant.parse("2027-05-01T12:00:00Z"), ZoneOffset.UTC),
                            createSavedStateHandle(),
                            campsiteId,
                        )
                    }
                }
            })
        }
    }

    /** Cancels every model and graph-owned subscription created by this host. */
    override fun close() {
        store.clear()
    }
}
