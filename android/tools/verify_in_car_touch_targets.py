#!/usr/bin/env python3
"""Verify the InCar 76dp preferred / 69dp compact touch-target contract."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PREFERRED_DP = 76
MINIMUM_DP = 69


class VerificationError(RuntimeError):
    """Raised when a required InCar sizing contract is not satisfied."""


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def resource_values(path: Path) -> dict[str, str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise VerificationError(f"unable to parse {path}: {exc}") from exc

    values: dict[str, str] = {}
    for child in root:
        name = child.attrib.get("name")
        if name:
            values[name] = (child.text or "").strip()
    return values


def require_value(values: dict[str, str], name: str, expected: str, source: Path) -> None:
    actual = values.get(name)
    if actual != expected:
        raise VerificationError(f"{source}: {name} must be {expected}, found {actual!r}")


def require_text(path: Path, pattern: str, description: str) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise VerificationError(f"unable to read {path}: {exc}") from exc
    if re.search(pattern, text, re.MULTILINE) is None:
        raise VerificationError(f"{path}: missing {description}")


def verify_ratio() -> None:
    if MINIMUM_DP * 10 < PREFERRED_DP * 9:
        raise VerificationError("compact touch target is below 90% of the preferred target")
    if (MINIMUM_DP - 1) * 10 >= PREFERRED_DP * 9:
        raise VerificationError("compact touch target is not the integer ceiling of the 90% floor")


def verify_resources(root: Path) -> None:
    runtime_path = root / "android/app/src/main/res/values/in_car_runtime_ui.xml"
    visuals_path = root / "android/app/src/main/res/values/in_car_visuals.xml"
    override_path = root / "android/app/src/inCar/res/values/in_car_layout.xml"

    runtime = resource_values(runtime_path)
    visuals = resource_values(visuals_path)
    overrides = resource_values(override_path)

    require_value(runtime, "in_car_touch_target_preferred", f"{PREFERRED_DP}dp", runtime_path)
    require_value(runtime, "in_car_touch_target_min", f"{MINIMUM_DP}dp", runtime_path)
    require_value(runtime, "in_car_runtime_button_size", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_runtime_row_min_height", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_action_min_height", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_save_width", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_other_width", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_quick_marker_touch_radius", "38dp", runtime_path)

    preferred_visuals = (
        "in_car_map_button_size",
        "in_car_button_min_touch_target",
        "in_car_routing_action_button_size",
        "in_car_routing_toolbar_cell_height",
        "in_car_routing_close_button_size",
        "in_car_nav_button_height",
        "in_car_place_page_close_button_size",
    )
    for name in preferred_visuals:
        require_value(visuals, name, "@dimen/in_car_touch_target_preferred", visuals_path)

    compact_visuals = (
        "in_car_compact_map_button_size",
        "in_car_compact_button_min_touch_target",
        "in_car_compact_routing_action_button_size",
        "in_car_compact_routing_toolbar_cell_height",
        "in_car_compact_close_button_size",
        "in_car_compact_nav_button_height",
    )
    for name in compact_visuals:
        require_value(visuals, name, "@dimen/in_car_touch_target_min", visuals_path)

    preferred_overrides = (
        "map_button_size",
        "primary_button_min_height",
        "menu_line_height",
        "menu_list_item_height",
        "nav_button_height",
        "nav_street_height",
        "routing_action_button_size",
        "routing_toolbar_cell_height",
    )
    for name in preferred_overrides:
        require_value(overrides, name, "@dimen/in_car_touch_target_preferred", override_path)
    require_value(overrides, "routing_bottom_buttons_max_height", "96dp", override_path)


def verify_code(root: Path) -> None:
    quick_policy = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsLayoutPolicy.java"
    choice_adapter = root / "android/app/src/main/java/app/organicmaps/incar/InCarChoiceAdapter.java"
    routing_layouts = (
        root / "android/app/src/main/res/layout/routing_bottom_sheet.xml",
        root / "android/app/src/main/res/layout-land/routing_bottom_sheet.xml",
    )

    require_text(
        quick_policy,
        rf"PREFERRED_ACTION_SIZE_DP\s*=\s*{PREFERRED_DP}\s*;",
        f"{PREFERRED_DP}dp Quick Destination preferred target",
    )
    require_text(
        quick_policy,
        rf"MIN_ACTION_SIZE_DP\s*=\s*{MINIMUM_DP}\s*;",
        f"{MINIMUM_DP}dp Quick Destination compact floor",
    )
    require_text(
        choice_adapter,
        r"R\.dimen\.in_car_runtime_row_min_height",
        "resource-backed InCar choice-row minimum",
    )

    for layout in routing_layouts:
        try:
            text = layout.read_text(encoding="utf-8")
        except OSError as exc:
            raise VerificationError(f"unable to read {layout}: {exc}") from exc
        if 'app:fabCustomSize="40dp"' in text:
            raise VerificationError(f"{layout}: route actions still hard-code a 40dp touch target")
        if text.count('app:fabCustomSize="@dimen/routing_action_button_size"') != 4:
            raise VerificationError(f"{layout}: expected four resource-sized routing action FABs")


def main() -> int:
    try:
        root = repository_root()
        verify_ratio()
        verify_resources(root)
        verify_code(root)
    except VerificationError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print(
        f"SUCCESS: InCar touch targets prefer {PREFERRED_DP}dp and compact no lower than {MINIMUM_DP}dp."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
