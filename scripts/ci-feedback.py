#!/usr/bin/env python3
"""Explain a completed CI failure on its current pull request using trusted workflow code."""

import argparse
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys

MARKER = "<!-- campsbridge-ci-failure:{run_id}:{attempt} -->"
BOT = "github-actions[bot]"
FAILURES = {"failure", "timed_out", "startup_failure"}


class GitHub:
    """Use the workflow token through gh; never execute downloaded logs or PR code."""

    def request(self, path, *, method="GET", data=None, text=False):
        command = ["gh", "api", "--method", method, path]
        if text:
            # Logs can contain ANSI colors; capture them here and strip before rendering.
            command += ["--allow-escape-sequences"]
        if data is not None:
            command += ["--input", "-"]
        try:
            result = subprocess.run(
                command, input=json.dumps(data) if data is not None else None,
                capture_output=True, text=True, check=True,
            )
        except subprocess.CalledProcessError as failure:
            print(f"GitHub API {method} {path} failed (exit {failure.returncode}).", file=sys.stderr)
            for name, output in (("stderr", failure.stderr), ("response", failure.stdout)):
                if not output:
                    continue
                for variable in ("GH_TOKEN", "GITHUB_TOKEN"):
                    token = os.environ.get(variable)
                    if token:
                        output = output.replace(token, "[REDACTED]")
                print(f"{name}: {output.rstrip()}", file=sys.stderr)
            raise
        return result.stdout if text else json.loads(result.stdout or "null")

    def pages(self, path, key=None):
        """Read every page of jobs, associated PRs or existing comments."""
        result = []
        for page in range(1, 101):
            data = self.request(f"{path}?per_page=100&page={page}")
            items = data[key] if key else data
            result.extend(items)
            if len(items) < 100:
                return result
        raise RuntimeError("GitHub response exceeded the pagination limit")


def diagnostic(log):
    """Select a bounded block around an actual diagnostic rather than a stacktrace tail."""
    log = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", log)
    lines = [re.sub(r"^\d{4}-\d\d-\d\dT\S+\s+", "", line) for line in log.splitlines()]
    lines = [line for line in lines if line.strip() and not line.startswith("##[group]")]
    patterns = [
        r"^(?:e:|error:)|\.(?:kt|kts|java):\d+.*(?:error|unresolved)",
        r"\s>\s.*\sFAILED$|^not ok |^FAIL:|^ERROR:|^✖ (?!tests |suites |pass |fail |cancelled |skipped |todo |duration_ms)",
        r"(?:standard:|ktlint).*|Lint found|Rule violated",
        r"^\* What went wrong:",
        r"^> Task .* FAILED$|^FAILURE:|##\[error\]",
    ]
    for pattern in patterns:
        index = next((i for i, line in enumerate(lines) if re.search(pattern, line)), None)
        if index is not None:
            start = max(0, index - 1)
            excerpt = []
            for line in lines[start:start + 20]:
                excerpt.append(line[:400])
                if line.startswith("##[error]Process completed"):
                    break  # Following cleanup/artifact steps are unrelated to this failure.
            return "\n".join(excerpt)[:4000]
    return ""  # Do not invent a cause or substitute unrelated trailing output.


def failure_kind(job, log):
    """Describe only failure categories supported by the job's output."""
    if re.search(r"(?:compile\w*(?:Kotlin|JavaWithJavac).*FAILED|^e: |Compilation error)", log, re.M):
        return "Compilation failed"
    if "connectedDebugAndroidTest FAILED" in log:
        return "Android UI tests failed"
    if "testDebugUnitTest FAILED" in log or re.search(r"\s>\s.*\sFAILED$", log, re.M):
        return "Unit tests failed" if job["name"] == "Android checks" else "Tests failed"
    if "pureLogicCoverage FAILED" in log or "Rule violated" in log:
        return "Coverage requirements failed"
    if re.search(r"ktlint|standard:(?:indent|import|property|wrapping|max-line)", log, re.I):
        return "Kotlin formatting failed"
    if re.search(r"lintDebug FAILED|Lint found \d+ error", log):
        return "Android Lint failed"
    return {
        "Repository checks": "Repository configuration checks failed",
        "Pre-commit checks": "Repository file checks failed",
        "Firebase contracts": "Firebase checks failed",
        "Android UI checks": "Android UI checks failed",
    }.get(job["name"], "CI job failed")


def render_comment(pr, run, failures):
    """Build a concise author-facing comment with bounded excerpts and reproduction commands."""
    marker = MARKER.format(run_id=run["id"], attempt=run["run_attempt"])
    body = [marker, f"@{pr['user']['login']}, CI did not pass for commit `{pr['head']['sha'][:7]}`.",
            "Please check the failures below, run the checks locally, and push your fixes to this same PR. "
            "The required CI checks must pass before it can be merged.",
            f"[Open the CI run and downloadable test reports]({run['html_url']}).", ""]
    for job, log in failures:
        steps = [step["name"] for step in job.get("steps", []) if step.get("conclusion") in FAILURES]
        detail = f" ({'; '.join(steps)})" if steps else ""
        label = html.escape(f"{job['name']}: {failure_kind(job, log)}{detail}")
        body.append(f"- [{label}]({job['html_url']})")
        excerpt = diagnostic(log)
        if excerpt:
            # Log text is inert code. A longer fence prevents embedded backticks escaping it.
            fence = "`" * max(3, max((len(x) + 1 for x in re.findall(r"`+", excerpt)), default=0))
            body += ["", "<details><summary>Diagnostic excerpt (up to 20 lines)</summary>", "",
                     f"{fence}text", excerpt, fence, "", "</details>", ""]
        else:
            body += ["", "A useful log excerpt was unavailable; open the failed job above for details.", ""]
    body += ["Run the ordinary Android checks:", "", "```sh", "bash scripts/check.sh", "```", "",
             "On Windows PowerShell: `./gradlew.bat --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`.", "",
             "Optional: run `pre-commit install` once and `pre-commit run --all-files` "
             "to catch issues earlier. If hooks change files, include those fixes in your commit. "
             "The bot applies formatting after approval, so installing local hooks is not required."]
    names = {job["name"] for job, _ in failures}
    if "Firebase contracts" in names:
        body += ["", "For Firebase failures: `bash scripts/check-firebase.sh` after the [Firebase test setup](https://github.com/CMPUT301F26stderr/stderr-campsbridge/wiki/Testing-and-Demo#firebase-integration-checks)."]
    if "Android UI checks" in names:
        body += ["", "For device-test failures, start an emulator in Android Studio and run the tests in `app/src/androidTest`. On macOS/Linux, set `ANDROID_SERIAL` to its serial from `adb devices` and run `bash scripts/check-android-ui.sh`."]
    body += ["", "For runner, dependency or setup failures, ask the team lead for help with the linked logs."]
    return "\n\n".join(body[:4]) + "\n" + "\n".join(body[4:])


def report(api, repository, event, *, dry_run=False):
    """Comment once per failed run attempt, ignoring closed PRs, stale commits and reruns."""
    run = event["workflow_run"]
    if run["name"] != "CI" or run["event"] not in {"pull_request", "workflow_dispatch"} or run["conclusion"] not in FAILURES:
        return []
    prefix = f"repos/{repository}"
    # Re-read rather than trusting a delayed workflow_run event after a newer attempt started.
    current = api.request(f"{prefix}/actions/runs/{run['id']}")
    if (current["run_attempt"] != run["run_attempt"] or current["status"] != "completed"
            or current["conclusion"] not in FAILURES):
        return []
    candidates = run.get("pull_requests") or api.pages(f"{prefix}/commits/{run['head_sha']}/pulls")
    jobs = api.pages(f"{prefix}/actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", "jobs")
    failures = []
    for job in jobs:
        if job["conclusion"] not in FAILURES or job["name"] == "CI ready":
            continue
        try:
            log = api.request(f"{prefix}/actions/jobs/{job['id']}/logs", text=True)
        except subprocess.CalledProcessError:
            log = ""
        failures.append((job, log))
    if not failures:
        failures = [({"name": "CI", "html_url": run["html_url"]}, "")]
    comments = []
    for candidate in candidates:
        number = candidate["number"]
        pr = api.request(f"{prefix}/pulls/{number}")
        if pr["state"] != "open" or pr["head"]["sha"] != run["head_sha"]:
            continue
        if pr["base"]["repo"]["full_name"] != repository:
            continue
        marker = MARKER.format(run_id=run["id"], attempt=run["run_attempt"])
        existing = api.pages(f"{prefix}/issues/{number}/comments")
        if any(item["user"]["login"] == BOT and marker in item.get("body", "") for item in existing):
            continue
        body = render_comment(pr, run, failures)
        # A new commit may have arrived while downloading logs or reading comments.
        latest = api.request(f"{prefix}/pulls/{number}")
        if latest["state"] != "open" or latest["head"]["sha"] != run["head_sha"]:
            continue
        if dry_run:
            print(body)
        else:
            api.request(f"{prefix}/issues/{number}/comments", method="POST", data={"body": body})
        comments.append(number)
    return comments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"), required=False)
    parser.add_argument("--dry-run", action="store_true", help="Print feedback without posting it")
    args = parser.parse_args()
    if not args.event:
        parser.error("--event or GITHUB_EVENT_PATH is required")
    event = json.loads(Path(args.event).read_text())
    report(GitHub(), os.environ["GITHUB_REPOSITORY"], event, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
