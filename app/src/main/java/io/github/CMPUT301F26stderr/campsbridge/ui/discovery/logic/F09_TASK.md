# Campsite text matching

`matchesCampsiteText(campsite, query)` checks the campsite's name, location label, and description. It trims the query and looks for the whole substring in any one field, ignoring case with `Locale.ROOT`. A blank query matches every campsite.

The function does not split the query into words, change the campsite, search other fields, or read a repository. For example, `" PINE "` matches the name `"Pine Ridge"`, and `"ban"` matches the location `"Banff"`.

`exploreViewModelFactory` supplies this predicate to `ExploreViewModel` as `matchesText`. The debug task example calls the same function. Guest-capacity matching is a separate predicate.

## Checks

Run the supplied contracts, then the full unit suite and coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*CampsiteTextMatchTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

For a local example, run the debug app and open **Demo tools > Task examples > H05**. Search for `river` or `quiet`; River Camp should remain. Clear the search to show the local listings. **Reset example** restores the sample data.
