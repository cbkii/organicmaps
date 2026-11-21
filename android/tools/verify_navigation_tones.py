#!/usr/bin/env python3
"""Verify six original InCar navigation PCM tones and their shared 800 ms identity."""
from __future__ import annotations

import argparse
import hashlib
import io
import sys
import wave
import zipfile
from pathlib import Path

NAMES = ("advance", "left", "right", "complex", "route", "gps")
RATE = 16000
FRAMES = 24960
SHARED_FRAMES = 12800


def verify(source: Path, apk: Path | None) -> None:
    tones: dict[str, bytes] = {}
    prefixes: list[bytes] = []
    tails: set[bytes] = set()
    for name in NAMES:
        path = source / f"in_car_nav_{name}.wav"
        data = path.read_bytes()
        with wave.open(io.BytesIO(data), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(),
                audio.getnframes(), audio.getcomptype()) != (1, 2, RATE, FRAMES, "NONE"):
                raise ValueError(f"{path}: unexpected WAV encoding or duration")
            frames = audio.readframes(FRAMES)
        prefix = frames[:SHARED_FRAMES * 2]
        tail = frames[SHARED_FRAMES * 2:]
        if len(frames) != FRAMES * 2 or not any(prefix) or not any(tail):
            raise ValueError(f"{path}: empty or incomplete PCM")
        prefixes.append(prefix)
        tails.add(hashlib.sha256(tail).digest())
        tones[name] = hashlib.sha256(data).digest()
    if len(set(prefixes)) != 1 or len(tails) != len(NAMES):
        raise ValueError("Tones must share byte-identical first 800ms and have six different endings")

    if apk is not None:
        with zipfile.ZipFile(apk) as archive:
            found: dict[str, int] = {n: 0 for n in NAMES}
            for info in archive.infolist():
                if not info.filename.startswith("res/") or info.is_dir() or info.file_size != 44 + FRAMES * 2:
                    continue
                digest = hashlib.sha256(archive.read(info)).digest()
                for name, expected in tones.items():
                    if digest == expected:
                        if info.compress_type != zipfile.ZIP_STORED:
                            raise ValueError(f"{info.filename}: compressed raw WAV cannot be used by openRawResourceFd")
                        found[name] += 1
            if any(count != 1 for count in found.values()):
                raise ValueError(f"Expected each navigation WAV exactly once in APK: {found}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    try:
        verify(args.source_dir, args.apk)
    except (OSError, ValueError, wave.Error, zipfile.BadZipFile) as exc:
        print(f"Navigation tone verification failed: {exc}", file=sys.stderr)
        return 1
    print("Navigation tones verified: six distinct 1.56s PCM files, common 800ms prefix"
          + ("; APK assets verified" if args.apk else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
