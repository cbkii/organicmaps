#!/usr/bin/env python3
"""Report deterministic size metrics for an Organic Maps InCar APK.

Python 3.9+; standard library only. The APK is inspected from ZIP metadata and
is never extracted, so metrics collection is bounded by the archive directory.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

DEX_RE = re.compile(r"^classes(?:\d+)?\.dex$")
ORGANICMAPS_SO_RE = re.compile(r"^lib/([^/]+)/liborganicmaps\.so$")


class MetricsError(RuntimeError):
    """The requested APK metrics could not be produced safely."""


@dataclass(frozen=True)
class SizeBucket:
    entries: int = 0
    compressed_bytes: int = 0
    uncompressed_bytes: int = 0

    def add(self, info: zipfile.ZipInfo) -> "SizeBucket":
        return SizeBucket(
            entries=self.entries + 1,
            compressed_bytes=self.compressed_bytes + info.compress_size,
            uncompressed_bytes=self.uncompressed_bytes + info.file_size,
        )

    def as_dict(self) -> dict[str, int]:
        return {
            "entries": self.entries,
            "compressed_bytes": self.compressed_bytes,
            "uncompressed_bytes": self.uncompressed_bytes,
        }


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="InCar APK to inspect")
    parser.add_argument("--output-json", type=Path, help="Atomically write the JSON report here")
    parser.add_argument("--summary-file", type=Path, help="Append a compact Markdown summary here")
    return parser.parse_args(argv)


def _sum_bucket(entries: Iterable[zipfile.ZipInfo]) -> SizeBucket:
    bucket = SizeBucket()
    for info in entries:
        if not info.is_dir():
            bucket = bucket.add(info)
    return bucket


def collect_metrics(apk: Path) -> dict[str, object]:
    if not apk.is_file():
        raise MetricsError(f"APK not found: {apk}")

    try:
        apk_bytes = apk.stat().st_size
    except OSError as exc:
        raise MetricsError(f"Unable to stat APK: {apk}") from exc

    try:
        with zipfile.ZipFile(apk, "r") as archive:
            bad_entry = archive.testzip()
            if bad_entry is not None:
                raise MetricsError(f"APK ZIP integrity check failed at entry: {bad_entry}")
            entries = [info for info in archive.infolist() if not info.is_dir()]
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        if isinstance(exc, MetricsError):
            raise
        raise MetricsError(f"Unable to inspect APK ZIP: {apk}") from exc

    dex_entries = [info for info in entries if DEX_RE.fullmatch(info.filename)]
    organicmaps_native: dict[str, SizeBucket] = {}
    for info in entries:
        match = ORGANICMAPS_SO_RE.fullmatch(info.filename)
        if match is None:
            continue
        abi = match.group(1)
        organicmaps_native[abi] = organicmaps_native.get(abi, SizeBucket()).add(info)

    if not dex_entries:
        raise MetricsError("No classes*.dex entries found; refusing to report a non-Android APK")
    if not organicmaps_native:
        raise MetricsError("No lib/*/liborganicmaps.so entry found; refusing to report a non-InCar runtime archive")

    total = _sum_bucket(entries)
    dex = _sum_bucket(dex_entries)
    resources_arsc = _sum_bucket(info for info in entries if info.filename == "resources.arsc")
    assets = _sum_bucket(info for info in entries if info.filename.startswith("assets/"))
    resources = _sum_bucket(info for info in entries if info.filename.startswith("res/"))
    native_all = _sum_bucket(info for info in entries if info.filename.startswith("lib/"))

    native_per_abi = {abi: bucket.as_dict() for abi, bucket in sorted(organicmaps_native.items())}
    organicmaps_native_total = SizeBucket()
    for bucket in organicmaps_native.values():
        organicmaps_native_total = SizeBucket(
            entries=organicmaps_native_total.entries + bucket.entries,
            compressed_bytes=organicmaps_native_total.compressed_bytes + bucket.compressed_bytes,
            uncompressed_bytes=organicmaps_native_total.uncompressed_bytes + bucket.uncompressed_bytes,
        )

    return {
        "schema_version": 1,
        "apk": {
            "file_name": apk.name,
            "file_bytes": apk_bytes,
            "zip_entries": total.entries,
            "zip_compressed_bytes": total.compressed_bytes,
            "zip_uncompressed_bytes": total.uncompressed_bytes,
        },
        "dex": dex.as_dict(),
        "liborganicmaps": {
            **organicmaps_native_total.as_dict(),
            "per_abi": native_per_abi,
        },
        "native_all": native_all.as_dict(),
        "resources_arsc": resources_arsc.as_dict(),
        "res": resources.as_dict(),
        "assets": assets.as_dict(),
    }


def write_atomic_json(path: Path, metrics: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(metrics, handle, indent=2, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except (OSError, TypeError, ValueError):
        try:
            temporary.unlink(missing_ok=True)
        finally:
            raise


def append_summary(path: Path, metrics: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    apk = metrics["apk"]
    dex = metrics["dex"]
    native = metrics["liborganicmaps"]
    assets = metrics["assets"]
    resources = metrics["res"]
    arsc = metrics["resources_arsc"]
    buckets = {"apk": apk, "dex": dex, "liborganicmaps": native, "assets": assets, "res": resources,
               "resources_arsc": arsc}
    invalid = [name for name, value in buckets.items() if not isinstance(value, dict)]
    if invalid:
        raise MetricsError("Internal metrics shape is invalid for: " + ", ".join(invalid))

    with path.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write("\n### InCar APK size metrics\n\n")
        handle.write(f"- APK file: `{apk['file_bytes']}` bytes\n")
        handle.write(
            f"- DEX: `{dex['entries']}` file(s), `{dex['compressed_bytes']}` compressed / "
            f"`{dex['uncompressed_bytes']}` uncompressed bytes\n"
        )
        handle.write(
            f"- liborganicmaps.so: `{native['compressed_bytes']}` compressed / "
            f"`{native['uncompressed_bytes']}` uncompressed bytes\n"
        )
        handle.write(
            f"- resources.arsc: `{arsc['compressed_bytes']}` compressed / "
            f"`{arsc['uncompressed_bytes']}` uncompressed bytes\n"
        )
        handle.write(
            f"- res/: `{resources['compressed_bytes']}` compressed / "
            f"`{resources['uncompressed_bytes']}` uncompressed bytes\n"
        )
        handle.write(
            f"- assets/: `{assets['compressed_bytes']}` compressed / "
            f"`{assets['uncompressed_bytes']}` uncompressed bytes\n"
        )


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        metrics = collect_metrics(args.apk)
        if args.output_json is not None:
            write_atomic_json(args.output_json, metrics)
        if args.summary_file is not None:
            append_summary(args.summary_file, metrics)
    except (MetricsError, OSError) as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    json.dump(metrics, sys.stdout, indent=2, sort_keys=True)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
