# Published campsite editor — issue #146

Owner path: **Campsites → published listing → Edit campsite**. Drafts retain **Continue setup**.
The editor uses the existing setup fields, validator, theme and image controls. The `ref` repository was consulted for the section-menu UI only.

Screenshots are exported by `CampsiteEditPreviewsTest` from the real screen with a static fixture:

- [Normal text](editor-font-1.png), 390 dp wide.
- [2× text](editor-font-2.png), 360 dp wide; sections scroll and Save remains reachable.
- [Failed save at 2× text](editor-failure-large.png), with retained input and Retry.

## Local verification

- `bash scripts/check.sh`: Android Lint, all 1,075 unit tests, and debug APK build passed.
- `CampsiteEditFlowTest`: 5 device tests passed through `scripts/check-android-ui.sh`.
- Direct AndroidJUnitRunner invocation: 29 device tests passed across `CampsiteEditPreviewsTest`, `CampsiteNavigationTest`, `CampsiteSetupFlowTest` and `BookingFlowTest`.
- Device: Pixel 10 Pro XL emulator, Android 17.
- Commit hooks passed, including Kotlin formatting and staged secret checks.

The direct runner was used for the broader suite because AGP retained the earlier class filter even when configuration caching was disabled. Its final result was `OK (29 tests)`, with no failures or ignored tests.

No repository, schema or booking persistence implementation changed. The existing booking confirmation unit tests verify that a changed price requires reapproval and changed rules clear the prior agreement. Details editing does not write inventory, reservations or charges; photos retain their independent explicit Save/Remove actions.
