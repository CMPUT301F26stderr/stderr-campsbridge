# F13: Validate lottery schedule ordering

This is an intentionally unfinished handoff. Its function stub compiles and its contract tests fail until the reserved body is implemented. Application binding and coverage configuration are already prepared on this branch.

## Your task

Edit only the body and KDoc of `validateLotteryWindows` in `LotteryWindowInput.kt`. Keep its signature, package and supplied result types unchanged. Do not edit tests, factories, Gradle configuration or another feature's helper.

The function receives four `Instant` values: registration opening, registration closing, booking opening and booking closing. Return the first matching result in this order:

1. Registration opening is at or after registration closing: `InvalidRegistration`.
2. Booking opening is before registration closing: `BookingBeforeRegistrationClose`.
3. Booking opening is at or after booking closing: `InvalidBooking`.
4. All checks passed: `Valid`.

Booking may open exactly at registration closing. Both registration and booking must have positive duration. Check earlier rules before later ones, including when several inputs are invalid.

Compare the supplied Instants directly and preserve nanosecond precision. Do not read the current time, convert time zones, parse strings, write records or throw for an invalid schedule. Publication separately checks whether registration opens in the future.

Examples, written as second offsets from any fixed Instant:

| Registration open / close | Booking open / close | Result |
| --- | --- | --- |
| 0 / 10 | 10 / 20 | Valid |
| 10 / 10 | 20 / 30 | InvalidRegistration |
| 0 / 10 | 5 / 0 | BookingBeforeRegistrationClose |
| 0 / 10 | 20 / 20 | InvalidBooking |

## Suggested steps

1. Read the supplied result types, function stub and `LotteryWindowInputTest` cases.
2. Run the focused contracts below and observe their assertion failures.
3. Read the completed `parsePositiveCount` in `ui/common/input/PositiveCountInput.kt` as an example of explicit input and feedback. No parsing is needed here.
4. Implement only the assigned body and update its KDoc to describe the completed behavior.
5. Rerun the contracts, then the prepared coverage gate.

## Checks

Focused contracts:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*LotteryWindowInputTest'
```

Prepared coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection and acceptance

`LotterySetupViewModel` accepts this function as `validateWindows`. It first requires explicit local date/time fields, converts through the existing site-zone/DST helper and confirms any changed-zone interpretation. Only then does it invoke this four-Instant contract. This branch connects `::validateLotteryWindows` in `lotterySetupViewModelFactory`; the shared application branch keeps that dependency null until review and has no substitute ordering function.

The exercise is ready for review when the focused tests and measured 100% executable source-line/source-branch coverage pass and your diff changes only the permitted body/KDoc. Submit the task branch for lead review. The lead verifies actual factory/controller integration. Completing this helper does not implement lottery persistence, publication recovery or participant filtering.

## Android Studio and local example

Update your personal branch from main, then merge the prepared handoff named in the issue. In Android Studio, open `LotteryWindowInputTest` and run it using the green gutter triangle before editing. The supplied stub intentionally fails its contracts. Implement only the assigned body and KDoc, then rerun that class and the full unit/coverage command above. On Windows use `gradlew.bat`.

Select the debug variant, run app and open **Demo tools → Task examples → H07**. Both production and this local example use the same assigned function; no factory, Firebase or ADB setup is required. Before implementation, the compiling placeholder returns InvalidRegistration for every selection. After implementation: Try Valid, Registration reversed, Booking overlaps and Booking reversed. Compare the actual result with the expected result shown below the supplied instants. This view does not save or publish. Reset example restores the local records.
