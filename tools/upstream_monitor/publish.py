"""Fork-only evidence and Issue publisher. No upstream write endpoint exists.

Only executed in the isolated publish job; requires a fork-scoped GITHUB_TOKEN.
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path
import re
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .core import FORK, MonitorError, slug, MAX_BODY

EVIDENCE_BRANCH = "automation/upstream-review-evidence"
ALLOWED = {"GET", "POST", "PATCH"}
MANAGED_START = "<!-- in-car-upstream-monitor:start -->"
MANAGED_END = "<!-- in-car-upstream-monitor:end -->"


def assert_fork_write(method: str, path: str) -> None:
    """Reject every write unless it addresses the exact fork REST path."""
    if method not in ALLOWED or not path.startswith(f"/repos/{FORK}/"):
        raise MonitorError(f"GitHub API operation outside fork allowlist rejected: {method} {path}")
    if ".." in path or "//" in path or "%" in path or "?" in path:
        raise MonitorError("Unsafe write API path")


class ForkAPI:
    def __init__(self, token: str):
        if os.getenv("GITHUB_REPOSITORY") != FORK:
            raise MonitorError("Publisher only runs in cbkii/organicmaps")
        if not token or len(token) < 10:
            raise MonitorError("Missing fork-scoped GITHUB_TOKEN")
        self.token = token

    def call(self, method: str, path: str, body: dict | None = None, missing_ok: bool = False) -> Any:
        assert_fork_write(method, path)
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = Request("https://api.github.com" + path, data=data, method=method,
                      headers={"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                               "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28",
                               "User-Agent": "InCar-Upstream-Fork-Only-Publisher"})
        try:
            with urlopen(req, timeout=50) as resp:
                response = resp.read()
            return json.loads(response) if response else {}
        except HTTPError as exc:
            if missing_ok and exc.code == 404:
                return None
            raise MonitorError(f"Fork GitHub API error ({method} {path}): HTTP {exc.code}: {exc.read(500).decode('utf-8', 'replace')}") from exc
        except (URLError, TimeoutError) as exc:
            raise MonitorError(f"Fork GitHub API unavailable for {method} {path}: {exc}") from exc


def manifest_verified(folder: Path) -> dict[str, bytes]:
    if folder.is_symlink() or not folder.is_dir():
        raise MonitorError("Evidence directory must be a real directory")
    manifest = folder / "MANIFEST.sha256"
    if not manifest.is_file() or manifest.is_symlink():
        raise MonitorError("Missing sealed evidence manifest")
    expected = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        m = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        if not m or m.group(2) == "MANIFEST.sha256":
            raise MonitorError("Invalid evidence manifest entry")
        if m.group(2) in expected:
            raise MonitorError("Duplicate manifest filename")
        expected[m.group(2)] = m.group(1)
    seen = {x.name for x in folder.iterdir() if x.is_file() and not x.is_symlink()} - {"MANIFEST.sha256"}
    if seen != set(expected):
        raise MonitorError("Unsealed or missing evidence files")
    files: dict[str, bytes] = {}
    total = 0
    for name, digest in expected.items():
        file = folder / name
        if not file.is_file() or file.is_symlink():
            raise MonitorError("Unsafe evidence member")
        if file.stat().st_size > 30 * 1024 * 1024:
            raise MonitorError(f"Evidence member too large: {name}")
        contents = file.read_bytes()
        if hashlib.sha256(contents).hexdigest() != digest:
            raise MonitorError(f"Evidence checksum mismatch: {name}")
        total += len(contents)
        if total > 45 * 1024 * 1024:
            raise MonitorError("Evidence package exceeds safe publication budget")
        files[name] = contents
    files["MANIFEST.sha256"] = manifest.read_bytes()
    return files


def git_blob_sha(content: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()


def get_branch(api: ForkAPI) -> tuple[str | None, str | None]:
    ref = api.call("GET", f"/repos/{FORK}/git/ref/heads/{EVIDENCE_BRANCH}", missing_ok=True)
    if not ref:
        return None, None
    head = ref["object"]["sha"]
    commit = api.call("GET", f"/repos/{FORK}/git/commits/{head}")
    return head, commit["tree"]["sha"]


def existing_content_sha(api: ForkAPI, path: str) -> str | None:
    # Looking up a content SHA by evidence branch is optional; if absent, write the blob.
    # GitHub contents API uses a URL-encoded ref query, disallowed for write paths
    # but this is an independently guarded GET request.
    from urllib.parse import quote
    url_path = f"/repos/{FORK}/contents/{path}?ref={quote(EVIDENCE_BRANCH, safe='')}"
    req = Request("https://api.github.com" + url_path, method="GET",
                  headers={"Authorization": f"Bearer {api.token}", "Accept": "application/vnd.github+json",
                           "User-Agent": "InCar-Upstream-Fork-Only-Publisher"})
    try:
        with urlopen(req, timeout=35) as resp:
            obj = json.load(resp)
        return obj.get("sha")
    except HTTPError as exc:
        if exc.code == 404:
            return None
        raise MonitorError(f"Cannot read evidence branch content SHA: HTTP {exc.code}") from exc


def publish_evidence(api: ForkAPI, evidence_dir: Path, release: dict, fork_sha: str) -> str:
    files = manifest_verified(evidence_dir)
    prefix = f"releases/{slug(release['tag'], int(release['id']))}/{fork_sha[:12]}"
    head, base_tree = get_branch(api)
    entries = []
    for name, contents in sorted(files.items()):
        path = f"{prefix}/{name}"
        blob_sha = git_blob_sha(contents)
        if head and existing_content_sha(api, path) == blob_sha:
            continue
        response = api.call("POST", f"/repos/{FORK}/git/blobs", {"content": base64.b64encode(contents).decode(), "encoding": "base64"})
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": response["sha"]})
    if entries:
        tree_req: dict[str, Any] = {"tree": entries}
        if base_tree:
            tree_req["base_tree"] = base_tree
        tree_sha = api.call("POST", f"/repos/{FORK}/git/trees", tree_req)["sha"]
        commit_sha = api.call("POST", f"/repos/{FORK}/git/commits", {
            "message": f"evidence: assess {release['tag']} against fork {fork_sha[:12]}",
            "tree": tree_sha, "parents": [head] if head else []})["sha"]
        if head:
            api.call("PATCH", f"/repos/{FORK}/git/refs/heads/{EVIDENCE_BRANCH}", {"sha": commit_sha, "force": False})
        else:
            api.call("POST", f"/repos/{FORK}/git/refs", {"ref": f"refs/heads/{EVIDENCE_BRANCH}", "sha": commit_sha})
    return f"https://github.com/{FORK}/tree/{EVIDENCE_BRANCH}/{prefix}"


def locate_issue(api: ForkAPI, release_id: int) -> dict | None:
    matched = []
    for page in range(1, 101):
        issues = read_issue_page(api, page)
        if not isinstance(issues, list):
            raise MonitorError("Issue API did not return a list")
        for item in issues:
            if "pull_request" in item:
                continue
            if f"<!-- inc-ar-upstream-release-id:{release_id} -->" in (item.get("body") or ""):
                matched.append(item)
        if len(issues) < 100:
            if len(matched) > 1:
                raise MonitorError("Multiple release Issues matched; do not mutate ambiguous target")
            return matched[0] if matched else None
    raise MonitorError("Issue pagination incomplete; refusing duplicate publication")


def read_issue_page(api: ForkAPI, page: int) -> list:
    # Allow only exact read-only, fixed-query target; do not generalise URL override.
    req = Request(f"https://api.github.com/repos/{FORK}/issues?state=all&per_page=100&page={page}", method="GET",
                  headers={"Authorization": f"Bearer {api.token}", "Accept": "application/vnd.github+json",
                           "User-Agent": "InCar-Upstream-Fork-Only-Publisher"})
    try:
        with urlopen(req, timeout=35) as resp:
            return json.load(resp)
    except (URLError, TimeoutError, ValueError) as exc:
        raise MonitorError(f"Cannot enumerate existing Issues: {exc}") from exc


def managed_issue_text(metadata: dict, evidence_url: str) -> str:
    rel = metadata["release"]
    files = metadata["file_count"]
    commits = metadata["commit_count"]
    cats = metadata["categories"]
    source_url = f"https://github.com/organicmaps/organicmaps/compare/{metadata['previous_commit_sha']}...{metadata['upstream_commit_sha']}"
    ranked = sorted(cats.items(), key=lambda kv: -kv[1])
    formatted = "\n".join(f"| {k} | {v} |" for k, v in ranked)
    return f"""{MANAGED_START}
<!-- inc-ar-upstream-release-id:{rel['id']} -->
## Source and comparison identity

- **Release:** [{rel['tag']}]({rel['url']}) (published {rel['published_at']})
- **Upstream source SHA:** `{metadata['upstream_commit_sha']}`
- **Previous release baseline:** `{metadata['previous_commit_sha']}`
- **Fork master SHA assessed:** `{metadata['fork_commit_sha']}`
- **Historical curated checkpoint:** `{metadata['reviewed_checkpoint']}` (ancestor: {metadata['checkpoint_is_ancestor']})
- **Complete source compare:** {source_url}
- **Durable fork-only evidence:** {evidence_url}

## Assessment coverage

The monitor indexed **{commits} commits** and **{files} changed paths**, including removed-platform and shared-native changes. The detailed source and fork inventories are preserved in the evidence branch.

| Scope | Files |
| --- | ---: |
{formatted}

**Read all evidence files, not only this summary:** `metadata.json`, `commits.json`, `files.json`, `report.md`, `selected-source-diffs.patch`, `patch-coverage.json` and `MANIFEST.sha256`.

Bundled patch coverage: **{metadata['patches']['included']} files**; **{metadata['patches']['omitted']} files** not bundled (documented retrieval instructions and immutable SHAs remain available). This is exhaustive commit/file inventory, **not a claim of complete patch bundling or semantic equivalence**.

## Agent investigation / implementation instructions

- [ ] Read `AGENTS.md`, `docs/UPSTREAM.md`, the existing October decision ledger, and **all linked evidence**.
- [ ] Re-check latest fork `master`, open PRs, code and tests; assessed fork SHA may now be historical.
- [ ] Group related commits into features; inspect JNI/C++/Android/data dependencies and current fork implementations.
- [ ] Identify proven matches, already adapted paths, intentionally rejected work and genuinely missing functionality.
- [ ] Recommend `ADOPT`, `ADAPT`, `ALREADY_PRESENT`, `SUPERSEDED`, `DEFER`, `EXCLUDE` or `BLOCKED` with evidence, test plan and priorities.
- [ ] If authorised separately, implement the smallest coherent changes only in `cbkii/organicmaps` branches and PRs. Respect Android 10/API29, curated Android-only tree policy and vehicle UI/lifecycle contracts.
- [ ] Run relevant Android/native/tests and report CI versus device installation/physical TS18 qualification separately.

**UPSTREAM WRITE BAN:** `organicmaps/organicmaps` is strictly read-only. Never push, create PRs or Issues, edit comments or labels, create releases, or mutate anything there. Do not merge upstream's full tree into this fork.

**Human controls:** This Issue is an assessment and task brief, not an automatic implementation request. No coding agent is dispatched. Human comments and decisions outside this managed section are preserved on reanalysis.
{MANAGED_END}"""


def upsert_issue(api: ForkAPI, metadata: dict, evidence_url: str) -> int:
    rel = metadata["release"]
    title = f"[Upstream Review] Organic Maps {rel['tag']} — In-Car assessment"
    new_managed = managed_issue_text(metadata, evidence_url)
    if len(new_managed) > MAX_BODY:
        raise MonitorError("Generated Issue body exceeds safe size limit")
    old = locate_issue(api, int(rel["id"]))
    if old is None:
        body = new_managed + "\n\n## Human review notes\n\n(Record decisions and agent handoff here; preserved on reruns.)\n"
        created = api.call("POST", f"/repos/{FORK}/issues", {"title": title, "body": body})
        return int(created["number"])
    original = old.get("body") or ""
    if original.count(MANAGED_START) != 1 or original.count(MANAGED_END) != 1:
        raise MonitorError("Existing Issue has ambiguous/missing managed section; refusing to overwrite human text")
    before, after_start = original.split(MANAGED_START, 1)
    _managed_old, after = after_start.split(MANAGED_END, 1)
    new_body = before + new_managed + after
    if len(new_body) > 65_000:
        raise MonitorError("Issue body too large after preserving human notes")
    if new_body != original:
        api.call("PATCH", f"/repos/{FORK}/issues/{old['number']}", {"body": new_body})
    return int(old["number"])


def publish_bundle(api: ForkAPI, root: Path) -> list[dict]:
    index_path = root / "index.json"
    if not index_path.is_file():
        raise MonitorError("Missing analysis index")
    index = json.loads(index_path.read_text(encoding="utf-8"))
    if index.get("schema_version") != 1:
        raise MonitorError("Unsupported analysis index")
    results = []
    for entry in index.get("items", []):
        folder = root / entry["folder"]
        if folder.resolve().parent != root.resolve() or folder.is_symlink():
            raise MonitorError("Evidence folder escapes analysis root")
        files = manifest_verified(folder)
        obj = json.loads(files["metadata.json"])
        if obj["fork_repo"] != FORK or obj["fork_commit_sha"] != entry["fork_commit_sha"] or obj["release"]["id"] != entry["release_id"]:
            raise MonitorError("Analysis index/manifest identity mismatch")
        evidence_url = publish_evidence(api, folder, obj["release"], obj["fork_commit_sha"])
        issue_number = upsert_issue(api, obj, evidence_url)
        results.append({"release": obj["release"]["tag"], "issue": issue_number, "evidence": evidence_url})
    return results
