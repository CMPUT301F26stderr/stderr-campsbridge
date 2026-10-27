# F09: Match campsite search text

This is an intentionally unfinished handoff. The function stub compiles and its contract tests fail until the reserved body is implemented. The branch already contains its application binding and coverage configuration.

## Your task

Edit only the body and KDoc of `matchesCampsiteText` in `CampsiteTextMatch.kt`. Keep the signature and package unchanged. Do not edit tests, Gradle configuration, factories or another feature's helper.

The function receives one `Campsite` and raw query text, and returns a Boolean:

- Blank query text matches every campsite.
- Trim only the query's outside whitespace.
- Search `campsite.details.name`, `locationLabel` and `description` independently. A substring in any one field matches; it need not be a whole word.
- Lowercase the query and searched fields with `java.util.Locale.ROOT`. Device language settings must not change the result. This is literal substring matching, with no fuzzy spelling, transliteration or internal-whitespace rewriting.
- IDs, owner identity, timezone, rules, amenities and tags are not searchable fields here.
- Keep the query, campsite facts and their collections unchanged. Inputs are nonnull Kotlin values.

For a listing named `River Camp`, in `Jasper, Alberta`, described as `A quiet riverside campsite.`, queries ` RIVER `, `alberta` and `QUIET` match. `Banff`, `River  Camp` and a match spanning two different fields do not.

## Suggested steps

1. Read the function stub and all cases in `CampsiteTextMatchTest`.
2. Run the focused command below and observe the intended failures.
3. Read the completed `parsePositiveCount` example in `app/src/main/java/io/github/CMPUT301F26stderr/campsbridge/ui/common/input/PositiveCountInput.kt`. It shows a small function with explicit inputs, direct field feedback and concise KDoc.
4. Implement only the assigned body, then update its KDoc if needed to describe the behavior accurately.
5. Run the focused command again. All contracts must pass.
6. Run the prepared pure-logic coverage gate. It must measure every executable line and branch in this helper; the lead already configured it.

## Checks

Focused contracts:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*CampsiteTextMatchTest'
```

Prepared coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

## App connection and acceptance

`ExploreViewModel` accepts this predicate as `matchesText`. This branch connects `::matchesCampsiteText` in `exploreViewModelFactory`; do not edit the factory. The shared application branch supplies null until review/integration and contains no alternative text-matching implementation. H04 campsite ordering is already accepted. H06 guest matching remains a separate exercise; this handoff does not require it.

The exercise is ready for review when the focused tests and coverage gate pass and your diff changes only the permitted body/KDoc. Submit the task branch for lead review. The lead checks actual factory/controller integration before removing the pending behavior.

## Android Studio and local example

Update your personal branch from main, then merge the prepared handoff named in the issue. In Android Studio, open `CampsiteTextMatchTest` and run it using the green gutter triangle before editing. The supplied stub intentionally fails its contracts. Implement only the assigned body and KDoc, then rerun that class and the full unit/coverage command above. On Windows use `gradlew.bat`.

Select the debug variant, run app and open **Demo tools → Task examples → H05**. Both production and this local example use the same assigned function; no factory, Firebase or ADB setup is required. Before implementation, the compiling placeholder returns false, so the list is empty. After implementation: Search for river or quiet; River Camp should remain. Clear the search to show all three listings. Reset example restores the local records.
