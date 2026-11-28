# stderr-campsbridge

An Android campsite reservation app for CMPUT 301, built by a team of six. Entrants join campsite lotteries; selected entrants can book available dates. Campsite owners manage listings and reservations, and administrators moderate the app.

## Getting started

1. Clone this repository.
2. Open it in Android Studio.
3. Sync Gradle.
4. Build and run the app on an emulator or Android device.

## Testing

[![Build status](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/CMPUT301F26stderr/stderr-campsbridge/actions/workflows/ci.yml?query=branch%3Amain+event%3Apush)

Download current APKs from the [beta release](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/tag/beta). The completed [Halfway walkthrough](https://cmput301f26stderr.github.io/stderr-campsbridge/archive/halfway-demo/) and [checkpoint APKs](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/tag/halfway-demo) are archived.

- [Offline debug demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/download/beta/app-debug.apk): runs without Firebase setup.
- [Firebase release demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/releases/download/beta/app-release.apk): connects to Firebase. See [Firebase setup](firebase/README.md).

Install the APK on your Android device. Both downloads work without signing in to GitHub.

The intent tests in `app/src/androidTest` drive the real screens on a device or emulator, covering every finished user story area: joining and drawing lotteries, booking, reservations, messaging, reviews, campsite setup, profiles and administration. Local unit tests in `app/src/test` cover the model and ViewModel classes, and Firebase emulator tests cover queries, transactions and access rules. See [Testing and demo](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki/Testing-and-Demo) for the commands.

Read [Team contributing](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki/Team-Contributing) before contributing. This project uses the [GNU GPL v3 license](LICENSE).
