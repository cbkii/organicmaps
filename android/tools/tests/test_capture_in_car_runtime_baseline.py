#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "capture_in_car_runtime_baseline.py"
SPEC = importlib.util.spec_from_file_location("capture_in_car_runtime_baseline", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load runtime baseline module from {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

PACKAGE = "app.organicmaps.incar"


class CaptureInCarRuntimeBaselineTest(unittest.TestCase):
    def test_detects_fatal_from_pre_restart_pid(self) -> None:
        system_log = "\n".join(
            [
                "09-05 12:00:00.000  1000  1000 I ActivityManager: Start proc 4321:app.organicmaps.incar/u0a123 for activity",
                "09-05 12:00:00.100  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main",
                "09-05 12:00:01.000  1000  1000 I ActivityManager: Start proc 8765:app.organicmaps.incar/u0a123 for activity",
            ]
        )

        failure = MODULE.has_failure_evidence(PACKAGE, "8765", "", system_log)

        self.assertIsNotNone(failure)
        self.assertIn("4321", failure)

    def test_tombstone_pid_match_is_delimited(self) -> None:
        system_log = (
            "09-05 12:00:00.000  1000  1000 I tombstoned: received crash request for pid 91234\n"
        )

        failure = MODULE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertIsNone(failure)

    def test_detects_exact_tombstone_pid(self) -> None:
        system_log = (
            "09-05 12:00:00.000  1000  1000 I tombstoned: received crash request for pid 1234\n"
        )

        failure = MODULE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertEqual("tombstone evidence for observed pid 1234", failure)

    def test_ignores_unrelated_fatal_process(self) -> None:
        system_log = "\n".join(
            [
                "09-05 12:00:00.000  1000  1000 I ActivityManager: Start proc 1234:app.organicmaps.incar/u0a123 for activity",
                "09-05 12:00:00.100  7777  7777 E AndroidRuntime: FATAL EXCEPTION: main",
            ]
        )

        failure = MODULE.has_failure_evidence(PACKAGE, "1234", "", system_log)

        self.assertIsNone(failure)

    def test_parse_timings_requires_total_and_wait(self) -> None:
        with self.assertRaises(MODULE.BaselineError):
            MODULE.parse_timings("Status: ok\nTotalTime: 123\n")

        self.assertEqual(
            {"TotalTime": 123, "WaitTime": 140},
            MODULE.parse_timings("Status: ok\nTotalTime: 123\nWaitTime: 140\n"),
        )


if __name__ == "__main__":
    unittest.main()
