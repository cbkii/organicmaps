#!/usr/bin/env python3
"""Resolve a canonical Organic Maps InCar release version.

The resolver preserves the existing 0..999 semantic-version encoding while also
accepting date-style explicit input such as 2026.08.09. Date-style input is
normalised to canonical dotted integers (2026.8.9) and uses the reserved
2.000.000.000..2.099.999.999 Android versionCode range.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass


LEGACY_VERSION_CODE_BASE = 1_000_000_000
MAX_COMPONENT = 999
EXTENDED_MAJOR_MIN = 2000
EXTENDED_MAJOR_MAX = 2099
MAX_ANDROID_VERSION_CODE = 2_100_000_000

_VERSION_RE = re.compile(r"^([0-9]+)\.([0-9]+)\.([0-9]+)$")
_CANONICAL_TAG_RE = re.compile(
    r"^in-car-v((?:0|[1-9][0-9]*))\.((?:0|[1-9][0-9]*))\.((?:0|[1-9][0-9]*))$"
)


class VersionError(ValueError):
    """Raised when release-version input cannot be resolved safely."""


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int

    @property
    def text(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def tag(self) -> str:
        return f"in-car-v{self.text}"

    @property
    def version_code(self) -> int:
        if self.major >= EXTENDED_MAJOR_MIN:
            code = self.major * 1_000_000 + self.minor * 1_000 + self.patch
        else:
            code = (
                LEGACY_VERSION_CODE_BASE
                + self.major * 1_000_000
                + self.minor * 1_000
                + self.patch
            )
        if not 1 <= code <= MAX_ANDROID_VERSION_CODE:
            raise VersionError(f"resolved Android versionCode {code} is outside the supported range")
        return code


@dataclass(frozen=True)
class Resolution:
    version: Version
    previous_tag: str

    def as_lines(self) -> tuple[str, ...]:
        # Contract: manual-in-car-release.yml accepts exactly these output keys.
        # Update its parser at the same time if this tuple changes.
        return (
            f"version={self.version.text}",
            f"version_code={self.version.version_code}",
            f"version_name={self.version.text}-InCar",
            f"tag={self.version.tag}",
            f"previous_tag={self.previous_tag}",
        )


def _strip_optional_prefix(value: str) -> str:
    stripped = value.strip()
    if stripped.startswith("in-car-v"):
        return stripped[len("in-car-v") :]
    if stripped.startswith("v"):
        return stripped[1:]
    return stripped


def parse_version(value: str, *, allow_leading_zeroes: bool) -> Version:
    candidate = _strip_optional_prefix(value)
    match = _VERSION_RE.fullmatch(candidate)
    if match is None:
        raise VersionError(f"invalid version '{value}': expected MAJOR.MINOR.PATCH")

    major_text, minor_text, patch_text = match.groups()
    if len(major_text) > 4 or len(minor_text) > 3 or len(patch_text) > 3:
        raise VersionError(f"invalid version '{value}': component is outside the supported range")

    if not allow_leading_zeroes:
        for component in (major_text, minor_text, patch_text):
            if len(component) > 1 and component.startswith("0"):
                raise VersionError(f"invalid canonical version '{value}': leading zeroes are not allowed")

    major = int(major_text, 10)
    minor = int(minor_text, 10)
    patch = int(patch_text, 10)

    if minor > MAX_COMPONENT or patch > MAX_COMPONENT:
        raise VersionError(f"invalid version '{value}': minor/patch components must be 0..999")

    if not (major <= MAX_COMPONENT or EXTENDED_MAJOR_MIN <= major <= EXTENDED_MAJOR_MAX):
        raise VersionError(
            f"invalid version '{value}': major must be 0..999 or {EXTENDED_MAJOR_MIN}..{EXTENDED_MAJOR_MAX}"
        )

    return Version(major, minor, patch)


def parse_latest_tag(tag: str) -> Version | None:
    if not tag:
        return None
    match = _CANONICAL_TAG_RE.fullmatch(tag)
    if match is None:
        raise VersionError(f"unable to parse latest in-car tag '{tag}'")
    return parse_version(tag, allow_leading_zeroes=False)


def bump_version(version: Version, bump: str) -> Version:
    if bump == "patch":
        if version.patch >= MAX_COMPONENT:
            raise VersionError("patch component is already 999; choose a minor or major bump")
        return Version(version.major, version.minor, version.patch + 1)

    if bump == "minor":
        if version.minor >= MAX_COMPONENT:
            raise VersionError("minor component is already 999; choose a major bump")
        return Version(version.major, version.minor + 1, 0)

    if bump == "major":
        if version.major == MAX_COMPONENT:
            raise VersionError(
                "legacy major component is already 999; use an explicit 20xx.x.x version "
                "to enter the reserved extended range"
            )
        if version.major >= EXTENDED_MAJOR_MAX:
            raise VersionError(
                f"extended major component is already {EXTENDED_MAJOR_MAX}; "
                "no larger encoded in-car version is available"
            )
        return Version(version.major + 1, 0, 0)

    raise VersionError(f"unsupported bump '{bump}'")


def resolve(latest_tag: str, requested: str, bump: str) -> Resolution:
    previous = parse_latest_tag(latest_tag)

    if requested.strip():
        version = parse_version(requested, allow_leading_zeroes=True)
    elif previous is None:
        version = Version(0, 1, 0)
    else:
        version = bump_version(previous, bump)

    previous_code = previous.version_code if previous is not None else 0
    if version.version_code <= previous_code:
        previous_label = latest_tag or "no previous release"
        raise VersionError(
            f"requested in-car version {version.text} is not newer than {previous_label}"
        )

    return Resolution(version=version, previous_tag=latest_tag)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--latest-tag", default="", help="Latest canonical in-car-vX.Y.Z tag, or blank")
    parser.add_argument("--requested", default="", help="Optional explicit version")
    parser.add_argument("--bump", choices=("patch", "minor", "major"), default="patch")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        resolution = resolve(args.latest_tag, args.requested, args.bump)
    except VersionError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 2

    for line in resolution.as_lines():
        print(line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
