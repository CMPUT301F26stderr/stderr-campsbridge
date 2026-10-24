package io.github.CMPUT301F26stderr.campsbridge.ui.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import io.github.CMPUT301F26stderr.campsbridge.domain.account.AccountSettings
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Preferences
import io.github.CMPUT301F26stderr.campsbridge.domain.account.Role
import io.github.CMPUT301F26stderr.campsbridge.ui.theme.CampsbridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Content contracts: supplied state and callbacks, independent of repositories. */
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Welcome opens guidelines locally and reports only its real continuation action. */
    @Test fun welcomeAndGuidelines() {
        var next = 0
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(isLoading = false),
                    { next++ },
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Guidelines").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Get started").performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }

    /** A public intent reports the exact selected capability; Next needs a supplied choice. */
    @Test fun intentSelectionIsControlled() {
        var selected: Role? = null
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(OnboardingStep.INTENT, isLoading = false),
                    {},
                    {},
                    { selected = it },
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Manage campsites").performClick()
        compose.runOnIdle { assertEquals(Role.OWNER, selected) }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("Administrator").assertDoesNotExist()
    }

    /** Name input, required feedback, retained value and save error are separately observable. */
    @Test fun nameErrorsRetainInputAndReportEdits() {
        var input = ""
        val name = mutableStateOf("Alex")
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        OnboardingStep.NAME,
                        OnboardingDraft(name = name.value),
                        isLoading = false,
                        nameRequired = true,
                        failed = true,
                    ),
                    {},
                    {},
                    {},
                    {
                        input = it
                        name.value = it
                    },
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Enter your name to continue.").assertIsDisplayed()
        compose.onNodeWithText("Alex").performTextReplacement("Taylor")
        compose.runOnIdle { assertEquals("Taylor", input) }
        compose.onNodeWithText("Taylor").assertIsDisplayed()
        compose
            .onNodeWithText("Could not save your changes. Your input is still here. Try again.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** A reserved parser is an explicit pending integration, not an accepted name. */
    @Test fun pendingNameValidationBlocksSubmission() {
        var next = 0
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        OnboardingStep.NAME,
                        isLoading = false,
                        nameValidationPending = true,
                    ),
                    { next++ },
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose
            .onNodeWithText("Name setup is awaiting validation support in this build.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, next) }
    }

    /** Submitting disables form edits and all step exits. */
    @Test fun submittingBlocksInputAndNavigation() {
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        OnboardingStep.NAME,
                        OnboardingDraft(name = "Alex"),
                        isLoading = false,
                        isSubmitting = true,
                    ),
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Alex").assertIsNotEnabled()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
    }

    /** Optional choices preserve unrelated amenities and Skip is a distinct callback. */
    @Test fun interestsAndSkip() {
        val initial = Preferences(amenities = setOf("water"))
        var result: Preferences? = null
        var skips = 0
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        OnboardingStep.INTERESTS,
                        OnboardingDraft(preferences = initial),
                        isLoading = false,
                    ),
                    {},
                    {},
                    {},
                    {},
                    { result = it },
                    { skips++ },
                    {},
                )
            }
        }
        compose.onNodeWithText("Lakeside").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.copy(tags = setOf("lakeside")), result) }
        compose.onNodeWithText("Skip for now").performClick()
        compose.runOnIdle { assertEquals(1, skips) }
    }

    /** Owner completion works while the future campsite editor remains unavailable. */
    @Test fun ownerSetupHasAnHonestFeatureBoundary() {
        var completions = 0
        compose.setContent {
            CampsbridgeTheme {
                OnboardingScreen(
                    OnboardingUiState(OnboardingStep.OWNER, isLoading = false),
                    { completions++ },
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Add first campsite").assertIsNotEnabled()
        compose.onNodeWithText("Set up later").performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    /** Large system and account fonts preserve access to the last choice and fixed actions. */
    @Test fun enlargedInterestsRemainReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CampsbridgeTheme(AccountSettings(fontScale = 1.25, controlScale = 1.5)) {
                    OnboardingScreen(
                        OnboardingUiState(OnboardingStep.INTERESTS, isLoading = false),
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Stargazing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Finish setup").assertIsDisplayed()
        compose.onNodeWithText("Skip for now").assertIsDisplayed()
    }
}
