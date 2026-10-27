# H04 / F11: Sort campsites by name

This is a deliberately unfinished, expected-red handoff. The tests compile against a stub and fail for non-empty input until the function is implemented. This task branch includes its application wiring and coverage configuration; the shared application branch keeps the dependent ordering explicitly pending until integration.

## Starting branch and local example

The prepared `part3/h04-campsite-ordering` branch contains this task's unchanged contract tests, unfinished function, coverage and production/debug bindings. After its baseline is accepted and the issue becomes Ready, fetch and merge it into your personal `part3/<name>` branch using Android Studio Git > Merge. Preserve your work and keep unrelated tasks out of an open PR. Do not merge the unfinished handoff directly to main.

Run `CampsiteOrderingTest.kt` using its Android Studio green triangle before editing. The documented starting failures are intentional. Implement only the named function body and KDoc, then run the same tests and the full unit/coverage command. Tests, factories and Gradle changes are supplied and must stay unchanged. No Firebase, adb, Compose, coroutine or architecture work is required.

Select debug, run app and choose Demo tools > Task examples > H04 after completing the function. Reset example restores the original fixtures. H04 starts with River Camp, Alpine Meadow and Forest Clearing; the completed helper displays Alpine Meadow, Forest Clearing, River Camp. Peer helpers may remain pending; this example uses your real assigned function and local records.

## Your task

Edit only the body and KDoc of `sortCampsitesByName` in `CampsiteOrdering.kt`. Do not edit the tests, Gradle files, or other application files.

The function receives a list of `Campsite` records and returns a list containing every same record in this order:

1. Compare each campsite name after lowercasing it with `Locale.ROOT`.
2. When those lowercase names are equal, compare the exact campsite IDs in ascending, case-sensitive order.

Keep the caller's list and campsite records unchanged. Do not trim names, change their spelling, remove duplicates, or consult the device's language settings. The input is not null. An empty list returns an empty list, and a one-item list returns that item.

For example, records named `Birch`, `aspen`, and `Cedar` return in the order `aspen`, `Birch`, `Cedar`. Two records named `Pine Camp` with IDs `site-b` and `site-a` return with `site-a` first.

## Suggested steps

1. Read the compiling function stub and all cases in `CampsiteOrderingTest`.
2. Run the focused test command below once. The non-empty cases are expected to fail before implementation.
3. Read the completed `parsePositiveCount` example in `app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/common/input/PositiveCountInput.kt`. It shows the project's approach: a small named Kotlin function, explicit inputs, direct behavior, and concise KDoc.
4. Implement the ordering in the permitted function only. Keep the list immutable from the caller's point of view.
5. Run the same focused test command again. All cases should pass.
6. Run the pure-logic coverage command below. The lead has already prepared the coverage configuration for this task; do not change it.

## Checks

Focused contract tests:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*CampsiteOrderingTest'
```

Pure-logic coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection

`CampsiteListViewModel` accepts this helper through its `sortCampsites` parameter. This B2 handoff connects `::sortCampsitesByName` in `campsiteListViewModelFactory` and the H04 local example. Do not edit the factory. Discovery is a later baseline and is outside this handoff; the lead reuses the accepted helper there when that caller is published. The shared baseline supplies null until review and integration; it has no substitute ordering implementation.

The exercise is complete when the focused tests and coverage gate pass and the implementation changes only the assigned function and its KDoc. The lead verifies the containing controller/route behavior before integration.
