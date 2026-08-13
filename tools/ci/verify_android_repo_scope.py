#!/usr/bin/env python3
"""Fail CI when the Android-only repository scope regresses."""

from __future__ import annotations

import configparser
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
MAX_TRACKED_BYTES = 220 * 1024 * 1024
LARGE_BLOB_BYTES = 10 * 1024 * 1024
FORBIDDEN_PREFIXES = (
    "iphone/",
    "xcode/",
    "qt/",
    "dev_sandbox/",
    "packaging/",
    "data/borders/",
    "data/test_data/",
    "tools/shaders_compiler/",
    "3party/glfw/",
    "3party/googletest/",
    "3party/CMake-MetalShaderSupport/",
    "3party/imgui/",
)
ALLOWED_LARGE_BLOBS = {
    "data/World.mwm",
    "data/packed_polygons.bin",
}


def git(*args: str, input_text: str | None = None) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode != 0:
        print(f"ERROR: git {' '.join(args)} failed ({proc.returncode})", file=sys.stderr)
        if proc.stderr:
            print(proc.stderr.rstrip(), file=sys.stderr)
        raise SystemExit(2)
    return proc.stdout


def tracked_entries() -> list[tuple[str, str, str]]:
    raw = git("ls-files", "-s", "-z")
    entries: list[tuple[str, str, str]] = []
    for record in raw.split("\0"):
        if not record:
            continue
        meta, path = record.split("\t", 1)
        mode, sha, _stage = meta.split(" ", 2)
        entries.append((mode, sha, path))
    return entries


def parse_submodule_paths() -> set[str]:
    path = ROOT / ".gitmodules"
    if not path.exists():
        return set()
    parser = configparser.ConfigParser()
    parser.read(path, encoding="utf-8")
    result: set[str] = set()
    for section in parser.sections():
        if section.startswith("submodule ") and parser.has_option(section, "path"):
            result.add(parser.get(section, "path").strip().rstrip("/"))
    return result


def blob_sizes(entries: list[tuple[str, str, str]]) -> dict[str, int]:
    blob_shas = sorted({sha for mode, sha, _path in entries if mode != "160000"})
    if not blob_shas:
        return {}
    output = git(
        "cat-file",
        "--batch-check=%(objectname) %(objecttype) %(objectsize)",
        input_text="".join(f"{sha}\n" for sha in blob_shas),
    )
    sizes: dict[str, int] = {}
    for line in output.splitlines():
        sha, obj_type, size_text = line.split(" ", 2)
        if obj_type != "blob":
            print(f"ERROR: expected blob {sha}, got {obj_type}", file=sys.stderr)
            raise SystemExit(2)
        sizes[sha] = int(size_text)
    return sizes


def main() -> int:
    errors: list[str] = []
    entries = tracked_entries()
    tracked = {path for _mode, _sha, path in entries}

    for prefix in FORBIDDEN_PREFIXES:
        bare = prefix.rstrip("/")
        if bare in tracked or any(path.startswith(prefix) for path in tracked):
            errors.append(f"forbidden repository path returned: {bare}")

    gitlinks = {path for mode, _sha, path in entries if mode == "160000"}
    declared = parse_submodule_paths()
    if gitlinks != declared:
        missing_declarations = sorted(gitlinks - declared)
        missing_gitlinks = sorted(declared - gitlinks)
        if missing_declarations:
            errors.append("gitlinks missing from .gitmodules: " + ", ".join(missing_declarations))
        if missing_gitlinks:
            errors.append(".gitmodules paths without tracked gitlinks: " + ", ".join(missing_gitlinks))

    for mode, _sha, path in entries:
        if mode != "120000":
            continue
        link = ROOT / path
        if not link.is_symlink():
            errors.append(f"tracked symlink is not materialised as a symlink: {path}")
            continue
        target_text = os.readlink(link)
        target = (link.parent / target_text).resolve(strict=False)
        try:
            relative = target.relative_to(ROOT.resolve()).as_posix()
        except ValueError:
            errors.append(f"symlink escapes repository: {path} -> {target_text}")
            continue
        if not target.exists():
            errors.append(f"broken symlink: {path} -> {target_text}")
            continue
        if relative not in tracked and not any(p.startswith(relative.rstrip('/') + '/') for p in tracked):
            errors.append(f"symlink target is not tracked: {path} -> {relative}")

    sizes = blob_sizes(entries)
    total = sum(sizes[sha] for mode, sha, _path in entries if mode != "160000")
    print(f"Tracked blob bytes: {total} ({total / 1024 / 1024:.2f} MiB)")
    print(f"Tracked paths: {len(entries)}")
    print(f"Submodules: {len(gitlinks)}")
    if total > MAX_TRACKED_BYTES:
        errors.append(
            f"tracked tree exceeds {MAX_TRACKED_BYTES / 1024 / 1024:.0f} MiB budget: "
            f"{total / 1024 / 1024:.2f} MiB"
        )

    large: list[tuple[int, str]] = []
    for mode, sha, path in entries:
        if mode == "160000":
            continue
        size = sizes[sha]
        if size >= LARGE_BLOB_BYTES:
            large.append((size, path))
            if path not in ALLOWED_LARGE_BLOBS:
                errors.append(
                    f"unexplained large blob ({size / 1024 / 1024:.2f} MiB): {path}; "
                    "add only if it is a required Android runtime asset"
                )

    if large:
        print("Large tracked blobs:")
        for size, path in sorted(large, reverse=True):
            print(f"  {size / 1024 / 1024:8.2f} MiB  {path}")

    if errors:
        print("\nRepository scope verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Repository scope verification PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
