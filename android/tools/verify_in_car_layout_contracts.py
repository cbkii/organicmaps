#!/usr/bin/env python3
"""Verify critical InCar Android layout contracts across resource qualifiers."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"

# These IDs are dereferenced as required views by RoutingPlanFragment before any
# optional InCar presentation logic can safely continue. Every routing bottom-sheet
# qualifier selected by Android must therefore provide the same structural contract.
ROUTING_BOTTOM_SHEET_REQUIRED_IDS = (
    "routing_root",
    "routing_bottom_container",
    "routing_sheet_frame",
    "routing_bottom_buttons",
    "routing_btn_search",
    "routing_btn_bookmarks",
    "btn__save",
    "routing_btn_more",
)


class LayoutContractError(RuntimeError):
    """Raised when a required Android layout contract cannot be inspected safely."""


def parse_args(argv: list[str]) -> argparse.Namespace:
    default_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=default_root,
        help="Organic Maps repository root (defaults to the script-relative root)",
    )
    return parser.parse_args(argv)


def resource_id_name(value: str | None) -> str | None:
    if value is None:
        return None
    for prefix in ("@+id/", "@id/"):
        if value.startswith(prefix):
            return value.removeprefix(prefix)
    return None


def read_layout_ids(path: Path) -> set[str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise LayoutContractError(f"unable to parse {path}: {exc}") from exc

    ids: set[str] = set()
    for element in root.iter():
        name = resource_id_name(element.get(ANDROID_ID))
        if name:
            ids.add(name)
    return ids


def verify_routing_bottom_sheet_contract(repo_root: Path) -> list[str]:
    res_root = repo_root / "android/app/src/main/res"
    variants = sorted(res_root.glob("layout*/routing_bottom_sheet.xml"))
    if not variants:
        raise LayoutContractError(f"no routing_bottom_sheet.xml layouts found under {res_root}")

    failures: list[str] = []
    print("[routing-layout-contract]")
    for path in variants:
        ids = read_layout_ids(path)
        missing = [view_id for view_id in ROUTING_BOTTOM_SHEET_REQUIRED_IDS if view_id not in ids]
        relative = path.relative_to(repo_root)
        if missing:
            print(f"FAIL {relative}: missing {', '.join(missing)}")
            failures.append(f"{relative}: missing required IDs: {', '.join(missing)}")
        else:
            print(f"PASS {relative}")
    print()
    return failures


def verify_layout_contracts(repo_root: Path) -> list[str]:
    return verify_routing_bottom_sheet_contract(repo_root)


def main(argv: list[str]) -> int:
    if sys.version_info < (3, 10):
        print("FAILED: Python 3.10 or newer is required", file=sys.stderr)
        return 2

    args = parse_args(argv)
    try:
        failures = verify_layout_contracts(args.repo_root.resolve())
    except LayoutContractError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 2

    if failures:
        for failure in failures:
            print(f"FAILED: {failure}", file=sys.stderr)
        print("FAILED", file=sys.stderr)
        return 1

    print("SUCCESS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
