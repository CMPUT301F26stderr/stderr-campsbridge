"""Check real diff selection and read-only CI behavior in disposable Git checkouts."""

import contextlib
import importlib.util
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("check_hooks", ROOT / "scripts/check-hooks.py")
CHECKS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKS)


class CheckHooksTest(unittest.TestCase):
    """Own changed-file selection, aggregate failures and the absence of auto-fixes."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        previous_cwd = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous_cwd)
        self.environment = patch.dict(os.environ, {"BASE_REF": "", "HEAD_REF": ""})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.git("init", "--quiet")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.test")
        Path(".git/info/exclude").write_text("tools/\nsecret-calls.txt\n")
        shutil.copy(ROOT / ".pre-commit-config.yaml", self.root)
        self.calls = self.root / "secret-calls.txt"
        tools = self.root / "tools"
        tools.mkdir()
        scanner = tools / "gitleaks"
        scanner.write_text('#!/usr/bin/env python3\nimport os, sys\n'
                           'with open(os.environ["SCANNER_CALLS"], "a") as out:\n'
                           '    out.write(" ".join(sys.argv[1:]) + "\\n")\n'
                           'sys.exit(int(os.environ.get("SCANNER_EXIT", "0")))\n')
        scanner.chmod(0o755)
        self.tools = patch.dict(os.environ, {
            "PATH": f"{tools}:{os.environ['PATH']}",
            "SCANNER_CALLS": str(self.calls), "SCANNER_EXIT": "0",
        })
        self.tools.start()
        self.addCleanup(self.tools.stop)

    def git(self, *args):
        """Execute real Git operations restricted to the disposable checkout."""
        return subprocess.check_output(["git", *args], text=True).strip()

    def commit(self):
        """Record fixture files and return the commit selected by CI."""
        self.git("add", "--all")
        self.git("commit", "--quiet", "-m", "fixture")
        return self.git("rev-parse", "HEAD")

    def test_checkout_selection_ignores_untracked_deleted_and_symlink_files(self):
        Path("with space\nand newline.json").write_text('{}\n')
        Path("deleted.md").write_text("deleted\n")
        Path("link.json").symlink_to("with space\nand newline.json")
        self.commit()
        Path("deleted.md").unlink()
        Path("untracked.json").write_text("invalid JSON")
        paths = CHECKS.selected_files()
        self.assertEqual({".pre-commit-config.yaml", "with space\nand newline.json"}, set(paths))
        os.environ["BASE_REF"] = "0" * 40
        self.assertEqual(paths, CHECKS.selected_files())

    def test_diff_selection_uses_both_revisions_and_ignores_deletions(self):
        Path("old name.json").write_text('{}\n')
        Path("deleted.md").write_text("old\n")
        base = self.commit()
        self.git("mv", "old name.json", "new name.json")
        Path("deleted.md").unlink()
        Path("added.toml").write_text("value = 1\n")
        head = self.commit()
        Path("untracked.json").write_text("invalid JSON")
        os.environ.update(BASE_REF=base, HEAD_REF=head)
        self.assertEqual({"new name.json", "added.toml"}, set(CHECKS.selected_files()))

    def test_diff_without_head_fails_clearly(self):
        os.environ.update(BASE_REF="base", HEAD_REF="")
        with self.assertRaisesRegex(SystemExit, "HEAD_REF is required"):
            CHECKS.selected_files()

    def test_valid_checkout_passes_without_reformatting_and_secrets_can_fail_it(self):
        content = 'Trailing whitespace stays here.  \nNo final newline'
        Path("notes.md").write_text(content)
        self.commit()
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, CHECKS.main())
            os.environ["SCANNER_EXIT"] = "1"
            self.assertEqual(1, CHECKS.main())
        self.assertEqual(content, Path("notes.md").read_text())
        self.assertEqual(["dir --redact --no-banner"] * 2, self.calls.read_text().splitlines())

    def test_file_failures_are_aggregated_and_secret_scan_still_runs(self):
        Path("bad.json").write_text('{invalid\n')
        Path("bad.toml").write_text('value = [\n')
        Path("conflict.md").write_text('<<<<<<< ours\nours\n=======\ntheirs\n>>>>>>> theirs\n')
        Path("large.txt").write_text("x" * (1024 * 1024 + 1))
        self.commit()
        originals = {name: Path(name).read_bytes() for name in
                     ["bad.json", "bad.toml", "conflict.md", "large.txt"]}
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(1, CHECKS.main())
        for hook in ["check-json", "check-toml", "check-merge-conflict", "check-added-large-files"]:
            self.assertIn(f"{hook}: failed", output.getvalue())
        self.assertEqual("dir --redact --no-banner\n", self.calls.read_text())
        self.assertEqual(originals, {name: Path(name).read_bytes() for name in originals})


if __name__ == "__main__":
    unittest.main()
