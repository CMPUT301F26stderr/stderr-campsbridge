# stderr-campsbridge

An Android campsite reservation app for CMPUT 301, built by a team of six. Entrants join campsite lotteries; selected entrants can book available dates. Campsite owners manage listings and reservations, and administrators moderate the app.

## Team workspace

- [GitHub Wiki](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki): onboarding, architecture, responsibilities, UI storyboards, decisions, and meeting notes.
- [Project site](https://cmput301f26stderr.github.io/stderr-campsbridge/): interactive storyboard and generated Kotlin API documentation.
- [Issues](https://github.com/CMPUT301F26stderr/stderr-campsbridge/issues): requirements, tasks, bugs, and acceptance criteria.
- [Projects](https://github.com/orgs/CMPUT301F26stderr/projects): the team's Kanban board.
- Canva: editable prototypes; keep the design links and exported diagrams in the wiki.
- [Course requirements](doc/00_requirements.md) and [preparation deliverables](doc/01_prep.md): reference documents.

## Getting started

1. Clone this repository and open its root in Android Studio.
2. Install the SDK required by `app/build.gradle.kts` (currently Android API 37). The Gradle daemon requires JDK 25, as recorded in `gradle/gradle-daemon-jvm.properties`.
3. Allow Gradle sync to finish, select the `debug` build variant, then run the `app` configuration on an emulator or device. It opens the offline demo without Firebase setup. Use **Demo tools** to switch scenarios, reset sample data, or inspect screens and the component gallery.
4. [Install the shared pre-commit hooks](CONTRIBUTING.md#install-local-hooks) after cloning. They run automatically on commits.
5. Before opening a PR, run the same Android checks used by CI:

   ```bash
   bash scripts/check.sh
   ```

   On Windows PowerShell:

   ```powershell
   .\gradlew.bat --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
   ```

The checks run Android Lint, local domain/schema/listener/controller tests, and a debug APK build. Run persistence integration tests separately:

```bash
nix develop --command bash scripts/check-firebase.sh
```

This starts disposable Auth, Firestore, and Storage emulators, runs the rules and Kotlin repository tests, and stops the services afterward. The Kotlin tests use a Robolectric host; they do not need an Android virtual device. Device/UI tests remain separate:

```bash
nix develop .#ui --command bash scripts/check-android-ui.sh
```

This uses a disposable API 35 emulator and requires usable KVM on Linux. See [CONTRIBUTING.md](CONTRIBUTING.md#test-boundaries) for existing-device and coverage commands.

The default `debug` build uses local sample accounts and private cached photos. Profile display, workspace switching and photo selection/save/removal work locally. The demo menu offers an existing entrant, an existing owner, a new account and screen previews. Scenario state survives activity recreation and resets when its host finishes or the process restarts. Name submission remains blocked until the reserved teammate validator is integrated; name/contact editing and other feature screens remain pending.

Select `firebaseDebug` in Android Studio for real account identity, Firestore and Storage, or use `release` for the delivery build. See [Firebase setup](firebase/README.md) for cloud/emulator configuration and [demo boundaries](app/src/debug/java/io/github/CMPUT301F26stderr/campsbridge/demo/README.md) for supported local behavior. Firebase startup does not create sample accounts.

## Repository layout

| Path | Purpose |
| --- | --- |
| `app/` | Android application, resources, and tests |
| `.github/` | Issue and PR templates, review ownership, and Actions workflows |
| `doc/` | Course reference documents and team roster |
| `firebase/` | Persistence contract, Firestore/Storage rules, indexes, and emulator configuration |
| `scripts/` | Shared development commands |
| `pages/` | Minimal landing page for the published storyboard and API documentation |
| `gradle/` and root build files | Reproducible build configuration |

Detailed team documentation belongs in the wiki. Keep executable configuration, agent instructions, and short contribution rules in this repository.

## Testing

[![Build status](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush) [![Download Last Success APK](https://img.shields.io/badge/Download-Latest%20APK-blue?logo=android)](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/download/beta/app-debug.apk)

Use **Download Latest APK** above to download `app-debug.apk` from the rolling [beta prerelease](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/tag/beta), without signing in to GitHub. CI updates it after all checks pass on `main`. Open the APK on your Android device to install it, and allow installation from the app you used to open the APK if Android prompts you. The build-status badge links to CI runs and check reports.

CI publishes the offline `debug` demo to the rolling beta. Firebase-connected builds use the separate `firebaseDebug` variant. The beta download stays available until replaced by a newer verified build. Per-run APKs and check reports remain available as Actions artifacts for 14 days.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing. This project uses the [GNU GPL v3 license](LICENSE).

### Assigned helper examples

In the `debug` app, open **Demo tools → Task examples** or select the checked-in **Task examples** Android Studio run configuration. H01 display name and H02 contacts use the real profile editors with local sample records. Other task examples remain labelled pending until their baselines are published. Reset restores sample records; activity recreation retains the current example and input. No Firebase setup is needed.

The shared baseline leaves assigned helper bindings disconnected. Follow the issue's prepared handoff to connect its real implementation and tests; a preview or pending example does not complete the task. Release and Firebase builds do not include the debug task host.
