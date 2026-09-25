#!/usr/bin/env python3
"""Guard the strengthened InCar light-map glare contrast and road geometry."""

from __future__ import annotations

from pathlib import Path

from verify_in_car_light_palette import (
    STYLE_COLOR,
    VAR,
    VerificationError,
    contrast,
    find_rule_properties,
    read_values,
    require_colour,
)

ROAD_NAMES = (
    "trunk0",
    "trunk1",
    "primary0",
    "primary1",
    "primary2",
    "secondary0",
    "residential",
    "unclassified",
)

GEOMETRY_FLOORS = (
    ("line|z12[highway=motorway][!tunnel]", "width", 4.25),
    ("line|z14[highway=motorway][!tunnel]", "width", 5.65),
    ("line|z16[highway=motorway][!tunnel]", "width", 10.35),
    ("line|z18[highway=motorway][!tunnel]", "width", 28.75),
    ("line|z12[highway=primary][!tunnel]", "width", 2.75),
    ("line|z14[highway=primary][!tunnel]", "width", 4.05),
    ("line|z16[highway=primary][!tunnel]", "width", 7.50),
    ("line|z18[highway=primary][!tunnel]", "width", 25.30),
    ("line|z12[highway=secondary][!tunnel]", "width", 2.90),
    ("line|z14[highway=secondary][!tunnel]", "width", 3.70),
    ("line|z16[highway=secondary][!tunnel]", "width", 6.90),
    ("line|z18[highway=secondary][!tunnel]", "width", 17.25),
    ("line|z12[highway=residential][!tunnel]", "width", 0.95),
    ("line|z14[highway=residential][!tunnel]", "width", 2.55),
    ("line|z16[highway=residential][!tunnel]", "width", 5.90),
    ("line|z18[highway=residential][!tunnel]", "width", 13.80),
)


def require_ratio(name: str, first: str, second: str, minimum: float) -> None:
    ratio = contrast(first, second)
    if ratio < minimum:
        raise VerificationError(
            f"{name}: contrast {ratio:.2f}:1 is below strengthened {minimum:.2f}:1 floor"
        )


def require_numeric_property(path: Path, selector: str, property_name: str, minimum: float) -> None:
    properties = find_rule_properties(path, selector)
    raw = properties.get(property_name)
    if raw is None:
        raise VerificationError(f"{path}: {selector} does not set {property_name}")
    try:
        value = float(raw)
    except ValueError as exc:
        raise VerificationError(
            f"{path}: {selector} has non-numeric {property_name}={raw!r}"
        ) from exc
    if value < minimum:
        raise VerificationError(
            f"{path}: {selector} {property_name}={value:g} is below {minimum:g}"
        )


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    palette_path = root / "data/styles/in_car/light/colors.mapcss"
    style_path = root / "data/styles/in_car/light/style.mapcss"
    geometry_path = root / "data/styles/in_car/include/InCarOverrides.mapcss"

    try:
        palette = read_values(palette_path, VAR)
        style_colours = read_values(style_path, STYLE_COLOR)
        background = require_colour(palette, "background", palette_path)
        route = require_colour(style_colours, "Route", style_path)
        roads = {name: require_colour(palette, name, palette_path) for name in ROAD_NAMES}

        # The old qualified palette only guaranteed 3.0:1 road/base and 3.5:1 route/road.
        # The revised warm fills deliberately lift both floors without darkening the base and
        # thereby sacrificing the existing dark-label/base relationship.
        for name, road in roads.items():
            require_ratio(f"{name}/background", road, background, 3.35)
            require_ratio(f"Route/{name}", route, road, 3.90)

        for casing_name in ("casing_road_major", "casing_road_local"):
            casing = require_colour(palette, casing_name, palette_path)
            for road_name, road in roads.items():
                require_ratio(f"{casing_name}/{road_name}", casing, road, 10.0)

        # Kothic requires geometry shared by the packed light/dark variants. Keep the
        # glare-specific colour contract above light-only, but assert the shared stroke floors.
        for selector, property_name, minimum in GEOMETRY_FLOORS:
            require_numeric_property(geometry_path, selector, property_name, minimum)
    except (OSError, ValueError, VerificationError) as exc:
        print(f"FAILED: {exc}")
        return 1

    print("PASS: strengthened InCar light glare contrast and shared road geometry verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
