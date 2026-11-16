# Published campsite editor (CS1)

Open an owner's published campsite, then choose **Edit campsite**.

The editor uses the CS1 implementation from [ref@44c4c47](https://github.com/CMPUT301F26stderr/ref/tree/44c4c47c224722a68677d6f5af9103d6fa14a761). The 12 files listed in [reference-files.json](reference-files.json) match their recorded Git blob hashes. Shared factories and application hosts connect it to the current `main` baseline.

`CampsiteEditReferenceRenderTest` exports these screenshots from the real screens with static preview data. The enlarged captures use 2× font and control scaling, matching the reference previews.

| Screen | Normal text | 2× text |
| --- | --- | --- |
| Editor menu | [View](menu-1x.png) | [View](menu-2x.png) |
| Basics | [View](basics-1x.png) | [View](basics-2x.png) |
| Independent photo controls | [View](photo-1x.png) | [View](photo-2x.png) |
| Failed details save | | [View](failed-save-2x.png) |

## Verification

- `bash scripts/check.sh :app:assembleDebugAndroidTest :app:compileFirebaseDebugKotlin` passed: Android Lint, 1,140 unit tests, debug APK and test APK builds, and FirebaseDebug compilation.
- AndroidJUnitRunner passed 40 device tests on the Pixel 10 Pro XL Android 17 emulator. The selected suites were `CampsiteEditFlowTest`, `CampsiteEditReferenceRenderTest`, `CampsiteNavigationTest`, `CampsiteSetupFlowTest`, `CampsiteDraftSaveFlowTest` and `BookingFlowTest`.
- Manual pre-commit checks passed for the changed files, including Kotlin formatting and the checkout secret scan.
- All 12 source hashes matched the pinned reference after verification.

Details Save updates the existing campsite. Photo Save/Remove actions use their separate controller, and availability opens its existing editor. A pending photo choice remains in the editor after a details save.
