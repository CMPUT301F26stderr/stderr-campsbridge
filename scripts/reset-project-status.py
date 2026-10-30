#!/usr/bin/env python3
"""Move reopened issues out of Done in the configured team Project."""

import argparse
import json
import os
import subprocess


ITEM_FIELDS = """
  id isArchived project { id }
  fieldValueByName(name: "Status") { ... on ProjectV2ItemFieldSingleSelectValue { name } }
  content { ... on Issue { number state repository { nameWithOwner } } }
"""
TARGETS = """
query ProjectStatusTargets($owner: String!, $number: Int!, $after: String) {
  organization(login: $owner) {
    projectV2(number: $number) {
      id
      field(name: "Status") { ... on ProjectV2SingleSelectField { id options { id name } } }
      items(first: 100, after: $after) {
        nodes { %s }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
""" % ITEM_FIELDS
CURRENT = """
query ProjectStatusCurrent($id: ID!) {
  node(id: $id) { ... on ProjectV2Item { %s } }
}
""" % ITEM_FIELDS
RESET = """
mutation ResetIssueProjectStatus($input: UpdateProjectV2ItemFieldValueInput!) {
  updateProjectV2ItemFieldValue(input: $input) { projectV2Item { id } }
}
"""


class GitHub:
    """Use gh's token without placing credentials in arguments or logs."""

    def graphql(self, query, **variables):
        result = subprocess.run(
            ["gh", "api", "graphql", "--input", "-"],
            input=json.dumps({"query": query, "variables": variables}),
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError("GitHub Project API request failed; check token access and the workflow logs.")
        response = json.loads(result.stdout)
        if response.get("errors"):
            raise RuntimeError("GitHub Project API returned errors: " + json.dumps(response["errors"]))
        return response["data"]


def needs_reset(row, project_id, repository, issue_number):
    """Match only live open issues still marked Done in this Project."""
    if not row or row["isArchived"] or row["project"]["id"] != project_id:
        return False
    content = row.get("content") or {}
    status = row.get("fieldValueByName") or {}
    return (
        content.get("repository", {}).get("nameWithOwner") == repository
        and content.get("state") == "OPEN"
        and status.get("name") == "Done"
        and (issue_number is None or content.get("number") == issue_number)
    )


def reset_status(api, owner, project_number, repository, issue_number=None, *, dry_run=False):
    """Reset open Done cards while leaving other statuses and archived items alone."""
    changed = []
    after = None
    while True:
        data = api.graphql(TARGETS, owner=owner, number=project_number, after=after)
        organization = data.get("organization") or {}
        project = organization.get("projectV2")
        if not project:
            raise RuntimeError(f"Project {owner}/{project_number} is unavailable to this token.")
        status = project.get("field") or {}
        options = {option["name"]: option["id"] for option in status.get("options") or []}
        if "id" not in status or not {"Todo", "Done"} <= options.keys():
            raise RuntimeError("The Project needs a Status field with Todo and Done options.")
        for row in project["items"]["nodes"]:
            if not needs_reset(row, project["id"], repository, issue_number):
                continue
            # A queued close/reopen event mustn't undo a later manual status or closure.
            current = api.graphql(CURRENT, id=row["id"])["node"]
            if not needs_reset(current, project["id"], repository, issue_number):
                continue
            if not dry_run:
                api.graphql(RESET, input={
                    "projectId": project["id"], "itemId": row["id"], "fieldId": status["id"],
                    "value": {"singleSelectOptionId": options["Todo"]},
                })
            changed.append(current["content"]["number"])
        page = project["items"]["pageInfo"]
        if not page["hasNextPage"]:
            return changed
        after = page["endCursor"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    scope = parser.add_mutually_exclusive_group(required=True)
    scope.add_argument("--issue", type=int, help="Recheck one closed/reopened issue")
    scope.add_argument("--all", action="store_true", help="Repair existing open Done cards")
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--project", type=int, default=3)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.repository or len(args.repository.split("/")) != 2:
        parser.error("--repository must be OWNER/REPO, or set GITHUB_REPOSITORY")
    if args.project <= 0 or args.issue is not None and args.issue <= 0:
        parser.error("Project and issue numbers must be positive")
    changed = reset_status(
        GitHub(), args.repository.split("/")[0], args.project, args.repository,
        args.issue, dry_run=args.dry_run,
    )
    action = "Would move" if args.dry_run else "Moved"
    for number in changed:
        print(f"{action} #{number} from Done to Todo.")
    if not changed:
        print("No open Done cards needed resetting.")


if __name__ == "__main__":
    main()
