# Filter participant entries by exact state

This is an intentionally unfinished handoff. The function compiles and its contracts fail until its reserved body is implemented. This branch prepares the production factory binding and measured coverage gate.

## Your task

Edit only the body and KDoc of `filterParticipantsByState` in `ParticipantStateFilter.kt`. Keep its package and signature unchanged. Do not edit tests, controllers, factories, Gradle configuration or another helper.

Inputs are a list of `LotteryEntry` values and an optional `EntryState`:

- Null state retains all supplied entries.
- Nonnull state retains only entries whose state equals that value.
- Keep the original relative order and complete entry values.
- Do not change the input list, including a mutable list.
- Empty input or no matching entries returns an empty list.

This function does not sort, deduplicate, read the clock, derive lifecycle, query Firebase or change an entry's state. There are no extra owner, account or timing checks to invent. The caller owns those boundaries and supplies safe values.

Examples in source order:

| Entries | Filter | Result |
| --- | --- | --- |
| A Waiting, B Withdrawn, C Waiting | null | A, B, C |
| A Waiting, B Withdrawn, C Waiting | WAITING | A, C |
| A Waiting | OFFERED | empty |
| empty | any state or null | empty |

## Suggested steps

1. Read `LotteryEntry`, `EntryState`, the supplied stub and `ParticipantStateFilterTest`.
2. Run the focused tests below and observe their assertion failures.
3. Read the completed `lotteryMembershipStatus` in the parent lotteries package as an example of a small explicit function. Do not copy its lifecycle rules into this task.
4. Implement the permitted body and describe its completed behavior in KDoc.
5. Rerun the focused tests and the prepared coverage gate.

## Checks

Focused contracts:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*ParticipantStateFilterTest'
```

Measured coverage:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection and acceptance

`lotteryDetailViewModelFactory` connects this function as `filterParticipants`. Its saved parent owns the filter selection; `LotteryParticipantsController` invokes this helper only for a requested exact state. All participants works without invoking it. Before draw completion the controller masks provisional OFFERED/LOST as WAITING and hides outcome filter choices. After completion it supplies actual states. These boundaries remain in the caller.

The shared application branch keeps this dependency null until the body is implemented and reviewed. The pending state is visible instead of fabricating a filtered result. This handoff remains separate from green application work.

Ready for review means the focused contracts and measured 100% executable source-line/source-branch coverage pass, and the diff changes only the permitted body/KDoc. Submit the task branch for lead review; the lead verifies the actual containing controller and factory integration. This task does not implement profile loading, drawing, navigation or persistence.

## Android Studio and local example

Update your personal branch from main, then merge the prepared handoff named in the issue. In Android Studio, open `ParticipantStateFilterTest` and run it using the green gutter triangle before editing. The supplied stub intentionally fails its contracts. Implement only the assigned body and KDoc, then rerun that class and the full unit/coverage command above. On Windows use `gradlew.bat`.

Select the debug variant, run app and open **Demo tools → Task examples → H08**. Both production and this local example use the same assigned function; no factory, Firebase or ADB setup is required. Before implementation, the compiling placeholder returns an empty list for exact-state selections; All still shows both entries. After implementation: Choose All participants to show the two completed-draw entries, Selected (OFFERED) to show Alex, or Not selected (LOST) to show Morgan. All is supplied directly by the controller; exact-state selections use your function. Reset example restores the local records.
