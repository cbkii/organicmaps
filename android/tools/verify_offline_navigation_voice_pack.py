#!/usr/bin/env python3
"""Verify the exact InCar offline navigation voice pack source or APK asset."""

from __future__ import annotations

import argparse
import hashlib
import io
import sys
import zipfile
from pathlib import Path

PACK_DIRECTORY = "offline_navigation_voice_pack_v2"
PACK_FILENAME = "pack.zip"
EXPECTED_SIZE = 48_544
EXPECTED_SHA256 = "effdbacbd9984382f578602123f7f12409425125b0babcce088cc3dce490ffc1"
EXPECTED_CLIPS = {
    "02_navigation_started_lets_roll.ogg",
    "05_continue_straight_nice_easy.ogg",
    "07_turn_left.ogg",
    "08_turn_right.ogg",
    "09_bear_left_stay_cool.ogg",
    "10_bear_right_easy_now.ogg",
    "13_make_u_turn.ogg",
    "15_take_first_exit.ogg",
    "16_take_second_exit.ogg",
    "17_take_third_exit.ogg",
    "18_take_fourth_exit.ogg",
    "19_exit_left.ogg",
    "20_exit_right.ogg",
    "26_take_next_exit.ogg",
    "36_you_made_it_irie.ogg",
    "39_way_updated.ogg",
    "40_gps_lost.ogg",
    "41_gps_restored.ogg",
    "47_sharp_turn.ogg",
}
FORBIDDEN_CLIPS = {"68_gps_lost_short.ogg", "69_gps_restored_short.ogg"}


def read_from_source(asset_root: Path) -> bytes:
    pack_root = asset_root / PACK_DIRECTORY
    if not pack_root.is_dir():
        raise RuntimeError(f"Missing pack directory: {pack_root}")

    entries = list(pack_root.iterdir())
    if (
        len(entries) != 1
        or entries[0].name != PACK_FILENAME
        or not entries[0].is_file()
        or entries[0].is_symlink()
    ):
        raise RuntimeError(
            f"Unexpected source pack entries; expected one regular {PACK_FILENAME}, "
            f"actual={sorted(path.name for path in entries)}"
        )

    return entries[0].read_bytes()


def read_from_apk(apk: Path) -> bytes:
    if not apk.is_file():
        raise RuntimeError(f"APK does not exist: {apk}")

    with zipfile.ZipFile(apk) as archive:
        prefix = f"assets/{PACK_DIRECTORY}/"
        entries = [
            info
            for info in archive.infolist()
            if info.filename.startswith(prefix) and not info.is_dir()
        ]
        actual_names = [info.filename.removeprefix(prefix) for info in entries]
        if len(entries) != 1 or actual_names != [PACK_FILENAME]:
            raise RuntimeError(
                f"Unexpected packaged pack entries; expected={[PACK_FILENAME]}, "
                f"actual={sorted(actual_names)}"
            )
        if entries[0].file_size != EXPECTED_SIZE:
            raise RuntimeError(
                f"Pack metadata size mismatch: size={entries[0].file_size}, "
                f"expected={EXPECTED_SIZE}"
            )
        return archive.read(entries[0])


def verify_pack(pack: bytes) -> None:
    digest = hashlib.sha256(pack).hexdigest()
    if len(pack) != EXPECTED_SIZE or digest != EXPECTED_SHA256:
        raise RuntimeError(
            f"Pack integrity mismatch: size={len(pack)}, sha256={digest}; "
            f"expected size={EXPECTED_SIZE}, sha256={EXPECTED_SHA256}"
        )

    with zipfile.ZipFile(io.BytesIO(pack)) as archive:
        bad_entry = archive.testzip()
        if bad_entry is not None:
            raise RuntimeError(f"Corrupt ZIP member: {bad_entry}")

        names = [info.filename for info in archive.infolist() if not info.is_dir()]
        if len(names) != len(set(names)):
            raise RuntimeError("Duplicate ZIP member detected")

        actual = set(names)
        if actual != EXPECTED_CLIPS:
            raise RuntimeError(
                f"Unexpected clip set; missing={sorted(EXPECTED_CLIPS - actual)}, "
                f"extra={sorted(actual - EXPECTED_CLIPS)}"
            )
        if actual & FORBIDDEN_CLIPS:
            raise RuntimeError(
                f"Forbidden duplicate GPS clips present: {sorted(actual & FORBIDDEN_CLIPS)}"
            )
        if any("dude" in name.lower() for name in actual):
            raise RuntimeError("A clip name still contains the removed 'dude' wording")
        if any(info.file_size <= 0 for info in archive.infolist() if not info.is_dir()):
            raise RuntimeError("Empty voice clip detected")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--asset-root", type=Path, help="InCar assets directory")
    source.add_argument("--apk", type=Path, help="Built InCar APK")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        pack = (
            read_from_source(args.asset_root)
            if args.asset_root is not None
            else read_from_apk(args.apk)
        )
        verify_pack(pack)
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        print(f"offline navigation voice pack verification failed: {exc}", file=sys.stderr)
        return 1

    print(
        f"offline navigation voice pack verified: {EXPECTED_SIZE} bytes, "
        f"sha256={EXPECTED_SHA256}, clips={len(EXPECTED_CLIPS)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
