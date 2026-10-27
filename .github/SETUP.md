# Repository maintenance

Repository administrators maintain branch rules, review ownership, and the self-hosted runners. Contributors should follow [CONTRIBUTING.md](../CONTRIBUTING.md). Detailed administration procedures belong in the team Wiki.

## Review ownership and merge checks

[`CODEOWNERS`](CODEOWNERS) assigns workflow and `CODEOWNERS` changes to `@by-justin`. Other ownership entries are inactive examples. Update active entries when the team agrees on responsibilities.

Use `CI ready` as the required status in branch rules. Review requirements and bypass permissions are managed in GitHub repository settings. Keep the status name unique. When adding a mandatory verification job, add it to both the gate's dependencies and its success assertion.

## CI behavior

[`ci.yml`](workflows/ci.yml) runs on pull requests, pushes to `main`, and manual dispatches:

| Check | Coverage |
| --- | --- |
| Repository checks | Actions syntax, shell scripts, hook configuration, and flake evaluation |
| Pre-commit checks | YAML/JSON/TOML syntax, conflict markers, oversized files, secret scanning, and repository command tests |
| Android checks | Android Lint, local unit tests, line/branch reporting, scoped pure-logic coverage, debug APK build, Dokka HTML generation, and artifact upload |
| Firebase contracts | Rules plus Kotlin repository queries, transactions and recovery using disposable Auth/Firestore/Storage emulators |
| Android UI checks | Instrumented Compose interactions on a disposable API 35 emulator |
| CI ready | Exact success from every mandatory verification job |
| Publish beta APK | Update the rolling prerelease after successful main checks |
| Publish project site | Publish the storyboard and generated API documentation after successful main checks |

File checks cover the PR/push diff; secret scanning covers the checkout. Manual runs audit all tracked files. Required CI verification does not reject Kotlin formatting or whitespace/newline style differences. Local hooks remain available, and the separate approval workflow applies formatting automatically.

[`ci-feedback.yml`](workflows/ci-feedback.yml) runs after failed PR CI and manual CI runs associated with an open PR. It mentions the author once per failed run attempt, links failed jobs and reports, and includes up to 20 relevant diagnostic lines per job when logs are available. It distinguishes common compilation, unit-test, UI-test, lint, coverage and file-check failures; unknown causes retain a generic description. Duplicate events, cancelled runs and superseded PR revisions do not post comments. The script runs from the default branch and treats logs only as text. It does not change the required `CI ready` result.

[`format-approved-pr.yml`](workflows/format-approved-pr.yml) runs on approval of a same-repository PR targeting the default branch. It runs Nix-provided ktlint and Python whitespace fixers directly, using the trusted default branch's hook file filters and whitespace arguments. This avoids relying on persistent pre-commit virtual environments on self-hosted runners. Keep the ktlint version and JAR checksum in `flake.nix` and `.pre-commit-config.yaml` aligned. Clean files produce no commit. Otherwise, it pushes one formatting commit under `github-actions[bot]` and explicitly dispatches CI on the updated source branch. This uses the workflow token; no personal access token is needed. Fork branches are not modified. Stale approvals and concurrent author updates are skipped or rejected without a force push.

The current stale-review rule can dismiss approval after a formatting commit. Approve the formatted revision again when required; the formatter makes no further commit if its files are clean. An uncorrectable formatting error fails the formatting workflow and needs a manual fix. Neither automation merges PRs. Both workflows take effect after they are present on the default branch.

After Android checks pass, CI uploads `app-debug.apk` directly. A missing APK or failed upload fails the Android job. Android reports upload even when checks fail. Actions artifacts are retained for 14 days.

Android checks also generate `app/build/dokka/html/` and upload it as the run's API documentation artifact. Generation or upload failure fails the Android job and therefore `CI ready`. Dokka generation does not enforce KDoc coverage.

After `CI ready` passes on a main push or manual main run, CI calls [`pages.yml`](workflows/pages.yml). It combines the documentation artifact from that run with the checked-in site assets and deploys one GitHub Pages site: `/` is the landing page, `/storyboard/` is the interactive prototype, and `/docs/` is the generated API reference. Pull requests generate documentation without deploying. Use the **CI** workflow for manual publication; Pages must use GitHub Actions as its publishing source.

After `CI ready` passes, main pushes and manual main runs publish that run's APK to the rolling `beta` prerelease. Pull requests do not publish. Within CI, only the publishing job has `contents: write`; it also has `actions: read` to download the verified artifact. `scripts/publish-apk.sh` creates or updates the prerelease, moves its tag to the verified commit, and replaces `app-debug.apk`. Superseded commits do not publish. Keep the tag and asset name synchronized with the [README download link](../README.md#testing). Release assets remain available until replaced or removed.

Firebase deployment and AI review are not part of the current workflow.

## Runner and toolchain maintenance

All jobs require a Linux x64 self-hosted runner with the `self-hosted`, `linux`, and `x64` labels. The runner needs Nix, Bash, Git, access to the Nix daemon, a writable home/cache, and network access for downloads. Jobs wait when no matching runner is online.

The locked flake supplies `repository` for configuration checks, `hooks` for pre-commit tools, `default` for Android builds, and `ui` for Android builds plus a pinned emulator/system image. The UI job requires usable `/dev/kvm`; its script owns a temporary AVD and its emulator process. Images are downloaded on first use and retained in the Nix store. Device test reports are uploaded even on failure. Keep SDK/JDK selections synchronized with the Gradle daemon and application configuration. Workflows enable `nix-command` and `flakes` through `NIX_CONFIG`.

Persistent runners reuse the Nix store and Gradle cache. CI file checks use Nix-packaged pre-commit-hooks 6.0.0, matching the local hook release, and read their arguments from `.pre-commit-config.yaml`. CI does not create or restore Python hook environments. The Android and Firebase jobs restore executable permissions on cached Gradle launchers for daemon cleanup.

Store text files with normalized Git line endings; `.gitattributes` preserves CRLF when checking out Windows batch scripts. A batch file with CRLF already stored in its Git blob can otherwise appear modified immediately after checkout.

Validate repository-tooling changes locally:

```bash
nix develop .#repository --command bash scripts/check-repository.sh
```

For runner or toolchain changes, also inspect an Actions run on the affected runner.
