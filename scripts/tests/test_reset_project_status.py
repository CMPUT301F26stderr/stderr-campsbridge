"""Check reopen and repair behavior without changing GitHub records."""

import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("reset_project_status", ROOT / "scripts/reset-project-status.py")
STATUS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STATUS)


def item(number=96, *, state="OPEN", status="Done", archived=False,
         repository="team/app", project="project-3"):
    return {
        "id": f"item-{number}", "isArchived": archived, "project": {"id": project},
        "fieldValueByName": {"name": status},
        "content": {"number": number, "state": state, "repository": {"nameWithOwner": repository}},
    }


class FakeGitHub:
    """Serve Project pages and fresh card state; record requested field updates."""

    def __init__(self, items=None):
        self.rows = items if items is not None else [item()]
        self.current = {row["id"]: copy.deepcopy(row) for row in self.rows}
        self.updates = []
        self.reads = []
        self.options = [{"id": "todo", "name": "Todo"}, {"id": "done", "name": "Done"}]
        self.project_exists = True
        self.page_size = 100
        self.failure = None

    def graphql(self, query, **variables):
        self.reads.append(variables)
        if self.failure:
            raise self.failure
        if "ProjectStatusTargets" in query:
            offset = int(variables.get("after") or 0)
            end = offset + self.page_size
            project = {
                "id": "project-3",
                "field": {"id": "status-field", "options": self.options},
                "items": {"nodes": self.rows[offset:end], "pageInfo": {
                    "hasNextPage": end < len(self.rows), "endCursor": str(end),
                }},
            }
            return {"organization": {"projectV2": project if self.project_exists else None}}
        if "ProjectStatusCurrent" in query:
            return {"node": self.current.get(variables["id"])}
        if "ResetIssueProjectStatus" in query:
            self.updates.append(variables["input"])
            return {"updateProjectV2ItemFieldValue": {"projectV2Item": {"id": variables["input"]["itemId"]}}}
        raise AssertionError("Unexpected GraphQL operation")


class ResetProjectStatusTest(unittest.TestCase):
    def reset(self, api, number=96, **options):
        return STATUS.reset_status(api, "team", 3, "team/app", number, **options)

    def test_reopened_done_card_returns_to_todo(self):
        api = FakeGitHub()
        self.assertEqual([96], self.reset(api))
        self.assertEqual([{
            "projectId": "project-3", "itemId": "item-96", "fieldId": "status-field",
            "value": {"singleSelectOptionId": "todo"},
        }], api.updates)

    def test_closed_and_non_done_cards_are_unchanged(self):
        cases = [item(state="CLOSED")] + [item(status=s) for s in (
            "Todo", "Ready", "Blocked", "In Progress", "In Review",
        )]
        for row in cases:
            with self.subTest(row=row):
                api = FakeGitHub([row])
                self.assertEqual([], self.reset(api))
                self.assertEqual([], api.updates)

    def test_archived_foreign_and_untracked_issues_are_unchanged(self):
        for rows in ([item(archived=True)], [item(repository="team/other")], [item(97)], []):
            with self.subTest(rows=rows):
                api = FakeGitHub(rows)
                self.assertEqual([], self.reset(api))
                self.assertEqual([], api.updates)

    def test_rechecks_closure_manual_status_and_membership_before_writing(self):
        for current in (item(state="CLOSED"), item(status="In Progress"), item(archived=True),
                        item(project="other-project"), None):
            with self.subTest(current=current):
                api = FakeGitHub()
                api.current["item-96"] = current
                self.assertEqual([], self.reset(api))
                self.assertEqual([], api.updates)

    def test_repair_reads_every_page_and_changes_only_open_done_cards(self):
        api = FakeGitHub([item(94), item(95, status="Blocked"), item(96),
                          item(97, state="CLOSED"), item(98, repository="team/other")])
        api.page_size = 2
        self.assertEqual([94, 96], self.reset(api, number=None))
        self.assertEqual(["item-94", "item-96"], [r["itemId"] for r in api.updates])
        self.assertEqual([None, "2", "4"], [r.get("after") for r in api.reads if "owner" in r])

    def test_missing_configuration_and_api_failures_are_reported(self):
        for mode in ("project", "todo", "status", "api"):
            with self.subTest(mode=mode):
                api = FakeGitHub()
                if mode == "project":
                    api.project_exists = False
                elif mode == "todo":
                    api.options = []
                elif mode == "status":
                    api.options = None
                else:
                    api.failure = RuntimeError("Project API unavailable")
                with self.assertRaises(RuntimeError):
                    self.reset(api)
                self.assertEqual([], api.updates)

    def test_dry_run_reports_candidates_without_writing(self):
        api = FakeGitHub()
        self.assertEqual([96], self.reset(api, dry_run=True))
        self.assertEqual([], api.updates)


if __name__ == "__main__":
    unittest.main()
