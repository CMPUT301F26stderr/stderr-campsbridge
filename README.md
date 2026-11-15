# stderr-campsbridge

An Android campsite reservation app for CMPUT 301, built by a team of six. Entrants join campsite lotteries; selected entrants can book available dates. Campsite owners manage listings and reservations, and administrators moderate the app.

## Getting started

1. Clone this repository.
2. Open it in Android Studio.
3. Sync Gradle.
4. Build and run the app on an emulator or Android device.

## Testing

[![Build status](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)

Download either Halfway APK from the [beta release](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/tag/beta). Current source adds shared Final support; its Firebase deployment and rehearsal are pending.

- [Offline debug demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/download/beta/app-debug.apk): runs without Firebase setup.
- [Firebase release demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/download/beta/app-release.apk): connects to Firebase. See [Firebase setup](firebase/README.md).

Install the APK on your Android device. Both downloads work without signing in to GitHub. See [Testing and demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki/Testing-and-Demo) for test commands.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing. This project uses the [GNU GPL v3 license](LICENSE).

## AI usage

We used OpenAI Codex and other AI tools extensively for implementation, tests, debugging, UI work, documentation and build configuration. Selected conversations, recorded tools/models and dates are in [citations](citations/), grouped by team member. The [AI usage Wiki page](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki/AI-Usage) combines those records.
