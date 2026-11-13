"""Discover and analyse upstream releases without any remote write capability.

No third-party libraries are required. Git access is deliberately restricted to fetch,
log, diff, ls-tree and object inspection; publication lives in another process/job.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from typing import Any
import urllib.error
import urllib.request

FORK = "cbkii/organicmaps"
UPSTREAM = "organicmaps/organicmaps"
REVIEWED_SHA = "e78a075b7ae77bb31510918a26545d77ea4ae690"
MAX_RELEASE_PAGES = 30
MAX_RELEASES_PER_RUN = 12
MAX_COMMITS = 10000
MAX_FILES = 200000
MAX_PATCH_FILES = 100
MAX_PATCH_PER_FILE = 320_000
MAX_PATCH_TOTAL = 8_000_000
PATCH_PATH_SUFFIXES = (".java", ".kt", ".cpp", ".cc", ".c", ".h", ".hpp", ".xml", ".gradle", ".kts", ".py", ".json", ".cmake", ".txt")
SOURCE_RELEVANCE = ("android/", "libs/", "3party/", "data/", "cmake/", "drape/", "routing/", "search/", "indexer/", "storage/", "map/", "platform/", "geometry/", "coding/", "base/", "track_analyzing/", "generator/")
EXCLUDED_PLATFORM = ("iphone/", "xcode/", "qt/", "packaging/")
MAX_BODY = 54_000


class MonitorError(Exception):
    """One required prerequisite failed; never publish an incomplete assessment."""


def safe_tag(tag: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+/-]{0,180}", tag) or ".." in tag or "@{" in tag or tag.endswith("/"):
        raise MonitorError(f"Unusable upstream tag: {tag!r}")
    return tag


def slug(tag: str, release_id: int) -> str:
    return f"{release_id}-{re.sub(r'[^A-Za-z0-9._-]+', '-', tag)[:110]}"


def api_get_public(path: str, token: str = "") -> Any:
    if not path.startswith("/repos/organicmaps/organicmaps/"):
        raise MonitorError("Public acquisition must target upstream read-only API paths")
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "InCar-Upstream-ReadOnly-Monitor", "X-GitHub-Api-Version": "2022-11-28"}
    # Deliberately no upstream write credentials, even if the caller has a token.
    req = urllib.request.Request("https://api.github.com" + path, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=35) as resp:
            return json.load(resp)
    except (urllib.error.URLError, ValueError, TimeoutError) as exc:
        raise MonitorError(f"Release discovery failed for {path}: {exc}") from exc


def release_catalog() -> list[dict[str, Any]]:
    published: list[dict[str, Any]] = []
    for page in range(1, MAX_RELEASE_PAGES + 1):
        results = api_get_public(f"/repos/{UPSTREAM}/releases?per_page=100&page={page}")
        if not isinstance(results, list):
            raise MonitorError("Release API returned unexpected data")
        for item in results:
            if not item.get("draft") and not item.get("prerelease") and item.get("published_at") and item.get("tag_name", "").endswith("-android"):
                safe_tag(item["tag_name"])
                published.append({"id": int(item["id"]), "tag": item["tag_name"], "published_at": item["published_at"],
                                  "url": item["html_url"], "name": item.get("name", ""), "body": item.get("body") or ""})
        if len(results) < 100:
            break
    else:
        raise MonitorError("Release catalog exceeds page safety bound; cannot assert complete discovery")
    published.sort(key=lambda r: (r["published_at"], r["id"]))
    return published


def issue_index_public() -> set[int]:
    """Read-only issue discovery. Fail closed if pagination does not complete."""
    existing: set[int] = set()
    for page in range(1, 101):
        url = f"https://api.github.com/repos/{FORK}/issues?state=all&per_page=100&page={page}"
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "InCar-Upstream-ReadOnly-Monitor"}
        if os.getenv("GITHUB_TOKEN") and os.getenv("GITHUB_REPOSITORY") == FORK:
            headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
        req = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=35) as resp:
                issues = json.load(resp)
        except (urllib.error.URLError, ValueError, TimeoutError) as exc:
            raise MonitorError(f"Cannot completely inspect existing fork issues: {exc}") from exc
        if not isinstance(issues, list):
            raise MonitorError("Issue API returned unexpected data")
        for issue in issues:
            if "pull_request" in issue:
                continue
            match = re.search(r"<!-- inc-ar-upstream-release-id:(\d+) -->", issue.get("body") or "")
            if match:
                existing.add(int(match.group(1)))
        if len(issues) < 100:
            return existing
    raise MonitorError("Too many issues to deduplicate safely")


def select_releases(catalog: list[dict], existing: set[int], mode: str, requested: str) -> list[tuple[dict, dict | None]]:
    chosen = []
    if requested:
        safe_tag(requested)
        if requested not in {r["tag"] for r in catalog}:
            raise MonitorError(f"Tag {requested} is not a published official Android release")
    for i, release in enumerate(catalog):
        if requested and release["tag"] != requested:
            continue
        if not requested and mode == "analyse" and release["id"] in existing:
            continue
        if not requested and mode == "reanalyse":
            raise MonitorError("Bulk reanalysis is disallowed; supply an exact release tag")
        if requested and mode == "analyse" and release["id"] in existing:
            continue
        previous = catalog[i - 1] if i else None
        chosen.append((release, previous))
    return chosen


def git(repo: Path, *args: str, timeout: int = 180, max_bytes: int = 48_000_000) -> bytes:
    cmd = ["git", "-C", str(repo), *args]
    # Git's input is fully controlled; do not invoke a shell or Git hooks.
    with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
        try:
            proc = subprocess.run(cmd, stdout=out, stderr=err, timeout=timeout, check=False,
                                  env={**os.environ, "GIT_TERMINAL_PROMPT": "0", "GIT_OPTIONAL_LOCKS": "0"})
        except subprocess.TimeoutExpired as exc:
            raise MonitorError(f"Git operation timed out: {args[0]}") from exc
        out.seek(0, os.SEEK_END)
        length = out.tell()
        err.seek(0)
        msg = err.read(3000).decode("utf-8", "replace")
        if proc.returncode:
            raise MonitorError(f"Git operation failed [{args[0]}] rc={proc.returncode}: {msg}")
        if length > max_bytes:
            raise MonitorError(f"Git output too large for {args[0]}: {length} bytes (bound={max_bytes})")
        out.seek(0)
        return out.read()


def fetch_upstream(repo: Path, tags: list[str]) -> None:
    # Existing checkout is the fork, using no persisted credential. Never push, ever.
    remotes = git(repo, "remote", "-v").decode()
    if "upstream" not in {line.split()[0] for line in remotes.splitlines() if line.strip()}:
        git(repo, "remote", "add", "upstream", f"https://github.com/{UPSTREAM}.git")
    remote_url = git(repo, "remote", "get-url", "upstream").decode().strip()
    if remote_url != f"https://github.com/{UPSTREAM}.git":
        raise MonitorError("Unexpected upstream remote; refusing to proceed")
    git(repo, "remote", "set-url", "--push", "upstream", "disabled://write-prohibited")
    for tag in sorted(set(tags)):
        safe_tag(tag)
        # Obtain complete commit/tree ancestry while lazily fetching blobs only as needed.
        git(repo, "-c", "protocol.version=2", "fetch", "--filter=blob:none", "--no-tags", "upstream",
            f"refs/tags/{tag}:refs/monitor-upstream/tags/{tag}", timeout=2100)


def rev(repo: Path, tag: str) -> str:
    return git(repo, "rev-parse", "--verify", f"refs/monitor-upstream/tags/{safe_tag(tag)}^{{commit}}").decode().strip()


def is_ancestor(repo: Path, ancestor: str, target: str) -> bool:
    p = subprocess.run(["git", "-C", str(repo), "merge-base", "--is-ancestor", ancestor, target],
                       capture_output=True, timeout=45, check=False)
    if p.returncode not in (0, 1):
        raise MonitorError("Could not verify upstream ancestry: " + p.stderr.decode("utf-8", "replace")[:250])
    return p.returncode == 0


def tree_index(repo: Path, commit: str) -> dict[str, str]:
    raw = git(repo, "ls-tree", "-r", "-z", commit, max_bytes=90_000_000)
    files = {}
    for entry in raw.split(b"\x00"):
        if not entry:
            continue
        meta, path = entry.split(b"\t", 1)
        mode, kind, sha = meta.split(b" ", 2)
        if kind == b"blob":
            files[path.decode("utf-8", "surrogateescape")] = sha.decode()
    return files




def parse_name_status(raw: bytes) -> list[dict[str, str]]:
    chunks = raw.split(b"\x00")
    if chunks and chunks[-1] == b"":
        chunks.pop()
    if len(chunks) % 2:
        raise MonitorError("Malformed Git name-status -z result")
    return [{"status": chunks[i].decode("ascii"),
             "path": chunks[i + 1].decode("utf-8", "surrogateescape")}
            for i in range(0, len(chunks), 2)]

def diff_index(repo: Path, base: str, target: str) -> list[dict[str, str]]:
    raw = git(repo, "diff", "--no-ext-diff", "--no-renames", "--name-status", "-z", base, target, max_bytes=50_000_000)
    items = parse_name_status(raw)
    if len(items) > MAX_FILES:
        raise MonitorError("Release file inventory exceeds safety bound")
    return items


def relevance(path: str) -> tuple[str, int, str]:
    lower = path.lower()
    if path.startswith(EXCLUDED_PLATFORM):
        return "excluded-platform", 0, "Removed/non-release platform; inspect cross-platform dependencies before adoption"
    if path.startswith((".github/", "docs/", "tools/", "scripts/")):
        return "tooling-or-docs", 1, "Review only when needed by retained Android dependencies"
    if path.startswith("android/"):
        return "android", 4, "Retained Android implementation: investigate exact fork contract"
    if path.startswith(SOURCE_RELEVANCE):
        return "shared-runtime-or-data", 3, "Potential Android JNI/native/runtime/data dependency"
    return "other-or-unknown", 2, "Review source/dependency reachability; classification uncertain"


def git_commits(repo: Path, base: str, target: str, review_ledger: dict[str, str]) -> list[dict[str, Any]]:
    raw = git(repo, "log", "--reverse", "--format=%H%x1f%P%x1f%s%x1f%aI%x1e", f"{base}..{target}", max_bytes=20_000_000)
    commits = []
    for record in raw.decode("utf-8", "replace").strip().split("\x1e"):
        record = record.strip()
        if not record:
            continue
        values = record.split("\x1f")
        if len(values) != 4:
            raise MonitorError("Malformed upstream commit metadata")
        sha, parents, subject, authored = values
        # Do not hide prior human dispositions just because ancestry was acknowledged.
        commits.append({"sha": sha, "parents": parents.split(), "subject": subject, "authored": authored,
                        "reviewed_decision": review_ledger.get(sha),
                        "url": f"https://github.com/{UPSTREAM}/commit/{sha}"})
    if len(commits) > MAX_COMMITS:
        raise MonitorError("Commit inventory exceeds bound; requires partitioned review")
    # Per-commit changed-path inventory, including renames as A/D pairs when
    # blob comparisons are too expensive; every entry has an immutable source SHA.
    for commit in commits:
        parents = commit["parents"]
        if parents:
            raw_changes = git(repo, "diff", "--no-ext-diff", "--no-renames", "--name-status", "-z", parents[0], commit["sha"], max_bytes=12_000_000)
        else:
            raw_changes = git(repo, "diff-tree", "--root", "--no-renames", "--no-commit-id", "--name-status", "-r", "-z", commit["sha"], max_bytes=12_000_000)
        commit["changed_paths"] = parse_name_status(raw_changes)
        if len(commit["changed_paths"]) > MAX_FILES:
            raise MonitorError("One commit changed more files than inventory safety bound")
        ranks = [relevance(item["path"])[1] for item in commit["changed_paths"]]
        commit["relevance"] = max(ranks, default=0)
        commit["priority_hint"] = priority_hint(commit["subject"], commit["relevance"])
    return commits


def priority_hint(subject: str, relevance_rank: int) -> str:
    text = subject.lower()
    high = ("crash", "corrupt", "security", "race", "deadlock", "fix", "gps", "route", "position", "memory leak", "hang", "regression", "accuracy")
    if relevance_rank >= 2 and any(term in text for term in high):
        return "HIGH-REVIEW"
    if relevance_rank >= 2:
        return "NORMAL-REVIEW"
    return "LOW-REVIEW"


def ledger_decisions(ledger_path: Path) -> dict[str, str]:
    if not ledger_path.exists():
        raise MonitorError("Required October 2026 curated decision ledger not found")
    text = ledger_path.read_text(encoding="utf-8")
    result = {}
    for row in text.splitlines():
        match = re.search(r"/commit/([0-9a-f]{40})", row)
        if match and "|" in row:
            fields = [x.strip() for x in row.split("|")]
            decision = next((x for x in fields if re.fullmatch("Import|Adapt|Exclude|Defer|Matched|Superseded", x, re.I)), None)
            if decision:
                result[match.group(1)] = decision.upper()
    return result


def file_analysis(items: list[dict], upstream_tree: dict[str, str], fork_tree: dict[str, str]) -> tuple[list[dict], dict]:
    detail = []
    summary: dict[str, int] = {}
    for entry in items:
        path = entry["path"]
        category, rank, note = relevance(path)
        fork_blob = fork_tree.get(path)
        upstream_blob = upstream_tree.get(path)
        exact = bool(fork_blob and upstream_blob and fork_blob == upstream_blob)
        decision = "ALREADY_PRESENT" if exact else "INVESTIGATE" if rank >= 2 else "DEFER" if rank == 1 else "EXCLUDE"
        record = {**entry, "category": category, "rank": rank, "disposition": decision,
                  "assessment": "Exact blob match at current path" if exact else note,
                  "fork_path_present": bool(fork_blob), "exact_blob_match": exact,
                  "upstream_blob": upstream_blob, "fork_blob": fork_blob}
        detail.append(record)
        summary[category] = summary.get(category, 0) + 1
    detail.sort(key=lambda x: (-x["rank"], x["path"]))
    return detail, summary


def capture_patches(repo: Path, base: str, target: str, files: list[dict], destination: Path) -> dict[str, Any]:
    """Produce bounded relevant diffs. Skipped files remain indexed with immutable SHAs."""
    patches = []
    omitted = []
    total = 0
    destination.mkdir(parents=True, exist_ok=True)
    for entry in files:
        path = entry["path"]
        if entry["rank"] < 2 or not path.lower().endswith(PATCH_PATH_SUFFIXES):
            omitted.append({"path": path, "reason": "low-relevance-or-non-source"})
            continue
        if len(patches) >= MAX_PATCH_FILES or total >= MAX_PATCH_TOTAL:
            omitted.append({"path": path, "reason": "bounded-patch-budget; retrieve via immutable source SHAs"})
            continue
        try:
            diff = git(repo, "diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--unified=3", base, target, "--", path,
                       timeout=240, max_bytes=MAX_PATCH_PER_FILE)
        except MonitorError as exc:
            omitted.append({"path": path, "reason": "patch-unavailable-or-too-large", "detail": str(exc)[:150]})
            continue
        if total + len(diff) > MAX_PATCH_TOTAL:
            omitted.append({"path": path, "reason": "bounded-patch-budget"})
            continue
        patches.append(diff)
        total += len(diff)
    (destination / "selected-source-diffs.patch").write_bytes(b"\n".join(patches))
    (destination / "patch-coverage.json").write_text(json.dumps({"included": len(patches), "omitted": omitted,
                                     "included_bytes": total, "all_paths_remain_in_files_json": True}, indent=2), encoding="utf-8")
    return {"included": len(patches), "omitted": len(omitted), "included_bytes": total,
            "full_patch_coverage": len(omitted) == 0}


def analyse_one(repo: Path, release: dict, previous: dict | None, fork_sha: str, folder: Path, ledger: dict[str, str]) -> dict:
    release_sha = rev(repo, release["tag"])
    prev_sha = rev(repo, previous["tag"]) if previous else None
    if prev_sha and not is_ancestor(repo, prev_sha, release_sha):
        raise MonitorError(f"Release tags are not ancestrally ordered: {previous['tag']} -> {release['tag']}")
    if not prev_sha:
        if not is_ancestor(repo, REVIEWED_SHA, release_sha):
            raise MonitorError("First available release has no safe prior tag or reviewed ancestor")
        prev_sha = REVIEWED_SHA
    checkpoint_reachable = is_ancestor(repo, REVIEWED_SHA, release_sha)
    checkpoint_base = REVIEWED_SHA if checkpoint_reachable else None
    release_commits = git_commits(repo, prev_sha, release_sha, ledger)
    if not release_commits and prev_sha != release_sha:
        raise MonitorError("Unexpected empty commit range")
    file_changes = diff_index(repo, prev_sha, release_sha)
    current_fork_tree = tree_index(repo, fork_sha)
    upstream_tree = tree_index(repo, release_sha)
    files, summary = file_analysis(file_changes, upstream_tree, current_fork_tree)
    checkpoint_shas = git(repo, "log", "--format=%H", f"{checkpoint_base}..{release_sha}").decode().splitlines() if checkpoint_base else []
    folder.mkdir(parents=True, exist_ok=True)
    patch_report = capture_patches(repo, prev_sha, release_sha, files, folder)
    obj = {"schema_version": 1, "assessment_status": "COMPLETE_INVENTORY_WITH_BOUNDED_PATCHES",
           "release": release, "upstream_commit_sha": release_sha, "previous_release": previous, "previous_commit_sha": prev_sha,
           "reviewed_checkpoint": REVIEWED_SHA, "checkpoint_is_ancestor": checkpoint_reachable,
           "unreviewed_since_checkpoint": [sha for sha in checkpoint_shas if sha not in ledger],
           "fork_repo": FORK, "fork_commit_sha": fork_sha, "commit_count": len(release_commits),
           "file_count": len(files), "categories": summary, "patches": patch_report,
           "ambiguity": "Path/hash matches are not functional equivalence; all proposed classifications require agent review",
           "fork_tree_policy": "Independent Android-only InCar product; never merge upstream tree automatically"}
    (folder / "metadata.json").write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (folder / "commits.json").write_text(json.dumps(release_commits, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (folder / "files.json").write_text(json.dumps(files, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (folder / "report.md").write_text(render_report(obj, release_commits, files), encoding="utf-8")
    manifest = []
    for path in sorted(folder.iterdir()):
        if path.name == "MANIFEST.sha256":
            continue
        manifest.append(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name)
    (folder / "MANIFEST.sha256").write_text("\n".join(manifest) + "\n", encoding="utf-8")
    return obj


def render_report(info: dict, commits: list[dict], files: list[dict]) -> str:
    rel = info["release"]
    base = info["previous_commit_sha"]
    target = info["upstream_commit_sha"]
    fork_sha = info["fork_commit_sha"]
    lines = [f"# Upstream assessment: {rel['tag']}", "", "## Reproducible source identity", "",
             f"- Upstream release: {rel['url']}", f"- Upstream tag commit: `{target}`",
             f"- Previous release baseline: `{base}`", f"- Fork: `{FORK}` at `{fork_sha}`",
             f"- Earlier curated decision checkpoint: `{REVIEWED_SHA}` (ancestor: {info['checkpoint_is_ancestor']})",
             f"- Release changes: **{len(commits)} commits, {len(files)} paths**",
             f"- Full immutable upstream compare: https://github.com/{UPSTREAM}/compare/{base}...{target}",
             f"- Exact fork commit: https://github.com/{FORK}/tree/{fork_sha}", "",
             "## Scope and certainty", "",
             "All commit/file inventory entries are retained in `commits.json` and `files.json`. ",
             "Classifications are static triage only. A matching Git blob proves identical bytes at that path, **not** the broader feature's runtime behaviour.",
             "Historical upstream acknowledgement merges do not imply code import. Native, Android and runtime-data dependencies must be inspected before changes.", "",
             "## Coverage by area", "",
             "| Area | Changed files |", "| --- | ---: |"]
    for key, value in sorted(info["categories"].items(), key=lambda kv: -kv[1]):
        lines.append(f"| {key} | {value} |")
    high_commits = sorted([x for x in commits if x["priority_hint"] != "LOW-REVIEW"],
                          key=lambda x: (x["priority_hint"] != "HIGH-REVIEW", x["sha"]))
    lines.extend(["", "## Prioritised commit candidates (static triage)", "",
                  "This ranking is a review cue, not proof that a change is valuable or absent from the fork.", "",
                  "| Priority | Upstream commit | File count | Prior decision |", "| --- | --- | ---: | --- |"])
    for change in high_commits[:60]:
        subject = change["subject"].replace("|", "\\|").replace("`", "'")[:120]
        lines.append(f"| {change['priority_hint']} | [{subject}]({change['url']}) | {len(change['changed_paths'])} | {change.get('reviewed_decision') or 'Unreviewed'} |")
    if len(high_commits) > 60:
        lines.append(f"\n{len(high_commits) - 60} additional candidate commits are indexed in `commits.json`.")
    lines.extend(["", "## Prioritised candidate file inventory (preview)", "", "| Status | Path | Static assessment |", "| --- | --- | --- |"])
    for item in files[:80]:
        safe = item["path"].replace("|", "\\|").replace("`", "'")
        lines.append(f"| {item['disposition']} | `{safe}` | {item['assessment']} |")
    if len(files) > 80:
        lines.append(f"\n**{len(files)-80} additional changed paths:** consult `files.json`; never interpret the preview as exhaustive.")
    lines.extend(["", "## Commit decisions and audit trail", "",
                  f"- Previous curated ledger decisions found in release range: {sum(bool(c.get('reviewed_decision')) for c in commits)} of {len(commits)}",
                  f"- Unreviewed commits since the October checkpoint: {len(info['unreviewed_since_checkpoint'])} (where checkpoint is ancestral)",
                  "- Full list with SHAs, parent identities, original messages and direct source links: `commits.json`", "",
                  "## Patch coverage", "", f"- Selected relevant patches: {info['patches']['included']} files ({info['patches']['included_bytes']} bytes)",
                  f"- Excluded / unbundled patch entries: {info['patches']['omitted']} (see `patch-coverage.json`)",
                  "- Use immutable commit/tag SHAs to retrieve every remaining diff; the local bundle is intentionally bounded to prevent large binary or asset exports.", "",
                  "## Agent investigation and implementation contract", "",
                  "1. Re-fetch `cbkii/organicmaps:master`, current open PRs, `AGENTS.md`, `docs/UPSTREAM.md` and existing source/CI before any change.",
                  "2. Read this report AND the full `commits.json`/`files.json`/patch coverage. Resolve feature group dependencies, including native assets and JNI.",
                  "3. For every worthwhile feature, check current actual fork code, earlier imported/adapted decisions and alternative implementations. Distinguish matches from guesses.",
                  "4. Produce separate adopt/adapt/defer/exclude decisions with source SHAs, target source owners, tests, cost and device-compatibility impact.",
                  "5. Implement only in a NEW branch of `cbkii/organicmaps`, via a separately reviewed PR. Preserve Android 10/API29 and the InCar build/signer identity.",
                  "6. Verify Android-only repository scope, relevant Android/native tests and report TS18 installation/physical testing separately. Never claim unrun suites passed.",
                  "7. ABSOLUTE WRITE BAN: never create/update/delete/push/comment/PR/release anything in `organicmaps/organicmaps`. No upstream-directed writes of any kind.", "",
                  "## Evidence integrity", "",
                  "All report files have SHA-256 entries in `MANIFEST.sha256`. The evidence branch stores immutable per-fork-SHA snapshots; GitHub Action logs are not durable evidence.", ""])
    return "\n".join(lines)
