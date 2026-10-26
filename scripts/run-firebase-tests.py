#!/usr/bin/env python3
"""Run the agreed rules suite and retain emulator diagnostics before disposal."""

import os
from pathlib import Path
import subprocess
import sys
from urllib.request import urlopen


def main():
    """Preserve rules reports and reject evaluator exhaustion even for expected denials."""
    report_dir = Path("firebase/tooling/build/reports")
    report_dir.mkdir(parents=True, exist_ok=True)
    process = subprocess.Popen(
        ["npm", "--prefix", "firebase/tooling", "test"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    with (report_dir / "rules.log").open("w") as log:
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
    result = process.wait()
    host = os.environ["FIRESTORE_EMULATOR_HOST"]
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
    log = (report_dir / "rules.log").read_text()
    if "maximum of 1000 expressions" in log:
        print("Rules exhausted the expression budget; a denied request is not sufficient evidence.", file=sys.stderr)
        result = 1
    return result


if __name__ == "__main__":
    sys.exit(main())
