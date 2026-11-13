"""CLI: python -m tools.upstream_monitor analyse|publish."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

from .core import (FORK, REVIEWED_SHA, MonitorError, analyse_one, fetch_upstream,
                   git, issue_index_public, ledger_decisions, release_catalog,
                   select_releases, slug)
from .publish import ForkAPI, publish_bundle

BOOTSTRAP_DATE = "2026-10-19T00:00:00Z"


def analyse(args: argparse.Namespace) -> None:
    repo = Path(args.repo).resolve()
    out = Path(args.out).resolve()
    if not (repo / ".git").exists() and not (repo / ".git").is_file():
        raise MonitorError("Analysis must run from the fork's Git checkout")
    if os.environ.get("GITHUB_ACTIONS") == "true" and os.getenv("GITHUB_REPOSITORY") != FORK:
        raise MonitorError("Analysis only runs in cbkii/organicmaps")
    if args.mode == "reanalyse" and not args.release_tag:
        raise MonitorError("Reanalysis requires a specific release tag")
    catalog = release_catalog()
    existing = issue_index_public() if args.mode == "analyse" else set()
    chosen = select_releases(catalog, existing, args.mode, args.release_tag)
    if not args.release_tag:
        chosen = [(r, prev) for r, prev in chosen if r["published_at"] >= BOOTSTRAP_DATE]
    # Work per run is bounded; the oldest releases are evaluated first, and newer
    # ones remain pending for the next scheduled/manual run. Never silently omit.
    total_pending = len(chosen)
    from .core import MAX_RELEASES_PER_RUN
    chosen = chosen[:MAX_RELEASES_PER_RUN]
    if not chosen:
        out.mkdir(parents=True, exist_ok=True)
        (out / "index.json").write_text(json.dumps({"schema_version": 1, "items": [], "pending": 0}, indent=2) + "\n")
        print("No unreviewed published upstream Android releases", flush=True)
        return
    ledger = ledger_decisions(repo / "docs" / "UPSTREAM_SYNC_2026_10_19.md")
    fork_sha = git(repo, "rev-parse", "HEAD").decode().strip()
    if len(fork_sha) != 40:
        raise MonitorError("Unexpected fork HEAD identity")
    required_tags = [r["tag"] for r, prev in chosen] + [prev["tag"] for r, prev in chosen if prev]
    fetch_upstream(repo, required_tags)
    # If checkpoint is not in the tagged release's history, do not pretend it was reviewed.
    items = []
    for release, prev in chosen:
        folder_name = slug(release["tag"], release["id"])
        folder = out / folder_name
        print(f"Analysing {release['tag']} against {fork_sha[:12]}", flush=True)
        result = analyse_one(repo, release, prev, fork_sha, folder, ledger)
        items.append({"folder": folder_name, "release_id": release["id"], "tag": release["tag"],
                      "fork_commit_sha": fork_sha, "commit_count": result["commit_count"],
                      "file_count": result["file_count"]})
    out.mkdir(parents=True, exist_ok=True)
    (out / "index.json").write_text(json.dumps({"schema_version": 1, "items": items,
                        "pending": total_pending - len(chosen), "mode": args.mode,
                        "fork_commit_sha": fork_sha}, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"analysed": len(items), "remaining": total_pending - len(items), "items": items}, indent=2), flush=True)


def publish(args: argparse.Namespace) -> None:
    if os.getenv("GITHUB_ACTIONS") != "true":
        raise MonitorError("Publication is restricted to fork GitHub Actions environment")
    api = ForkAPI(os.getenv("GITHUB_TOKEN", ""))
    if os.getenv("GITHUB_EVENT_NAME") not in ("schedule", "workflow_dispatch"):
        raise MonitorError("Publication is never permitted on a PR or push trigger")
    results = publish_bundle(api, Path(args.input).resolve())
    print(json.dumps({"published": results}, indent=2), flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="InCar fork-only upstream release intelligence")
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("analyse", help="Read-only release and source assessment")
    run.add_argument("--repo", required=True)
    run.add_argument("--out", required=True)
    run.add_argument("--mode", choices=("analyse", "reanalyse", "dry-run"), default="analyse")
    run.add_argument("--release-tag", default="")
    pub = sub.add_parser("publish", help="Fork-only evidence and Issue publication")
    pub.add_argument("--input", required=True)
    args = parser.parse_args()
    try:
        if args.command == "analyse":
            analyse(args)
        else:
            publish(args)
        return 0
    except (MonitorError, ValueError, OSError, subprocess.SubprocessError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
