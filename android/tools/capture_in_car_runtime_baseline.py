#!/usr/bin/env python3
"""Capture a bounded host-side ADB runtime baseline for the InCar APK.

The tool is read-only with respect to application/system data except for launching
and force-stopping the target package and clearing logcat between measured launch
windows. It does not request root or reset global batterystats/gfx state.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence

PACKAGE_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$")
INT_RE = re.compile(r"^[1-9][0-9]*$")
STATUS_OK_RE = re.compile(r"^Status:\s+ok\s*$", re.MULTILINE)
LAUNCH_ERROR_RE = re.compile(r"^(?:Error:|Error type [0-9]+|Exception|Security exception:)", re.MULTILINE)
TIMING_RE = re.compile(r"^(TotalTime|WaitTime):\s*([0-9]+)\s*$", re.MULTILINE)


class BaselineError(RuntimeError):
    """Required evidence could not be captured safely."""


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


class Capture:
    def __init__(self, root: Path, adb: str, timeout_seconds: int) -> None:
        self.root = root
        self.adb = adb
        self.timeout_seconds = timeout_seconds
        self.warnings: list[str] = []
        self.status = root / "capture-status.tsv"
        self.status.write_text("class\tname\ttimeout_seconds\trc\tcommand\n", encoding="utf-8")

    def run(self, name: str, args: Sequence[str], *, required: bool, output: Path | None = None) -> CommandResult:
        command = [self.adb, *args]
        operation_class = "required" if required else "optional"
        try:
            completed = subprocess.run(
                command,
                text=True,
                capture_output=True,
                timeout=self.timeout_seconds,
                check=False,
            )
            result = CommandResult(completed.returncode, completed.stdout, completed.stderr)
        except subprocess.TimeoutExpired as exc:
            stdout = exc.stdout if isinstance(exc.stdout, str) else ""
            stderr = exc.stderr if isinstance(exc.stderr, str) else ""
            result = CommandResult(124, stdout, stderr + f"\nTIMEOUT after {self.timeout_seconds}s\n")
        except OSError as exc:
            result = CommandResult(127, "", f"{type(exc).__name__}: {exc}\n")

        with self.status.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(
                f"{operation_class}\t{name}\t{self.timeout_seconds}\t{result.returncode}\t"
                f"{shlex.join(command)}\n"
            )

        if output is not None:
            output.write_text(result.stdout + result.stderr, encoding="utf-8")

        if result.returncode != 0:
            message = f"{name} failed with rc={result.returncode}"
            if required:
                raise BaselineError(message)
            self.warnings.append(message)
        return result


def bounded_positive(value: str, maximum: int, option: str) -> int:
    if not INT_RE.fullmatch(value):
        raise argparse.ArgumentTypeError(f"{option} must be a positive integer")
    number = int(value)
    if number > maximum:
        raise argparse.ArgumentTypeError(f"{option} must be <= {maximum}")
    return number


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", default="app.organicmaps.incar")
    parser.add_argument("--out", type=Path, default=Path("./ts18-runtime-baseline"))
    parser.add_argument("--cold-samples", default="3")
    parser.add_argument("--settle-seconds", default="5")
    parser.add_argument("--timeout-seconds", default="30")
    parser.add_argument("--log-lines", default="4000")
    args = parser.parse_args(argv)
    if not PACKAGE_RE.fullmatch(args.package):
        parser.error(f"invalid Android package name: {args.package}")
    try:
        args.cold_samples = bounded_positive(args.cold_samples, 10, "--cold-samples")
        args.settle_seconds = bounded_positive(args.settle_seconds, 30, "--settle-seconds")
        args.timeout_seconds = bounded_positive(args.timeout_seconds, 120, "--timeout-seconds")
        args.log_lines = bounded_positive(args.log_lines, 100000, "--log-lines")
    except argparse.ArgumentTypeError as exc:
        parser.error(str(exc))
    return args


def parse_timings(text: str) -> dict[str, int]:
    return {name: int(value) for name, value in TIMING_RE.findall(text)}


def check_launch_output(text: str, label: str) -> None:
    if LAUNCH_ERROR_RE.search(text) or not STATUS_OK_RE.search(text):
        raise BaselineError(f"{label} did not report Status: ok")


def has_failure_evidence(package: str, pid: str, app_log: str, system_log: str) -> str | None:
    if re.search(r"FATAL EXCEPTION|Fatal signal [0-9]+ \((?:SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:", app_log):
        return "fatal Java/native crash evidence"
    for line in system_log.splitlines():
        if "tombstoned" in line and "received crash request" in line and (pid in line or package in line):
            return "PID/package-correlated tombstone evidence"
        if package in line and ("ANR in " in line or "am_anr" in line or "Input dispatching timed out" in line):
            return "package-correlated ANR evidence"
    return None


def atomic_json(path: Path, payload: dict[str, object]) -> None:
    fd, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    temp = Path(temp_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, path)
    except (OSError, TypeError, ValueError):
        temp.unlink(missing_ok=True)
        raise


def capture_logs(capture: Capture, package: str, pid: str, prefix: str, log_lines: int) -> None:
    system_file = capture.root / f"{prefix}-logcat-system.txt"
    app_file = capture.root / f"{prefix}-logcat-app.txt"
    system = capture.run(
        f"{prefix}-logcat-system",
        ["logcat", "-d", "-v", "threadtime", "-t", str(log_lines)],
        required=True,
        output=system_file,
    )
    app = capture.run(
        f"{prefix}-logcat-app",
        ["logcat", "-d", "-v", "threadtime", f"--pid={pid}", "-t", str(log_lines)],
        required=False,
        output=app_file,
    )
    failure = has_failure_evidence(package, pid, app.stdout + app.stderr, system.stdout + system.stderr)
    if failure is not None:
        raise BaselineError(f"{prefix}: {failure}")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    adb = shutil.which("adb")
    if adb is None:
        print("FAILED: adb was not found", file=sys.stderr)
        return 1

    args.out.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    root = Path(tempfile.mkdtemp(prefix=f"{timestamp}-runtime-", dir=args.out))
    capture = Capture(root, adb, args.timeout_seconds)
    summary: dict[str, object] = {
        "schema_version": 1,
        "package": args.package,
        "host_utc": timestamp,
        "cold_samples": [],
    }

    try:
        state = capture.run("adb-state", ["get-state"], required=True, output=root / "adb-state.txt")
        if state.stdout.strip() != "device":
            raise BaselineError(f"ADB target is not ready: {state.stdout.strip() or 'unknown'}")
        package_path = capture.run(
            "package-path", ["shell", "pm", "path", args.package], required=True, output=root / "package-path.txt"
        )
        if not any(line.startswith("package:") for line in package_path.stdout.splitlines()):
            raise BaselineError(f"{args.package} is not installed in the inspected Android user scope")

        capture.run("identity", ["shell", "id"], required=True, output=root / "identity.txt")
        capture.run("selinux", ["shell", "getenforce"], required=False, output=root / "selinux.txt")
        capture.run("boot-id", ["shell", "cat", "/proc/sys/kernel/random/boot_id"], required=False,
                    output=root / "boot-id.txt")
        capture.run("uptime", ["shell", "cat", "/proc/uptime"], required=False, output=root / "uptime.txt")
        capture.run("build-sdk", ["shell", "getprop", "ro.build.version.sdk"], required=True,
                    output=root / "build-sdk.txt")
        capture.run("build-display", ["shell", "getprop", "ro.build.display.id"], required=False,
                    output=root / "build-display.txt")
        capture.run("wm-size", ["shell", "wm", "size"], required=True, output=root / "wm-size.txt")
        capture.run("wm-density", ["shell", "wm", "density"], required=False, output=root / "wm-density.txt")

        resolved = capture.run(
            "resolve-launcher",
            ["shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
             "-c", "android.intent.category.LAUNCHER", args.package],
            required=True,
            output=root / "resolved-launcher.txt",
        )
        component = resolved.stdout.strip().splitlines()[-1] if resolved.stdout.strip() else ""
        if not component.startswith(args.package + "/"):
            raise BaselineError(f"Unable to resolve launcher component: {component or 'not found'}")
        summary["launcher"] = component

        capture.run("gfxinfo-reset", ["shell", "dumpsys", "gfxinfo", args.package, "reset"], required=False,
                    output=root / "gfxinfo-reset.txt")

        current_pid = ""
        for index in range(1, args.cold_samples + 1):
            prefix = f"cold-{index}"
            capture.run(prefix + "-force-stop", ["shell", "am", "force-stop", args.package], required=True)
            capture.run(prefix + "-logcat-clear", ["logcat", "-c"], required=True)
            launched = capture.run(
                prefix + "-launch",
                ["shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
                 "android.intent.category.LAUNCHER", "-n", component],
                required=True,
                output=root / f"{prefix}-launch.txt",
            )
            check_launch_output(launched.stdout + launched.stderr, prefix)
            time.sleep(args.settle_seconds)
            pid_result = capture.run(prefix + "-pid", ["shell", "pidof", "-s", args.package], required=True,
                                     output=root / f"{prefix}-pid.txt")
            current_pid = pid_result.stdout.strip()
            if not current_pid.isdigit():
                raise BaselineError(f"{prefix}: target process is not alive")
            capture.run(prefix + "-meminfo", ["shell", "dumpsys", "meminfo", args.package], required=False,
                        output=root / f"{prefix}-meminfo.txt")
            capture_logs(capture, args.package, current_pid, prefix, args.log_lines)
            cast_samples = summary["cold_samples"]
            if not isinstance(cast_samples, list):
                raise BaselineError("Internal summary state is invalid")
            cast_samples.append({"sample": index, "pid": int(current_pid), **parse_timings(launched.stdout)})

        if not current_pid:
            raise BaselineError("No cold-launch PID was captured")
        capture.run("warm-logcat-clear", ["logcat", "-c"], required=True)
        warm = capture.run(
            "warm-launch",
            ["shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
             "android.intent.category.LAUNCHER", "-n", component],
            required=True,
            output=root / "warm-launch.txt",
        )
        check_launch_output(warm.stdout + warm.stderr, "warm-launch")
        time.sleep(args.settle_seconds)
        warm_pid_result = capture.run("warm-pid", ["shell", "pidof", "-s", args.package], required=True,
                                      output=root / "warm-pid.txt")
        warm_pid = warm_pid_result.stdout.strip()
        if warm_pid != current_pid:
            raise BaselineError(f"Warm launcher re-entry replaced the process ({current_pid} -> {warm_pid or 'none'})")
        capture.run("warm-meminfo", ["shell", "dumpsys", "meminfo", args.package], required=False,
                    output=root / "warm-meminfo.txt")
        capture_logs(capture, args.package, warm_pid, "warm", args.log_lines)
        summary["warm_reentry"] = {"pid": int(warm_pid), "same_pid": True, **parse_timings(warm.stdout)}

        capture.run("gfxinfo-framestats", ["shell", "dumpsys", "gfxinfo", args.package, "framestats"], required=False,
                    output=root / "gfxinfo-framestats.txt")
        capture.run("batterystats", ["shell", "dumpsys", "batterystats", args.package], required=False,
                    output=root / "batterystats.txt")
        capture.run("activity", ["shell", "dumpsys", "activity", "activities"], required=False,
                    output=root / "activity.txt")
        capture.run("process", ["shell", "ps", "-A", "-o", "USER,PID,PPID,NAME"], required=False,
                    output=root / "process.txt")

        summary["warnings"] = capture.warnings
        atomic_json(root / "summary.json", summary)
    except (BaselineError, OSError, ValueError) as exc:
        print(f"FAILED: {exc}; partial evidence retained at {root}", file=sys.stderr)
        return 1

    if capture.warnings:
        print(f"COMPLETED WITH WARNINGS: {root} ({len(capture.warnings)} warning(s))")
    else:
        print(f"SUCCESS: {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
