"""Exercise failure diagnosis and PR-comment decisions without posting to GitHub."""

import contextlib
import copy
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("ci_feedback", ROOT / "scripts/ci-feedback.py")
FEEDBACK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FEEDBACK)


class FakeGitHub:
    """Supply controlled GitHub responses and record every requested mutation."""

    def __init__(self):
        self.run = {
            "id": 42, "run_attempt": 1, "name": "CI", "event": "pull_request",
            "status": "completed", "conclusion": "failure", "head_sha": "abc1234",
            "html_url": "https://github.com/team/app/actions/runs/42", "pull_requests": [{"number": 7}],
        }
        self.pr = {
            "number": 7, "state": "open", "user": {"login": "student"},
            "head": {"sha": "abc1234"}, "base": {"repo": {"full_name": "team/app"}},
        }
        self.job = {
            "id": 81, "name": "Android checks", "conclusion": "failure",
            "html_url": "https://github.com/team/app/actions/runs/42/job/81",
            "steps": [{"name": "Lint, test and build", "conclusion": "failure"}],
        }
        self.jobs = [self.job, {**self.job, "id": 82, "name": "CI ready"}]
        self.log = "e: file:///app/Name.kt:12:3 Unresolved reference 'result'\n> Task :app:compileDebugKotlin FAILED"
        self.comments = []
        self.posts = []
        self.queries = []
        self.next_pr = None
        self.log_unavailable = False

    def request(self, path, *, method="GET", data=None, text=False):
        self.queries.append((path, method))
        if method != "GET":
            self.posts.append((path, method, data))
            return {"id": 100}
        if path.endswith("/actions/runs/42"):
            return self.run
        if path.endswith("/pulls/7"):
            result = copy.deepcopy(self.pr)
            if self.next_pr is not None:
                self.pr = self.next_pr
                self.next_pr = None
            return result
        if path.endswith("/logs"):
            if self.log_unavailable:
                raise subprocess.CalledProcessError(1, ["gh", "api"])
            return self.log
        raise AssertionError(f"Unexpected API request: {path}")

    def pages(self, path, key=None):
        self.queries.append((path, "GET"))
        if path.endswith("/jobs"):
            return self.jobs
        if path.endswith("/comments"):
            return self.comments
        if path.endswith("/pulls"):
            return [{"number": 7}]
        raise AssertionError(f"Unexpected pagination: {path}")


class CiFeedbackTest(unittest.TestCase):
    def setUp(self):
        self.api = FakeGitHub()
        self.event = {"workflow_run": copy.deepcopy(self.api.run)}

    def report(self, **options):
        return FEEDBACK.report(self.api, "team/app", self.event, **options)

    def test_compile_failure_mentions_author_and_links_diagnostic(self):
        self.assertEqual([7], self.report())
        self.assertEqual(1, len(self.api.posts))
        path, method, data = self.api.posts[0]
        self.assertEqual(("repos/team/app/issues/7/comments", "POST"), (path, method))
        body = data["body"]
        for expected in ["@student", "Compilation failed", "Name.kt:12:3", "abc1234",
                         "pre-commit install", "pre-commit run --all-files", "bash scripts/check.sh"]:
            self.assertIn(expected, body)
        self.assertNotIn("CI ready:", body)

    def test_unit_test_failure_shows_test_name_and_assertion(self):
        self.api.log = "BookingTest > rejectsDuplicate FAILED\n    java.lang.AssertionError\n> Task :app:testDebugUnitTest FAILED"
        self.report()
        body = self.api.posts[0][2]["body"]
        self.assertIn("Unit tests failed", body)
        self.assertIn("rejectsDuplicate", body)
        self.assertIn("AssertionError", body)

    def test_duplicate_event_is_ignored_only_for_our_bot_marker(self):
        marker = FEEDBACK.MARKER.format(run_id=42, attempt=1)
        self.api.comments = [{"user": {"login": FEEDBACK.BOT}, "body": marker}]
        self.assertEqual([], self.report())
        self.assertEqual([], self.api.posts)
        self.api.comments[0]["user"]["login"] = "student"
        self.assertEqual([7], self.report())

    def test_a_new_run_attempt_can_notify_again(self):
        self.api.comments = [{"user": {"login": FEEDBACK.BOT},
                              "body": FEEDBACK.MARKER.format(run_id=42, attempt=1)}]
        self.api.run["run_attempt"] = self.event["workflow_run"]["run_attempt"] = 2
        self.assertEqual([7], self.report())

    def test_success_cancelled_and_non_pr_runs_never_comment(self):
        for key, value in [("conclusion", "success"), ("conclusion", "cancelled"),
                           ("event", "push"), ("name", "Other workflow")]:
            with self.subTest(key=key, value=value):
                self.setUp()
                self.event["workflow_run"][key] = value
                self.assertEqual([], self.report())
                self.assertEqual([], self.api.queries)

    def test_stale_events_closed_prs_and_other_repositories_are_ignored(self):
        for field, value in [("sha", "newer-sha"), ("state", "closed"), ("repo", "other/repo")]:
            with self.subTest(field=field):
                self.setUp()
                if field == "sha":
                    self.api.pr["head"]["sha"] = value
                elif field == "state":
                    self.api.pr["state"] = value
                else:
                    self.api.pr["base"]["repo"]["full_name"] = value
                self.assertEqual([], self.report())
                self.assertEqual([], self.api.posts)

    def test_delayed_event_cannot_comment_after_a_new_attempt_started(self):
        self.api.run["run_attempt"] = 2
        self.api.run["status"] = "in_progress"
        self.assertEqual([], self.report())
        self.assertEqual([], self.api.posts)

    def test_new_commit_arriving_during_log_collection_is_rechecked(self):
        self.api.next_pr = copy.deepcopy(self.api.pr)
        self.api.next_pr["head"]["sha"] = "new-commit"
        self.assertEqual([], self.report())
        self.assertEqual([], self.api.posts)

    def test_missing_pr_event_association_uses_commit_lookup(self):
        self.event["workflow_run"]["pull_requests"] = []
        self.assertEqual([7], self.report())
        self.assertIn(("repos/team/app/commits/abc1234/pulls", "GET"), self.api.queries)

    def test_missing_logs_still_produce_actionable_links(self):
        self.api.log_unavailable = True
        self.report()
        self.assertIn("log excerpt was unavailable", self.api.posts[0][2]["body"])
        self.assertIn(self.api.job["html_url"], self.api.posts[0][2]["body"])
        self.assertNotIn("Compilation failed", self.api.posts[0][2]["body"])

    def test_workflow_startup_failure_without_jobs_still_notifies(self):
        self.api.jobs = []
        self.report()
        self.assertIn("CI job failed", self.api.posts[0][2]["body"])
        self.assertIn(self.api.run["html_url"], self.api.posts[0][2]["body"])

    def test_bot_dispatched_ci_failure_can_find_its_pr(self):
        self.event["workflow_run"]["event"] = "workflow_dispatch"
        self.event["workflow_run"]["pull_requests"] = []
        self.assertEqual([7], self.report())

    def test_expected_runtime_errors_do_not_hide_the_actual_failure(self):
        log = "Unexpected rules runtime error: WARNING from runtime\nexpected PERMISSION_DENIED\n"
        log += "ordinary test output\nError: An unexpected error has occurred.\n##[error]Process completed with exit code 2."
        log += "\ncleanup step and unrelated artifacts"
        excerpt = FEEDBACK.diagnostic(log)
        self.assertIn("An unexpected error has occurred", excerpt)
        self.assertNotIn("WARNING from runtime", excerpt)
        self.assertNotIn("unrelated artifacts", excerpt)

    def test_dry_run_prints_without_posting(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual([7], self.report(dry_run=True))
        self.assertIn("@student", output.getvalue())
        self.assertEqual([], self.api.posts)

    def test_excerpts_select_diagnostic_not_tail_and_are_bounded(self):
        log = "\n".join(["setup"] * 30 + ["2026-10-24T12:00:00Z \x1b[31me: App.kt:8 Broken\x1b[0m"]
                         + [f"context {i}" for i in range(40)] + ["BUILD FAILED"])
        excerpt = FEEDBACK.diagnostic(log)
        self.assertIn("e: App.kt:8 Broken", excerpt)
        self.assertNotIn("BUILD FAILED", excerpt)
        self.assertNotIn("\x1b", excerpt)
        self.assertNotIn("2026-10-24", excerpt)
        self.assertLessEqual(len(excerpt.splitlines()), 20)
        self.assertLessEqual(len(FEEDBACK.diagnostic("e: " + "x" * 5000)), 4000)
        self.assertEqual("", FEEDBACK.diagnostic("ordinary output\nno diagnostic"))

    def test_embedded_fences_stay_inside_the_log_excerpt(self):
        self.api.log = "e: broken\n```\n@another-person\n```"
        self.report()
        body = self.api.posts[0][2]["body"]
        self.assertIn("````text\ne: broken\n```\n@another-person\n```\n````", body)

    def test_failure_categories_and_relevant_reproduction_commands(self):
        cases = [
            ("Pre-commit checks", "Name.kt:7 standard:indent", "Kotlin formatting failed"),
            ("Android checks", "> Task :app:lintDebug FAILED", "Android Lint failed"),
            ("Android checks", "> Task :app:pureLogicCoverage FAILED", "Coverage requirements failed"),
            ("Android UI checks", "> Task :app:connectedDebugAndroidTest FAILED", "Android UI tests failed"),
            ("Firebase contracts", "not ok 7 - blocked account", "Firebase checks failed"),
        ]
        for name, log, kind in cases:
            with self.subTest(name=name, kind=kind):
                self.setUp()
                self.api.job["name"] = name
                self.api.log = log
                self.report()
                body = self.api.posts[0][2]["body"]
                self.assertIn(kind, body)
                if name == "Firebase contracts":
                    self.assertIn("check-firebase.sh", body)
                if name == "Android UI checks":
                    self.assertIn("check-android-ui.sh", body)

    def test_api_pagination_and_structured_comment_body(self):
        responses = [subprocess.CompletedProcess([], 0, '"ok"', ''),
                     subprocess.CompletedProcess([], 0, '{"jobs": [1, 2]}', '')]
        with patch.object(FEEDBACK.subprocess, "run", side_effect=responses) as run:
            api = FEEDBACK.GitHub()
            self.assertEqual("ok", api.request("repos/team/app/issues/7/comments", method="POST", data={"body": "line one\nline two"}))
            self.assertEqual([1, 2], api.pages("repos/team/app/actions/runs/42/jobs", "jobs"))
            self.assertEqual('{"body": "line one\\nline two"}', run.call_args_list[0].kwargs["input"])
            self.assertIn("--input", run.call_args_list[0].args[0])

    def test_colored_logs_are_captured_for_sanitizing(self):
        with patch.object(FEEDBACK.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, '\x1b[31me: failure\x1b[0m', '')) as run:
            log = FEEDBACK.GitHub().request("repos/team/app/actions/jobs/81/logs", text=True)
            self.assertIn("--allow-escape-sequences", run.call_args.args[0])
            self.assertEqual("e: failure", FEEDBACK.diagnostic(log))

    def test_pagination_fetches_more_than_the_first_page(self):
        api = FEEDBACK.GitHub()
        with patch.object(api, "request", side_effect=[list(range(100)), [100]]) as request:
            self.assertEqual(list(range(101)), api.pages("repos/team/app/issues/7/comments"))
            self.assertIn("page=2", request.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
