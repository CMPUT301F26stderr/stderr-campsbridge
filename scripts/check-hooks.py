#!/usr/bin/env python3
"""Run CI's read-only checks with Nix-provided dependencies and shared hook args."""

import os
from pathlib import Path
import re
import subprocess

from identify.identify import tags_from_path
from pre_commit_hooks import (
    check_added_large_files,
    check_json,
    check_merge_conflict,
    check_toml,
    check_yaml,
)
import yaml


def selected_files():
    base = os.environ.get("BASE_REF", "")
    if base and base != "0" * 40:
        head = os.environ.get("HEAD_REF", "")
        if not head:
            raise SystemExit("HEAD_REF is required when BASE_REF is set")
        command = [
            "git", "diff", "--name-only", "-z", "--diff-filter=ACMRT", base, head, "--",
        ]
    else:
        command = ["git", "ls-files", "-z"]
    output = subprocess.check_output(command)
    paths = [os.fsdecode(name) for name in output.split(b"\0") if name]
    return {
        path: tags_from_path(path)
        for path in paths
        if Path(path).is_file() and not Path(path).is_symlink()
    }


def main():
    config = yaml.safe_load(Path(".pre-commit-config.yaml").read_text())
    hooks = [
        hook
        for repo in config["repos"]
        if repo["repo"] == "https://github.com/pre-commit/pre-commit-hooks"
        for hook in repo["hooks"]
    ]
    # Keep CI's allowlist explicit: auto-fix and style hooks are local/manual only.
    checks = {
        "check-yaml": ("yaml", check_yaml.main),
        "check-json": ("json", check_json.main),
        "check-toml": ("toml", check_toml.main),
        "check-merge-conflict": ("text", check_merge_conflict.main),
        "check-added-large-files": ("file", check_added_large_files.main),
    }
    files = selected_files()
    failed = False
    for hook in hooks:
        hook_id = hook["id"]
        if hook_id not in checks:
            continue
        file_type, check = checks[hook_id]
        args = hook.get("args", [])
        include = re.compile(hook.get("files", ""))
        exclude = re.compile(hook.get("exclude", "^$"))
        filenames = [
            path for path, tags in files.items()
            if file_type in tags and include.search(path) and not exclude.search(path)
        ]
        label = hook.get("name", hook_id)
        if not filenames:
            print(f"{label}: skipped (no matching files)", flush=True)
            continue
        result = check([*args, "--", *filenames])
        print(f"{label}: {'passed' if result == 0 else 'failed'}", flush=True)
        failed |= result != 0

    print("Scanning checkout for secrets", flush=True)
    secrets = subprocess.run(
        ["gitleaks", "dir", "--redact", "--no-banner"], check=False,
    )
    return int(failed or secrets.returncode != 0)


if __name__ == "__main__":
    raise SystemExit(main())
