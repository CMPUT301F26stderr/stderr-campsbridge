"""Check PR text matching and closure decisions without sending GitHub requests."""

import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("pr_policy", ROOT / "scripts/pr-contribution-policy.py")
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)


class FakeGitHub:
    """Return current PR text and record the policy's mutations."""

    def __init__(self):
        self.pr = {"number": 7, "state": "open", "title": "Edit campsites", "body": None,
                   "head": {"sha": "current-head"}}
        self.comments = []
        self.reviews = []
        self.inline = []
        self.commits = [["Save changes"]]
        self.posts = []
        self.cursors = []
        self.next_pr = None

    def request(self, route, *, method="GET", data=None):
        if method == "GET":
            value = copy.deepcopy(self.pr)
            if self.next_pr is not None:
                self.pr = self.next_pr
                self.next_pr = None
            return value
        if route == "graphql":
            cursor = data["variables"]["cursor"]
            self.cursors.append(cursor)
            page = int(cursor) if cursor else 0
            return {"data": {"repository": {"pullRequest": {"commits": {
                "nodes": [{"commit": {"message": message}} for message in self.commits[page]],
                "pageInfo": {"hasNextPage": page + 1 < len(self.commits), "endCursor": str(page + 1)},
            }}}}}
        self.posts.append((route, method, data))
        return {}

    def pages(self, route):
        if "/issues/" in route:
            return self.comments
        return self.reviews if route.endswith("/reviews") else self.inline


class PrContributionPolicyTest(unittest.TestCase):
    """Keep the requested word boundary and every covered PR text source explicit."""

    def test_standalone_word_is_case_insensitive_at_boundaries_and_punctuation(self):
        for text in ["ref", " REF ", "use\nRef\there", "(ref)", "https://example.org/ref/file"]:
            with self.subTest(text=text):
                self.assertTrue(POLICY.contains_ref(text))
        for text in [None, "", "reference", "refactor", "refresh", "refs/heads/main", "my_ref", "preferred"]:
            with self.subTest(text=text):
                self.assertFalse(POLICY.contains_ref(text))

    def test_each_requested_source_closes_and_comments_once(self):
        for source in ["title", "body", "comment", "review", "inline", "commit"]:
            with self.subTest(source=source):
                api = FakeGitHub()
                if source in ["title", "body"]:
                    api.pr[source] = "Use REF here"
                elif source == "commit":
                    api.commits = [["Details\n\nTaken from ref yesterday"]]
                else:
                    getattr(api, {"comment": "comments", "review": "reviews", "inline": "inline"}[source]).append({"body": "Use ref here"})
                self.assertTrue(POLICY.enforce(api, "team/app", {"pull_request": {"number": 7}}))
                self.assertEqual([
                    ("repos/team/app/issues/7/comments", "POST", {"body": POLICY.MESSAGE}),
                    ("repos/team/app/pulls/7", "PATCH", {"state": "closed"}),
                ], api.posts)

    def test_scans_later_commit_pages_and_does_not_repeat_policy_comment(self):
        api = FakeGitHub()
        api.commits = [["Save details"] * 100, ["Adjust layout"] * 100, ["Finish UI"] * 100, ["Copy ref editor"]]
        api.comments = [{"body": POLICY.MESSAGE, "user": {"login": "github-actions[bot]"}}]
        self.assertTrue(POLICY.enforce(api, "team/app", {"issue": {"number": 7, "pull_request": {"url": "pr"}}}))
        self.assertEqual([None, "1", "2", "3"], api.cursors)
        self.assertEqual([("repos/team/app/pulls/7", "PATCH", {"state": "closed"})], api.posts)

    def test_clean_pr_closed_pr_and_ordinary_issue_have_no_side_effects(self):
        for payload in [{"pull_request": {"number": 7}}, {"issue": {"number": 8}}]:
            api = FakeGitHub()
            self.assertFalse(POLICY.enforce(api, "team/app", payload))
            self.assertEqual([], api.posts)
        api = FakeGitHub()
        api.pr.update(state="closed", title="Use ref")
        self.assertFalse(POLICY.enforce(api, "team/app", {"pull_request": {"number": 7}}))
        self.assertEqual([], api.posts)

    def test_changed_head_or_corrected_title_does_not_close_from_stale_results(self):
        for change in [{"head": {"sha": "new-head"}}, {"title": "Fixed title"}, {"state": "closed"}]:
            api = FakeGitHub()
            api.pr["title"] = "Use ref editor"
            api.next_pr = {**copy.deepcopy(api.pr), **change}
            self.assertFalse(POLICY.enforce(api, "team/app", {"pull_request": {"number": 7}}))
            self.assertEqual([], api.posts)


if __name__ == "__main__":
    unittest.main()
