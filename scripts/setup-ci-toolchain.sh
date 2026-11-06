#!/usr/bin/env bash
#
# Exposes the current Nix shell's JDK and tool paths to subsequent GitHub Actions steps.

set -euo pipefail

: "${JAVA_HOME:?Run this script inside the hooks or default Nix shell}"
: "${GITHUB_ENV:?GitHub Actions environment file is required}"
: "${GITHUB_PATH:?GitHub Actions path file is required}"

# Java and archive tools must also be available to JavaScript actions and
# their post-job cache steps, which execute outside nix develop.
printf 'JAVA_HOME=%s\n' "$JAVA_HOME" >> "$GITHUB_ENV"
IFS=: read -r -a tool_paths <<< "$PATH"
printf '%s\n' "${tool_paths[@]}" >> "$GITHUB_PATH"
