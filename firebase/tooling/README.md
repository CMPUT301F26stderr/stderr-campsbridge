# Persistence tooling

Seed and migration commands are not implemented. Use the following conventions when adding them:

- Keep reusable development fixtures in `fixtures/` when seeding is implemented.
- Default tooling to the Firebase emulators; require an explicit project for cloud writes.
- Migrations must be safe to rerun and write `_migrations/{migrationId}.completedAt` only on success.
- Use reseeding for disposable development data. Do not run migrations from Android startup.

## Permission verification

Run `nix develop --command bash scripts/check-firebase.sh` from the repository root. It uses disposable Auth, Firestore and Storage emulators; no cloud data is read or changed. The rules cases run before Kotlin repository integration tests. `scripts/run-firebase-tests.py` retains the rules output and Firestore evaluator reports in ignored `build/reports/` before emulator shutdown. CI uploads those reports. Evaluator expression exhaustion is a failure even if a denial assertion passed; do not remove cases to fit the budget.
