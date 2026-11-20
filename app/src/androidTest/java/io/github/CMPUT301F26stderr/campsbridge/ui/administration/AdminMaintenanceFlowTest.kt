package io.github.CMPUT301F26stderr.campsbridge.ui.administration

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import io.github.CMPUT301F26stderr.campsbridge.demo.DemoPreviewData
import io.github.CMPUT301F26stderr.campsbridge.demo.DemoRepositories
import io.github.CMPUT301F26stderr.campsbridge.demo.DemoScenario
import io.github.CMPUT301F26stderr.campsbridge.demo.data.DemoStore
import io.github.CMPUT301F26stderr.campsbridge.demo.data.documents
import io.github.CMPUT301F26stderr.campsbridge.di.AppRepositories
import io.github.CMPUT301F26stderr.campsbridge.di.AppServices
import io.github.CMPUT301F26stderr.campsbridge.di.adminBrowseViewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.domain.maintenance.Maintenance
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeCampsiteRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeIdentityProvider
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeImageRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.ui.components.layout.AppScreen
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** The actual Browse route and production factory reach only the explicit recovery boundary. */
class AdminMaintenanceFlowTest {
    @get:Rule val compose = createComposeRule()

    /** One request shows progress, retains failure and retries through the signed-in identity. */
    @Test fun browseRecoveryUsesTheProductionFactoryAndRetainsRetry() {
        val identity = FakeIdentityProvider()
        val photos = FakeImageRepository()
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val data = DemoPreviewData(DemoScenario.ADMIN)
        val local =
            DemoRepositories(
                DemoStore(data.documents()),
                data,
                File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "maintenance-flow"),
            )
        val repositories =
            object : AppRepositories by local {
                override val campsites = FakeCampsiteRepository()
                override val accounts = FakeAccountRepository()
                override val images = photos
                override val maintenance =
                    Maintenance { actor ->
                        calls += actor
                        if (calls.size == 1) {
                            gate.await()
                            throw IOException("offline")
                        }
                    }
            }
        val services = AppServices(repositories, identity, FakeLocalAppPreferences())
        compose.setContent {
            val factory = remember { adminBrowseViewModelFactory(services) }
            val model: AdminBrowseViewModel = viewModel(factory = factory)
            CampsbridgeTheme {
                AppScreen("Browse") {
                    AdminBrowseRoute(model, {}, {}, {})
                }
            }
        }
        compose.runOnIdle { assertEquals(0, identity.calls) }
        compose.onNodeWithText("Resume pending work").performClick()
        compose.onNodeWithText("Resuming pending work…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Profiles").performClick()
        compose.runOnIdle {
            assertEquals(listOf("account-1"), calls)
            gate.complete(Unit)
        }
        compose.onNodeWithText("Recovery stopped. Completed steps are saved.").assertIsDisplayed()
        compose.onNodeWithText("Retry pending work").performClick()
        compose.onNodeWithText("Recovery pass finished.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("account-1", "account-1"), calls)
            assertEquals(0, photos.replaceCalls + photos.deleteCalls)
        }
    }
}
