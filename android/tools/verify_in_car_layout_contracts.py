#!/usr/bin/env python3
"""Verify critical InCar Android layout contracts across resource qualifiers."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"

# Each entry models views that current routing code treats as structurally
# mandatory. Android may independently select any matching layout qualifier for
# an included layout, so validate every variant of each owning resource rather
# than comparing only the top-level routing_bottom_sheet files.
LAYOUT_CONTRACTS: tuple[tuple[str, tuple[str, ...]], ...] = (
    (
        "routing_bottom_sheet.xml",
        (
            "routing_root",
            "routing_bottom_container",
            "routing_sheet_frame",
            "routing_bottom_buttons",
            "routing_btn_search",
            "routing_btn_bookmarks",
            "btn__save",
            "routing_btn_more",
        ),
    ),
    (
        "route_plan_line.xml",
        (
            "routing_types_frame",
            "manage_route_panel",
            "error",
        ),
    ),
    (
        "routing_plan_header.xml",
        (
            "route_type",
            "back",
        ),
    ),
    (
        "routing_start_button.xml",
        (
            "start",
        ),
    ),
    (
        "altitude_chart_panel.xml",
        (
            "time_vehicle",
            "time_elevation_line",
            "time",
            "altitude_difference",
            "transit_time",
            "time_ruler",
            "driving_options_btn_img",
            "driving_options_badge",
            "altitude_chart",
            "transit_recycler_view",
        ),
    ),
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


def verify_layout_contract(repo_root: Path, filename: str, required_ids: tuple[str, ...]) -> list[str]:
    res_root = repo_root / "android/app/src/main/res"
    variants = sorted(res_root.glob(f"layout*/{filename}"))
    if not variants:
        raise LayoutContractError(f"no {filename} layouts found under {res_root}")

    failures: list[str] = []
    print(f"[{filename} contract]")
    for path in variants:
        ids = read_layout_ids(path)
        missing = [view_id for view_id in required_ids if view_id not in ids]
        relative = path.relative_to(repo_root)
        if missing:
            print(f"FAIL {relative}: missing {', '.join(missing)}")
            failures.append(f"{relative}: missing required IDs: {', '.join(missing)}")
        else:
            print(f"PASS {relative}")
    print()
    return failures


def verify_layout_contracts(repo_root: Path) -> list[str]:
    failures: list[str] = []
    for filename, required_ids in LAYOUT_CONTRACTS:
        failures.extend(verify_layout_contract(repo_root, filename, required_ids))
    return failures


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
