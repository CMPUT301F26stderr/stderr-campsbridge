#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_REPOSITORY:?GitHub repository is required}"
: "${GITHUB_SHA:?Verified commit SHA is required}"
: "${GITHUB_RUN_ID:?CI run ID is required}"
: "${GH_TOKEN:?GitHub token is required}"

if [[ "${GITHUB_REF:-}" != refs/heads/main ||
    ("${GITHUB_EVENT_NAME:-}" != push && "${GITHUB_EVENT_NAME:-}" != workflow_dispatch) ]]; then
    echo 'Beta APK publication only runs for main pushes or manual main runs.' >&2
    exit 1
fi

debug_apk_path="${1:?Debug APK path is required}"
release_apk_path="${2:?Release APK path is required}"
if [[ ! -s "$debug_apk_path" || "${debug_apk_path##*/}" != app-debug.apk ||
    ! -s "$release_apk_path" || "${release_apk_path##*/}" != app-release.apk ]]; then
    echo 'Expected nonempty app-debug.apk and app-release.apk files from this CI run.' >&2
    exit 1
fi

main_sha="$(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq '.object.sha')"
if [[ "$main_sha" != "$GITHUB_SHA" ]]; then
    echo 'Skipping publication: this commit has been superseded on main.'
    exit 0
fi

notes_file="$(mktemp)"
trap 'rm -f -- "$notes_file"' EXIT
printf 'Verified APKs from main commit %s.\n\napp-debug.apk runs the offline demo. app-release.apk connects to Firebase.\n\n[CI run](https://github.com/%s/actions/runs/%s).\n' \
    "$GITHUB_SHA" "$GITHUB_REPOSITORY" "$GITHUB_RUN_ID" > "$notes_file"

release_id="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases" \
    --jq '.[] | select(.tag_name == "beta") | .id')"
if [[ -n "$release_id" ]]; then
    gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/beta" \
        -f sha="$GITHUB_SHA" -F force=true >/dev/null
    gh release upload beta "$debug_apk_path" "$release_apk_path" --repo "$GITHUB_REPOSITORY" --clobber
    gh release edit beta --repo "$GITHUB_REPOSITORY" \
        --title 'Beta - latest successful main build' \
        --notes-file "$notes_file" --prerelease --latest=false
else
    gh release create beta "$debug_apk_path" "$release_apk_path" --repo "$GITHUB_REPOSITORY" \
        --target "$GITHUB_SHA" --title 'Beta - latest successful main build' \
        --notes-file "$notes_file" --prerelease --latest=false
fi
