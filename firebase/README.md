# Firebase integration

This directory holds the [persistence contract](SCHEMA.md), Firestore/Storage rules, query indexes, and emulator configuration. Kotlin models, codecs, and repository implementations live in the Android module.

## Local development

For backend work, install Node.js 24.12 or newer and the Firebase CLI (`npm install -g firebase-tools`), and make the project JDK and Android SDK available to terminal commands. From the repository root, start local services with:

```sh
npm --prefix firebase/functions ci --ignore-scripts --no-audit --no-fund
firebase emulators:start --config firebase/firebase.json --project demo-stderr
```

`demo-stderr` is an emulator-only project identifier. Create one `FirebaseClients` instance using an explicitly initialized `FirebaseApp`, then pass it to `AppContainer`. Android emulator clients use host `10.0.2.2`; other devices require a reachable host. Configure emulator connections before using the clients. Firestore and Storage rules require signed-in accounts. Booking and cancellation go through the authenticated `reserve` and `cancelReservation` callable functions; direct reservation/calendar-claim writes are denied. Deploy the functions and rules together before using these operations in a cloud build.

## Application integration

The functions validate the full stay, claim or release every night, and write the payment and notification in one transaction. They use server time in cloud builds and pinned Moment Timezone data for campsite-local dates. The seed uses the same date conversion. Emulator contract tests supply a controlled clock. Server dependencies are pinned in `functions/package-lock.json`; Firebase Admin uses Apache-2.0; Firebase Functions, Moment.js and Moment Timezone use the MIT license.

The container exposes domain repository interfaces and account-scoped `PendingWork`. Ready foreground sessions observe owner lotteries and entrant/owner reservations, draw due lotteries, and resume their durable inbox jobs with bounded retries. `AdminMaintenance.resume(adminId)` separately authorizes global removal, draw, delivery and obsolete-image cleanup. Inbox delivery creates Firestore receipts. Ready Android sessions publish system alerts when account settings and Android permission allow them. Closed-app scheduling and FCM delivery remain separate integrations.

The `firebaseDebug` and `release` variants use the checked-in `app/google-services.json` for cloud resources. Anonymous Auth must be enabled in that project. The persistence repositories themselves still accept explicitly configured clients.

The normal `debug` launcher is an offline demo and does not connect to Firebase. Select `firebaseDebug` in Android Studio for backend work; no activity-launch commands are needed.

For a Firebase debug APK connected entirely to the disposable `demo-stderr` emulators:

```bash
bash ./gradlew :app:assembleFirebaseDebug -PfirebaseEmulatorHost=10.0.2.2
```

Use a host reachable from the device. `AppContainerFactory` configures Auth, Firestore, Storage, and Functions together before use. Omitting the property selects cloud configuration in Firebase-enabled variants; use the same property for subsequent Firebase builds until returning to cloud. The property does not change the offline `debug` launcher. Startup establishes identity and reads a profile; it does not create sample accounts. Seed development accounts deliberately through emulator tooling when testing existing-user flows. Keep cloud deployment procedures in the team Wiki.

## Testing and pending work

Local domain, document-contract, and listener tests run through `bash scripts/check.sh`.

Run the real rules and Kotlin repository contracts with:

```sh
bash scripts/check-firebase.sh
```

The command installs pinned Node test dependencies, starts disposable Auth/Firestore/Storage/Functions services for `demo-stderr`, runs both suites, and shuts down the services. Each invocation reserves random available loopback ports for the services, Firestore websocket, hub and logging; Kotlin and rules tests use the addresses exported by the CLI. Temporary configuration and hub files are isolated per run so self-hosted runners can share a VM. The checked-in configuration keeps the usual ports for manual development. The command does not import or export persistent data. Kotlin tests reset Firestore and Storage between scenarios and use an authenticated Robolectric host with real SDK clients. No Android virtual device is required. CI requires this suite in the `Firebase contracts` job.

Prepare each target project and rehearsal with the [Halfway setup and seed commands](tooling/README.md). They configure anonymous Auth, deploy the matching rules/indexes and booking functions, and populate records for the installed actors.

Closed-app scheduling and FCM push delivery remain outside the Halfway app. Ready foreground sessions already run account-scoped draw/inbox recovery; global removal/image recovery remains an explicit administrative operation.
