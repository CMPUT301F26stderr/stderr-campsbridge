# H01 / F01: Display-name input

Implement `parseDisplayName(text)` in `DisplayNameInput.kt`. The onboarding and personal-details name actions both call this function through their ViewModel factories. The branch contains a compiling placeholder that returns `Required` for every input; it is deliberately unfinished and must not be merged until its contract tests pass.

## Starting branch and Android Studio

The lead publishes `part3/h01-display-name` as an intentionally unfinished handoff. Merge that branch into your own `part3/<name>` branch using Android Studio Git > Merge, preserving your existing work. Do not merge this handoff directly to main or weaken any tests. Your PR should include the supplied wiring/tests and your completed helper.

Open `DisplayNameInputTest.kt` in Android Studio and run it with the green triangle before editing; its expected assertion failures show the unfinished behavior. After implementation, run it again, then run all unit tests. The terminal commands below are optional equivalents and require a configured JDK/Android SDK; no Nix or adb commands are needed for the exercise.

In the debug app choose Demo tools > Task examples > H01. The host calls this handoff's real function with local records; Reset example restores the original sample. The placeholder does not claim a successful save. Peer task helpers remain independent and may still be pending.

## Permitted change

Edit only the body and KDoc of `parseDisplayName` in `app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/logic/DisplayNameInput.kt`. Keep its signature and result types. Tests, dependency wiring and coverage configuration are supplied; do not edit them to make checks pass. No Firebase, Android, Compose or coroutine code is needed.

## Contract

- Remove surrounding whitespace.
- Return `DisplayNameInput.Required` when nothing remains.
- Otherwise return `DisplayNameInput.Valid` containing the trimmed text.
- Preserve internal whitespace, spelling, case, Unicode and punctuation. Do not add length or character restrictions.
- Do not throw for user input or perform persistence.

Examples: an empty field and a whitespace-only field return `Required`; `"  Alex  Chen  "` returns `Valid("Alex  Chen")`; `"A"` returns `Valid("A")`.

## Steps

1. Read the result types and `DisplayNameInputTest`.
2. Run the focused test command below. With the supplied placeholder, the seven valid-name tests fail; the two required-field tests pass.
3. Read the completed `ui/common/input/PositiveCountInput.kt` and its tests as an example of parsing editable text into an explicit result. This task needs fewer rules.
4. Implement only the permitted function body and rerun the focused tests.
5. Run the full unit suite and coverage gate, then format the changed file and inspect the diff.
6. Attach the test result to the assigned GitHub issue and request the lead's review before merging.

## Commands

Run from the repository root:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*DisplayNameInputTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
nix develop .#repository --command pre-commit run ktlint-format --hook-stage pre-commit --files app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/logic/DisplayNameInput.kt
```

The coverage command requires 100% line and branch coverage for the measured pure functions, including this parser. Run the full unit command after a focused test run so coverage includes all measured functions.

## Production integration and acceptance

`onboardingViewModelFactory` and `profileViewModelFactory` pass `::parseDisplayName` into their respective ViewModels. Name submission maps `Required` to field feedback and persists only `Valid.name`; screens do not duplicate this rule. Production has no temporary fallback validator. Personal-details saving preserves the latest confirmed contacts and unrelated account data.

Acceptance requires all nine contract tests and the full unit/coverage command to pass, only the permitted function body to change, and lead review. The lead runs the onboarding/profile route and Firebase checks before integrating the completed task. This task does not implement editor orchestration, contact validation or campsite creation.

The debug `taskExampleBindings()` passes this same helper into the real local feature controller. Source-only coverage is configured for the function facade; 100% must be verified after the implementation, not inferred from these initially failing tests.
