#!/usr/bin/env bash
#
# Configures the selected Final Firebase project and deploys rules, indexes, booking and scheduled work.
# The filename is historical; use a separate project to preserve the Halfway Firebase demo.
# Fixture seeding is a separate command with explicit actor UIDs.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

project="${1:?Usage: bash scripts/setup-halfway.sh FIREBASE_PROJECT}"
npm --prefix firebase/functions ci --ignore-scripts --no-audit --no-fund
npm --prefix firebase/functions run seed -- --project "$project" --configure
firebase deploy --project "$project" --config firebase/firebase.json \
    --only firestore,storage,functions:reserve,functions:cancelReservation,functions:processPendingWork --non-interactive --force
npm --prefix firebase/functions run seed -- --project "$project" --configure
