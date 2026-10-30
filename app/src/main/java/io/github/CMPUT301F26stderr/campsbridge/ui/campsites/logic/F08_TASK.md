# H03 / F08: Remaining availability changes

`remainingAvailabilityEdits` keeps requested dates whose offered values differ from the confirmed baseline. A date missing from `confirmed` means false. It preserves the requested dates and Boolean values, ignores unrelated confirmed dates, and doesn't mutate either input. An empty request returns an empty map; iteration order isn't part of the contract.

For example, offering an absent date remains a change. Withdrawing an absent date is already satisfied. Withdrawing a confirmed offered date remains a change.

## Application connection

Both `campsiteSetupViewModelFactory` and `availabilityViewModelFactory` use this function by default. `CampsiteAvailabilityController` owns unknown ranges, write ordering, transactions and retries. Tests can explicitly supply a missing comparison to exercise pending behavior.

The H03 Task example uses the same function with local records. In a debug build, open Demo tools > Task examples > H03. May 11, 2027 is offered; May 13 is booked and can't be selected. Withdraw May 11, save, then use Reset example to restore the fixtures. This example doesn't write to Firebase.

## Checks

Run `PendingAvailabilityEditsTest` in Android Studio, or use the Gradle wrapper from the repository root:

```sh
./gradlew :app:testDebugUnitTest --tests '*PendingAvailabilityEditsTest'
./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`. The existing coverage gate requires full line and branch coverage for this helper. Run `CampsiteTaskExamplesFlowTest` under `app/src/androidTest` on an emulator to check the local screen flow.
