#!/usr/bin/env python3
"""Verify the rendered high-glare InCar light-map contrast hierarchy."""

from __future__ import annotations

import re
import sys
from pathlib import Path

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
VAR = re.compile(r"^\s*@([A-Za-z0-9_]+)\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")
STYLE = re.compile(r"^\s*([A-Za-z0-9_]+)-color\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")

LIGHT_OPACITY_MARKER = "/* High-glare light-only driving-road opacity override. */"
LIGHT_ROAD_SELECTORS = (
    "line|z6-13[highway=motorway][!tunnel]",
    "line|z6-13[highway=trunk][!tunnel]",
    "line|z10-13[highway=motorway_link][!tunnel]",
    "line|z10-13[highway=trunk_link][!tunnel]",
    "line|z8-13[highway=primary][!tunnel]",
    "line|z11-13[highway=primary_link][!tunnel]",
    "line|z10-13[highway=secondary][!tunnel]",
    "line|z13[highway=secondary_link][!tunnel]",
    "line|z11-13[highway=tertiary][!tunnel]",
    "line|z12-13[highway=residential][!tunnel]",
    "line|z11-13[highway=unclassified][!tunnel]",
    "line|z12-13[highway=road][!tunnel]",
    "line|z12-13[highway=living_street][!tunnel]",
)

ROAD_NAMES = (
    "trunk0",
    "primary0",
    "primary1",
    "primary2",
    "secondary0",
    "residential",
    "unclassified",
)

# Final rendered opacity for the checked ordinary driving hierarchy. Lower-zoom
# vehicle rules use 0.3/0.7/0.8, but the light-only rule below overrides them
# to 1.0. z14+ is independently forced to 1.0 by InCarOverrides.
ROAD_OPACITY = 1.0
CASING_OPACITY = 1.0


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


def srgb_channels(value: str) -> tuple[float, float, float]:
    return tuple(int(value[index:index + 2], 16) / 255.0 for index in (1, 3, 5))


def composite_srgb(
    foreground: tuple[float, float, float],
    background: tuple[float, float, float],
    opacity: float,
) -> tuple[float, float, float]:
    if not 0.0 <= opacity <= 1.0:
        raise VerificationError(f"invalid opacity {opacity}")
    return tuple(
        foreground[channel] * opacity + background[channel] * (1.0 - opacity)
        for channel in range(3)
    )


def format_srgb(channels: tuple[float, float, float]) -> str:
    return "#" + "".join(f"{round(channel * 255):02X}" for channel in channels)


def relative_luminance(channels: tuple[float, float, float]) -> float:
    def linear(channel: float) -> float:
        if channel <= 0.04045:
            return channel / 12.92
        return ((channel + 0.055) / 1.055) ** 2.4

    red, green, blue = (linear(channel) for channel in channels)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast(
    first: tuple[float, float, float],
    second: tuple[float, float, float],
) -> float:
    first_l = relative_luminance(first)
    second_l = relative_luminance(second)
    lighter = max(first_l, second_l)
    darker = min(first_l, second_l)
    return (lighter + 0.05) / (darker + 0.05)


def effective(value: str, opacity: float, background: str) -> tuple[float, float, float]:
    return composite_srgb(srgb_channels(value), srgb_channels(background), opacity)


def require_rendered_contrast(
    first_name: str,
    first: str,
    first_opacity: float,
    second_name: str,
    second: str,
    second_opacity: float,
    background: str,
    minimum: float,
) -> None:
    first_effective = effective(first, first_opacity, background)
    second_effective = effective(second, second_opacity, background)
    ratio = contrast(first_effective, second_effective)
    if ratio < minimum:
        raise VerificationError(
            f"{first_name} {first} opacity {first_opacity:.2f} "
            f"(effective {format_srgb(first_effective)}) vs "
            f"{second_name} {second} opacity {second_opacity:.2f} "
            f"(effective {format_srgb(second_effective)}): "
            f"contrast {ratio:.2f}:1 is below {minimum:.2f}:1"
        )


def verify_compositor() -> None:
    # Deterministic regression case: 70% white over black is encoded-sRGB #B2B2B2.
    result = composite_srgb((1.0, 1.0, 1.0), (0.0, 0.0, 0.0), 0.7)
    if format_srgb(result) != "#B2B2B2":
        raise VerificationError(
            f"sRGB compositing self-test failed: expected #B2B2B2, found {format_srgb(result)}"
        )


def verify_render_opacity_contract(
    light_style_path: Path,
    vehicle_roads_path: Path,
    in_car_overrides_path: Path,
) -> None:
    light_style = light_style_path.read_text(encoding="utf-8")
    vehicle_roads = vehicle_roads_path.read_text(encoding="utf-8")
    in_car_overrides = in_car_overrides_path.read_text(encoding="utf-8")

    marker = light_style.find(LIGHT_OPACITY_MARKER)
    if marker < 0:
        raise VerificationError(
            f"{light_style_path}: missing light-only road opacity contract"
        )
    block_start = light_style.find("\n", marker)
    block_end = light_style.find("{opacity: 1;}", block_start)
    if block_end < 0:
        raise VerificationError(
            f"{light_style_path}: light-only road opacity contract does not set opacity 1"
        )
    block = light_style[block_start:block_end + len("{opacity: 1;}")]

    for selector in LIGHT_ROAD_SELECTORS:
        if selector not in block:
            raise VerificationError(
                f"{light_style_path}: light-only opacity contract missing selector {selector}"
            )

    # Guard the premise that makes compositing relevant. If the imported vehicle
    # style changes these faded tiers, re-audit the final cascade instead of
    # silently relying on stale assumptions.
    inherited_fragments = (
        "{color: @trunk0; opacity: 0.3;}",
        "{color: @trunk0; opacity: 0.7;}",
        "{color: @primary0; opacity: 0.7; casing-linecap: butt;}",
        "{color: @secondary0;opacity: 0.8;casing-linecap: butt; casing-color:@casing_road;}",
        "{color: @residential; opacity: 0.7;casing-linecap: butt; casing-color:@casing_road;}",
        "{color: @unclassified; opacity: 0.7;casing-linecap: butt;}",
    )
    for fragment in inherited_fragments:
        if fragment not in vehicle_roads:
            raise VerificationError(
                f"{vehicle_roads_path}: inherited road-opacity contract changed near {fragment}"
            )

    major = (
        "{opacity: 1; casing-opacity: 1; casing-color: @casing_road_major; "
        "casing-linecap: butt;}"
    )
    local = (
        "{opacity: 1; casing-opacity: 1; casing-color: @casing_road_local; "
        "casing-linecap: butt;}"
    )
    if major not in in_car_overrides:
        raise VerificationError(
            f"{in_car_overrides_path}: major-road full-opacity casing contract changed"
        )
    if local not in in_car_overrides:
        raise VerificationError(
            f"{in_car_overrides_path}: local-road full-opacity casing contract changed"
        )


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    palette_path = root / "data/styles/in_car/light/colors.mapcss"
    light_style_path = root / "data/styles/in_car/light/style.mapcss"
    vehicle_roads_path = root / "data/styles/vehicle/include/Roads.mapcss"
    in_car_overrides_path = root / "data/styles/in_car/include/InCarOverrides.mapcss"

    try:
        verify_compositor()
        verify_render_opacity_contract(
            light_style_path, vehicle_roads_path, in_car_overrides_path
        )

        palette = read_colours(palette_path, VAR)
        style = read_colours(light_style_path, STYLE)

        background = require(palette, "background", palette_path)
        route = require(style, "Route", light_style_path)
        roads = {name: require(palette, name, palette_path) for name in ROAD_NAMES}

        # Check rendered colours after the actual final opacity contract, not raw
        # palette hex values. Background is opaque by definition.
        for name, road in roads.items():
            require_rendered_contrast(
                name, road, ROAD_OPACITY,
                "background", background, 1.0,
                background, 3.0,
            )
            require_rendered_contrast(
                "Route", route, 1.0,
                name, road, ROAD_OPACITY,
                background, 3.5,
            )

        # Visible ordinary road casings begin where InCarOverrides sets both
        # fill and casing to opacity 1. Test the casing actually selected by
        # those final major/local rules rather than the superseded base token.
        casing_groups = {
            "casing_road_major": ("trunk0", "primary1", "primary2", "secondary0"),
            "casing_road_local": ("residential", "unclassified"),
        }
        for casing_name, road_names in casing_groups.items():
            casing = require(palette, casing_name, palette_path)
            for road_name in road_names:
                require_rendered_contrast(
                    casing_name, casing, CASING_OPACITY,
                    road_name, roads[road_name], ROAD_OPACITY,
                    background, 7.0,
                )

        if background != "#78838C":
            raise VerificationError(
                f"background must retain the qualified cool-slate anchor "
                f"#78838C, found {background}"
            )
    except (OSError, VerificationError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar high-glare rendered contrast hierarchy verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
