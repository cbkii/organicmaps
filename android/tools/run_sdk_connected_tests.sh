#!/usr/bin/env bash
# Keep the real connected-test result and retain native crash evidence before AVD teardown.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
proof_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/organicmaps-sdk-connected"
mkdir -p "$proof_dir"
capture_failure() {
  result=$?
  trap - EXIT
  if (( result != 0 )); then
    timeout 15s adb logcat -d -v threadtime -b main -b system -b crash > "$proof_dir/logcat.txt" 2>&1 || true
    tail -n 400 "$proof_dir/logcat.txt" >&2 || true
  fi
  exit "$result"
}
trap capture_failure EXIT
cd "$repo_root/android"
./gradlew -Px86_64 sdk:connectedDebugAndroidTest
