# Persistence tooling

The Final development declarations are in `../fixtures/halfway.json`. The filename is historical: the fixture now includes shared lotteries and nullable ratings that the published Halfway Firebase APK doesn't fully support. Keep that demo on its existing Firebase project. Use a separate project for Final and build its Firebase APK with the matching `app/google-services.json`.

The fixture retains the earlier journey and adds a lottery shared by two owners, a reservation at the second owner's campsite, messages in both directions, and rating-only/text-only/combined feedback. Lottery declarations name an organizer, participants and offers; reservations name their selected campsite. Dates use one supplied anchor and the declared campsite timezone. Photos reuse the credited files under `assets/photos/`.

Install the pinned setup dependencies with `npm --prefix firebase/functions ci`. Run setup with an application-default Firebase administrator credential (`GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth application-default login`). Despite its historical filename, this command deploys the current Final rules, indexes, booking/cancellation and scheduled worker, then applies the declared Auth, function service roles and callable HTTP access. It doesn't seed demo records:

```sh
bash scripts/setup-halfway.sh YOUR_PROJECT
```

Install the Final Firebase build on the demo devices and complete onboarding in that project. Copy five distinct Firebase Auth UIDs into a JSON file with `host`, `cohost`, `entrant`, `other` and `admin` keys. These aliases are the fixture's people; existing anonymous accounts remain the installed identities. The seed grants their declared roles, including all three capabilities for the admin account. Set `REHEARSAL_START_UTC` to the rehearsal start time in ISO 8601 UTC format before running the seed.

```sh
npm --prefix firebase/functions run seed -- --project YOUR_PROJECT --actors /path/to/actors.json --anchor "$REHEARSAL_START_UTC" --validate
npm --prefix firebase/functions run seed -- --project YOUR_PROJECT --actors /path/to/actors.json --anchor "$REHEARSAL_START_UTC"
```

Use the rehearsal start time as the anchor. Use `--reset` after changing the schema or declarations, including the Final participant/review changes. An ordinary seed preserves existing campsites, lotteries and their children; it adds missing aggregates and grants declared roles without replacing profile edits. The seed writes related Firestore records together after the photo uploads. Reset replaces the declared campsite/lottery/reservation history and refreshes its windows; Auth accounts and unrelated records remain. The seed uploads the declared photos before publishing their attachment metadata. Set `FIREBASE_STORAGE_BUCKET` when targeting a bucket other than the checked-in app's bucket.

Current source includes the Final collaboration, feedback, reservation conversation and in-app QR flows. The published beta APKs are the Halfway snapshot. CSV export, external QR launches and closed-app push banners remain separate work. Admin Browse's **Resume pending work** action retries saved removals, due draws, Inbox deliveries and obsolete image cleanup through the existing repositories; it doesn't reseed data.

Final deployment/reseeding and a real closed-app rehearsal are still pending. After deploying `processPendingWork`, each minute tick starts due draws and resumes persisted Inbox delivery without a foreground app. Seeded history supports inspection; the live lottery creates new results through the shared draw protocol.

## Permission verification

Run `bash scripts/check-firebase.sh` from the repository root. It checks seed materialization and shared draw-order vectors, then uses disposable Auth, Firestore, Storage and Functions emulators; no cloud data is read or changed. Rules cases run before Node worker persistence and Kotlin repository/worker interoperability tests. The worker tests invoke the same operation as the schedule with controlled time, so they do not wait for a cron tick. Kotlin and Node read the same ranking vectors, and interoperability tests run both implementations against the same isolated documents.

`scripts/run-firebase-tests.py` retains `rules.log`, `worker.log`, `integration.log` and Firestore evaluator reports in ignored `firebase/tooling/build/reports/` before emulator shutdown. CI uploads those reports. Evaluator expression exhaustion is a failure even if a denial assertion passed; do not remove cases to fit the budget. Emulator checks prove persisted behavior; the deployed scheduler trigger still needs the closed-app rehearsal.
