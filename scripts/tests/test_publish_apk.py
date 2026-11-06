"""Exercise release decisions without credentials or writes to GitHub."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "publish-apk.sh"


class PublishApkTest(unittest.TestCase):
    """Own the publication command's policy and external command sequence."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.debug_apk = self.root / "build output" / "app-debug.apk"
        self.release_apk = self.root / "build output" / "app-release.apk"
        self.debug_apk.parent.mkdir()
        self.debug_apk.write_bytes(b"fixture debug APK")
        self.release_apk.write_bytes(b"fixture release APK")
        self.calls = self.root / "calls.jsonl"
        gh = self.root / "gh"
        gh.write_text(textwrap.dedent('''\
            #!/usr/bin/env python3
            import json, os, pathlib, sys
            args = sys.argv[1:]
            call = {"args": args}
            if "--notes-file" in args:
                notes = pathlib.Path(args[args.index("--notes-file") + 1])
                call.update(notes_path=str(notes), notes=notes.read_text())
            with open(os.environ["CALLS"], "a") as log:
                log.write(json.dumps(call) + "\\n")
            if args[:2] == ["release", "upload"] and os.environ.get("FAIL_UPLOAD"):
                sys.exit(9)
            if args[0] == "api" and any(a.endswith("/git/ref/heads/main") for a in args):
                print(os.environ["MAIN_SHA"])
            elif args[0] == "api" and any(a.endswith("/releases") for a in args):
                print(os.environ.get("RELEASE_ID", ""))
            '''))
        gh.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.root}:{os.environ['PATH']}",
                        CALLS=str(self.calls), GH_TOKEN="fixture-token",
                        GITHUB_REPOSITORY="fixture/repository", GITHUB_SHA="verified",
                        GITHUB_RUN_ID="123", GITHUB_REF="refs/heads/main",
                        GITHUB_EVENT_NAME="push", MAIN_SHA="verified")
        self.env.pop("RELEASE_ID", None)
        self.env.pop("FAIL_UPLOAD", None)

    def run_script(self, paths=None):
        """Run the actual shell script with only the GitHub CLI substituted."""
        if paths is None:
            paths = (self.debug_apk, self.release_apk)
        return subprocess.run(["bash", str(SCRIPT), *(str(path) for path in paths)],
                              env=self.env, text=True, capture_output=True, check=False)

    def recorded_calls(self):
        """Read observed external operations, including release notes before cleanup."""
        if not self.calls.exists():
            return []
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def test_invalid_context_or_artifact_never_calls_github(self):
        wrong_name = self.root / "artifact.zip"
        wrong_name.write_bytes(b"fixture archive")
        empty_debug = self.root / "empty" / "app-debug.apk"
        empty_release = self.root / "empty" / "app-release.apk"
        empty_debug.parent.mkdir()
        empty_debug.touch()
        empty_release.touch()
        apks = (self.debug_apk, self.release_apk)
        cases = [({"GITHUB_REF": "refs/pull/42/merge"}, apks),
                 ({"GITHUB_EVENT_NAME": "pull_request"}, apks),
                 ({}, (self.root / "missing-debug.apk", self.release_apk)),
                 ({}, (self.debug_apk, self.root / "missing-release.apk")),
                 ({}, (wrong_name, self.release_apk)),
                 ({}, (self.debug_apk, wrong_name)),
                 ({}, (empty_debug, self.release_apk)),
                 ({}, (self.debug_apk, empty_release)),
                 ({}, (self.debug_apk,)), ({}, ())]
        for changes, paths in cases:
            with self.subTest(changes=changes, paths=paths):
                previous = self.env.copy()
                self.env.update(changes)
                result = self.run_script(paths)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], self.recorded_calls())
                self.env = previous

    def test_superseded_build_does_not_replace_beta(self):
        self.env["MAIN_SHA"] = "newer"
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.recorded_calls()
        self.assertEqual(1, len(calls))
        self.assertIn("repos/fixture/repository/git/ref/heads/main", calls[0]["args"])

    def test_initial_beta_uploads_raw_apk_and_cleans_notes(self):
        self.env["GITHUB_EVENT_NAME"] = "workflow_dispatch"
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        publish = self.recorded_calls()[-1]
        args = publish["args"]
        self.assertEqual(["release", "create", "beta", str(self.debug_apk),
                          str(self.release_apk)], args[:5])
        self.assertEqual("verified", args[args.index("--target") + 1])
        self.assertIn("--prerelease", args)
        self.assertIn("--latest=false", args)
        self.assertIn("verified", publish["notes"])
        self.assertIn("https://github.com/fixture/repository/actions/runs/123", publish["notes"])
        self.assertFalse(Path(publish["notes_path"]).exists())

    def test_existing_beta_moves_tag_then_replaces_apk_and_notes(self):
        self.env["RELEASE_ID"] = "456"
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        patch, upload, edit = self.recorded_calls()[-3:]
        self.assertIn("repos/fixture/repository/git/refs/tags/beta", patch["args"])
        self.assertIn("sha=verified", patch["args"])
        self.assertIn("PATCH", patch["args"])
        self.assertIn("force=true", patch["args"])
        self.assertEqual(["release", "upload", "beta", str(self.debug_apk),
                          str(self.release_apk)], upload["args"][:5])
        self.assertIn("--clobber", upload["args"])
        self.assertEqual(["release", "edit", "beta"], edit["args"][:3])
        self.assertFalse(Path(edit["notes_path"]).exists())

    def test_failed_upload_stops_before_release_edit_and_cleans_notes(self):
        self.env.update(RELEASE_ID="456", FAIL_UPLOAD="1")
        # Isolate temp notes so cleanup is observable even when gh never receives them.
        notes_dir = self.root / "notes"
        notes_dir.mkdir()
        self.env["TMPDIR"] = str(notes_dir)
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(["release", "upload"], self.recorded_calls()[-1]["args"][:2])
        self.assertEqual([], list(notes_dir.iterdir()))


if __name__ == "__main__":
    unittest.main()
