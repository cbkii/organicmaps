#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]


def load_tool(module_name: str, filename: str):
    script = TOOLS / filename
    spec = importlib.util.spec_from_file_location(module_name, script)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load {module_name} from {script}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


METRICS = load_tool("report_in_car_apk_metrics", "report_in_car_apk_metrics.py")
BASELINE = load_tool("capture_in_car_runtime_baseline", "capture_in_car_runtime_baseline.py")
PACKAGE = "app.organicmaps.incar"


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
            metrics = METRICS.collect_metrics(apk)

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
            with self.assertRaises(METRICS.MetricsError):
                METRICS.collect_metrics(apk)


class CaptureInCarRuntimeBaselineTest(unittest.TestCase):
    def test_detects_fatal_from_pre_restart_pid(self) -> None:
        system_log = "\n".join(
            [
                "09-05 12:00:00.000  1000  1000 I ActivityManager: Start proc 4321:app.organicmaps.incar/u0a123 for activity",
                "09-05 12:00:00.100  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main",
                "09-05 12:00:01.000  1000  1000 I ActivityManager: Start proc 8765:app.organicmaps.incar/u0a123 for activity",
            ]
        )

        failure = BASELINE.has_failure_evidence(PACKAGE, "8765", "", system_log)

        self.assertIsNotNone(failure)
        self.assertIn("4321", failure)

    def test_tombstone_pid_match_is_delimited(self) -> None:
        system_log = "09-05 12:00:00.000  1000  1000 I tombstoned: received crash request for pid 12345\n"

        failure = BASELINE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertIsNone(failure)

    def test_detects_exact_tombstone_pid(self) -> None:
        system_log = "09-05 12:00:00.000  1000  1000 I tombstoned: received crash request for pid 1234\n"

        failure = BASELINE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertEqual("tombstone evidence for observed pid 1234", failure)

    def test_ignores_unrelated_fatal_process(self) -> None:
        system_log = "\n".join(
            [
                "09-05 12:00:00.000  1000  1000 I ActivityManager: Start proc 1234:app.organicmaps.incar/u0a123 for activity",
                "09-05 12:00:00.100  7777  7777 E AndroidRuntime: FATAL EXCEPTION: main",
            ]
        )

        failure = BASELINE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertIsNone(failure)

    def test_parse_timings_requires_total_and_wait(self) -> None:
        with self.assertRaises(BASELINE.BaselineError):
            BASELINE.parse_timings("Status: ok\nTotalTime: 123\n")

        self.assertEqual(
            {"TotalTime": 123, "WaitTime": 140},
            BASELINE.parse_timings("Status: ok\nTotalTime: 123\nWaitTime: 140\n"),
        )


if __name__ == "__main__":
    unittest.main()
