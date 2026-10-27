#!/usr/bin/env python3
"""Run the agreed Firebase suites and retain emulator diagnostics before disposal."""

import os
from pathlib import Path
import subprocess
import sys
from urllib.request import urlopen


def run_logged(command, path):
    """Stream one existing verification command and preserve its combined output."""
    process = subprocess.Popen(
        command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    with path.open("w") as log:
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
    return process.wait()


def capture_coverage(report_dir):
    """Capture evaluator reports while the disposable Firestore service is still running."""
    host = os.environ["FIRESTORE_EMULATOR_HOST"]
    result = 0
    for extension in ("json", "html"):
        url = f"http://{host}/emulator/v1/projects/demo-stderr:ruleCoverage"
        if extension == "html":
            url += ".html"
        try:
            with urlopen(url, timeout=30) as response:
                (report_dir / f"rules-coverage.{extension}").write_bytes(response.read())
        except Exception as failure:
            print(f"Could not capture rules {extension} report: {failure}", file=sys.stderr)
            result = 1
    return result


def main():
    """Retain both suites' evidence and reject evaluator exhaustion even for expected denials."""
    report_dir = Path("firebase/tooling/build/reports")
    report_dir.mkdir(parents=True, exist_ok=True)
    rules_log = report_dir / "rules.log"
    integration_log = report_dir / "integration.log"
    # Remove only this runner's prior evidence so failed rules never expose an old integration log.
    integration_log.unlink(missing_ok=True)
    result = run_logged(["npm", "--prefix", "firebase/tooling", "test"], rules_log)
    if result == 0:
        result = run_logged(
            ["bash", "./gradlew", "--no-daemon", ":app:firebaseIntegrationTest", "--console=plain"],
            integration_log,
        )
    if capture_coverage(report_dir):
        result = 1
    server_log = Path("firestore-debug.log")
    if server_log.is_file():
        (report_dir / "firestore.log").write_bytes(server_log.read_bytes())
    logs = [rules_log, integration_log, server_log]
    if any("maximum of 1000 expressions" in path.read_text(errors="replace")
           for path in logs if path.is_file()):
        print("Rules exhausted the expression budget; a denied request is not sufficient evidence.", file=sys.stderr)
        result = 1
    return result


if __name__ == "__main__":
    sys.exit(main())
