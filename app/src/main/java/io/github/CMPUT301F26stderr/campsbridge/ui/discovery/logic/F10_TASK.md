# Guest-capacity matching

`matchesGuestCapacity(maxGuests, requestedGuests)` first requires a positive listing capacity. A nonpositive maximum throws `IllegalArgumentException`, even when the request is null or invalid.

With a valid maximum, a null request means no guest filter. Otherwise, the request matches only when it is between one and the maximum, inclusive. Zero, negative, and over-capacity requests return false. Counts stay as `Long` values without narrowing or overflow.

For a maximum of 8, null, 1, 3, and 8 match; 0, -1, and 9 do not. A maximum and request both equal to `Long.MAX_VALUE` match.

`exploreViewModelFactory` supplies this predicate to `ExploreViewModel` as `matchesCapacity`. The debug task example calls the same function. Text matching is a separate predicate.

## Checks

Run the supplied contracts, then the full unit suite and coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*GuestCapacityMatchTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

For a local example, run the debug app and open **Demo tools > Task examples > H06**. Apply a filter for 5 guests: River Camp and Forest Clearing should remain. Apply 9 guests: no listings should remain. **Reset example** restores the sample data.
