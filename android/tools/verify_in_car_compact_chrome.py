#!/usr/bin/env python3
"""Verify the InCar compact-chrome refinement and emergency sizing exception."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"


class VerificationError(RuntimeError):
    pass


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise VerificationError(f"unable to read {path}: {exc}") from exc


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


def require_text(path: Path, text: str, description: str) -> None:
    if text not in read_text(path):
        raise VerificationError(f"{path}: missing {description}")


def reject_text(path: Path, text: str, description: str) -> None:
    if text in read_text(path):
        raise VerificationError(f"{path}: contains forbidden {description}")


def verify_map_selector(path: Path) -> None:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise VerificationError(f"unable to parse {path}: {exc}") from exc
    items = list(root.findall("item"))
    if not items:
        raise VerificationError(f"{path}: selector contains no items")
    alpha_attr = f"{{{ANDROID_NS}}}alpha"
    color_attr = f"{{{ANDROID_NS}}}color"
    normal = items[-1]
    if normal.attrib.get(alpha_attr) != "0.60":
        raise VerificationError(f"{path}: normal surface alpha must be 0.60")
    if normal.attrib.get(color_attr) != "?menuBackground":
        raise VerificationError(f"{path}: normal surface must remain theme-aware via ?menuBackground")


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    runtime = root / "android/app/src/main/res/values/in_car_runtime_ui.xml"
    visuals = root / "android/app/src/main/res/values/in_car_visuals.xml"
    quick_colours = root / "android/app/src/main/res/values/in_car_quick_destinations_colors.xml"
    day_colours = root / "android/app/src/inCar/res/values/colors.xml"
    night_colours = root / "android/app/src/inCar/res/values-night/colors.xml"
    map_selector = root / "android/app/src/inCar/res/color/in_car_map_button_background.xml"
    visual_source = root / "android/app/src/main/java/app/organicmaps/util/InCarVisuals.java"
    quick_source = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsUi.java"
    button_source = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickActionButton.java"

    try:
        runtime_values = resource_values(runtime)
        visuals_values = resource_values(visuals)
        quick_values = resource_values(quick_colours)
        day_values = resource_values(day_colours)
        night_values = resource_values(night_colours)

        require_value(runtime_values, "in_car_touch_target_preferred", "76dp", runtime)
        require_value(runtime_values, "in_car_touch_target_min", "69dp", runtime)
        require_value(runtime_values, "in_car_touch_target_extra_compact", "55dp", runtime)
        require_value(runtime_values, "in_car_map_control_outline_width", "1dp", runtime)
        require_value(runtime_values, "in_car_map_button_surface_alpha", "153", runtime)
        require_value(runtime_values, "in_car_quick_surface_alpha", "163", runtime)
        require_value(runtime_values, "in_car_quick_pressed_feedback_alpha", "82", runtime)
        require_value(runtime_values, "in_car_quick_focused_feedback_alpha", "61", runtime)
        require_value(runtime_values, "in_car_quick_elevation", "2dp", runtime)

        for name in (
            "in_car_extra_compact_map_button_size",
            "in_car_extra_compact_button_min_touch_target",
            "in_car_extra_compact_routing_action_button_size",
            "in_car_extra_compact_routing_toolbar_cell_height",
            "in_car_extra_compact_routing_close_button_size",
            "in_car_extra_compact_nav_button_height",
            "in_car_extra_compact_close_button_size",
        ):
            require_value(visuals_values, name, "@dimen/in_car_touch_target_extra_compact", visuals)

        require_value(quick_values, "in_car_quick_surface", "@color/bg_menu", quick_colours)
        require_value(quick_values, "in_car_quick_foreground", "@color/icon_tint", quick_colours)
        require_value(quick_values, "in_car_map_control_outline", "#E6FFFFFF", quick_colours)
        require_value(day_values, "in_car_map_control_outline", "#E6FFFFFF", day_colours)
        require_value(night_values, "in_car_map_control_outline", "#E6FFFFFF", night_colours)
        verify_map_selector(map_selector)

        require_text(visual_source, "static final int EXTRA_COMPACT_HEIGHT_DP = 360;", "360dp live-height threshold")
        require_text(visual_source, "heightDp < EXTRA_COMPACT_HEIGHT_DP", "strict below-threshold selection")
        require_text(visual_source, "ControlSizeTier.EXTRA_COMPACT", "extra-compact control tier")
        require_text(visual_source, "currentQuickActionSizePx", "central Quick action sizing accessor")
        require_text(visual_source, "R.dimen.in_car_extra_compact_map_button_size", "extra-compact map sizing")

        require_text(quick_source, "mDirectActions", "semantic direct-action list")
        require_text(quick_source, "directActionCountForCapacity", "capacity-aware direct action selection")
        require_text(quick_source, "for (int i = visibleDirectCount - 1; i >= 0; --i)",
                     "bottom-anchored reverse priority rendering")
        require_text(quick_source, "rendered.add(ensureMoreButton());", "More button rendered at the bottom anchor")
        require_text(quick_source, "InCarVisuals.currentQuickActionSizePx", "central responsive Quick sizing")
        require_text(quick_source, "R.dimen.in_car_quick_elevation", "resource-backed Quick elevation")

        require_text(button_source, "in_car_quick_pressed_feedback_alpha", "resource-backed pressed feedback")
        require_text(button_source, "in_car_quick_focused_feedback_alpha", "resource-backed focus feedback")
        reject_text(button_source, "feedbackColor, 112", "legacy hard-coded pressed feedback alpha")
        reject_text(button_source, "feedbackColor, 92", "legacy hard-coded focus feedback alpha")
    except VerificationError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar compact chrome and emergency sizing contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
