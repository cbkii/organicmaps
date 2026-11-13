"""Offline tests; no network, tokens or GitHub writes."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from tools.upstream_monitor import core, publish


def run(*args, cwd):
    return subprocess.check_output(["git", *args], cwd=cwd, stderr=subprocess.PIPE).decode().strip()


class MonitorTests(unittest.TestCase):
    def test_valid_tag_and_rejected_injection(self):
        self.assertEqual(core.safe_tag("2026.11.11-39-android"), "2026.11.11-39-android")
        for name in ("../bad", "--upload-pack", "@{", "a b", "a;rm", "a/", "-bad"):
            with self.subTest(name=name), self.assertRaises(core.MonitorError):
                core.safe_tag(name)

    def test_relevance_is_bounded_and_not_equivalence(self):
        self.assertEqual(core.relevance("android/app/A.kt")[0], "android")
        self.assertEqual(core.relevance("iphone/abc.swift")[0], "excluded-platform")
        classified, summary = core.file_analysis([
            {"status": "M", "path": "android/app/A.kt"},
            {"status": "A", "path": "libs/routing/Route.cpp"},
            {"status": "M", "path": "iphone/a.swift"}],
            {"android/app/A.kt": "a", "libs/routing/Route.cpp": "b"},
            {"android/app/A.kt": "a", "libs/routing/Route.cpp": "c"})
        by_path = {entry["path"]: entry for entry in classified}
        self.assertEqual(by_path["android/app/A.kt"]["disposition"], "ALREADY_PRESENT")
        self.assertEqual(by_path["libs/routing/Route.cpp"]["disposition"], "INVESTIGATE")
        self.assertEqual(by_path["iphone/a.swift"]["disposition"], "EXCLUDE")
        self.assertEqual(sum(summary.values()), 3)

    def test_release_selection_no_duplicate(self):
        catalog = [{"tag": "a-android", "id": 1}, {"tag": "b-android", "id": 2}]
        selected = core.select_releases(catalog, {1}, "analyse", "")
        self.assertEqual([(x["id"], previous["id"]) for x, previous in selected], [(2, 1)])
        self.assertEqual(core.select_releases(catalog, {1, 2}, "analyse", ""), [])
        self.assertEqual(len(core.select_releases(catalog, {1, 2}, "reanalyse", "a-android")), 1)
        with self.assertRaises(core.MonitorError):
            core.select_releases(catalog, set(), "analyse", "missing")

    def test_git_full_file_and_per_commit_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            run("init", "-q", cwd=path)
            run("config", "user.name", "Offline Test", cwd=path)
            run("config", "user.email", "test@example.invalid", cwd=path)
            (path / "android").mkdir()
            (path / "iphone").mkdir()
            (path / "android" / "A.kt").write_text("alpha\n")
            run("add", ".", cwd=path)
            run("commit", "-qm", "initial", cwd=path)
            base = run("rev-parse", "HEAD", cwd=path)
            (path / "android" / "A.kt").write_text("beta\n")
            (path / "iphone" / "Other.swift").write_text("other\n")
            run("add", ".", cwd=path)
            run("commit", "-qm", "Fix routing crash", cwd=path)
            head = run("rev-parse", "HEAD", cwd=path)
            changes = core.diff_index(path, base, head)
            self.assertEqual({x["path"] for x in changes}, {"android/A.kt", "iphone/Other.swift"})
            commits = core.git_commits(path, base, head, {})
            self.assertEqual(commits[0]["sha"], head)
            self.assertEqual(len(commits[0]["changed_paths"]), 2)
            self.assertEqual(commits[0]["priority_hint"], "HIGH-REVIEW")
            self.assertTrue(core.is_ancestor(path, base, head))
            self.assertFalse(core.is_ancestor(path, head, base))
            self.assertIn("android/A.kt", core.tree_index(path, head))

    def test_ledger_decision_parsing(self):
        sha = "f" * 40
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "ledger.md"
            p.write_text(f"| [abcd](https://github.com/organicmaps/organicmaps/commit/{sha}) | fix | Adapt | why |\n")
            self.assertEqual(core.ledger_decisions(p)[sha], "ADAPT")

    def test_publish_target_allowlist(self):
        publish.assert_fork_write("POST", "/repos/cbkii/organicmaps/issues")
        publish.assert_fork_write("PATCH", "/repos/cbkii/organicmaps/git/refs/heads/automation/upstream-review-evidence")
        for method, path in (("POST", "/repos/organicmaps/organicmaps/issues"),
                             ("POST", "/repos/cbkii/other/issues"),
                             ("DELETE", "/repos/cbkii/organicmaps/issues/1"),
                             ("POST", "/repos/cbkii/organicmaps/../organicmaps/issues")):
            with self.subTest(method=method, path=path), self.assertRaises(core.MonitorError):
                publish.assert_fork_write(method, path)

    def test_publisher_fork_context_required(self):
        with mock.patch.dict(os.environ, {"GITHUB_REPOSITORY": "organicmaps/organicmaps"}):
            with self.assertRaises(core.MonitorError):
                publish.ForkAPI("some-fake-fork-scoped-token")

    def test_sealed_evidence_rejects_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            contents = b"important evidence"
            (p / "report.md").write_bytes(contents)
            (p / "MANIFEST.sha256").write_text(hashlib.sha256(contents).hexdigest() + "  report.md\n")
            self.assertEqual(publish.manifest_verified(p)["report.md"], contents)
            (p / "report.md").write_text("changed")
            with self.assertRaises(core.MonitorError):
                publish.manifest_verified(p)

    def test_issue_human_notes_survive_managed_update(self):
        meta = {"release": {"tag": "2026.11.11-39-android", "id": 123, "published_at": "2026-11-12", "url": "https://github.com/organicmaps/organicmaps/releases/tag/test"},
                "upstream_commit_sha": "a" * 40, "previous_commit_sha": "b" * 40, "fork_commit_sha": "c" * 40,
                "reviewed_checkpoint": "d" * 40, "checkpoint_is_ancestor": True, "file_count": 1,
                "commit_count": 2, "categories": {"android": 1}, "patches": {"included": 0, "omitted": 1}}
        class FakeAPI:
            def __init__(self):
                self.calls = []
            def call(self, method, path, body=None):
                self.calls.append((method, path, body))
                return {}
        api = FakeAPI()
        original = publish.MANAGED_START + "\nold machine text\n" + publish.MANAGED_END + "\n\n## Decision\nKeep adaptation.\n"
        with mock.patch.object(publish, "locate_issue", return_value={"number": 47, "body": original, "state": "closed"}):
            issue = publish.upsert_issue(api, meta, "https://github.com/cbkii/organicmaps/tree/automation/upstream-review-evidence")
        self.assertEqual(issue, 47)
        self.assertEqual(len(api.calls), 1)
        self.assertIn("Keep adaptation.", api.calls[0][2]["body"])
        self.assertNotIn("old machine text", api.calls[0][2]["body"])
        self.assertEqual(api.calls[0][0], "PATCH")
        self.assertNotIn("state", api.calls[0][2])

    def test_no_implicit_upstream_http_write(self):
        with self.assertRaises(core.MonitorError):
            core.api_get_public("/repos/cbkii/organicmaps/issues")

    def test_immutable_blob_identity(self):
        self.assertEqual(publish.git_blob_sha(b"hello\n"), "ce013625030ba8dba906f756967f9e9ca394464a")


if __name__ == "__main__":
    unittest.main()
