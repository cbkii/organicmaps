#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: smoke_test_in_car_window_resize.sh --package PACKAGE [options]

Runs bounded emulator display-size changes against an already installed InCar package.
This validates process/lifecycle survival and records geometry logs. A clean CI image may still
be blocked by the World/WorldCoasts bootstrap before MwmActivity/MapView is reached.

Options:
  --proof-dir PATH      Evidence directory (default: temporary directory).
  --cycles N            Number of compact/full round trips (default: 3; max: 20).
  --timeout-seconds N   Per-ADB-command timeout (default: 45; max: 120).
  -h, --help            Show this help.
USAGE
}

fail() {
  printf '::error::FAILED: %s\n' "$*" >&2
  exit 1
}

warning_count=0
warn() {
  warning_count=$((warning_count + 1))
  printf '::warning::%s\n' "$*" >&2
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
cycles="3"
timeout_seconds="45"
max_cycles="20"
max_timeout_seconds="120"
original_override=""
window_size_captured="false"
timeout_cmd=""

run_bounded() {
  "${timeout_cmd}" --signal=TERM --kill-after=5s "${timeout_seconds}s" "$@"
}

restore_window_size() {
  local rc
  local verify_rc
  local restored_override
  if [[ "${window_size_captured}" != "true" || -z "${timeout_cmd}" ]]; then
    return
  fi

  if [[ -n "${original_override}" ]]; then
    run_bounded adb shell wm size "${original_override}" >/dev/null 2>&1
    rc=$?
  else
    run_bounded adb shell wm size reset >/dev/null 2>&1
    rc=$?
  fi
  if [[ "${rc}" -ne 0 ]]; then
    warn "Unable to restore the emulator display-size override (rc=${rc})"
    return
  fi

  run_bounded adb shell wm size > "${proof_dir}/wm-size-restore.txt" 2>&1
  verify_rc=$?
  if [[ "${verify_rc}" -ne 0 ]]; then
    warn "Unable to verify the restored emulator display size (rc=${verify_rc})"
    return
  fi
  restored_override="$(sed -n 's/^Override size:[[:space:]]*//p' "${proof_dir}/wm-size-restore.txt" | tail -n1 | tr -d '\r[:space:]')"
  if [[ "${restored_override}" != "${original_override}" ]]; then
    warn "Emulator display-size restore did not take effect (expected override '${original_override:-none}', got '${restored_override:-none}')"
    return
  fi
  window_size_captured="false"
}

handle_interrupt() {
  printf '::error::INTERRUPTED: window-resize smoke aborted; restoring display size on exit\n' >&2
  exit 130
}

trap restore_window_size EXIT
trap handle_interrupt INT TERM HUP

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
    --timeout-seconds)
      require_value "$1" "${2:-}"
      timeout_seconds="$2"
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
[[ "${cycles}" =~ ^[1-9][0-9]*$ ]] || fail "--cycles must be a positive integer"
decimal_le "${cycles}" "${max_cycles}" || fail "--cycles must be <= ${max_cycles}"
[[ "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]] || fail "--timeout-seconds must be a positive integer"
decimal_le "${timeout_seconds}" "${max_timeout_seconds}" || fail "--timeout-seconds must be <= ${max_timeout_seconds}"
command -v adb >/dev/null 2>&1 || fail "adb was not found"

if command -v timeout >/dev/null 2>&1; then
  timeout_cmd="$(command -v timeout)"
elif command -v gtimeout >/dev/null 2>&1; then
  timeout_cmd="$(command -v gtimeout)"
else
  fail "GNU timeout was not found (install coreutils or provide gtimeout)"
fi

if [[ -z "${proof_dir}" ]]; then
  base_tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
  proof_dir="$(mktemp -d "${base_tmp%/}/in-car-window-resize.XXXXXX")" ||
    fail "Unable to create a proof directory"
fi
mkdir -p -- "${proof_dir}" || fail "Unable to create proof directory: ${proof_dir}"

run_bounded adb get-state > "${proof_dir}/adb-state.txt" 2>&1
adb_state_rc=$?
[[ "${adb_state_rc}" -eq 0 ]] || fail "ADB device is not ready (rc=${adb_state_rc})"

package_path_file="${proof_dir}/package-path.txt"
run_bounded adb shell pm path "${package_name}" > "${package_path_file}" 2>&1
package_path_rc=$?
[[ "${package_path_rc}" -eq 0 ]] || fail "Unable to inspect installed package ${package_name} (rc=${package_path_rc})"
grep -q '^package:' "${package_path_file}" || fail "${package_name} is not installed"

{
  printf 'package=%s\n' "${package_name}"
  printf 'cycles=%s\n' "${cycles}"
  printf 'timeout_seconds=%s\n' "${timeout_seconds}"
  printf 'host_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > "${proof_dir}/manifest.txt" || fail "Unable to write smoke manifest"

size_before="${proof_dir}/wm-size-before.txt"
run_bounded adb shell wm size > "${size_before}" 2>&1 || fail "Unable to read emulator display size"
original_override="$(sed -n 's/^Override size:[[:space:]]*//p' "${size_before}" | tail -n1 | tr -d '\r[:space:]')"
physical_size="$(sed -n 's/^Physical size:[[:space:]]*//p' "${size_before}" | tail -n1 | tr -d '\r[:space:]')"
[[ "${physical_size}" =~ ^([1-9][0-9]*)x([1-9][0-9]*)$ ]] ||
  fail "Unable to parse emulator physical size: ${physical_size:-not found}"
physical_width="${BASH_REMATCH[1]}"
physical_height="${BASH_REMATCH[2]}"
if (( physical_width >= physical_height )); then
  landscape_width="${physical_width}"
  landscape_height="${physical_height}"
else
  landscape_width="${physical_height}"
  landscape_height="${physical_width}"
fi
compact_width=$((landscape_width * 3 / 4))
compact_height=$((landscape_height * 3 / 4))
(( compact_width > 0 && compact_height > 0 )) || fail "Unable to derive a compact emulator resize target"
compact_target="${compact_width}x${compact_height}"
full_target="${landscape_width}x${landscape_height}"
[[ "${compact_target}" != "${full_target}" ]] || fail "Compact and full emulator resize targets are identical"
{
  printf 'physical_size=%s\n' "${physical_size}"
  printf 'compact_target=%s\n' "${compact_target}"
  printf 'full_target=%s\n' "${full_target}"
} >> "${proof_dir}/manifest.txt" || fail "Unable to record resize targets"
window_size_captured="true"

resolve_file="${proof_dir}/resolved-activity.txt"
run_bounded adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  "${package_name}" > "${resolve_file}" 2>&1
resolve_rc=$?
[[ "${resolve_rc}" -eq 0 ]] || fail "Unable to resolve launcher activity for ${package_name} (rc=${resolve_rc})"
component="$(tr -d '\r' < "${resolve_file}" | tail -n1)"
[[ "${component}" == "${package_name}/"* ]] ||
  fail "Unable to resolve launcher activity for ${package_name}: ${component:-not found}"

run_bounded adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name}"
run_bounded adb logcat -c || fail "Unable to clear logcat"
run_bounded adb shell am start -W -n "${component}" > "${proof_dir}/launch.txt" 2>&1
launch_rc=$?
if [[ "${launch_rc}" -ne 0 ]] ||
   grep -Eq '^(Error:|Error type [0-9]+|Exception|Security exception:)' "${proof_dir}/launch.txt" ||
   ! tr -d '\r' < "${proof_dir}/launch.txt" | grep -Eq '^Status:[[:space:]]+ok[[:space:]]*$'; then
  sed -n '1,160p' "${proof_dir}/launch.txt" >&2
  fail "Unable to launch ${package_name} successfully (rc=${launch_rc})"
fi
sleep 3

pid_file="${proof_dir}/pid.txt"
run_bounded adb shell pidof -s "${package_name}" > "${pid_file}" 2>/dev/null
pid_rc=$?
pid="$(tr -d '\r[:space:]' < "${pid_file}")"
[[ "${pid_rc}" -eq 0 && -n "${pid}" ]] || fail "${package_name} is not alive before window-resize smoke"
initial_pid="${pid}"

has_crash_evidence() {
  local file="$1"
  grep -E 'FATAL EXCEPTION|Fatal signal [0-9]+ \((SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:' \
    "${file}" >/dev/null
}

capture_failure_logcat() {
  run_bounded adb logcat -d -v threadtime > "${proof_dir}/logcat-failure.txt" 2>&1
  local rc=$?
  if [[ "${rc}" -ne 0 ]]; then
    warn "Unable to capture failure logcat (rc=${rc})"
  fi
}

cycle=1
while [[ "${cycle}" -le "${cycles}" ]]; do
  for target in "${compact_target}" "${full_target}"; do
    evidence="${proof_dir}/cycle-${cycle}-${target}.txt"
    printf 'cycle=%s target=%s\n' "${cycle}" "${target}" > "${evidence}" || fail "Unable to write resize evidence"

    run_bounded adb shell wm size "${target}" >> "${evidence}" 2>&1
    resize_rc=$?
    if [[ "${resize_rc}" -ne 0 ]]; then
      sed -n '1,160p' "${evidence}" >&2
      fail "Unable to apply emulator size ${target} (rc=${resize_rc})"
    fi
    sleep 2

    size_after="${proof_dir}/wm-size-cycle-${cycle}-${target}.txt"
    run_bounded adb shell wm size > "${size_after}" 2>&1 || fail "Unable to read size after ${target}"
    cat "${size_after}" >> "${evidence}" || fail "Unable to record size after ${target}"
    applied_override="$(sed -n 's/^Override size:[[:space:]]*//p' "${size_after}" | tail -n1 | tr -d '\r[:space:]')"
    if [[ "${applied_override}" != "${target}" ]]; then
      sed -n '1,160p' "${size_after}" >&2
      fail "Emulator size ${target} was not applied (override=${applied_override:-none})"
    fi

    run_bounded adb shell dumpsys window displays >> "${evidence}" 2>&1 || fail "Unable to read window display state"
    run_bounded adb shell dumpsys activity activities >> "${evidence}" 2>&1 || fail "Unable to read Activity state"

    run_bounded adb shell pidof -s "${package_name}" > "${pid_file}" 2>/dev/null
    pid_rc=$?
    pid="$(tr -d '\r[:space:]' < "${pid_file}")"
    if [[ "${pid_rc}" -ne 0 || -z "${pid}" ]]; then
      capture_failure_logcat
      fail "${package_name} died after emulator size ${target}"
    fi
    if [[ "${pid}" != "${initial_pid}" ]]; then
      capture_failure_logcat
      fail "${package_name} changed pid during resize smoke (${initial_pid} -> ${pid})"
    fi
  done
  cycle=$((cycle + 1))
done

run_bounded adb logcat -d -v threadtime > "${proof_dir}/logcat-system.txt" 2>&1 || fail "Unable to capture resize logcat"
grep -E 'InCarWindowGeometryCoordinator|InCarVisuals|MapView|ActivityTaskManager|WindowManager|app\.organicmaps' \
  "${proof_dir}/logcat-system.txt" > "${proof_dir}/logcat-window-filtered.txt"
grep_rc=$?
if [[ "${grep_rc}" -gt 1 ]]; then
  fail "Unable to filter resize logcat"
fi

run_bounded adb logcat -d -v threadtime --pid="${initial_pid}" > "${proof_dir}/logcat-app.txt" 2>&1 ||
  fail "Unable to capture app-scoped resize logcat"
if has_crash_evidence "${proof_dir}/logcat-app.txt"; then
  tail -n 300 "${proof_dir}/logcat-app.txt" >&2
  fail "Crash evidence for ${package_name} was found during window-resize smoke"
fi

tombstone_file="${proof_dir}/tombstone-correlated.txt"
grep -E "tombstoned.*received crash request.*pid[ =:]${initial_pid}([^0-9]|$)" \
  "${proof_dir}/logcat-system.txt" > "${tombstone_file}"
tombstone_grep_rc=$?
if [[ "${tombstone_grep_rc}" -gt 1 ]]; then
  fail "Unable to correlate tombstone evidence"
fi
if [[ "${tombstone_grep_rc}" -eq 0 ]]; then
  cat "${tombstone_file}" >&2
  fail "Tombstone evidence for ${package_name} pid ${initial_pid} was found during window-resize smoke"
fi

if grep -E 'InCarWindowGeometryCoordinator.*Window geometry converged' "${proof_dir}/logcat-window-filtered.txt" >/dev/null; then
  printf 'geometry_runtime=map-surface-path-observed\n' > "${proof_dir}/result.txt"
else
  printf 'geometry_runtime=process-survival-only; map path may be blocked by clean-install resource bootstrap\n' \
    > "${proof_dir}/result.txt"
fi

run_bounded adb shell am force-stop "${package_name}" >/dev/null 2>&1 || fail "Unable to force-stop ${package_name} after resize smoke"
restore_window_size
if [[ "${window_size_captured}" == "true" ]]; then
  fail "Unable to restore the emulator display size; EXIT cleanup will retry"
fi
trap - EXIT
if [[ "${warning_count}" -gt 0 ]]; then
  printf 'COMPLETED WITH WARNINGS: %s resize round trips completed with pid %s (%d warning(s)).\n' \
    "${cycles}" "${initial_pid}" "${warning_count}"
else
  printf 'SUCCESS: %s resize round trips completed with pid %s.\n' "${cycles}" "${initial_pid}"
fi
