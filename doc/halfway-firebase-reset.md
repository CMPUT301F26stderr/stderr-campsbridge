# Halfway Firebase reset

This is the shared practice project for the published Halfway release APK. Teammates can use it to try the app and create records. Before the presentation, reset it from the checked-in Halfway fixture.

**Set up once**

Use Node.js 24.12 or newer. Install the Firebase function dependencies from the repository root:

```sh
npm --prefix firebase/functions ci
```

Use an administrator credential through `GOOGLE_APPLICATION_CREDENTIALS`, or sign in with `gcloud auth application-default login`. Keep credentials outside the repository. Do not paste them into an issue or commit.

The target project and Storage bucket are in [`firebase/halfway-demo/config.json`](../firebase/halfway-demo/config.json). This tool is for the Halfway release project. Never run Final setup or seed commands against it.

Copy the actor template into the ignored local directory and replace its values with the Auth UIDs from the four installed release-app identities:

```sh
mkdir -p .firebase/halfway-demo
cp firebase/halfway-demo/actors.example.json .firebase/halfway-demo/actors.json
```

The file needs `host`, `camper`, `newcamper` and `admin`. These are existing anonymous Auth identities. Reset changes Firestore records and role bindings but does not delete or recreate Auth accounts.

Run the local contract check when the pinned Firebase CLI and JDK 21 are available:

```sh
python3 scripts/check-halfway-demo.py
```

**Save a practice snapshot**

Let pending saves finish, then force-stop the app on every device before taking a snapshot. Keep sessions stopped until the command finishes.

```sh
node firebase/halfway-demo/cli.cjs snapshot \
  --out .firebase/halfway-demo/baseline
```

A snapshot contains every Firestore document and every Storage object under `images/`, including bytes and metadata. It does not contain Auth accounts, rules, indexes or deployed functions. The output directory must be new; the command does not overwrite an existing snapshot.

**Reset for practice**

Finish pending saves and force-stop all team app sessions first. Then run:

```sh
node firebase/halfway-demo/cli.cjs reset \
  --actors .firebase/halfway-demo/actors.json \
  --mode practice
```

Practice mode anchors the fixture at the current time and closes the active lottery 15 minutes later. Use `--anchor 2026-11-17T18:00:00-07:00` when a repeatable clock is useful.

Before replacing data, the command saves the current project under `.firebase/halfway-demo/backups/<timestamp>`. It then replaces all Firestore documents and Storage objects under `images/` with the fixture, removing practice additions and restoring the shared campsite, lotteries, reservations, notification receipts and images. Auth identities remain in place.

**Reset before the TA demo**

Run this before the presentation, with every app session stopped:

```sh
node firebase/halfway-demo/cli.cjs reset \
  --actors .firebase/halfway-demo/actors.json \
  --mode demo
```

Demo mode uses Tuesday, November 17 at 6:00 PM Edmonton time. Registration closes at 6:08 PM. For another presentation slot, update `demoStart` in the config or supply `--anchor` explicitly. It includes the December 1–3 booking path, a prepared December 9–11 owner rejection, the Forest clearing draft, and a future lottery draft. The newcomer profile is absent so onboarding can be shown. The declared role bindings return to the four installed identities.

Reopen the release app after the reset without clearing app data or reinstalling. Check the owner, camper and admin sessions. Follow the [Halfway walkthrough](../pages/halfway-demo/index.html) and [story checklist](../pages/halfway-demo/checklist.pdf).

**Restore a saved snapshot**

A reset or restore saves the current state first. With app sessions stopped, run:

```sh
node firebase/halfway-demo/cli.cjs restore \
  --from .firebase/halfway-demo/baseline
```

Restore puts the saved Firestore documents and `images/` Storage bytes, metadata and timestamps back, and removes records or uploads added afterward. It does not shift dates or change Auth identities. Use `--backup PATH` to choose a separate backup directory. Existing backup directories are never overwritten. If replacement fails after saving its backup, keep apps stopped and restore from the printed backup directory.

Do not use a practice snapshot for the TA presentation because its dates and registration window follow the time it was made. Run the demo reset immediately before the presentation instead.

Snapshot, backup and actor files live under the ignored `.firebase/halfway-demo/` directory and are not committed.
