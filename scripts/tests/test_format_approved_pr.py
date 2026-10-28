"""Verify approval formatting with disposable Git repositories and no GitHub mutations."""

import copy
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("format_approved", ROOT / "scripts/format-approved-pr.py")
FORMAT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FORMAT)


class FormatApprovedPrTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "origin.git"
        self.author = self.root / "author"
        self.checkout = self.root / "reviewed"
        subprocess.run(["git", "init", "--bare", "--quiet", "--initial-branch=main", str(self.remote)], check=True)
        subprocess.run(["git", "clone", "--quiet", str(self.remote), str(self.author)], check=True,
                       capture_output=True)
        self.git(self.author, "config", "user.name", "Student")
        self.git(self.author, "config", "user.email", "student@example.test")
        (self.author / "Existing.kt").write_text("old code stays unchanged\n")
        self.git(self.author, "add", "Existing.kt")
        self.git(self.author, "commit", "--quiet", "-m", "base")
        self.base = self.git(self.author, "rev-parse", "HEAD")
        self.git(self.author, "push", "--quiet", "origin", "main")
        self.git(self.author, "checkout", "--quiet", "-b", "student/task")
        (self.author / "Feature.kt").write_text("unformatted\n")
        self.git(self.author, "add", "Feature.kt")
        self.git(self.author, "commit", "--quiet", "-m", "feature")
        self.head = self.git(self.author, "rev-parse", "HEAD")
        self.git(self.author, "push", "--quiet", "origin", "student/task")
        subprocess.run(["git", "clone", "--quiet", str(self.remote), str(self.checkout)], check=True)
        self.git(self.checkout, "checkout", "--quiet", self.head)
        self.config = self.root / "trusted-hooks.yaml"
        self.config.write_text((ROOT / ".pre-commit-config.yaml").read_text())
        self.pr = {
            "state": "open", "draft": False,
            "head": {"sha": self.head, "ref": "student/task", "repo": {"full_name": "team/app"}},
            "base": {"sha": self.base, "ref": "main", "repo": {"full_name": "team/app"}},
        }
        self.event = {
            "repository": {"full_name": "team/app", "default_branch": "main"},
            "review": {"state": "approved", "commit_id": self.head},
            "pull_request": {"number": 7},
        }
        self.dispatches = []
        self.formatted = []
        self.next_pr = None

    def git(self, directory, *args):
        return subprocess.check_output(["git", *args], cwd=directory, stderr=subprocess.PIPE, text=True).strip()

    def api(self, path, *, data=None):
        if data is not None:
            self.dispatches.append((path, data))
            return None
        result = copy.deepcopy(self.pr)
        if self.next_pr is not None:
            self.pr = self.next_pr
            self.next_pr = None
        return result

    def formatter(self, checkout, config, files):
        self.assertEqual(self.config, config)
        self.formatted = files
        for name in files:
            (checkout / name).write_text("formatted\n")
        (checkout / "untracked-output.txt").write_text("do not commit this\n")

    def run_format(self, **options):
        return FORMAT.format_approved(self.event, self.checkout, self.config,
                                      api=self.api, formatter=options.get("formatter", self.formatter))

    def test_formats_only_pr_files_pushes_bot_commit_and_dispatches_ci(self):
        self.assertTrue(self.run_format())
        self.assertEqual(["Feature.kt"], self.formatted)
        new_head = self.git(self.checkout, "rev-parse", "HEAD")
        self.assertNotEqual(self.head, new_head)
        self.assertEqual(new_head, self.git(self.checkout, "ls-remote", "origin", "refs/heads/student/task").split()[0])
        self.assertEqual(self.head, self.git(self.checkout, "rev-parse", "HEAD^"))
        self.assertEqual(FORMAT.BOT_EMAIL, self.git(self.checkout, "log", "-1", "--format=%ae"))
        self.assertEqual("Feature.kt", self.git(self.checkout, "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"))
        self.assertEqual("old code stays unchanged\n", (self.checkout / "Existing.kt").read_text())
        self.assertEqual([("repos/team/app/actions/workflows/ci.yml/dispatches", {"ref": "student/task"})], self.dispatches)

    def test_clean_formatting_does_not_push_or_dispatch(self):
        self.assertFalse(self.run_format(formatter=lambda *_: None))
        self.assertEqual(self.head, self.git(self.checkout, "rev-parse", "HEAD"))
        self.assertEqual([], self.dispatches)

    def test_stale_review_fork_closed_or_draft_pr_never_formats(self):
        cases = [("head", "sha", "newer"), ("head_repo", "full_name", "fork/app"),
                 ("pr", "state", "closed"), ("pr", "draft", True), ("base", "ref", "release")]
        for target, key, value in cases:
            with self.subTest(target=target, key=key):
                original = copy.deepcopy(self.pr)
                record = self.pr if target == "pr" else self.pr["head"]["repo"] if target == "head_repo" else self.pr[target]
                record[key] = value
                self.assertFalse(self.run_format())
                self.assertEqual([], self.formatted)
                self.pr = original

    def test_non_approval_event_does_nothing(self):
        self.event["review"]["state"] = "commented"
        self.assertFalse(self.run_format())
        self.assertEqual([], self.formatted)

    def test_dirty_or_wrong_revision_checkout_is_rejected(self):
        (self.checkout / "Feature.kt").write_text("local work\n")
        with self.assertRaisesRegex(RuntimeError, "clean, disposable"):
            self.run_format()
        (self.checkout / "Feature.kt").write_text("unformatted\n")
        self.git(self.checkout, "checkout", "--quiet", self.base)
        with self.assertRaisesRegex(RuntimeError, "approved revision"):
            self.run_format()
        self.assertEqual([], self.dispatches)

    def test_new_author_commit_during_formatting_is_not_overwritten(self):
        self.next_pr = copy.deepcopy(self.pr)
        self.next_pr["head"]["sha"] = "author-pushed-new-work"
        self.assertFalse(self.run_format())
        self.assertEqual(self.head, self.git(self.checkout, "rev-parse", "HEAD"))
        self.assertEqual([], self.dispatches)

    def test_concurrent_push_after_metadata_check_is_rejected_by_git(self):
        def concurrent_formatter(checkout, config, files):
            self.formatter(checkout, config, files)
            (self.author / "Feature.kt").write_text("author's newer implementation\n")
            self.git(self.author, "add", "Feature.kt")
            self.git(self.author, "commit", "--quiet", "-m", "new author work")
            self.git(self.author, "push", "--quiet", "origin", "student/task")
        with self.assertRaises(subprocess.CalledProcessError):
            self.run_format(formatter=concurrent_formatter)
        expected = self.git(self.author, "rev-parse", "HEAD")
        remote = self.git(self.checkout, "ls-remote", "origin", "refs/heads/student/task").split()[0]
        self.assertEqual(expected, remote)
        self.assertEqual([], self.dispatches)

    def test_hooks_repeat_after_fixes_but_stop_on_uncorrectable_failure(self):
        fixed = subprocess.CompletedProcess([], 1)
        clean = subprocess.CompletedProcess([], 0)
        with patch.object(FORMAT.subprocess, "run", side_effect=[fixed, clean, clean, clean]) as run:
            FORMAT.format_hooks(self.checkout, self.config, ["Feature.kt"])
            calls = run.call_args_list
            self.assertEqual(4, len(calls))
            self.assertEqual(calls[0].args, calls[1].args)
            self.assertEqual(self.checkout, calls[0].kwargs["cwd"])
        with patch.object(FORMAT.subprocess, "run", return_value=fixed):
            with self.assertRaisesRegex(RuntimeError, "could not finish automatically"):
                FORMAT.format_hooks(self.checkout, self.config, ["Feature.kt"])
        with patch.object(FORMAT.subprocess, "run", return_value=subprocess.CompletedProcess([], 3)) as run:
            with self.assertRaises(RuntimeError):
                FORMAT.format_hooks(self.checkout, self.config, ["Feature.kt"])
            self.assertEqual(1, run.call_count)

    def test_github_dispatch_body_is_structured_json(self):
        with patch.object(FORMAT.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, '', '')) as run:
            self.assertIsNone(FORMAT.github("repos/team/app/actions/workflows/ci.yml/dispatches", data={"ref": "student/task"}))
            self.assertEqual('{"ref": "student/task"}', run.call_args.kwargs["input"])
            self.assertIn("--input", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
