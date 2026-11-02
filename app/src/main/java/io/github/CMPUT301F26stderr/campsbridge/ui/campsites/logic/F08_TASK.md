# H03 / F08: Find remaining availability changes

This isolated handoff is intentionally unfinished. Its contract tests compile and fail until the function is implemented. The shared application branch keeps date saving explicitly pending; do not merge this unfinished branch or disable its tests to obtain green CI.

## Starting branch and local example

The prepared `part3/h03-availability-differences` branch contains this task's unchanged contract tests, unfinished function, coverage and production/debug bindings. After its baseline is accepted and the issue becomes Ready, fetch and merge it into your personal `part3/<name>` branch using Android Studio Git > Merge. Preserve your work and keep unrelated tasks out of an open PR. Do not merge the unfinished handoff directly to main.

Run `PendingAvailabilityEditsTest.kt` using its Android Studio green triangle before editing. The documented starting failures are intentional. Implement only the named function body and KDoc, then run the same tests and the full unit/coverage command. Tests, factories and Gradle changes are supplied and must stay unchanged. No Firebase, adb, Compose, coroutine or architecture work is required.

Select debug, run app and choose Demo tools > Task examples > H03 after completing the function. Reset example restores the original fixtures. H03 starts in May 2027: May 11 is offered, May 13 is booked and cannot be selected. Withdraw May 11, save, then reset to restore it. Peer helpers may remain pending; this example uses your real assigned function and local records.

## Permitted change

Edit only the body and KDoc of `remainingAvailabilityEdits` in `PendingAvailabilityEdits.kt`. Do not edit the tests, controller, ViewModel, factory or Gradle configuration.

The function receives two maps from `LocalDate` to `Boolean`:

- `requested` contains the owner's desired offered value for edited dates.
- `confirmed` contains known offered values. A date missing from this map means false.

Return a map containing only requested entries whose values differ from their confirmed values. Keep each returned date and requested Boolean unchanged. Do not mutate either input, add unrelated confirmed dates, apply a date limit or perform IO. An empty request returns an empty map. The result's iteration order is not part of the contract; the controller sorts actual writes.

For example, a requested offer for May 14 stays in the result when May 14 is absent or confirmed false. It is excluded when confirmed true. A requested withdrawal for May 15 stays only when May 15 is confirmed true; absent or confirmed false means it is already satisfied.

## Steps

1. Read the function KDoc and each case in `PendingAvailabilityEditsTest`.
2. Run the focused test command below. The unfinished function is expected to fail all cases.
3. Read the completed `permittedWorkspace` function in `ui/app/WorkspaceSelection.kt` for a concise function with immutable inputs and explicit return behavior. `ui/common/input/PositiveCountInput.kt` provides another completed example.
4. Replace only the unfinished function body, keeping its public contract and KDoc accurate.
5. Rerun the focused tests, then the coverage gate. Fix your implementation until both pass; do not change tests or coverage rules.

## Checks

From the repository root:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*PendingAvailabilityEditsTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

The lead has included the production function in the existing source-backed 100% line/branch coverage gate. Keep the measured class present; do not remove or weaken that configuration.

## Application connection

`campsiteSetupViewModelFactory` and `availabilityViewModelFactory` supply this same function through the `remainingAvailabilityEdits` dependency. `CampsiteAvailabilityController` invokes it only for known values; the controller owns unknown ranges, sorting, transactions, failed calls and retry acknowledgements. The shared branch supplies null until this task is reviewed and integrated. This task branch connects the real function reference, so complete the function before trying date editing in the running app; the unfinished body throws rather than pretending a save succeeded.

After the tests and coverage gate pass, submit changes to the function and its KDoc for review. The lead reruns controller, route and Firebase checks before integration. The implemented setup wizard and standalone availability editor both use this controller and the same comparison contract; do not create a second implementation for either screen.
