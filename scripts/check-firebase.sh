#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# These generated diagnostics belong to this invocation, including failed setup/startup.
rm -rf -- firebase/tooling/build/reports
rm -f -- firestore-debug.log

npm --prefix firebase/tooling ci --ignore-scripts --no-audit --no-fund

# No import/export: each invocation starts with disposable data and stops on command exit.
exec python3 scripts/run-test-emulators.py firebase
