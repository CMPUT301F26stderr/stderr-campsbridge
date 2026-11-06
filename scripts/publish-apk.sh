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

apk_path="${1:?APK path is required}"
if [[ ! -s "$apk_path" || "${apk_path##*/}" != app-release.apk ]]; then
    echo 'Expected a nonempty app-release.apk from this CI run.' >&2
    exit 1
fi

main_sha="$(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq '.object.sha')"
if [[ "$main_sha" != "$GITHUB_SHA" ]]; then
    echo 'Skipping publication: this commit has been superseded on main.'
    exit 0
fi

notes_file="$(mktemp)"
trap 'rm -f -- "$notes_file"' EXIT
printf 'Halfway Firebase release APK from main commit %s.\n\n[CI run](https://github.com/%s/actions/runs/%s).\n\nThis beta prerelease is updated after successful main checks.\n' \
    "$GITHUB_SHA" "$GITHUB_REPOSITORY" "$GITHUB_RUN_ID" > "$notes_file"

release_id="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases" \
    --jq '.[] | select(.tag_name == "beta") | .id')"
if [[ -n "$release_id" ]]; then
    gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/beta" \
        -f sha="$GITHUB_SHA" -F force=true >/dev/null
    gh release upload beta "$apk_path" --repo "$GITHUB_REPOSITORY" --clobber
    gh release edit beta --repo "$GITHUB_REPOSITORY" \
        --title 'Beta - latest successful main build' \
        --notes-file "$notes_file" --prerelease --latest=false
else
    gh release create beta "$apk_path" --repo "$GITHUB_REPOSITORY" \
        --target "$GITHUB_SHA" --title 'Beta - latest successful main build' \
        --notes-file "$notes_file" --prerelease --latest=false
fi
