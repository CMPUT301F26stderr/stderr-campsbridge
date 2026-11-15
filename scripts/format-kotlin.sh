#!/usr/bin/env bash
#
# Formats Kotlin sources with the repository-pinned ktlint version and configuration.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

formatter_args=(--format)
if [[ "${1:-}" == --check && $# == 1 ]]; then
    formatter_args=()
elif (($# != 0)); then
    echo 'Usage: bash scripts/format-kotlin.sh [--check]' >&2
    exit 2
fi

# Include new team files, but exclude ignored build outputs and deleted files.
kotlin_files=()
while IFS= read -r -d '' file; do
    if [[ -f "$file" ]]; then
        kotlin_files+=("$file")
    fi
done < <(git ls-files --cached --others --exclude-standard -z -- '*.kt' '*.kts')

if ((${#kotlin_files[@]})); then
    ktlint "${formatter_args[@]}" --relative -- "${kotlin_files[@]}"
fi
