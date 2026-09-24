#!/usr/bin/env python3
"""Capture one labelled InCar screenshot plus read-only Android state for qualification."""

from __future__ import annotations

import argparse
import datetime as dt
import pathlib
import re
import subprocess
import sys

LABELS = (
    "browse-light",
    "browse-dark",
    "route-preview",
    "active-navigation",
    "speeding-at-or-above-5pct",
    "speeding-below-5pct",
    "compact-windowed",
    "quick-destinations-expanded",
    "quick-destinations-normal",
    "my-position-free",
    "my-position-follow",
    "search-results",
    "driver-modal",
    "pip-active",
    "pip-restored-fullscreen",
)


def run(adb: list[str], *args: str, binary: bool = False):
    result = subprocess.run(
        [*adb, *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=not binary,
    )
    if result.returncode != 0:
        stderr = result.stderr.decode(errors="replace") if binary else result.stderr
        raise RuntimeError(f"adb {' '.join(args)} failed ({result.returncode}): {stderr.strip()}")
    return result.stdout


def adb_command(serial: str | None) -> list[str]:
    command = ["adb"]
    if serial:
        command.extend(["-s", serial])
    return command


def verify_target(adb: list[str]) -> None:
    if run(adb, "get-state").strip() != "device":
        raise RuntimeError("ADB target is not in device state")
    sdk = run(adb, "shell", "getprop", "ro.build.version.sdk").strip()
    if sdk and sdk != "29":
        print(f"WARNING: target reports API {sdk}; TS18 baseline is API 29 unless current evidence disproves it.", file=sys.stderr)


def write_text(path: pathlib.Path, text: str) -> None:
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def safe_label(label: str) -> str:
    if label not in LABELS:
        raise ValueError(f"unsupported label {label!r}; choose one of: {', '.join(LABELS)}")
    if re.fullmatch(r"[a-z0-9-]+", label) is None:
        raise ValueError("label contains unsafe characters")
    return label


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label", choices=LABELS)
    parser.add_argument("--serial", help="ADB serial when more than one device is connected")
    parser.add_argument("--package", default="app.organicmaps", help="package to inspect")
    parser.add_argument("--output", default="in-car-validation", help="host output directory")
    args = parser.parse_args()

    label = safe_label(args.label)
    adb = adb_command(args.serial)
    try:
        verify_target(adb)
    except (OSError, RuntimeError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    bundle = pathlib.Path(args.output) / f"{timestamp}-{label}"
    bundle.mkdir(parents=True, exist_ok=False)

    commands = {
        "device.txt": ("shell", "sh", "-c", "getprop ro.product.model; getprop ro.build.fingerprint; getprop ro.build.version.release; getprop ro.build.version.sdk"),
        "display.txt": ("shell", "sh", "-c", "wm size; wm density"),
        "window.txt": ("shell", "dumpsys", "window", "windows"),
        "activity.txt": ("shell", "dumpsys", "activity", "activities"),
        "package.txt": ("shell", "dumpsys", "package", args.package),
    }

    try:
        for filename, command in commands.items():
            write_text(bundle / filename, run(adb, *command))
        screenshot = run(adb, "exec-out", "screencap", "-p", binary=True)
        (bundle / f"{label}.png").write_bytes(screenshot)
    except (OSError, RuntimeError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    write_text(bundle / "capture.txt", f"label={label}\npackage={args.package}\nutc={timestamp}")
    print(bundle)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
