#!/usr/bin/env bash
# Keep both connected-test results and retain crash evidence before AVD teardown.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
proof_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/organicmaps-sdk-connected"
if (( $# > 1 )) || [[ -n "${1:-}" && "${1:-}" != --with-in-car-app ]]; then
  printf 'Usage: %s [--with-in-car-app]\n' "${0##*/}" >&2
  exit 2
fi
mkdir -p "$proof_dir"
cd "$repo_root/android"

run_suite() {
  local task="$1" log="$2" result=0
  ./gradlew -Px86_64 "$task" || result=$?
  if (( result != 0 )); then
    timeout 15s adb logcat -d -v threadtime -b main -b system -b crash > "$proof_dir/$log" 2>&1 || true
    tail -n 400 "$proof_dir/$log" >&2 || true
  fi
  return "$result"
}

sdk_result=0
app_result=0
run_suite sdk:connectedDebugAndroidTest logcat.txt || sdk_result=$?
if [[ "${1:-}" == --with-in-car-app ]]; then
  run_suite app:connectedInCarDebugAndroidTest app-logcat.txt || app_result=$?
fi
printf 'SDK connected status: %s\n' "$sdk_result"
if [[ "${1:-}" == --with-in-car-app ]]; then
  printf 'InCar app connected status: %s\n' "$app_result"
fi
if (( sdk_result != 0 )); then
  exit "$sdk_result"
fi
exit "$app_result"
