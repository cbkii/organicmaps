#!/usr/bin/env python3
"""Unit tests for resolve_in_car_release_version.py."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import resolve_in_car_release_version as resolver


class ResolveInCarReleaseVersionTest(unittest.TestCase):
    def test_first_release_defaults_to_0_1_0(self) -> None:
        result = resolver.resolve("", "", "patch")
        self.assertEqual("0.1.0", result.version.text)
        self.assertEqual(1_000_001_000, result.version.version_code)

    def test_patch_bump_preserves_existing_encoding(self) -> None:
        result = resolver.resolve("in-car-v0.1.0", "", "patch")
        self.assertEqual("0.1.1", result.version.text)
        self.assertEqual(1_000_001_001, result.version.version_code)

    def test_date_style_explicit_input_is_normalised(self) -> None:
        result = resolver.resolve("in-car-v0.1.0", "2026.08.09", "patch")
        self.assertEqual("2026.8.9", result.version.text)
        self.assertEqual("in-car-v2026.8.9", result.version.tag)
        self.assertEqual(2_026_008_009, result.version.version_code)

    def test_prefixed_date_style_input_is_normalised(self) -> None:
        result = resolver.resolve("in-car-v0.1.0", "in-car-v2026.08.09", "patch")
        self.assertEqual("2026.8.9", result.version.text)

    def test_patch_bump_after_extended_version(self) -> None:
        result = resolver.resolve("in-car-v2026.8.9", "", "patch")
        self.assertEqual("2026.8.10", result.version.text)
        self.assertEqual(2_026_008_010, result.version.version_code)

    def test_equal_explicit_version_is_rejected(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "not newer"):
            resolver.resolve("in-car-v2026.8.9", "2026.08.09", "patch")

    def test_reserved_major_gap_is_rejected(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "major must be"):
            resolver.resolve("in-car-v0.1.0", "1000.0.0", "patch")

    def test_component_over_999_is_rejected(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "supported range"):
            resolver.resolve("in-car-v0.1.0", "2026.1000.0", "patch")

    def test_legacy_major_999_cannot_auto_cross_reserved_gap(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "explicit 20xx"):
            resolver.resolve("in-car-v999.1.1", "", "major")

    def test_extended_major_2099_cannot_bump(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "no larger encoded"):
            resolver.resolve("in-car-v2099.1.1", "", "major")

    def test_latest_tag_must_be_canonical(self) -> None:
        with self.assertRaisesRegex(resolver.VersionError, "latest in-car tag"):
            resolver.resolve("in-car-v2026.08.09", "", "patch")

    def test_prefix_v_is_accepted_for_explicit_input(self) -> None:
        result = resolver.resolve("in-car-v0.1.0", "v1.2.3", "patch")
        self.assertEqual("1.2.3", result.version.text)


if __name__ == "__main__":
    unittest.main()
