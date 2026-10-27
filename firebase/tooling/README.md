# Persistence tooling

Seed commands are not implemented. Use the following conventions when adding them:

- Keep reusable development fixtures in `fixtures/` when seeding is implemented.
- Default tooling to the Firebase emulators; require an explicit project for cloud writes.
- Recreate disposable development data from current fixtures after a schema change; do not maintain old-schema compatibility or migrations.

## Permission verification

Run `nix develop --command bash scripts/check-firebase.sh` from the repository root. It uses disposable Auth, Firestore and Storage emulators; no cloud data is read or changed. The rules cases run before Kotlin repository integration tests. `scripts/run-firebase-tests.py` retains both suites' output and Firestore evaluator reports in ignored `build/reports/` before emulator shutdown. CI uploads those reports. Evaluator expression exhaustion is a failure even if a denial assertion passed; do not remove cases to fit the budget.
