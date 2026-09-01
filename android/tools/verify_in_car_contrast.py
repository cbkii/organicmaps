#!/usr/bin/env python3
"""Verify critical direct-display InCar colour contrast and layout contracts.

Requires Python 3.10 or newer and uses only the standard library.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from verify_in_car_layout_contracts import LayoutContractError, verify_layout_contracts


class PaletteError(RuntimeError):
    """Raised when required palette input cannot be resolved safely."""


@dataclass(frozen=True)
class Rgba:
    red: float
    green: float
    blue: float
    alpha: float


@dataclass(frozen=True)
class ContrastCheck:
    label: str
    foreground: str
    background: str
    minimum: float


QUICK_DESTINATION_CHECKS = (
    ContrastCheck("quick controls", "in_car_quick_foreground", "in_car_quick_surface", 5.5),
)

DAY_CHECKS = (
    ContrastCheck("primary text on cards", "text_dark", "bg_cards", 7.0),
    ContrastCheck("secondary text on cards", "text_dark_subtitle", "bg_cards", 7.0),
    ContrastCheck("dialog body text on cards", "black_secondary", "bg_cards", 7.0),
    ContrastCheck("hint text on cards", "text_dark_hint", "bg_cards", 4.5),
    ContrastCheck("theme map-button icons", "black_54", "bg_menu", 7.0),
    ContrastCheck("secondary icons on floating chrome", "icon_tint_light", "bg_menu", 4.5),
    ContrastCheck("essential dividers on cards", "divider", "bg_cards", 3.0),
    ContrastCheck("primary CTA", "button_accent_text", "button_accent_normal", 5.5),
    ContrastCheck("pressed primary CTA", "button_accent_text", "button_accent_pressed", 5.5),
    ContrastCheck("disabled primary CTA", "black_38", "button_accent_disabled", 4.5),
    ContrastCheck("destructive CTA", "button_red_text", "button_red_normal", 5.5),
    ContrastCheck("selected routing mode", "routing_button_activated_tint", "routing_tab_active_bg", 5.0),
    ContrastCheck("routing bottom FAB icon", "black_54", "routing_bottom_button_tint", 7.0),
    ContrastCheck("light hint on primary chrome", "text_light_hint", "bg_primary", 4.5),
) + QUICK_DESTINATION_CHECKS

NIGHT_CHECKS = (
    ContrastCheck("primary text on cards", "text_light", "bg_cards", 7.0),
    ContrastCheck("secondary text on cards", "text_light_subtitle", "bg_cards", 7.0),
    ContrastCheck("dialog body text on cards", "white_secondary", "bg_cards", 7.0),
    ContrastCheck("hint text on cards", "text_light_hint", "bg_cards", 4.5),
    ContrastCheck("legacy dialog control on cards", "text_dark_hint", "bg_cards", 4.5),
    ContrastCheck("inverse primary text", "text_dark", "text_light", 7.0),
    ContrastCheck("inverse secondary text", "text_dark_subtitle", "text_light", 7.0),
    ContrastCheck("legacy black secondary on inverse surface", "black_secondary", "text_light", 7.0),
    ContrastCheck("legacy black icon on inverse surface", "black_54", "text_light", 7.0),
    ContrastCheck("theme map-button icons", "white_secondary", "bg_menu", 7.0),
    ContrastCheck("secondary icons on floating chrome", "icon_tint_light", "bg_menu", 4.5),
    ContrastCheck("essential dividers on cards", "divider", "bg_cards", 3.0),
    ContrastCheck("primary CTA", "button_accent_text", "button_accent_normal", 5.5),
    ContrastCheck("pressed primary CTA", "button_accent_text", "button_accent_pressed", 4.5),
    ContrastCheck("disabled primary CTA", "button_accent_text_disabled", "button_accent_disabled", 4.5),
    ContrastCheck("destructive CTA", "button_red_text", "button_red_normal", 5.5),
    ContrastCheck("selected routing mode", "routing_button_activated_tint", "routing_tab_active_bg", 5.5),
    ContrastCheck("routing bottom FAB icon", "white_secondary", "routing_bottom_button_tint", 7.0),
) + QUICK_DESTINATION_CHECKS

OPAQUE_SURFACES = ("bg_window", "bg_cards", "bg_menu")


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


def read_colors(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise PaletteError(f"required resource file is missing: {path}")

    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise PaletteError(f"invalid XML in {path}: {exc}") from exc

    colors: dict[str, str] = {}
    for element in root.findall("color"):
        name = element.get("name")
        value = (element.text or "").strip()
        if not name or not value:
            raise PaletteError(f"invalid <color> entry in {path}")
        colors[name] = value
    return colors


def build_palette(repo_root: Path, night: bool) -> dict[str, str]:
    main_values = repo_root / "android/app/src/main/res/values/colors.xml"
    quick_values = repo_root / "android/app/src/main/res/values/in_car_quick_destinations_colors.xml"
    incar_values = repo_root / "android/app/src/inCar/res/values/colors.xml"

    defaults = read_colors(main_values)
    defaults.update(read_colors(quick_values))
    defaults.update(read_colors(incar_values))
    if not night:
        return defaults

    # Android chooses the best matching qualifier after resource merging. Model that
    # by applying night-specific resources over the merged default source-set values.
    night_values = read_colors(repo_root / "android/app/src/main/res/values-night/colors.xml")
    quick_night_values = read_colors(
        repo_root / "android/app/src/main/res/values-night/in_car_quick_destinations_colors.xml"
    )
    incar_night_values = read_colors(repo_root / "android/app/src/inCar/res/values-night/colors.xml")
    night_values.update(quick_night_values)
    night_values.update(incar_night_values)

    palette = defaults.copy()
    palette.update(night_values)
    return palette


def parse_hex_color(value: str) -> Rgba:
    digits = value.removeprefix("#")
    try:
        if len(digits) == 3:  # RGB
            red, green, blue = (int(char * 2, 16) for char in digits)
            alpha = 255
        elif len(digits) == 4:  # ARGB
            alpha, red, green, blue = (int(char * 2, 16) for char in digits)
        elif len(digits) == 6:  # RRGGBB
            red, green, blue = (int(digits[index : index + 2], 16) for index in (0, 2, 4))
            alpha = 255
        elif len(digits) == 8:  # AARRGGBB
            alpha, red, green, blue = (int(digits[index : index + 2], 16) for index in (0, 2, 4, 6))
        else:
            raise PaletteError(f"unsupported colour literal: {value}")
    except ValueError as exc:
        raise PaletteError(f"invalid colour literal: {value}") from exc

    return Rgba(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0)


def resolve_color(name: str, palette: dict[str, str], stack: tuple[str, ...] = ()) -> Rgba:
    if name in stack:
        chain = " -> ".join((*stack, name))
        raise PaletteError(f"cyclic colour reference: {chain}")

    try:
        value = palette[name]
    except KeyError as exc:
        raise PaletteError(f"required colour is undefined: {name}") from exc

    if value.startswith("#"):
        return parse_hex_color(value)
    if value.startswith("@color/"):
        return resolve_color(value.removeprefix("@color/"), palette, (*stack, name))
    if value == "@android:color/white":
        return Rgba(1.0, 1.0, 1.0, 1.0)
    if value == "@android:color/black":
        return Rgba(0.0, 0.0, 0.0, 1.0)
    if value in ("@android:color/transparent", "@null"):
        return Rgba(0.0, 0.0, 0.0, 0.0)
    raise PaletteError(f"unsupported colour reference for {name}: {value}")


def composite(foreground: Rgba, background: Rgba) -> Rgba:
    if background.alpha < 1.0:
        raise PaletteError("contrast background must be opaque")
    alpha = foreground.alpha
    return Rgba(
        foreground.red * alpha + background.red * (1.0 - alpha),
        foreground.green * alpha + background.green * (1.0 - alpha),
        foreground.blue * alpha + background.blue * (1.0 - alpha),
        1.0,
    )


def linear_component(component: float) -> float:
    if component <= 0.04045:
        return component / 12.92
    return ((component + 0.055) / 1.055) ** 2.4


def relative_luminance(color: Rgba) -> float:
    return (
        0.2126 * linear_component(color.red)
        + 0.7152 * linear_component(color.green)
        + 0.0722 * linear_component(color.blue)
    )


def contrast_ratio(foreground: Rgba, background: Rgba) -> float:
    visible_foreground = composite(foreground, background)
    foreground_luminance = relative_luminance(visible_foreground)
    background_luminance = relative_luminance(background)
    lighter = max(foreground_luminance, background_luminance)
    darker = min(foreground_luminance, background_luminance)
    return (lighter + 0.05) / (darker + 0.05)


def verify_palette(label: str, palette: dict[str, str], checks: tuple[ContrastCheck, ...]) -> list[str]:
    failures: list[str] = []
    print(f"[{label}]")

    for surface in OPAQUE_SURFACES:
        color = resolve_color(surface, palette)
        if color.alpha < 1.0:
            failures.append(f"{label}: {surface} must be opaque")
            print(f"FAIL opacity {surface}: {color.alpha:.3f}")
        else:
            print(f"PASS opacity {surface}: 1.000")

    for check in checks:
        foreground = resolve_color(check.foreground, palette)
        background = resolve_color(check.background, palette)
        ratio = contrast_ratio(foreground, background)
        status = "PASS" if ratio + 1e-9 >= check.minimum else "FAIL"
        print(f"{status} {check.label}: {ratio:.2f}:1 >= {check.minimum:.2f}:1")
        if status == "FAIL":
            failures.append(
                f"{label}: {check.label} is {ratio:.2f}:1, below {check.minimum:.2f}:1 "
                f"({check.foreground} on {check.background})"
            )

    print()
    return failures


def main(argv: list[str]) -> int:
    if sys.version_info < (3, 10):
        print("FAILED: Python 3.10 or newer is required", file=sys.stderr)
        return 2

    args = parse_args(argv)
    repo_root = args.repo_root.resolve()

    try:
        failures = verify_layout_contracts(repo_root)
        day_palette = build_palette(repo_root, night=False)
        night_palette = build_palette(repo_root, night=True)
        failures.extend(verify_palette("day", day_palette, DAY_CHECKS))
        failures.extend(verify_palette("night", night_palette, NIGHT_CHECKS))
    except (LayoutContractError, PaletteError) as exc:
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
