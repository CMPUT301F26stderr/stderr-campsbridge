# UI conventions

Use these conventions when implementing UI features. Keep screen designs and storyboards in the team Wiki. Add the preferred dependencies below as their features are implemented.

The visual intent, interaction patterns, and approved prototypes are recorded in the [team Wiki](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki). The shared theme now supplies white surfaces, green accents, bundled Inter, spacing, and account font/control scaling. Wallpaper colors and automatic dark mode do not replace the approved palette.

## Dependency policy

Prefer Jetpack and Android platform components. Use official Google SDKs for Firebase, maps, and scanning. Allow a small number of established libraries when they remove substantial infrastructure work.

| Feature | Preferred approach |
| --- | --- |
| Screens, forms, dialogs, navigation | Compose, Material 3, and Jetpack Navigation |
| Featured carousel | Compose carousel or `HorizontalPager`; ranking remains application logic |
| Date/range selection | Material 3 `DatePicker` / `DateRangePicker` |
| Booked/free-night calendar | A shared Compose component initially; consider a calendar library if the design becomes substantial |
| Photo selection | Android Photo Picker through Activity Result contracts |
| Maps | Google Maps Compose; project/API setup is required |
| QR scanning | Google Code Scanner; depends on Google Play services |
| QR generation | ZXing; generate from the stable campsite identity |
| Remote image display | Coil for downloading, decoding, resizing, caching, and loading/error states |
| CSV export | A small tested encoder with correct escaping, connected to Android file creation/sharing APIs |

Coil and ZXing are the initial community-library exceptions. Check Android/Compose compatibility and licensing when selecting versions. Add application dependencies through the Gradle version catalog; development tools belong in the Nix flake.

## Shared foundation

Use `AppScreen` for app bars, system/keyboard insets, gutters, optional bottom actions, and snackbar placement. Do not add a permanent header divider. Use `StepScaffold` for focused wizard steps: short content keeps whitespace and long/enlarged content can scroll while Next stays reachable.

`AppActionButton` blocks repeated taps while submitting. `AppTextField` handles labels, errors, and IME callbacks; callers supply validation. `LoadingContent`, `EmptyContent`, and `FailureContent` distinguish outcomes. `ConfirmationDialog` separates confirm, cancel, and dismissal. Compose ordinary Material components directly when a shared wrapper adds no useful behavior.

Run the debug gallery with Android Studio's `demo.DemoActivity`, or launch its component with adb. It demonstrates local interactions and feedback without signing in or performing repository writes. Default and enlarged-text previews are colocated with the gallery. The gallery is absent from release builds.

## Components and state

- Follow the agreed prototypes and shared theme. Use standard Material components when they fit.
- Share feature components such as `CampsiteCalendar` and `CampsiteMap`, with application models and callbacks as their public API. Keep specialized library details within their integration components.
- Screen content receives state and callbacks. Routes connect ViewModels and navigation. Reusable components do not construct repositories or acquire ViewModels.
- Keep lottery eligibility, availability validation, booking, refunds, and persistence in application/repository logic. Date-picker selection must still validate every occupied night.
- Trigger writes through explicit user actions. Display confirmed success only after the repository operation succeeds.
- Keep loading, empty, failure, and in-progress states explicit. Preserve entered data after failures.

## Images

`ImageSlotController` owns one immutable `ImageTarget`, selected content, attachment observation, serialized actions, and transient download references. Its parent ViewModel owns the lifetime. Selection/cancellation/discard do not upload; Save is explicit. Failed saves retain the candidate. An unconfirmed publication blocks another write until a matching server read establishes success; presentation code never deletes Storage blobs directly.

Routes use the platform Photo Picker and `PhotoContentReader`. The reader checks MIME/access on IO and supplies a fresh lazy stream factory; the existing image repository opens/closes the stream. Shared `ImageSlot` and `RemoteImage` content use Material controls and Coil, including its OkHttp network module. Decode/URL errors retry display; they do not reupload.

The profile currently connects this path to its existing account's image slot. The debug-only FileProvider exposes only the test-photo cache directory to support instrumented fixtures; release builds do not register it.

## Accessibility and previews

- Use shared colors, typography, spacing, and shapes; put user-visible text in string resources.
- Preserve system font scaling and apply the account's font/control scaling consistently. Use layouts that tolerate large text.
- Provide meaningful semantics and labels. Communicate availability and errors with more than color alone; color-blind mode uses a shared palette.
- Add themed previews with realistic fixtures, long text, and enlarged fonts. Keep interactive demos separate from production screens; do not fake successful saves or put throwing placeholders in preview paths.

## References

- [Jetpack libraries](https://developer.android.com/jetpack)
- [Date pickers](https://developer.android.com/develop/ui/compose/components/datepickers), [carousel](https://developer.android.com/develop/ui/compose/components/carousel), and [accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner), and [Maps Compose](https://github.com/googlemaps/android-maps-compose)
- [Image loading guidance](https://developer.android.com/develop/ui/compose/graphics/images/loading), [Coil](https://github.com/coil-kt/coil), and [ZXing](https://github.com/zxing/zxing)
