#!/usr/bin/env python3
"""Apply trusted formatting hooks to an approved PR and run CI on any resulting commit."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from identify.identify import tags_from_path
import yaml

HOOKS = ("pretty-format-kotlin", "trailing-whitespace", "end-of-file-fixer")
BOT_NAME = "github-actions[bot]"
BOT_EMAIL = "41898282+github-actions[bot]@users.noreply.github.com"


def github(path, *, data=None):
    """Pass JSON as stdin so branch names and comment content never become shell commands."""
    command = ["gh", "api", "--method", "POST" if data is not None else "GET", path]
    if data is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(data) if data is not None else None,
                            capture_output=True, text=True, check=True)
    return json.loads(result.stdout or "null")


def eligible(pr, repository, reviewed_sha, default_branch):
    """Only the still-open, same-repository PR revision explicitly approved may be changed."""
    return (pr["state"] == "open" and not pr.get("draft", False)
            and pr["head"]["sha"] == reviewed_sha
            and pr["head"]["repo"]["full_name"] == repository
            and pr["base"]["repo"]["full_name"] == repository
            and pr["base"]["ref"] == default_branch)


def git(checkout, *args):
    """Run a Git command only in the disposable reviewed checkout."""
    return subprocess.check_output(["git", *args], cwd=checkout)


def format_hooks(checkout, config, files):
    """Run Nix-pinned formatters directly, preserving the trusted hook filters and args."""
    configuration = yaml.safe_load(config.read_text())
    definitions = {
        hook["id"]: hook
        for repo in configuration["repos"]
        for hook in repo["hooks"]
        if hook["id"] in HOOKS
    }
    tags = {name: tags_from_path(checkout / name) for name in files}
    for hook in HOOKS:
        definition = definitions[hook]
        selected = [
            name for name in files
            if re.search(configuration.get("files", ""), name)
            and not re.search(configuration.get("exclude", "^$"), name)
            and re.search(definition.get("files", ""), name)
            and not re.search(definition.get("exclude", "^$"), name)
            and set(definition.get("types", ["file" if hook == "pretty-format-kotlin" else "text"])) <= tags[name]
        ]
        if not selected:
            print(f"{hook}: skipped (no matching files)", flush=True)
            continue
        if hook == "pretty-format-kotlin":
            # flake.nix owns the same pinned ktlint JAR used by the local hook.
            command = ["ktlint", "--format", "--", *selected]
        else:
            module = "trailing_whitespace_fixer" if hook == "trailing-whitespace" else "end_of_file_fixer"
            command = ["python3", "-m", f"pre_commit_hooks.{module}",
                       *definition.get("args", []), "--", *selected]
        result = subprocess.run(command, cwd=checkout, check=False)
        if result.returncode == 1:
            # Whitespace fixers return 1 after modifying files; rerun to verify convergence.
            result = subprocess.run(command, cwd=checkout, check=False)
        if result.returncode:
            raise RuntimeError(f"{hook} could not finish automatically; see its diagnostics above")


def format_approved(event, checkout, config, *, api=github, formatter=format_hooks):
    """Format and push only while approval still names the current head; never force-push."""
    if event.get("review", {}).get("state") != "approved":
        return False
    repository = event["repository"]["full_name"]
    default_branch = event["repository"]["default_branch"]
    reviewed_sha = event["review"]["commit_id"]
    number = event["pull_request"]["number"]
    path = f"repos/{repository}/pulls/{number}"
    pr = api(path)
    if not eligible(pr, repository, reviewed_sha, default_branch):
        print("Skipping: the PR or reviewed revision is no longer eligible.")
        return False
    if git(checkout, "rev-parse", "HEAD").decode().strip() != reviewed_sha:
        raise RuntimeError("Checkout does not match the approved revision")
    if git(checkout, "status", "--porcelain"):
        raise RuntimeError("The formatter requires a clean, disposable checkout")
    # The base may have advanced after checkout; fetch it before calculating the PR's changes.
    git(checkout, "fetch", "--no-tags", "origin", pr["base"]["sha"])
    changed = git(checkout, "diff", "--name-only", "-z", "--diff-filter=ACMRT",
                  f"{pr['base']['sha']}...{reviewed_sha}", "--")
    files = [os.fsdecode(name) for name in changed.split(b"\0") if name]
    files = [name for name in files if (checkout / name).is_file() and not (checkout / name).is_symlink()]
    if not files:
        print("No changed files need formatting.")
        return False
    # A leading dash is legal in a filename, but must not become a formatter option.
    formatter(checkout, config, [f"./{name}" if name.startswith("-") else name for name in files])
    if not git(checkout, "diff", "--name-only"):
        print("Formatting is already clean; no bot commit is needed.")
        return False
    current = api(path)
    if not eligible(current, repository, reviewed_sha, default_branch):
        print("Skipping push: the author updated or closed the PR while formatting ran.")
        return False
    git(checkout, "add", "--", *files)
    git(checkout, "-c", f"user.name={BOT_NAME}", "-c", f"user.email={BOT_EMAIL}",
        "commit", "-m", f"style: format approved PR #{number}")
    branch = pr["head"]["ref"]
    # A concurrent author push is rejected by ordinary fast-forward rules.
    git(checkout, "push", "origin", f"HEAD:refs/heads/{branch}")
    # Explicit dispatch runs with GITHUB_TOKEN without depending on a push-triggered run.
    api(f"repos/{repository}/actions/workflows/ci.yml/dispatches", data={"ref": branch})
    print("Formatting pushed; CI requested for the formatted commit. A new approval may be required.")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkout", type=Path, required=True)
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    args = parser.parse_args()
    if not args.event:
        parser.error("--event or GITHUB_EVENT_PATH is required")
    config = Path(__file__).resolve().parents[1] / ".pre-commit-config.yaml"
    format_approved(json.loads(Path(args.event).read_text()), args.checkout.resolve(), config)


if __name__ == "__main__":
    main()
