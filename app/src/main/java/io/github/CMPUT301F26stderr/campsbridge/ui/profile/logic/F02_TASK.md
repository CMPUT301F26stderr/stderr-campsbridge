# H02 / F02: Optional email and phone input

Implement `parseOptionalEmail` and `parseOptionalPhone` in `OptionalContactInput.kt`. Each function takes editable text and returns `OptionalContactInput.Valid(value)` or `Invalid`. The initial bodies reject everything on purpose; this prepared branch stays out of main until its tests are green.

## Starting branch and Android Studio

The lead publishes `part3/h02-optional-contact` as an intentionally unfinished handoff. Merge that branch into your own `part3/<name>` branch using Android Studio Git > Merge, preserving your existing work. Do not merge this handoff directly to main or weaken any tests. Your PR should include the supplied wiring/tests and your completed helper.

Open `OptionalContactInputTest.kt` in Android Studio and run it with the green triangle before editing; its expected assertion failures show the unfinished behavior. After implementation, run it again, then run all unit tests. The terminal commands below are optional equivalents and require a configured JDK/Android SDK; no Nix or adb commands are needed for the exercise.

In the debug app choose Demo tools > Task examples > H02. The host calls this handoff's real function with local records; Reset example restores the original sample. The placeholder does not claim a successful save. Peer task helpers remain independent and may still be pending.

## Permitted change

Edit only those two function bodies and their KDoc. Keep the signatures/result types, supplied tests, factory wiring and coverage configuration. No Android, Firebase, Compose, coroutine or architecture work is required.

## Contract

For both fields, trim surrounding whitespace. If the trimmed value is blank, return `Valid(null)` to clear the optional saved contact. Preserve accepted spelling, case, internal spaces and punctuation; do not convert a phone to a number or add a length cap. Do not throw for user input or write data.

Email accepts exactly one `@`, nonempty text on each side and no whitespace inside. A dot is not required; contact ownership is not verified.

Phone requires at least one ASCII digit (`0` through `9`). It permits ASCII digits, spaces, parentheses and hyphens, plus at most one `+` at the beginning. Parenthesis balancing and country/length rules are outside this task. Internal tabs/newlines, letters and other punctuation are invalid.

Examples:

| Function | Input | Result |
| --- | --- | --- |
| Either | `"   "` | `Valid(null)` |
| Email | `"  Alex@Example.COM  "` | `Valid("Alex@Example.COM")` |
| Email | `"alex example.com"` | `Invalid` |
| Phone | `"  +1 (780) 123-4567  "` | `Valid("+1 (780) 123-4567")` |
| Phone | `"1+2"` | `Invalid` |

## Steps and commands

1. Read the result type and `OptionalContactInputTest`. Run the focused command and observe the valid/blank cases fail against the supplied placeholders.
2. Read the completed `ui/common/input/PositiveCountInput.kt` and its tests as an example of mapping editable text to an explicit result.
3. Implement one parser at a time; rerun the tests after each change. Keep all supplied tests unchanged.
4. Run the full unit/coverage command; the actual source-line and source-branch coverage gate must be 100%. A test count alone does not establish that coverage.
5. Format only the permitted source file and inspect the diff. Attach command results to the assigned issue and tag the lead for review. Submit a PR once local tests pass; all mandatory PR checks must pass before merging.

From the repository root:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*OptionalContactInputTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
nix develop .#repository --command pre-commit run ktlint-format --hook-stage pre-commit --files app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/profile/logic/OptionalContactInput.kt
```

## Production integration and acceptance

`profileViewModelFactory` passes these functions to `ProfileViewModel`. Contact Save shows a field error for `Invalid`, accepts `Valid(null)` as a cleared value, and calls `AccountRepository.updateProfile` only when both fields are valid. The controller preserves the current confirmed name and unrelated account data. Display-name validation is the independent F01 task.

Acceptance requires the supplied contracts and full unit/coverage command to pass, changes limited to the permitted bodies/KDoc, and lead review. The lead runs the actual profile route and Firebase verification before integration. Do not merge the initial red handoff branch or weaken CI to publish it.

The debug `taskExampleBindings()` passes this same helper into the real local feature controller. Source-only coverage is configured for the function facade; 100% must be verified after the implementation, not inferred from these initially failing tests.
