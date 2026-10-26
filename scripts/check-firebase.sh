#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

npm --prefix firebase/tooling ci --ignore-scripts --no-audit --no-fund

# No import/export: each invocation starts with disposable data and stops on command exit.
exec firebase emulators:exec --project demo-stderr --config firebase/firebase.json \
    --only auth,firestore,storage \
    'python3 scripts/run-firebase-tests.py'
