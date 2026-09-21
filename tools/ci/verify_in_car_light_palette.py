#!/usr/bin/env python3
"""Verify the high-glare InCar light-map contrast hierarchy."""

from __future__ import annotations

import re
import sys
from pathlib import Path

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
VAR = re.compile(r"^\s*@([A-Za-z0-9_]+)\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")
STYLE = re.compile(r"^\s*([A-Za-z0-9_]+)-color\s*:\s*(#[0-9A-Fa-f]{6})\s*;\s*$")


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


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    palette_path = root / "data/styles/in_car/light/colors.mapcss"
    style_path = root / "data/styles/in_car/light/style.mapcss"

    try:
        palette = read_colours(palette_path, VAR)
        style = read_colours(style_path, STYLE)

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

        # Glare resilience must not depend on hue alone: each driving road fill
        # retains a clear luminance separation from the general land surface.
        for name, road in roads.items():
            require_contrast(name, road, "background", background, 3.0)

        # The active route remains blue. Warm roads must keep it distinct even
        # when ambient light reduces chroma on the head-unit panel.
        for name, road in roads.items():
            require_contrast("Route", route, name, road, 3.5)

        # Casings provide an independent edge cue and therefore need much
        # stronger contrast against the light road fills than colour alone.
        for casing_name in ("casing_road", "casing_road_major", "casing_road_local"):
            casing = require(palette, casing_name, palette_path)
            for road_name, road in roads.items():
                require_contrast(casing_name, casing, road_name, road, 7.0)

        # Keep the palette intentionally cool outside the warm road hierarchy.
        if background != "#78838C":
            raise VerificationError(
                f"background must retain the qualified cool-slate anchor #78838C, found {background}"
            )
    except (OSError, VerificationError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar high-glare light palette contrast hierarchy verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
