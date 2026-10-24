package io.github.CMPUT301F26stderr.campsbridge.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.CMPUT301F26stderr.campsbridge.navigation.OnboardingNavigation
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeAccountRepository
import io.github.CMPUT301F26stderr.campsbridge.testsupport.fakes.FakeLocalAppPreferences
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingStep
import io.github.CMPUT301F26stderr.campsbridge.ui.onboarding.OnboardingViewModel
import io.github.CMPUT301F26stderr.campsbridge.ui.preview.OnboardingPreviewContent
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme

/** Debug-only onboarding fixtures; interactive mode stops at the reserved name integration. */
class OnboardingPreviewActivity : ComponentActivity() {
    /** Shows a supplied step or a pending flow without Firebase dependencies or simulated saves.
     * @param savedInstanceState Previous Android activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val step =
            intent.getStringExtra("step")?.let(OnboardingStep::valueOf) ?: OnboardingStep.WELCOME
        val interactive = intent.getBooleanExtra("interactive", false)
        val factory =
            viewModelFactory {
                initializer {
                    OnboardingViewModel(
                        "preview",
                        FakeAccountRepository(),
                        FakeLocalAppPreferences(),
                        createSavedStateHandle(),
                        parseName = null,
                    )
                }
            }
        setContent {
            if (interactive) {
                CampsbridgeTheme { OnboardingNavigation(factory) }
            } else {
                OnboardingPreviewContent(
                    step,
                    intent.getBooleanExtra("failed", false),
                    intent.getBooleanExtra("submitting", false),
                    intent.getBooleanExtra("pending", false),
                    intent.getBooleanExtra("large", false),
                )
            }
        }
    }
}
