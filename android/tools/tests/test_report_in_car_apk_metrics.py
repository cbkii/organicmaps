#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "report_in_car_apk_metrics.py"
SPEC = importlib.util.spec_from_file_location("report_in_car_apk_metrics", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load metrics module from {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ReportInCarApkMetricsTest(unittest.TestCase):
    def make_apk(self, path: Path) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("classes.dex", b"d" * 100, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("classes2.dex", b"e" * 50, compress_type=zipfile.ZIP_STORED)
            archive.writestr("lib/arm64-v8a/liborganicmaps.so", b"n" * 200, compress_type=zipfile.ZIP_STORED)
            archive.writestr("resources.arsc", b"r" * 80, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("res/layout/main.xml", b"x" * 40, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("assets/drules_in_car.bin", b"a" * 60, compress_type=zipfile.ZIP_DEFLATED)

    def test_collects_expected_categories(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            apk = Path(temporary_dir) / "test.apk"
            self.make_apk(apk)
            metrics = MODULE.collect_metrics(apk)

            self.assertGreater(metrics["apk"]["file_bytes"], 0)
            self.assertEqual(2, metrics["dex"]["entries"])
            self.assertEqual(150, metrics["dex"]["uncompressed_bytes"])
            self.assertEqual(1, metrics["liborganicmaps"]["entries"])
            self.assertEqual(200, metrics["liborganicmaps"]["uncompressed_bytes"])
            self.assertEqual(200, metrics["liborganicmaps"]["per_abi"]["arm64-v8a"]["uncompressed_bytes"])
            self.assertEqual(80, metrics["resources_arsc"]["uncompressed_bytes"])
            self.assertEqual(40, metrics["res"]["uncompressed_bytes"])
            self.assertEqual(60, metrics["assets"]["uncompressed_bytes"])

    def test_rejects_archive_without_runtime_library(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            apk = Path(temporary_dir) / "not-incar.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", b"dex")
            with self.assertRaises(MODULE.MetricsError):
                MODULE.collect_metrics(apk)


if __name__ == "__main__":
    unittest.main()
