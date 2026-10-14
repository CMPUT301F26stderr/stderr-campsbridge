# Firebase integration

This directory holds the [persistence contract](SCHEMA.md), Firestore/Storage rules, query indexes, and emulator configuration. Kotlin models, codecs, and repository implementations live in the Android module.

## Local development

From the repository root, start local services with:

```sh
nix develop --command firebase emulators:start --config firebase/firebase.json --project demo-stderr
```

`demo-stderr` is an emulator-only project identifier. Create one `FirebaseClients` instance using an explicitly initialized `FirebaseApp`, then pass it to `AppContainer`. Android emulator clients use host `10.0.2.2`; other devices require a reachable host. Configure emulator connections before using the clients. Firestore and Storage rules require signed-in accounts.

## Application integration

The container exposes domain repository interfaces and `resumePendingWork()` for draws, removal cascades, inbox delivery, and obsolete image cleanup. Its current global queries require an admin-capable client. Application/controller scheduling after initialization and mutations still needs wiring with retry/error feedback. Inbox delivery creates Firestore receipts; OS/FCM push delivery requires a separate platform integration.

Provide Firebase options through bootstrap configuration. This persistence layer compiles without `google-services.json`. Keep cloud environment setup and deployment instructions in the team Wiki.

## Testing and pending work

Local domain, document-contract, and listener tests run through `bash scripts/check.sh`.

Run the real rules and Kotlin repository contracts with:

```sh
nix develop --command bash scripts/check-firebase.sh
```

The command installs pinned Node test dependencies, starts disposable Auth/Firestore/Storage services for `demo-stderr`, runs both suites, and shuts down the services. It does not import or export persistent data. Kotlin tests reset Firestore and Storage between scenarios and use an authenticated Robolectric host with real SDK clients. No Android virtual device is required. CI requires this suite in the `Firebase contracts` job.

Pending work:

- Firebase project configuration and device authentication/bootstrap.
- Application/controller lifecycle wiring and OS/FCM push delivery.
- Seed and migration commands; see [tooling conventions](tooling/README.md).
