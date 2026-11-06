#!/usr/bin/env bash
#
# Checks workflow syntax, shell scripts, hook configuration and the pinned Nix environment.

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

actionlint

shopt -s globstar nullglob
shell_scripts=(scripts/**/*.sh)
if ((${#shell_scripts[@]})); then
    shellcheck "${shell_scripts[@]}"
fi

pre-commit validate-config
nix flake check --no-build --no-update-lock-file
