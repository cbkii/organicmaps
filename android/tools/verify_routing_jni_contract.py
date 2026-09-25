#!/usr/bin/env python3
"""Verify the Java/native routing enum contract used by RoutingJni.cpp.

The routing JNI bridge resolves Java enum constants with GetStaticFieldID() by
field name. R8 may remove constants that are only referenced from native code,
so each such enum must remain @Keep. With --apk this also verifies the actual
post-R8 DEX definitions in a packaged APK.
"""

from __future__ import annotations

import argparse
import re
import struct
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ROUTING_JNI = REPO_ROOT / "android/sdk/src/main/cpp/app/organicmaps/sdk/routing/RoutingJni.cpp"
ROUTING_INFO = REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/RoutingInfo.java"

CONTRACTS = {
    "CarDirection": {
        "path": REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/CarDirection.java",
        "descriptor": "Lapp/organicmaps/sdk/routing/CarDirection;",
        "constants": (
            "NoTurn", "GoStraight", "TurnRight", "TurnSharpRight", "TurnSlightRight",
            "TurnLeft", "TurnSharpLeft", "TurnSlightLeft", "UTurnLeft", "UTurnRight",
            "EnterRoundAbout", "LeaveRoundAbout", "StayOnRoundAbout", "StartAtEndOfStreet",
            "ReachedYourDestination", "ExitHighwayToLeft", "ExitHighwayToRight",
        ),
    },
    "PedestrianDirection": {
        "path": REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/PedestrianDirection.java",
        "descriptor": "Lapp/organicmaps/sdk/routing/PedestrianDirection;",
        "constants": ("NoTurn", "GoStraight", "TurnRight", "TurnLeft", "ReachedYourDestination"),
    },
    "LaneWay": {
        "path": REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/LaneWay.java",
        "descriptor": "Lapp/organicmaps/sdk/routing/LaneWay;",
        "constants": (
            "None", "ReverseLeft", "SharpLeft", "Left", "MergeToLeft", "SlightLeft", "Through",
            "SlightRight", "MergeToRight", "Right", "SharpRight", "ReverseRight",
        ),
    },
    "RouteRecommendationType": {
        "path": REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/RouteRecommendationType.java",
        "descriptor": "Lapp/organicmaps/sdk/routing/RouteRecommendationType;",
        "constants": ("RebuildAfterPointsLoading",),
    },
    "RoadShieldType": {
        "path": REPO_ROOT / "android/sdk/src/main/java/app/organicmaps/sdk/routing/roadshield/RoadShieldType.java",
        "descriptor": "Lapp/organicmaps/sdk/routing/roadshield/RoadShieldType;",
        "constants": (
            "GenericWhite", "GenericGreen", "GenericBlue", "GenericRed", "GenericOrange",
            "USInterstate", "USHighway", "UKHighway",
        ),
    },
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def enum_constants(source: str, enum_name: str) -> tuple[str, ...]:
    match = re.search(rf"public\s+enum\s+{re.escape(enum_name)}\s*\{{", source)
    if not match:
        fail(f"Unable to find enum declaration for {enum_name}")

    constants: list[str] = []
    for raw_line in source[match.end():].splitlines():
        line = raw_line.strip()
        if not line or line.startswith("//") or line.startswith("/*") or line.startswith("*"):
            continue
        if line.startswith("}"):
            break
        constant = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*(?:\(|,|;|$)", line)
        if constant:
            constants.append(constant.group(1))
            if ";" in line:
                break
    return tuple(constants)


def verify_sources() -> None:
    routing_jni = ROUTING_JNI.read_text(encoding="utf-8")
    routing_info = ROUTING_INFO.read_text(encoding="utf-8")
    if not re.search(r"@Keep\s+@SuppressWarnings\(\"unused\"\)\s+public final class RoutingInfo", routing_info):
        fail("RoutingInfo must remain @Keep because JNI constructs it by class and constructor signature")

    for enum_name, contract in CONTRACTS.items():
        path = contract["path"]
        source = path.read_text(encoding="utf-8")
        if "import androidx.annotation.Keep;" not in source:
            fail(f"{path}: missing androidx.annotation.Keep import")
        if not re.search(rf"@Keep\s+public\s+enum\s+{re.escape(enum_name)}", source):
            fail(f"{path}: {enum_name} must be @Keep because JNI resolves its constants by field name")

        actual = enum_constants(source, enum_name)
        expected = contract["constants"]
        if actual != expected:
            fail(f"{path}: enum constants drifted from JNI contract; expected {expected}, got {actual}")

        descriptor = contract["descriptor"]
        internal_name = descriptor[1:-1]
        if internal_name not in routing_jni or descriptor not in routing_jni:
            fail(f"RoutingJni.cpp no longer contains the expected JNI class/signature for {enum_name}: {descriptor}")

    if "GetStaticObjectField" not in routing_jni or "GetStaticFieldID" not in routing_jni:
        fail("RoutingJni.cpp no longer exposes the name-based static enum lookup this verifier protects")


def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        if offset >= len(data) or shift >= 35:
            fail("Malformed DEX ULEB128 value")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7


def dex_strings(data: bytes) -> list[str]:
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        fail("Invalid DEX header")
    size, offset = struct.unpack_from("<II", data, 0x38)
    result: list[str] = []
    for index in range(size):
        (string_data_offset,) = struct.unpack_from("<I", data, offset + index * 4)
        _, cursor = read_uleb128(data, string_data_offset)
        end = data.find(b"\x00", cursor)
        if end < 0:
            fail("Unterminated DEX string_data_item")
        result.append(data[cursor:end].decode("utf-8", errors="replace"))
    return result


def dex_static_fields(data: bytes) -> set[tuple[str, str]]:
    strings = dex_strings(data)
    type_size, type_offset = struct.unpack_from("<II", data, 0x40)
    field_size, field_offset = struct.unpack_from("<II", data, 0x50)
    class_size, class_offset = struct.unpack_from("<II", data, 0x60)

    types = [strings[struct.unpack_from("<I", data, type_offset + i * 4)[0]] for i in range(type_size)]
    fields: list[tuple[str, str]] = []
    for i in range(field_size):
        class_idx, _type_idx, name_idx = struct.unpack_from("<HHI", data, field_offset + i * 8)
        fields.append((types[class_idx], strings[name_idx]))

    defined: set[tuple[str, str]] = set()
    for i in range(class_size):
        class_idx, _access, _super, _interfaces, _source, _annotations, class_data_offset, _static_values = \
            struct.unpack_from("<IIIIIIII", data, class_offset + i * 32)
        if class_data_offset == 0:
            continue
        cursor = class_data_offset
        static_count, cursor = read_uleb128(data, cursor)
        instance_count, cursor = read_uleb128(data, cursor)
        direct_count, cursor = read_uleb128(data, cursor)
        virtual_count, cursor = read_uleb128(data, cursor)
        del instance_count, direct_count, virtual_count

        field_index = 0
        for _ in range(static_count):
            field_diff, cursor = read_uleb128(data, cursor)
            _field_access, cursor = read_uleb128(data, cursor)
            field_index += field_diff
            if field_index >= len(fields):
                fail("Malformed DEX static field index")
            descriptor, name = fields[field_index]
            if descriptor == types[class_idx]:
                defined.add((descriptor, name))
    return defined


def verify_apk(apk: Path) -> None:
    if not apk.is_file():
        fail(f"APK not found: {apk}")

    defined: set[tuple[str, str]] = set()
    with zipfile.ZipFile(apk) as archive:
        dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r"classes(?:\d+)?\.dex", name))
        if not dex_names:
            fail(f"{apk}: no classes*.dex entries found")
        for dex_name in dex_names:
            defined.update(dex_static_fields(archive.read(dex_name)))

    missing: list[str] = []
    for enum_name, contract in CONTRACTS.items():
        descriptor = contract["descriptor"]
        for constant in contract["constants"]:
            if (descriptor, constant) not in defined:
                missing.append(f"{enum_name}.{constant}")
    if missing:
        fail("Post-R8 APK is missing JNI-resolved enum fields: " + ", ".join(missing))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, help="also verify post-R8 enum fields in this APK")
    args = parser.parse_args()
    try:
        verify_sources()
        if args.apk is not None:
            verify_apk(args.apk)
    except (OSError, RuntimeError, zipfile.BadZipFile, struct.error) as error:
        print(f"routing JNI contract verification failed: {error}", file=sys.stderr)
        return 1

    if args.apk is None:
        print("Routing JNI source contract OK")
    else:
        print(f"Routing JNI source + packaged DEX contract OK: {args.apk}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
