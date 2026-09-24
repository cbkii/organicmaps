#!/usr/bin/env python3
"""Verify critical InCar Android layout contracts across resource qualifiers."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"
LAYOUT = "layout"
MAX_LAYOUT_BYTES = 1024 * 1024

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


def layout_variants(repo_root: Path, filename: str) -> list[Path]:
    return sorted(
        path
        for source_set in ("main", "inCar")
        for path in (repo_root / f"android/app/src/{source_set}/res").glob(f"layout*/{filename}")
    )


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
    root = parse_layout(path)

    ids: set[str] = set()
    for element in root.iter():
        name = resource_id_name(element.get(ANDROID_ID))
        if name:
            ids.add(name)
    return ids


def parse_layout(path: Path) -> ET.Element:
    try:
        source = path.read_bytes()
    except OSError as exc:
        raise LayoutContractError(f"unable to parse {path}: {exc}") from exc
    if len(source) > MAX_LAYOUT_BYTES:
        raise LayoutContractError(f"{path}: layout exceeds {MAX_LAYOUT_BYTES} bytes")
    if b"<!DOCTYPE" in source or b"<!ENTITY" in source:
        raise LayoutContractError(f"{path}: DTD/entity declarations are forbidden")
    try:
        return ET.fromstring(source)
    except ET.ParseError as exc:
        raise LayoutContractError(f"unable to parse {path}: {exc}") from exc


def extract_between(source: str, start: str, end: str, description: str, failures: list[str]) -> str:
    if start not in source:
        failures.append(f"routing source contract failed: missing {description} start marker")
        return ""
    remainder = source.split(start, 1)[1]
    if end not in remainder:
        failures.append(f"routing source contract failed: missing {description} end marker")
        return ""
    return remainder.split(end, 1)[0]


def find_required_element(root: ET.Element, view_id: str, path: Path) -> ET.Element:
    for element in root.iter():
        if resource_id_name(element.get(ANDROID_ID)) == view_id:
            return element
    raise LayoutContractError(f"{path}: missing required owner @{view_id}")


def descendant_ids(element: ET.Element) -> set[str]:
    return {
        name
        for descendant in element.iter()
        if (name := resource_id_name(descendant.get(ANDROID_ID))) is not None
    }


def included_layouts(element: ET.Element) -> set[str]:
    return {
        layout.removeprefix("@layout/")
        for descendant in element.iter("include")
        if (layout := descendant.get(LAYOUT, "")).startswith("@layout/")
    }


def verify_routing_control_ownership(repo_root: Path) -> list[str]:
    """Protect the owner and dispatch boundaries that cannot be exercised in host JVM UI tests."""
    variants = layout_variants(repo_root, "routing_bottom_sheet.xml")
    failures: list[str] = []
    print("[routing control ownership]")
    for path in variants:
        try:
            root = parse_layout(path)
            frame = find_required_element(root, "routing_sheet_frame", path)
            buttons = find_required_element(root, "routing_bottom_buttons", path)
        except (OSError, ET.ParseError, LayoutContractError) as exc:
            failures.append(str(exc))
            continue

        button_ids = descendant_ids(buttons)
        missing = {"routing_btn_search", "btn__save"} - button_ids
        if "routing_start_button" not in included_layouts(buttons):
            missing.add("routing_start_button include")
        frame_conflicts = {"routing_btn_search", "btn__save", "start"} & descendant_ids(frame)
        if missing:
            failures.append(f"{path.relative_to(repo_root)}: bottom-buttons owner missing {sorted(missing)}")
        if frame_conflicts:
            failures.append(
                f"{path.relative_to(repo_root)}: sheet frame incorrectly owns {sorted(frame_conflicts)}"
            )
        if not missing and not frame_conflicts:
            print(f"PASS {path.relative_to(repo_root)}")

    controller = (
        repo_root
        / "android/app/src/main/java/app/organicmaps/routing/RoutingBottomMenuController.java"
    ).read_text(encoding="utf-8")
    fragment = (
        repo_root / "android/app/src/main/java/app/organicmaps/routing/RoutingPlanFragment.java"
    ).read_text(encoding="utf-8")
    start_listener = extract_between(
        controller, "mStart.setOnClickListener", "mTransitRecyclerView", "START listener", failures
    )
    search_listener = extract_between(
        fragment, "mSearchBtn.setOnClickListener", "mBookmarkBtn.setOnClickListener", "Search listener", failures
    )
    fragment_flat = " ".join(fragment.split())
    source_contracts = {
        "START is resolved from bottomButtons":
            "requireOwnedView(bottomButtons, R.id.start)" in controller,
        "Save is resolved from bottomButtons":
            "requireOwnedView(bottomButtons, R.id.btn__save)" in controller,
        "mandatory controls have no Activity fallback":
            "activity.findViewById(resourceId)" not in controller,
        "fragment passes the bottom-buttons owner":
            "newInstance(requireActivity(), mFrame, mButtonsLayout, mChartPanel" in fragment_flat,
        "Search is resolved from bottom-buttons owner":
            "requireOwnedView(mButtonsLayout, R.id.routing_btn_search)" in fragment,
        "START has exactly one listener": controller.count("mStart.setOnClickListener") == 1,
        "START dispatches only route start":
            "mListener.onRoutingStart();" in start_listener and "MapButtons.search" not in start_listener,
        "Search dispatches only search":
            "onMapButtonClick(MapButtonsController.MapButtons.search)" in search_listener
            and "onRoutingStart" not in search_listener,
    }
    for description, satisfied in source_contracts.items():
        if satisfied:
            print(f"PASS {description}")
        else:
            failures.append(f"routing source contract failed: {description}")
    print()
    return failures


def verify_layout_contract(repo_root: Path, filename: str, required_ids: tuple[str, ...]) -> list[str]:
    res_root = repo_root / "android/app/src"
    variants = layout_variants(repo_root, filename)
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
    failures.extend(verify_routing_control_ownership(repo_root))
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
