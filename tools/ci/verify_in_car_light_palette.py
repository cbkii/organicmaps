#!/usr/bin/env python3
"""Verify the rendered high-glare InCar light-map contrast hierarchy."""

from __future__ import annotations

import re
import sys
from pathlib import Path

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
VAR = re.compile(r"^\s*@([A-Za-z0-9_]+)\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")
STYLE_COLOR = re.compile(r"^\s*([A-Za-z0-9_]+)-color\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")
STYLE_OPACITY = re.compile(r"^\s*([A-Za-z0-9_]+)-opacity\s*:\s*([0-9.]+)\s*;\s*$")
PROPERTY = re.compile(r"(?:^|;)\s*([A-Za-z-]+)\s*:\s*([^;]+)\s*;")

LOW_ZOOM_LIGHT_SELECTORS = (
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

HIGH_ZOOM_MAJOR_SELECTORS = (
    "line|z14-[highway=motorway][!tunnel]",
    "line|z14-[highway=trunk][!tunnel]",
    "line|z14-[highway=motorway_link][!tunnel]",
    "line|z14-[highway=trunk_link][!tunnel]",
    "line|z14-[highway=primary][!tunnel]",
    "line|z14-[highway=primary_link][!tunnel]",
    "line|z14-[highway=secondary][!tunnel]",
    "line|z14-[highway=secondary_link][!tunnel]",
)

HIGH_ZOOM_LOCAL_SELECTORS = (
    "line|z14-[highway=tertiary][!tunnel]",
    "line|z14-[highway=tertiary_link][!tunnel]",
    "line|z14-[highway=residential][!tunnel]",
    "line|z14-[highway=unclassified][!tunnel]",
    "line|z14-[highway=road][!tunnel]",
    "line|z14-[highway=living_street][!tunnel]",
    "line|z14-[highway=service][!tunnel]",
)


class VerificationError(RuntimeError):
    pass


def read_values(path: Path, pattern: re.Pattern[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = pattern.match(line)
        if match is None:
            continue
        name, value = match.groups()
        if name in values:
            raise VerificationError(f"{path}:{line_number}: duplicate value {name}")
        values[name] = value.upper() if value.startswith("#") else value
    return values


def require_colour(colours: dict[str, str], name: str, source: Path) -> str:
    value = colours.get(name)
    if value is None or HEX.fullmatch(value) is None:
        raise VerificationError(f"{source}: missing six-digit colour {name}")
    return value


def find_rule_properties(path: Path, selector: str) -> dict[str, str]:
    source = path.read_text(encoding="utf-8")
    selector_index = source.find(selector)
    if selector_index < 0:
        raise VerificationError(f"{path}: missing selector {selector}")

    block_start = source.find("{", selector_index)
    if block_start < 0:
        raise VerificationError(f"{path}: missing rule body after {selector}")
    block_end = source.find("}", block_start)
    if block_end < 0:
        raise VerificationError(f"{path}: unterminated rule body after {selector}")

    properties: dict[str, str] = {}
    body = source[block_start + 1:block_end]
    for declaration in body.split(";"):
        declaration = declaration.strip()
        if not declaration:
            continue
        if ":" not in declaration:
            raise VerificationError(
                f"{path}: malformed declaration {declaration!r} after {selector}"
            )
        name, value = declaration.split(":", 1)
        properties[name.strip()] = value.strip()
    return properties


def require_opacity(path: Path, selector: str, property_name: str) -> float:
    properties = find_rule_properties(path, selector)
    value = properties.get(property_name)
    if value is None:
        raise VerificationError(f"{path}: {selector} does not set {property_name}")
    try:
        opacity = float(value)
    except ValueError as exc:
        raise VerificationError(
            f"{path}: {selector} has non-numeric {property_name}={value!r}"
        ) from exc
    if opacity != 1.0:
        raise VerificationError(
            f"{path}: {selector} must set {property_name}: 1, found {opacity:g}"
        )
    return opacity


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


def composite_encoded_srgb(foreground: str, background: str, opacity: float) -> str:
    """Return deterministic encoded-sRGB source-over compositing."""
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
    *,
    first_opacity: float = 1.0,
    second_opacity: float = 1.0,
    background: str,
) -> None:
    effective_first = composite_encoded_srgb(first, background, first_opacity)
    effective_second = composite_encoded_srgb(second, background, second_opacity)
    ratio = contrast(effective_first, effective_second)
    if ratio < minimum:
        raise VerificationError(
            f"{first_name} {first} @ {first_opacity:g} -> {effective_first} vs "
            f"{second_name} {second} @ {second_opacity:g} -> {effective_second}: "
            f"contrast {ratio:.2f}:1 is below {minimum:.2f}:1"
        )



def read_compiled_drules(
    path: Path,
) -> tuple[dict[str, str], dict[str, str], dict[tuple[str, int], list[str]]]:
    """Read the generated dump and retain the light-variant line-colour contract."""
    colours: dict[str, str] = {}
    named_colours: dict[str, str] = {}
    line_colours: dict[tuple[str, int], list[str]] = {}

    section = ""
    current_type: str | None = None
    current_zoom: int | None = None
    variants_seen = False

    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line == "variants: light dark":
            variants_seen = True
            continue
        if line == "colors:":
            section = "colors"
            continue
        if line == "named-colors:":
            section = "named"
            continue
        if line.startswith("type "):
            section = "types"
            current_type = line.removeprefix("type ")
            current_zoom = None
            continue

        if section == "colors":
            match = DRULE_COLOUR.fullmatch(line)
            if match is not None:
                name, light_value, _dark_value = match.groups()
                colours[name] = light_value.upper()
            continue

        if section == "named":
            match = DRULE_NAMED.fullmatch(line)
            if match is not None:
                name, colour_ref = match.groups()
                named_colours[name] = colour_ref
            continue

        if section == "types":
            zoom_match = DRULE_ZOOM.fullmatch(line)
            if zoom_match is not None:
                current_zoom = int(zoom_match.group(1))
                continue

            line_match = DRULE_LINE_COLOUR.match(line)
            if line_match is not None:
                if current_type is None or current_zoom is None:
                    raise VerificationError(
                        f"{path}:{line_number}: line colour outside type/zoom"
                    )
                line_colours.setdefault((current_type, current_zoom), []).append(
                    line_match.group(1)
                )

    if not variants_seen:
        raise VerificationError(f"{path}: expected 'variants: light dark'")
    if not colours or not line_colours:
        raise VerificationError(f"{path}: generated drawing-rule dump is incomplete")
    return colours, named_colours, line_colours


def opaque_compiled_colour(rgb: str) -> str:
    # Kothic's generated dump stores transparency in the leading byte:
    # 00RRGGBB is fully opaque, while non-zero prefixes are translucent.
    return "#00" + rgb.removeprefix("#").upper()


def require_compiled_line_colour(
    *,
    drules_path: Path,
    colours: dict[str, str],
    line_colours: dict[tuple[str, int], list[str]],
    type_name: str,
    zoom: int,
    expected_rgb: str,
    role: str,
) -> None:
    refs = line_colours.get((type_name, zoom), [])
    values = [colours.get(ref, "") for ref in refs]
    expected = opaque_compiled_colour(expected_rgb)

    if expected not in values:
        raise VerificationError(
            f"{drules_path}: {type_name} z{zoom} does not compile an opaque "
            f"{role} {expected}; found {values or 'no line colours'}"
        )

    rgb_suffix = expected[3:]
    translucent_matches = [
        value
        for value in values
        if value and value[3:] == rgb_suffix and value != expected
    ]
    if translucent_matches:
        raise VerificationError(
            f"{drules_path}: {type_name} z{zoom} retains translucent {role} "
            f"variants {translucent_matches} alongside {expected}"
        )


def verify_compiled_light_style(
    *,
    drules_path: Path,
    palette: dict[str, str],
    palette_path: Path,
    style_colours: dict[str, str],
    style_path: Path,
) -> None:
    colours, named_colours, line_colours = read_compiled_drules(drules_path)

    route_rgb = require_colour(style_colours, "Route", style_path)
    route_ref = named_colours.get("Route")
    if route_ref is None:
        raise VerificationError(f"{drules_path}: generated named colour Route is missing")
    compiled_route = colours.get(route_ref)
    expected_route = opaque_compiled_colour(route_rgb)
    if compiled_route != expected_route:
        raise VerificationError(
            f"{drules_path}: Route compiled as {compiled_route}, expected {expected_route}"
        )

    low_zoom_cases = (
        ("highway-motorway", range(6, 14), "trunk0"),
        ("highway-trunk", range(6, 14), "trunk0"),
        ("highway-motorway_link", range(10, 14), "trunk0"),
        ("highway-trunk_link", range(10, 14), "trunk0"),
        ("highway-primary", range(8, 14), "primary0"),
        ("highway-primary_link", range(11, 14), "primary1"),
        ("highway-secondary", range(10, 14), "secondary0"),
        ("highway-secondary_link", (13,), "secondary0"),
        ("highway-tertiary", range(11, 14), "residential"),
        ("highway-residential", range(12, 14), "residential"),
        ("highway-unclassified", range(11, 14), "unclassified"),
        ("highway-road", range(12, 14), "unclassified"),
        ("highway-living_street", range(12, 14), "unclassified"),
    )
    for type_name, zooms, palette_name in low_zoom_cases:
        expected_rgb = require_colour(palette, palette_name, palette_path)
        for zoom in zooms:
            require_compiled_line_colour(
                drules_path=drules_path,
                colours=colours,
                line_colours=line_colours,
                type_name=type_name,
                zoom=zoom,
                expected_rgb=expected_rgb,
                role="road fill",
            )

    high_zoom_cases = (
        ("highway-motorway", 14, "trunk0", "casing_road_major"),
        ("highway-trunk", 14, "trunk0", "casing_road_major"),
        ("highway-motorway_link", 14, "trunk0", "casing_road_major"),
        ("highway-trunk_link", 14, "trunk0", "casing_road_major"),
        ("highway-primary", 14, "primary1", "casing_road_major"),
        ("highway-primary_link", 14, "primary1", "casing_road_major"),
        ("highway-secondary", 14, "secondary0", "casing_road_major"),
        ("highway-secondary_link", 14, "secondary0", "casing_road_major"),
        ("highway-tertiary", 15, "residential", "casing_road_local"),
        ("highway-tertiary_link", 15, "residential", "casing_road_local"),
        ("highway-residential", 15, "residential", "casing_road_local"),
        ("highway-unclassified", 14, "unclassified", "casing_road_local"),
        ("highway-road", 14, "unclassified", "casing_road_local"),
        ("highway-living_street", 14, "unclassified", "casing_road_local"),
        ("highway-service", 15, "unclassified", "casing_road_local"),
    )
    for type_name, zoom, fill_name, casing_name in high_zoom_cases:
        require_compiled_line_colour(
            drules_path=drules_path,
            colours=colours,
            line_colours=line_colours,
            type_name=type_name,
            zoom=zoom,
            expected_rgb=require_colour(palette, fill_name, palette_path),
            role="road fill",
        )
        require_compiled_line_colour(
            drules_path=drules_path,
            colours=colours,
            line_colours=line_colours,
            type_name=type_name,
            zoom=zoom,
            expected_rgb=require_colour(palette, casing_name, palette_path),
            role="road casing",
        )


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    palette_path = root / "data/styles/in_car/light/colors.mapcss"
    style_path = root / "data/styles/in_car/light/style.mapcss"
    overrides_path = root / "data/styles/in_car/include/InCarOverrides.mapcss"
    drules_path = root / "data/drules_in_car.txt"

    try:
        palette = read_values(palette_path, VAR)
        style_colours = read_values(style_path, STYLE_COLOR)
        style_opacities = read_values(style_path, STYLE_OPACITY)

        # Regression check for the compositing path itself.
        if composite_encoded_srgb("#FFFFFF", "#000000", 0.5) != "#808080":
            raise VerificationError("encoded-sRGB compositing self-check failed")

        low_fill_opacities = [
            require_opacity(style_path, selector, "opacity")
            for selector in LOW_ZOOM_LIGHT_SELECTORS
        ]
        high_fill_opacities = [
            require_opacity(overrides_path, selector, "opacity")
            for selector in HIGH_ZOOM_MAJOR_SELECTORS + HIGH_ZOOM_LOCAL_SELECTORS
        ]
        high_casing_opacities = [
            require_opacity(overrides_path, selector, "casing-opacity")
            for selector in HIGH_ZOOM_MAJOR_SELECTORS + HIGH_ZOOM_LOCAL_SELECTORS
        ]

        road_opacity = min(low_fill_opacities + high_fill_opacities)
        casing_opacity = min(high_casing_opacities)

        background = require_colour(palette, "background", palette_path)
        route = require_colour(style_colours, "Route", style_path)
        route_opacity = float(style_opacities.get("Route", "1"))

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
        roads = {name: require_colour(palette, name, palette_path) for name in road_names}

        for name, road in roads.items():
            require_contrast(
                name,
                road,
                "background",
                background,
                3.0,
                first_opacity=road_opacity,
                background=background,
            )
            require_contrast(
                "Route",
                route,
                name,
                road,
                3.5,
                first_opacity=route_opacity,
                second_opacity=road_opacity,
                background=background,
            )

        # Only these casing tokens are selected by the existing z14+ InCar
        # override for ordinary driving roads. Lower zoom tiers have no visible
        # casing width, so inherited vehicle casing alpha is not part of the
        # rendered ordinary-road contract being qualified here.
        for casing_name in ("casing_road_major", "casing_road_local"):
            casing = require_colour(palette, casing_name, palette_path)
            for road_name, road in roads.items():
                require_contrast(
                    casing_name,
                    casing,
                    road_name,
                    road,
                    7.0,
                    first_opacity=casing_opacity,
                    second_opacity=road_opacity,
                    background=background,
                )

        verify_compiled_light_style(
            drules_path=drules_path,
            palette=palette,
            palette_path=palette_path,
            style_colours=style_colours,
            style_path=style_path,
        )

        if background != "#78838C":
            raise VerificationError(
                f"background must retain the qualified cool-slate anchor #78838C, found {background}"
            )
    except (OSError, ValueError, VerificationError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar high-glare source and compiled rendered-contrast hierarchy verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
