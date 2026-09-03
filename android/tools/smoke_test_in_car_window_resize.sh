#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: smoke_test_in_car_window_resize.sh --package PACKAGE [options]

Runs bounded emulator display-size changes against an already installed InCar package.
This validates process/lifecycle survival and records geometry logs. A clean CI image may still
be blocked by the World/WorldCoasts bootstrap before MwmActivity/MapView is reached.

Options:
  --proof-dir PATH   Evidence directory (default: temporary directory).
  --cycles N         Number of compact/full round trips (default: 3).
  -h, --help         Show this help.
USAGE
}

fail() {
  printf '::error::%s\n' "$*" >&2
  exit 1
}

warn() {
  printf '::warning::%s\n' "$*" >&2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "${value}" ]] || fail "${option} requires a value"
}

package_name=""
proof_dir=""
cycles="3"
original_override=""
window_size_captured="false"

restore_window_size() {
  local rc
  if [[ "${window_size_captured}" != "true" ]]; then
    return
  fi

  if [[ -n "${original_override}" ]]; then
    adb shell wm size "${original_override}" >/dev/null 2>&1
    rc=$?
  else
    adb shell wm size reset >/dev/null 2>&1
    rc=$?
  fi
  if [[ "${rc}" -ne 0 ]]; then
    warn "Unable to restore the emulator display-size override"
  fi
}

trap restore_window_size EXIT

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
    --cycles)
      require_value "$1" "${2:-}"
      cycles="$2"
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
[[ "${cycles}" =~ ^[1-9][0-9]*$ ]] || fail "--cycles must be a positive integer"
command -v adb >/dev/null 2>&1 || fail "adb was not found"

if [[ -z "${proof_dir}" ]]; then
  base_tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
  proof_dir="$(mktemp -d "${base_tmp%/}/in-car-window-resize.XXXXXX")" ||
    fail "Unable to create a proof directory"
fi
mkdir -p -- "${proof_dir}" || fail "Unable to create proof directory: ${proof_dir}"

adb get-state > "${proof_dir}/adb-state.txt" 2>&1 || fail "ADB device is not ready"

size_before="${proof_dir}/wm-size-before.txt"
adb shell wm size > "${size_before}" 2>&1 || fail "Unable to read emulator display size"
original_override="$(sed -n 's/^Override size:[[:space:]]*//p' "${size_before}" | tail -n1 | tr -d '\r[:space:]')"
window_size_captured="true"

component="$(
  adb shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    "${package_name}" 2>/dev/null \
    | tr -d '\r' \
    | tail -n1
)"
[[ "${component}" == "${package_name}/"* ]] ||
  fail "Unable to resolve launcher activity for ${package_name}: ${component:-not found}"

adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name}"
adb logcat -c || fail "Unable to clear logcat"
if ! adb shell am start -W -n "${component}" > "${proof_dir}/launch.txt" 2>&1; then
  sed -n '1,160p' "${proof_dir}/launch.txt" >&2
  fail "Unable to launch ${package_name}"
fi
sleep 3

pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r[:space:]')"
[[ -n "${pid}" ]] || fail "${package_name} is not alive before window-resize smoke"
initial_pid="${pid}"

has_crash_evidence() {
  local file="$1"
  grep -E 'FATAL EXCEPTION|Fatal signal [0-9]+ \((SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:|tombstoned.*received crash request' \
    "${file}" >/dev/null
}

cycle=1
while [[ "${cycle}" -le "${cycles}" ]]; do
  for target in 960x540 1280x720; do
    evidence="${proof_dir}/cycle-${cycle}-${target}.txt"
    printf 'cycle=%s target=%s\n' "${cycle}" "${target}" > "${evidence}" || fail "Unable to write resize evidence"

    if ! adb shell wm size "${target}" >> "${evidence}" 2>&1; then
      sed -n '1,160p' "${evidence}" >&2
      fail "Unable to apply emulator size ${target}"
    fi
    sleep 2

    adb shell wm size >> "${evidence}" 2>&1 || fail "Unable to read size after ${target}"
    adb shell dumpsys window displays >> "${evidence}" 2>&1 || fail "Unable to read window display state"
    adb shell dumpsys activity activities >> "${evidence}" 2>&1 || fail "Unable to read Activity state"

    pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r[:space:]')"
    if [[ -z "${pid}" ]]; then
      adb logcat -d -v threadtime > "${proof_dir}/logcat-failure.txt" 2>&1
      fail "${package_name} died after emulator size ${target}"
    fi
    if [[ "${pid}" != "${initial_pid}" ]]; then
      adb logcat -d -v threadtime > "${proof_dir}/logcat-failure.txt" 2>&1
      fail "${package_name} changed pid during resize smoke (${initial_pid} -> ${pid})"
    fi
  done
  cycle=$((cycle + 1))
done

adb logcat -d -v threadtime > "${proof_dir}/logcat-system.txt" 2>&1 || fail "Unable to capture resize logcat"
grep -E 'InCarWindowGeometryCoordinator|InCarVisuals|MapView|ActivityTaskManager|WindowManager|app\.organicmaps' \
  "${proof_dir}/logcat-system.txt" > "${proof_dir}/logcat-window-filtered.txt"
grep_rc=$?
if [[ "${grep_rc}" -gt 1 ]]; then
  fail "Unable to filter resize logcat"
fi
if has_crash_evidence "${proof_dir}/logcat-system.txt"; then
  tail -n 300 "${proof_dir}/logcat-system.txt" >&2
  fail "Crash evidence was found during window-resize smoke"
fi

if grep -E 'InCarWindowGeometryCoordinator.*Window geometry converged' "${proof_dir}/logcat-window-filtered.txt" >/dev/null; then
  printf 'geometry_runtime=map-surface-path-observed\n' > "${proof_dir}/result.txt"
else
  printf 'geometry_runtime=process-survival-only; map path may be blocked by clean-install resource bootstrap\n' \
    > "${proof_dir}/result.txt"
fi

adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name} after resize smoke"
printf 'SUCCESS: %s resize round trips completed with pid %s.\n' "${cycles}" "${initial_pid}"
