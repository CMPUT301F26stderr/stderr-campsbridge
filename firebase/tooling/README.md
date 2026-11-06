# Persistence tooling

The Halfway declarations are in `../fixtures/halfway.json`. They include profiles and roles, a published campsite and draft, a three-minute live lottery, an immediately bookable win, and active/past reservation history. Dates use one supplied anchor and the declared campsite timezone. Photos reuse the credited files under `assets/photos/`.

Install the pinned setup dependencies with `npm --prefix firebase/functions ci`. Run setup with an application-default Firebase administrator credential (`GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth application-default login`). Deploy the Halfway backend and apply its declared Auth, function service roles and callable HTTP access:

```sh
bash scripts/setup-halfway.sh YOUR_PROJECT
```

Install the release on the demo devices and complete onboarding. Copy the four Firebase Auth UIDs into a JSON file with `host`, `entrant`, `other` and `admin` keys. These aliases are the fixture's people; existing anonymous accounts remain the installed identities. The seed grants their declared roles, including admin.

```sh
npm --prefix firebase/functions run seed -- --project YOUR_PROJECT --actors /path/to/actors.json --anchor 2026-11-06T18:00:00Z --validate
npm --prefix firebase/functions run seed -- --project YOUR_PROJECT --actors /path/to/actors.json --anchor 2026-11-06T18:00:00Z
```

Use the rehearsal start time as the anchor. Repeat an ordinary seed to add missing records while preserving existing aggregate records and profile edits. The seed writes related Firestore records together after the photo uploads. Add `--reset` to replace the declared campsite/lottery/reservation history and refresh its windows; Auth accounts and unrelated records remain. Setup uploads the declared photos before publishing their attachment metadata. Set `FIREBASE_STORAGE_BUCKET` when targeting a bucket other than the checked-in app's bucket.

Release uses the existing MainActivity, Firebase repositories and Halfway navigation. Reviews/replies/reactions, messaging, announcements, QR/CSV features, moderation actions and closed-app push have no additional UI in this pass. Keep the host app foregrounded for the live draw. Seeded history supports inspection; the live lottery creates new results through the normal application operations.

## Permission verification

Run `bash scripts/check-firebase.sh` from the repository root. It uses disposable Auth, Firestore, Storage and Functions emulators; no cloud data is read or changed. The rules cases run before Kotlin repository integration tests. `scripts/run-firebase-tests.py` retains both suites' output and Firestore evaluator reports in ignored `build/reports/` before emulator shutdown. CI uploads those reports. Evaluator expression exhaustion is a failure even if a denial assertion passed; do not remove cases to fit the budget.
