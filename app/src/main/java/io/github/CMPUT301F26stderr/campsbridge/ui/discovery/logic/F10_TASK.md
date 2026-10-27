# F10: Match a requested group size

This is an intentionally unfinished handoff. The function stub compiles and its contract tests fail until the reserved body is implemented. The branch already contains its application binding and coverage configuration.

## Your task

Edit only the body and KDoc of `matchesGuestCapacity` in `GuestCapacityMatch.kt`. Keep the signature and package unchanged. Do not edit tests, Gradle configuration, factories or another feature's helper.

The function receives `maxGuests: Long` and `requestedGuests: Long?`, and returns a Boolean:

- Check the listing maximum first. A nonpositive `maxGuests` throws `IllegalArgumentException`, even when the request is null or invalid.
- With a valid maximum, null means no guest constraint and returns true.
- A supplied request matches only when it is between one and `maxGuests`, inclusive.
- Zero/negative requests and requests above the valid maximum return false; they do not throw.
- Preserve Long-sized comparisons. Do not narrow counts to Int or change the input parser.

There is no repository, UI, time or saved-state work in this function.

For a maximum of 8, null, 1, 3 and 8 match; 0, -1 and 9 do not. A maximum of 0 always throws. A maximum and request both equal to `Long.MAX_VALUE` match.

## Suggested steps

1. Read the function stub and all cases in `GuestCapacityMatchTest`.
2. Run the focused command below and observe the intended failures.
3. Read the completed `parsePositiveCount` example in `app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/common/input/PositiveCountInput.kt`. It shows a small function with explicit inputs, direct field feedback and concise KDoc.
4. Implement only the assigned body, then update its KDoc if needed to describe the behavior accurately.
5. Run the focused command again. All contracts must pass.
6. Run the prepared pure-logic coverage gate. It must measure every executable line and branch in this helper; the lead already configured it.

## Checks

Focused contracts:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*GuestCapacityMatchTest'
```

Prepared coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection and acceptance

`ExploreViewModel` accepts this predicate as `matchesCapacity`. This branch connects `::matchesGuestCapacity` in `exploreViewModelFactory`; do not edit the factory. The shared application branch supplies null until review/integration and contains no alternative guest-matching implementation. H04 ordering is already integrated. H05 text matching is an independent handoff, so implementing only H06 does not complete discovery integration.

The exercise is ready for review when the focused tests and coverage gate pass and your diff changes only the permitted body/KDoc. Submit the task branch for lead review. The lead checks actual factory/controller integration before removing the pending behavior.

## Android Studio and local example

Update your personal branch from main, then merge the prepared handoff named in the issue. In Android Studio, open `GuestCapacityMatchTest` and run it using the green gutter triangle before editing. The supplied stub intentionally fails its contracts. Implement only the assigned body and KDoc, then rerun that class and the full unit/coverage command above. On Windows use `gradlew.bat`.

Select the debug variant, run app and open **Demo tools → Task examples → H06**. Both production and this local example use the same assigned function; no factory, Firebase or ADB setup is required. Before implementation, the compiling placeholder returns false, so the list is empty. After implementation: Open Filters and apply 5 guests: River Camp and Forest Clearing should remain. Apply 9 guests: no listings should remain. Reset example restores the local records.
