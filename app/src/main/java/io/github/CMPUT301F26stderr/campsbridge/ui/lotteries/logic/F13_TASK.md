# F13: Validate lottery schedule ordering

H07 implements this contract in `validateLotteryWindows`. Production lottery setup and the debug H07 example call the same function; its supplied tests and coverage gate verify the behavior below.

## Contract

`validateLotteryWindows` lives in `LotteryWindowInput.kt` and uses the supplied `LotteryWindowInput` result types. It is a pure ordering rule; persistence and publication checks belong to their existing owners.

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

## Checks

Focused contracts:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*LotteryWindowInputTest'
```

Full unit suite and coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection

`lotterySetupViewModelFactory` supplies `::validateLotteryWindows` to `LotterySetupViewModel` as `validateWindows`. The ViewModel first requires explicit local date/time fields, converts through the existing site-zone/DST helper and confirms any changed-zone interpretation. Only then does it invoke this four-Instant contract. An explicitly null validator remains available for pending-dependency scenarios; production uses the completed function.

The coverage gate requires 100% executable source-line/source-branch coverage for this helper. Lottery persistence, publication recovery and participant filtering remain separate responsibilities.

## Android Studio and local example

In Android Studio, open `LotteryWindowInputTest` and run it using the green gutter triangle. All supplied cases should pass. The full unit/coverage command above checks the complete measured suite. On Windows use `gradlew.bat`.

Select the debug variant, run app and open **Demo tools → Task examples → H07**. Both production and this local example use the same function; no factory, Firebase or ADB setup is required. Try Valid, Registration reversed, Booking overlaps and Booking reversed. Compare the actual result with the expected result shown below the supplied instants. This view does not save or publish. Reset example restores the local records.
