# Offline debug demo

Select `debug` in Android Studio and run `app`. The default launcher supplies local repositories to the real root, profile, campsite, discovery and lottery code. It does not initialize Firebase. Use Demo tools to switch between entrant, owner, administrator and new-account scenarios, reset records, or open previews, the component gallery and Task examples.

`DemoPreviewData` keeps campsites, offered/booked nights, lotteries, entries, saved stays, events, receipts and credited photos related under one fixed clock. Scenario switching/reset retires the old graph and its subscriptions. Activity recreation retains it; finishing the host or restarting the process discards in-memory changes.

Profile editing, workspace switching and private cached photo changes work locally. Explore uses labelled sample filtering outcomes. Camp and lottery readers use saved sample records. Campsite saves, lottery coordination, booking, cancellation/refunds and OS notification delivery are unsupported in the offline adapters; they must not report successful simulated transactions.

Reservations, Inbox, booking, notification settings and read-only admin routes are registered but show pending feedback until their feature implementations arrive. Their fixtures and separate demo/test factory contracts are supplied so each feature can be built independently.

Accepted helper implementations remain connected in production and Task examples. Availability differences, participant filtering and later feature helpers stay pending. Task examples inject explicit peer outcomes; they don't implement replacement business rules. Selected photo bytes stay in private cache files, and fixture photos use the shared Unsplash credit catalogue in `assets/`.

Use `firebaseDebug` for real Firebase services, or `release` for delivery. Those variants use `MainActivity` and exclude the demo launcher and local repositories. See [Firebase setup](../../../../../../../../../firebase/README.md) for configuration. Run local demo contracts with `:app:testDebugUnitTest --tests '*demo.*'`; device tests live under `app/src/androidTest`.
