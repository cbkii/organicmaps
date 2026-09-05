#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: smoke_test_in_car_warm_reentry.sh --package PACKAGE [options]

Validates a real same-process MAIN/LAUNCHER re-entry against an already installed
InCar package and fails on package/PID-correlated crash or ANR evidence.

Options:
  --proof-dir PATH      Evidence directory (default: temporary directory).
  --wait-seconds N      Settle time after each launch (default: 5; max: 30).
  --timeout-seconds N   Per-ADB-command timeout (default: 30; max: 120).
  --log-lines N         Bounded system logcat tail (default: 4000; max: 100000).
  -h, --help            Show this help.
USAGE
}

fail() {
  printf '::error::FAILED: %s\n' "$*" >&2
  exit 1
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "${value}" ]] || fail "${option} requires a value"
}

decimal_le() {
  local value="$1"
  local limit="$2"
  if [[ "${#value}" -lt "${#limit}" ]]; then
    return 0
  fi
  if [[ "${#value}" -gt "${#limit}" ]]; then
    return 1
  fi
  [[ "${value}" == "${limit}" || "${value}" < "${limit}" ]]
}

package_name=""
proof_dir=""
wait_seconds="5"
timeout_seconds="30"
log_lines="4000"
timeout_cmd=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)
      require_value "$1" "${2:-}"
      package_name="$2"
      shift 2
      ;;
    --proof-dir)
      require_value "$1" "${2:-}"
      proof_dir="$2"
      shift 2
      ;;
    --wait-seconds)
      require_value "$1" "${2:-}"
      wait_seconds="$2"
      shift 2
      ;;
    --timeout-seconds)
      require_value "$1" "${2:-}"
      timeout_seconds="$2"
      shift 2
      ;;
    --log-lines)
      require_value "$1" "${2:-}"
      log_lines="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "${package_name}" ]] || fail "--package is required"
[[ "${package_name}" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$ ]] ||
  fail "--package is not a valid Android package name: ${package_name}"
[[ "${wait_seconds}" =~ ^[1-9][0-9]*$ ]] || fail "--wait-seconds must be a positive integer"
decimal_le "${wait_seconds}" 30 || fail "--wait-seconds must be <= 30"
[[ "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]] || fail "--timeout-seconds must be a positive integer"
decimal_le "${timeout_seconds}" 120 || fail "--timeout-seconds must be <= 120"
[[ "${log_lines}" =~ ^[1-9][0-9]*$ ]] || fail "--log-lines must be a positive integer"
decimal_le "${log_lines}" 100000 || fail "--log-lines must be <= 100000"
command -v adb >/dev/null 2>&1 || fail "adb was not found"

if command -v timeout >/dev/null 2>&1; then
  timeout_cmd="$(command -v timeout)"
elif command -v gtimeout >/dev/null 2>&1; then
  timeout_cmd="$(command -v gtimeout)"
else
  fail "GNU timeout was not found (install coreutils or provide gtimeout)"
fi

run_bounded() {
  "${timeout_cmd}" --signal=TERM --kill-after=5s "${timeout_seconds}s" "$@"
}

if [[ -z "${proof_dir}" ]]; then
  base_tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
  proof_dir="$(mktemp -d "${base_tmp%/}/in-car-warm-reentry.XXXXXX")" || fail "Unable to create proof directory"
fi
mkdir -p -- "${proof_dir}" || fail "Unable to create proof directory: ${proof_dir}"

run_bounded adb get-state > "${proof_dir}/adb-state.txt" 2>&1
adb_state_rc=$?
[[ "${adb_state_rc}" -eq 0 ]] || fail "ADB device is not ready (rc=${adb_state_rc})"

run_bounded adb shell pm path "${package_name}" > "${proof_dir}/package-path.txt" 2>&1
package_rc=$?
[[ "${package_rc}" -eq 0 ]] || fail "Unable to inspect installed package ${package_name} (rc=${package_rc})"
grep -q '^package:' "${proof_dir}/package-path.txt" || fail "${package_name} is not installed"

run_bounded adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  "${package_name}" > "${proof_dir}/resolved-activity.txt" 2>&1
resolve_rc=$?
[[ "${resolve_rc}" -eq 0 ]] || fail "Unable to resolve launcher activity (rc=${resolve_rc})"
component="$(tr -d '\r' < "${proof_dir}/resolved-activity.txt" | tail -n1)"
[[ "${component}" == "${package_name}/"* ]] || fail "Unexpected launcher component: ${component:-not found}"

validate_launch() {
  local file="$1"
  local label="$2"
  if grep -Eq '^(Error:|Error type [0-9]+|Exception|Security exception:)' "${file}" ||
     ! tr -d '\r' < "${file}" | grep -Eq '^Status:[[:space:]]+ok[[:space:]]*$'; then
    sed -n '1,180p' "${file}" >&2
    fail "${label} did not report Status: ok"
  fi
}

run_bounded adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name}"
run_bounded adb logcat -c || fail "Unable to clear logcat before baseline launch"
run_bounded adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "${component}" \
  > "${proof_dir}/baseline-launch.txt" 2>&1
baseline_rc=$?
[[ "${baseline_rc}" -eq 0 ]] || fail "Baseline launcher start failed (rc=${baseline_rc})"
validate_launch "${proof_dir}/baseline-launch.txt" "Baseline launcher start"
sleep "${wait_seconds}"

run_bounded adb shell pidof -s "${package_name}" > "${proof_dir}/baseline-pid.txt" 2>/dev/null
pid_rc=$?
baseline_pid="$(tr -d '\r[:space:]' < "${proof_dir}/baseline-pid.txt")"
[[ "${pid_rc}" -eq 0 && "${baseline_pid}" =~ ^[0-9]+$ ]] || fail "Target process is not alive after baseline launch"

run_bounded adb logcat -c || fail "Unable to clear logcat before warm launcher re-entry"
run_bounded adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "${component}" \
  > "${proof_dir}/warm-launch.txt" 2>&1
warm_rc=$?
[[ "${warm_rc}" -eq 0 ]] || fail "Warm launcher re-entry failed (rc=${warm_rc})"
validate_launch "${proof_dir}/warm-launch.txt" "Warm launcher re-entry"
sleep "${wait_seconds}"

run_bounded adb shell pidof -s "${package_name}" > "${proof_dir}/warm-pid.txt" 2>/dev/null
pid_rc=$?
warm_pid="$(tr -d '\r[:space:]' < "${proof_dir}/warm-pid.txt")"
[[ "${pid_rc}" -eq 0 && "${warm_pid}" =~ ^[0-9]+$ ]] || fail "Target process died during warm launcher re-entry"
[[ "${warm_pid}" == "${baseline_pid}" ]] || fail "Warm launcher re-entry replaced process (${baseline_pid} -> ${warm_pid})"

run_bounded adb logcat -d -v threadtime --pid="${warm_pid}" -t "${log_lines}" > "${proof_dir}/logcat-app.txt" 2>&1 ||
  fail "Unable to capture PID-scoped logcat"
run_bounded adb logcat -d -v threadtime -t "${log_lines}" > "${proof_dir}/logcat-system.txt" 2>&1 ||
  fail "Unable to capture system logcat"

if grep -E 'FATAL EXCEPTION|Fatal signal [0-9]+ \((SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:' \
    "${proof_dir}/logcat-app.txt" >/dev/null; then
  tail -n 240 "${proof_dir}/logcat-app.txt" >&2
  fail "Crash evidence was found for ${package_name} during warm launcher re-entry"
fi

if grep -F "ANR in ${package_name}" "${proof_dir}/logcat-system.txt" >/dev/null ||
   grep -F 'am_anr' "${proof_dir}/logcat-system.txt" | grep -F "${package_name}" >/dev/null ||
   grep -F 'Input dispatching timed out' "${proof_dir}/logcat-system.txt" | grep -F "${package_name}" >/dev/null; then
  tail -n 300 "${proof_dir}/logcat-system.txt" >&2
  fail "ANR evidence was found for ${package_name} during warm launcher re-entry"
fi

if grep -E "tombstoned.*received crash request.*pid[ =:]${warm_pid}([^0-9]|$)" \
    "${proof_dir}/logcat-system.txt" > "${proof_dir}/tombstone-correlated.txt"; then
  cat "${proof_dir}/tombstone-correlated.txt" >&2
  fail "Tombstone evidence was found for ${package_name} pid ${warm_pid}"
fi

{
  printf 'package=%s\n' "${package_name}"
  printf 'component=%s\n' "${component}"
  printf 'baseline_pid=%s\n' "${baseline_pid}"
  printf 'warm_pid=%s\n' "${warm_pid}"
  printf 'same_pid=true\n'
  printf 'crash_scan=passed\n'
  printf 'anr_scan=passed\n'
} > "${proof_dir}/result.txt" || fail "Unable to write result evidence"

run_bounded adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name} after warm smoke"
printf 'SUCCESS: warm MAIN/LAUNCHER re-entry kept pid %s and produced no package-correlated crash/ANR evidence.\n' "${warm_pid}"
