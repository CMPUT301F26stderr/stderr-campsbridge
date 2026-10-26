# Contributing

Follow these contribution rules and the workflow and responsibilities recorded in the [team wiki](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki).

## Install local hooks

Run `nix develop` to enter the development shell. It includes pre-commit, Python, Go, Java, Gitleaks, and ktlint 1.8.0. The formatter and hooks use the shared `.editorconfig`: four-space indentation, a 100-character column limit, and expression bodies on a separate line. Pre-commit manages its own hook environments, so the first installation still downloads their dependencies.

Without Nix, install the pinned runner with `uv tool install pre-commit==4.6.2` (or `pipx install pre-commit==4.6.2`). Make Java available on `PATH` for Kotlin checks; the project's JDK 25 works. For the format command, install [ktlint 1.8.0](https://github.com/ktlint/ktlint/releases/tag/1.8.0) on `PATH`; hooks download and verify the same release automatically.

After cloning, run:

```bash
pre-commit install --install-hooks
```

Hooks check staged files for whitespace, final newlines, valid YAML/JSON/TOML, conflict markers, oversized files (over 1 MiB), secrets, and Kotlin formatting. Markdown hard line breaks are preserved. Whitespace, newline, and Kotlin hooks may fix files; review and stage those changes before retrying the commit. Hooks never stage files automatically. Manual audits check Kotlin without modifying it. CI omits formatting, whitespace, and final-newline checks.

Format all tracked and untracked Kotlin sources and Gradle scripts, excluding ignored build outputs:

```bash
bash scripts/format-kotlin.sh
# Check the same files without changing them:
bash scripts/format-kotlin.sh --check
```

Use the ktlint integration in Android Studio with the pinned version and shared `.editorconfig`. Disable competing Kotlin formatters. Compose component names are allowed. Remove the comment-only file exceptions from `.editorconfig` when the corresponding integration files are implemented.

For a full checkout audit, including secrets in files that are not staged:

```bash
pre-commit run --hook-stage manual --all-files
```

CI checks YAML/JSON/TOML syntax, conflict markers, and file size in the PR/push diff, and scans the entire checkout for secrets. Manual workflow runs check all tracked files. Run the same checks with `nix develop .#hooks --command bash scripts/check-hooks.sh`; the Nix shell supplies the check implementations directly. Automatic PR formatting, commit-message validation, and pre-push checks are not enabled.

## Work on an issue

- Create or claim an issue before starting. Reference relevant user-story IDs and write observable acceptance criteria.
- Add the issue to the team project and keep its status current: Backlog → Ready → In progress → In review → Done.
- Assign one primary owner. Agree on shared-file changes and cross-feature interfaces with affected teammates.
- Use a short-lived branch such as `feat/42-lottery-waitlist`, `fix/57-booking-dates`, or `chore/18-ci`.

## Open a pull request

- Keep the change focused and explain the resulting behavior.
- Use a Conventional Commit PR title such as `feat(lottery): add waitlist joining`; working commits can remain flexible.
- Link its issue with `Closes #123` when merging will complete the issue.
- Run `bash scripts/check.sh`, or the equivalent Gradle command in the README. For persistence changes, also run `nix develop --command bash scripts/check-firebase.sh`. Report checks you could not run and why.
- For repository configuration changes, also run `nix develop .#repository --command bash scripts/check-repository.sh`. See the [maintenance guide](.github/SETUP.md) for workflow and runner behavior.
- Include screenshots for visible UI changes and update affected wiki guidance or diagrams.
- Request at least one teammate's review. Human review remains required when AI review is added.
- Resolve feedback and get a passing `CI ready` status before merging. It requires Repository checks, Pre-commit checks, Android checks, Firebase contracts, and Android UI checks to succeed. Use squash merge for a concise main-branch history.

## Test boundaries

- `app/src/test/.../domain/`: pure model validation and derived values.
- `app/src/test/.../data/firestore/`: exact document contracts and shared listener behavior.
- `app/src/test/.../integration/firebase/`: feature-owned repository queries, coordinated writes, concurrency and interrupted-operation recovery against local emulators.
- `app/src/test/.../ui/`: ViewModel/controller contracts with fake dependencies and controlled coroutine scheduling.
- `app/src/test/.../testsupport/`: JUnit rules, fixtures, and emulator setup, with no business assertions.
- `app/src/debug/.../testsupport/`: shared debug fakes and controllable time, without JUnit dependencies.
- `app/src/androidTest/`: Compose interactions and Android platform wiring on a device or emulator.
- `scripts/tests/`: repository command behavior; run with `nix develop .#hooks --command python3 -m unittest discover -s scripts/tests -v`.

The ordinary unit-test task excludes Firebase integration tests; `:app:firebaseIntegrationTest` requires services started by `scripts/check-firebase.sh`. Keep domain constraints in domain tests and persistence invariants in their owning repository suites. For new features, define controller tests with fake repository interfaces and device/UI acceptance tests before implementing the behavior.

Run line/branch reporting and the scoped pure-logic gate with:

```bash
nix develop --command bash ./gradlew :app:unitCoverageReport :app:pureLogicCoverage
```

Reports are under `app/build/reports/jacoco/unitCoverageReport/`. The 100% line/branch gate covers `requireOperation`, `permittedWorkspace`, and image action availability; add new small pure-rule classes deliberately. This is not a module-wide coverage claim.

Run instrumented tests on a disposable API 35 device with:

```bash
nix develop .#ui --command bash scripts/check-android-ui.sh
```

The host must provide usable KVM. To use an already running dedicated test device, set `ANDROID_SERIAL` instead. The script fails if no tests execute or any test is skipped. Content tests do not require Firebase or production bootstrap.

## Done means

Acceptance criteria are met, relevant verification is recorded, review is complete, the PR is merged, and affected documentation is current. Update the project status after merging.

Keep local SDK paths, signing keys, credentials, and generated build outputs out of commits. Use the checked-in Gradle wrapper for builds.
