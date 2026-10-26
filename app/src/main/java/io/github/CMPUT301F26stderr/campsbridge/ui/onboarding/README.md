# Onboarding

One navigation entry owns the wizard ViewModel and its `SavedStateHandle`. `OnboardingScreen` and the step composables receive state and callbacks. The root session observes the account and local setup marker; it selects the workspace only after setup completes.

Welcome leads to starting intent, then name, then optional camper interests or the owner introduction. Back retains input. Save failures retain the current step and allow the same action to be retried. Account creation uses the established identity on every attempt. The `IN_PROGRESS` marker is persisted before profile creation; `COMPLETED` is written after required profile, capability and workspace operations succeed. Owner activation waits for its observed capability before completion.

Saved state retains the step, intent and unsubmitted field values across supported Android state restoration. A cold launch with an unfinished existing profile resumes the final branch using its confirmed name/preferences and local workspace choice. Dismissing the task before submitting a name does not promise durable draft recovery. Shared profile facts remain in Firestore; the local store contains only setup progress and workspace choice.

## Pending integrations

Display-name parsing is a reserved teammate function. Its result contract is `ui/profile/logic/DisplayNameInput.kt`. `OnboardingViewModel` accepts a nullable parser; the production factory currently passes null. Name input remains editable, but Continue is disabled and the missing validation support is explained. Do not add a temporary production validator. After the function is reviewed, connect `::parseDisplayName` in `onboardingViewModelFactory` and run the complete onboarding checks.

Tests supply a fixed lookup for known fixture names. That lookup exercises the controller contract; it does not implement or test the reserved parser. The separate parser task supplies its own initially failing input/output tests.

The owner introduction disables campsite creation until that feature is registered. Set up later performs actual owner activation and completion. The implemented workspace currently opens Profile; Explore and Campsites will become its entry destinations when those features exist.

## Verification

```sh
nix develop --command bash ./gradlew :app:testDebugUnitTest --tests '*OnboardingViewModelTest'
nix develop .#ui --command bash scripts/check-android-ui.sh
```

ViewModel tests cover branching, profile/completion boundaries, retry identity, optional preferences, restoration, duplicate actions and cancellation. Content tests exercise controlled fields, feedback, native actions and enlarged text. Route tests use the real root/ViewModels with explicit dependency fakes. Activity recreation verifies navigation-owned draft retention. Firebase onboarding tests verify owner activation/completion and entrant preferences through real account repositories.

The themed `@Preview` functions are at the bottom of `OnboardingScreen.kt` in `src/main`; open that file in Android Studio's Split or Design view. They use sample state without repositories or Firebase. The debug-only `OnboardingPreviewActivity` reuses the same passive content for device inspection:

```sh
adb shell am start -S -n io.github.CMPUT301F26stderr.campsbridge/.demo.OnboardingPreviewActivity --es step INTERESTS
```

Supported steps are WELCOME, INTENT, NAME, INTERESTS and OWNER. Boolean extras `large`, `failed`, `submitting` and `pending` select review states. The welcome vector is adapted from the approved local welcome prototype; ordinary controls use the shared Material theme.
