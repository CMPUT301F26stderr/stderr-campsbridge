#!/usr/bin/env bash
#
# Runs Android lint, local unit tests and the debug build through the Gradle wrapper.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
    echo 'Missing Gradle wrapper JAR: gradle/wrapper/gradle-wrapper.jar' >&2
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
    echo 'Java is unavailable. Configure JDK 25 using JAVA_HOME or PATH; see README.md.' >&2
    exit 1
fi

exec bash ./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug "$@"
