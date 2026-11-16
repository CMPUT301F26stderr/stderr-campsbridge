#!/usr/bin/env python3
"""Close PRs containing the prohibited standalone word in their text or commit messages."""

import json
import os
from pathlib import Path
import re
import subprocess

MESSAGE = (
    "This PR violates our contribution policy. "
    "Please review your AI output before making another PR."
)
COMMITS_QUERY = """
query($owner: String!, $name: String!, $number: Int!, $cursor: String) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      commits(first: 100, after: $cursor) {
        nodes { commit { message } }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
"""


class GitHub:
    """Read PR metadata with the workflow token; never check out or run contributor code."""

    def request(self, route, *, method="GET", data=None):
        command = ["gh", "api", "--method", method, route]
        if data is not None:
            command += ["--input", "-"]
        result = subprocess.run(
            command, input=json.dumps(data) if data is not None else None,
            text=True, capture_output=True, check=True,
        )
        return json.loads(result.stdout or "null")

    def pages(self, route):
        result = subprocess.run(
            ["gh", "api", "--paginate", "--slurp", route + "?per_page=100"],
            text=True, capture_output=True, check=True,
        )
        return [item for page in json.loads(result.stdout) for item in page]


def contains_ref(text):
    """Match the standalone word after lowercasing, including at punctuation boundaries."""
    return bool(re.search(r"\bref\b", (text or "").lower()))


def commit_messages(api, repository, number):
    """Read every commit page; the REST PR-commits endpoint stops at 250 commits."""
    owner, name = repository.split("/", 1)
    cursor = None
    while True:
        result = api.request("graphql", method="POST", data={
            "query": COMMITS_QUERY,
            "variables": {"owner": owner, "name": name, "number": number, "cursor": cursor},
        })
        if result.get("errors"):
            raise RuntimeError("Could not read all PR commit messages")
        page = result["data"]["repository"]["pullRequest"]["commits"]
        for node in page["nodes"]:
            yield node["commit"]["message"]
        if not page["pageInfo"]["hasNextPage"]:
            return
        cursor = page["pageInfo"]["endCursor"]


def enforce(api, repository, event):
    """Inspect current PR metadata and close an unchanged, open violation once."""
    pull = event.get("pull_request")
    issue = event.get("issue", {})
    if not pull and not issue.get("pull_request"):
        return False
    number = pull["number"] if pull else issue["number"]
    root = "repos/" + repository
    pr = api.request(f"{root}/pulls/{number}")
    if pr["state"] != "open":
        return False
    comments = api.pages(f"{root}/issues/{number}/comments")
    texts = [pr["title"], pr.get("body")]
    texts.extend(item.get("body") for item in comments)
    for endpoint in ["reviews", "comments"]:
        texts.extend(item.get("body") for item in api.pages(f"{root}/pulls/{number}/{endpoint}"))
    violated = any(contains_ref(text) for text in texts)
    if not violated:
        violated = any(contains_ref(message) for message in commit_messages(api, repository, number))
    if not violated:
        return False
    latest = api.request(f"{root}/pulls/{number}")
    if latest["state"] != "open" or any(latest.get(key) != pr.get(key) for key in ["title", "body", "head"]):
        return False
    already_commented = any(
        item.get("body") == MESSAGE and item.get("user", {}).get("login") == "github-actions[bot]"
        for item in comments
    )
    if not already_commented:
        api.request(f"{root}/issues/{number}/comments", method="POST", data={"body": MESSAGE})
    api.request(f"{root}/pulls/{number}", method="PATCH", data={"state": "closed"})
    return True


def main():
    """Return a failed policy check after closing a violating PR."""
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    violated = enforce(GitHub(), os.environ["GITHUB_REPOSITORY"], event)
    print("PR closed for violating contribution policy." if violated else "Contribution policy check complete.")
    return int(violated)


if __name__ == "__main__":
    raise SystemExit(main())
