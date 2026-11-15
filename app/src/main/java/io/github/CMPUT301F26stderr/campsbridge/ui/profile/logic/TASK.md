**H01 / F01: Display-name input**

`parseDisplayName` is implemented. The onboarding and personal-details factories both supply it to their ViewModels.

**Contract**

Trim surrounding whitespace. Return `DisplayNameInput.Required` if nothing remains; otherwise return `Valid` with the trimmed name. Preserve internal whitespace, spelling, case, Unicode and punctuation. The helper does not save data or add length or character restrictions.

For example, `"  Alex  Chen  "` becomes `Valid("Alex  Chen")`, and whitespace alone becomes `Required`.

**Where it is used**

`onboardingViewModelFactory` and `profileViewModelFactory` pass `::parseDisplayName`. The ViewModels own field feedback, explicit saves and preservation of unrelated account data. An explicitly missing parser is supported in isolated tests and previews; production uses the completed helper.

Debug's Demo tools > Task examples > H01 uses this same function with local records. Reset example restores its fixtures.

**When changing the helper**

Read `DisplayNameInputTest` and keep the signature and result types stable. Limit parser changes to `DisplayNameInput.kt`; editor orchestration and contact validation belong to their existing owners. Run the class from Android Studio, then the unit and coverage checks:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*DisplayNameInputTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

On Windows, use `gradlew.bat`. The supplied parser contracts cover valid, blank and boundary input; do not weaken them to accommodate a behavior change.
