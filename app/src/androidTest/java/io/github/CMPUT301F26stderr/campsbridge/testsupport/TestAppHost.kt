package io.github.CMPUT301F26stderr.campsbridge.testsupport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fixtures.sampleAccount
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppRoot
import io.github.CMPUT301F26stderr.campsbridge.ui.app.AppViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteDetailViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.campsites.CampsiteListViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.ProfileViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.DisplayNameInput
import io.github.CMPUT301F26stderr.campsbridge.ui.profile.logic.OptionalContactInput

/** Hosts real root/feature models with isolated dependencies and an explicitly closed lifetime. */
class TestAppHost : AutoCloseable {
    val accounts = FakeAccountRepository(sampleAccount())
    val identity = FakeIdentityProvider()
    val preferences = FakeLocalAppPreferences()
    val images = FakeImageRepository()
    val campsites = FakeCampsiteRepository()
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
    @Composable
    fun Content() {
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
                        CampsiteDetailViewModel(accountId, id, context, campsites, images)
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
