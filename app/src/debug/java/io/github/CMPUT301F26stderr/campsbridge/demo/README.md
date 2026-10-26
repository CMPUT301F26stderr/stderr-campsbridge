# Offline debug demo

Select the `debug` variant in Android Studio and run `app`. The launcher opens an existing entrant profile using the real root, profile, image and navigation code with local repository implementations. Firebase auto-initialization is removed from the debug manifest; the host never accesses Firebase application services.

**Demo tools** offers existing entrant, existing owner, new account, reset scenario, onboarding previews and the component gallery. Reset recreates the selected scenario. Scenario switching clears its ViewModel store and subscriptions before showing the next graph. Activity recreation retains the graph; finishing the host or restarting the process discards its in-memory state. Photo bytes are private cache files and can be evicted by Android; they are never uploaded.

Implemented local interactions include profile observation, capability-limited workspace switching, native photo selection, explicit photo save/replacement/removal, and account/preference operations used by onboarding. The new-account scenario stops at the reserved display-name validator. The preview chooser displays later steps without saving or advancing; Back returns to the demo host. Campsite screens, bookings, lottery coordination and push delivery are not simulated.

`AppRepositories` is the feature dependency boundary. `DemoAccountRepository`, `DemoAppPreferences` and `DemoImageRepository` provide local behavior without unit-test failure hooks. Add demo behavior alongside the first real consumer. Keep shared business rules in their existing owner and never implement reserved teammate functions as a demo fallback.

Select `firebaseDebug` to run the real Firebase-backed app, or build `release` for delivery. These variants use `MainActivity` and exclude the demo launcher, menu and local repositories. The existing `firebaseEmulatorHost` Gradle property selects emulator endpoints only for Firebase-enabled hosts; see the [Firebase guide](../../../../../../../../../firebase/README.md).

Run controller/data contracts with `:app:testDebugUnitTest --tests '*demo.*'`. Device tests in `demo/OfflineDemoTest.kt` launch the package's default launcher, verify real content and scenario controls, and assert that no Firebase app has been initialized.
