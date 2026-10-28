# Optional email and phone input

`parseOptionalEmail` and `parseOptionalPhone` return `OptionalContactInput.Valid(value)` or `Invalid`. Both trim surrounding whitespace. Blank input returns `Valid(null)` so the saved contact can be cleared. Accepted spelling, case, punctuation, and internal spaces are preserved; neither parser adds a length limit or writes data.

Email requires exactly one `@`, nonempty text on each side, and no internal whitespace. A domain dot is not required, and ownership is not verified. For example, `"  Alex@Example.COM  "` becomes `Valid("Alex@Example.COM")`.

Phone requires at least one ASCII digit (`0` through `9`). It allows ASCII digits, ordinary spaces, parentheses, and hyphens, plus one optional `+` at the beginning. Internal tabs/newlines, letters, and other punctuation are invalid. Parenthesis balancing and country/length rules are outside this contract. Accepted phone values remain strings.

`profileViewModelFactory` supplies these parsers to `ProfileViewModel`. Contact Save shows a field error for `Invalid`, accepts `Valid(null)` as a cleared value, and calls `AccountRepository.updateProfile` only when both fields are valid. Display-name validation is separate. The debug task example calls the same contact parsers.

## Checks

Run the supplied contracts, then the full unit suite and coverage gate:

```sh
bash ./gradlew :app:testDebugUnitTest --tests '*OptionalContactInputTest'
bash ./gradlew :app:testDebugUnitTest :app:pureLogicCoverage
```

For a local example, run the debug app and open **Demo tools > Task examples > H02**. Try accepted, invalid, and blank contact values. **Reset example** restores the original sample data; saves in this host remain local.
