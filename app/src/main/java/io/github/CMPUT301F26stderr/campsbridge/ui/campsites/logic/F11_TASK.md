**H04 / F11: Sort campsites by name**

`sortCampsitesByName` is implemented and supplied by the owner campsite list, Explore and the H04 debug example.

**Contract**

Compare names after lowercasing with `Locale.ROOT`, then use exact case-sensitive campsite IDs to break ties. Return every original record without changing the input list, trimming names, changing spelling or removing duplicates. Empty input returns an empty list.

For example, `Birch`, `aspen` and `Cedar` sort as `aspen`, `Birch`, `Cedar`. Two `Pine Camp` records with IDs `site-b` and `site-a` put `site-a` first.

**Where it is used**

`campsiteListViewModelFactory` and `exploreViewModelFactory` supply `::sortCampsitesByName`. The helper lives beside campsite presentation rules and is shared with discovery. Controllers own source reads, filtering and missing-dependency feedback; the helper only orders supplied records.

Debug's Demo tools > Task examples > H04 shows the same function with local records. Reset example restores the original fixtures.

**When changing the helper**

Read `CampsiteOrderingTest` and keep the signature stable. Limit ordering changes to `CampsiteOrdering.kt`; keep persistence, navigation and caller rules in their existing owners. Run the class from Android Studio, then the unit and coverage checks:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*CampsiteOrderingTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

On Windows, use `gradlew.bat`.
