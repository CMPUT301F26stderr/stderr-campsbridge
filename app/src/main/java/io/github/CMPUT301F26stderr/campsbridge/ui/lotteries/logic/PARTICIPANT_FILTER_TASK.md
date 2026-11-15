**H08: Filter participant entries by exact state**

`filterParticipantsByState` is implemented. `lotteryDetailViewModelFactory` and the H08 debug example use the completed helper.

**Contract**

A null filter keeps every supplied `LotteryEntry`. A nonnull filter keeps only entries with that exact `EntryState`. Preserve relative order and complete records, and do not mutate the input list. Empty input or no matching entries returns an empty list.

The helper does not sort, deduplicate, read time, derive lottery lifecycle, query Firebase or change entry state. `ParticipantStateFilterTest` owns these input/output contracts.

**Where it is used**

The saved lottery parent owns filter selection. `LotteryParticipantsController` calls the helper for an exact-state selection; All participants uses the supplied records directly. The controller masks provisional outcomes before the draw completes and only then exposes actual outcome filters. Those rules stay in the controller.

Debug's Demo tools > Task examples > H08 uses the same function with local records. All participants shows both completed-draw entries, Selected shows Alex, and Not selected shows Morgan. Reset example restores the fixtures. Explicitly missing dependencies remain available for isolated pending-state tests.

**When changing the helper**

Read `LotteryEntry`, `EntryState` and `ParticipantStateFilterTest`. Keep the package and signature stable and limit rule changes to `ParticipantStateFilter.kt`. Run the class from Android Studio, then the unit and coverage checks:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*ParticipantStateFilterTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

On Windows, use `gradlew.bat`.
