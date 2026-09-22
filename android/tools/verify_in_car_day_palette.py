#!/usr/bin/env python3
"""Verify the InCar daylight palette keeps its high-glare perceptual hierarchy."""

from __future__ import annotations

import math
import re
import sys
from pathlib import Path

TOKEN_RE = re.compile(r"^@([A-Za-z0-9_]+):\s*(#[0-9A-Fa-f]{6});\s*$", re.MULTILINE)


class VerificationError(RuntimeError):
    pass


def read_tokens(path: Path) -> dict[str, str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise VerificationError(f"unable to read {path}: {exc}") from exc
    return {name: value.upper() for name, value in TOKEN_RE.findall(text)}


def rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[index : index + 2], 16) for index in (1, 3, 5))


def channel_luminance(value: int) -> float:
    component = value / 255.0
    if component <= 0.04045:
        return component / 12.92
    return math.pow((component + 0.055) / 1.055, 2.4)


def luminance(value: str) -> float:
    red, green, blue = rgb(value)
    return (
        0.2126 * channel_luminance(red)
        + 0.7152 * channel_luminance(green)
        + 0.0722 * channel_luminance(blue)
    )


def contrast(left: str, right: str) -> float:
    lighter = max(luminance(left), luminance(right))
    darker = min(luminance(left), luminance(right))
    return (lighter + 0.05) / (darker + 0.05)


def require(tokens: dict[str, str], name: str) -> str:
    value = tokens.get(name)
    if value is None:
        raise VerificationError(f"missing palette token @{name}")
    return value


def require_contrast(
    tokens: dict[str, str], foreground: str, background: str, minimum: float
) -> None:
    foreground_value = require(tokens, foreground)
    background_value = require(tokens, background)
    actual = contrast(foreground_value, background_value)
    if actual + 1e-9 < minimum:
        raise VerificationError(
            f"@{foreground} ({foreground_value}) must contrast with "
            f"@{background} ({background_value}) by at least {minimum:.2f}:1; "
            f"found {actual:.2f}:1"
        )


def require_warmer_than_background(
    tokens: dict[str, str], road: str, minimum_red_blue_delta: int
) -> None:
    road_red, _, road_blue = rgb(require(tokens, road))
    background_red, _, background_blue = rgb(require(tokens, "background"))
    if road_red - road_blue < minimum_red_blue_delta:
        raise VerificationError(
            f"@{road} must retain a clearly warm road cue; "
            f"red-blue delta is {road_red - road_blue}, expected >= {minimum_red_blue_delta}"
        )
    if background_blue - background_red < 12:
        raise VerificationError(
            "@background must retain a cool slate cue with blue at least 12 levels above red"
        )


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    light = root / "data/styles/in_car/light/colors.mapcss"
    tokens = read_tokens(light)

    try:
        # Driving roads must remain distinguishable by luminance even if glare suppresses saturation.
        for road in ("trunk0", "trunk1", "primary0", "primary1", "primary2", "secondary0", "residential", "unclassified"):
            require_contrast(tokens, road, "background", 3.0)

        # Major roads also carry an independent warm-vs-cool chromatic cue.
        for road in ("trunk0", "trunk1", "primary0", "primary1", "primary2", "secondary0"):
            require_warmer_than_background(tokens, road, 40)

        # Casings must remain a very strong edge cue around warm road fills.
        for road in ("trunk0", "trunk1", "primary0", "primary1", "primary2", "secondary0"):
            require_contrast(tokens, road, "casing_road_major", 7.0)
        for road in ("residential", "unclassified"):
            require_contrast(tokens, road, "casing_road_local", 7.0)

        # Previously clustered grey surfaces must not collapse back into the base land tone.
        for surface in ("aerodrome0", "building0", "building1", "industrial", "parking", "parking_l"):
            require_contrast(tokens, surface, "background", 1.25)

        # Water and vegetation keep independent semantic hue channels.
        water_red, water_green, water_blue = rgb(require(tokens, "water"))
        if not (water_blue > water_red and water_green > water_red):
            raise VerificationError("@water must remain cyan/blue relative to the slate base")
        forest_red, forest_green, forest_blue = rgb(require(tokens, "forest"))
        if not (forest_green > forest_red and forest_green > forest_blue):
            raise VerificationError("@forest must remain visibly green relative to the slate base")
    except VerificationError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print("PASS: InCar daylight high-glare palette contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
