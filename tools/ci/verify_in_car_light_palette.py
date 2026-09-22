#!/usr/bin/env python3
"""Verify the high-glare InCar light-map contrast hierarchy."""

from __future__ import annotations

import re
import sys
from pathlib import Path

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
VAR = re.compile(r"^\s*@([A-Za-z0-9_]+)\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")
STYLE = re.compile(r"^\s*([A-Za-z0-9_]+)-color\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")

REQUIRED_OPAQUE_DRIVING_SELECTORS = (
    "line|z6-[highway=motorway][!tunnel]",
    "line|z6-[highway=trunk][!tunnel]",
    "line|z10-[highway=motorway_link][!tunnel]",
    "line|z10-[highway=trunk_link][!tunnel]",
    "line|z8-[highway=primary][!tunnel]",
    "line|z11-[highway=primary_link][!tunnel]",
    "line|z10-[highway=secondary][!tunnel]",
    "line|z13-[highway=secondary_link][!tunnel]",
    "line|z11-[highway=tertiary][!tunnel]",
    "line|z14-[highway=tertiary_link][!tunnel]",
    "line|z12-[highway=residential][!tunnel]",
    "line|z11-[highway=unclassified][!tunnel]",
    "line|z12-[highway=road][!tunnel]",
    "line|z12-[highway=living_street][!tunnel]",
    "line|z14-[highway=service][!tunnel]",
)


class VerificationError(RuntimeError):
    pass


def read_colours(path: Path, pattern: re.Pattern[str]) -> dict[str, str]:
    colours: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = pattern.match(line)
        if match is None:
            continue
        name, value = match.groups()
        if name in colours:
            raise VerificationError(f"{path}:{line_number}: duplicate colour {name}")
        colours[name] = value.upper()
    return colours


def require(colours: dict[str, str], name: str, source: Path) -> str:
    value = colours.get(name)
    if value is None or HEX.fullmatch(value) is None:
        raise VerificationError(f"{source}: missing six-digit colour {name}")
    return value


def relative_luminance(value: str) -> float:
    channels = [int(value[index:index + 2], 16) / 255.0 for index in (1, 3, 5)]

    def linear(channel: float) -> float:
        if channel <= 0.04045:
            return channel / 12.92
        return ((channel + 0.055) / 1.055) ** 2.4

    red, green, blue = (linear(channel) for channel in channels)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast(first: str, second: str) -> float:
    first_l = relative_luminance(first)
    second_l = relative_luminance(second)
    lighter = max(first_l, second_l)
    darker = min(first_l, second_l)
    return (lighter + 0.05) / (darker + 0.05)


def composite(foreground: str, background: str, opacity: float) -> str:
    """Return the effective sRGB colour after source-over alpha compositing."""
    if not 0.0 <= opacity <= 1.0:
        raise VerificationError(f"invalid opacity {opacity}")

    foreground_channels = [int(foreground[index:index + 2], 16) for index in (1, 3, 5)]
    background_channels = [int(background[index:index + 2], 16) for index in (1, 3, 5)]
    effective = [
        round(opacity * foreground_channel + (1.0 - opacity) * background_channel)
        for foreground_channel, background_channel in zip(foreground_channels, background_channels)
    ]
    return "#" + "".join(f"{channel:02X}" for channel in effective)


def require_contrast(
    first_name: str,
    first: str,
    second_name: str,
    second: str,
    minimum: float,
) -> None:
    ratio = contrast(first, second)
    if ratio < minimum:
        raise VerificationError(
            f"{first_name} {first} vs {second_name} {second}: "
            f"contrast {ratio:.2f}:1 is below {minimum:.2f}:1"
        )


def require_full_opacity_contract(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    for selector in REQUIRED_OPAQUE_DRIVING_SELECTORS:
        selector_index = source.find(selector)
        if selector_index < 0:
            raise VerificationError(f"{path}: missing full-opacity driving selector {selector}")

        block_start = source.find("{", selector_index)
        if block_start < 0:
            raise VerificationError(f"{path}: missing rule body after {selector}")
        block_end = source.find("}", block_start)
        if block_end < 0:
            raise VerificationError(f"{path}: unterminated rule body after {selector}")

        # A grouped selector must resolve to the first following declaration block.
        body = re.sub(r"\s+", "", source[block_start + 1:block_end])
        if "opacity:1;" not in body or "casing-opacity:1;" not in body:
            raise VerificationError(
                f"{path}: {selector} must render fill and casing at full opacity"
            )


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    palette_path = root / "data/styles/in_car/light/colors.mapcss"
    style_path = root / "data/styles/in_car/light/style.mapcss"
    overrides_path = root / "data/styles/in_car/include/InCarOverrides.mapcss"

    try:
        palette = read_colours(palette_path, VAR)
        style = read_colours(style_path, STYLE)
        require_full_opacity_contract(overrides_path)

        background = require(palette, "background", palette_path)
        route = require(style, "Route", style_path)

        road_names = (
            "trunk0",
            "trunk1",
            "primary0",
            "primary1",
            "primary2",
            "secondary0",
            "residential",
            "unclassified",
        )
        roads = {name: require(palette, name, palette_path) for name in road_names}

        # These are effective rendered colours, not raw swatches. The source
        # contract above intentionally pins core driving roads to opacity 1 from
        # their first visible zoom so glare cannot blend them back into the base.
        effective_roads = {
            name: composite(road, background, 1.0)
            for name, road in roads.items()
        }

        for name, road in effective_roads.items():
            require_contrast(name, road, "background", background, 3.0)

        # Route is opaque in the InCar style; compare it with the effective
        # composited road colour so any future alpha change cannot be ignored.
        effective_route = composite(route, background, 1.0)
        for name, road in effective_roads.items():
            require_contrast("Route", effective_route, name, road, 3.5)

        # Casings are also forced opaque by the same driving-road contract.
        for casing_name in ("casing_road", "casing_road_major", "casing_road_local"):
            casing = composite(require(palette, casing_name, palette_path), background, 1.0)
            for road_name, road in effective_roads.items():
                require_contrast(casing_name, casing, road_name, road, 7.0)

        if background != "#78838C":
            raise VerificationError(
                f"background must retain the qualified cool-slate anchor #78838C, found {background}"
            )
    except (OSError, VerificationError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar high-glare light palette rendered-contrast hierarchy verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
