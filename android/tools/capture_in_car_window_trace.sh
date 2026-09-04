#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: capture_in_car_window_trace.sh --label NAME [options]

Capture one read-only TS18/Android window-state snapshot after a user-performed transition.
Run it once for each labelled state/transition rather than leaving broad diagnostics running.

Options:
  --label NAME          Required short transition/state label.
  --package NAME        App package (default: app.organicmaps.incar).
  --out DIR             Export root (default: ./ts18-window-trace).
  --log-lines N         Tail this many logcat lines (default: 4000; max: 100000).
  --timeout-seconds N   Per-command timeout (default: 30; max: 120).
  -h, --help            Show this help.
USAGE
}

fail() {
  printf 'FAILED: %s\n' "$*" >&2
  exit 1
}

warning_count=0
warn() {
  warning_count=$((warning_count + 1))
  printf 'WARNING: %s\n' "$*" >&2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "${value}" ]] || fail "${option} requires a value"
}

package_name="app.organicmaps.incar"
output_root="./ts18-window-trace"
label=""
log_lines="4000"
timeout_seconds="30"
max_log_lines="100000"
max_timeout_seconds="120"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --label)
      require_value "$1" "${2:-}"
      label="$2"
      shift 2
      ;;
    --package)
      require_value "$1" "${2:-}"
      package_name="$2"
      shift 2
      ;;
    --out)
      require_value "$1" "${2:-}"
      output_root="$2"
      shift 2
      ;;
    --log-lines)
      require_value "$1" "${2:-}"
      log_lines="$2"
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

[[ -n "${label}" ]] || fail "--label is required"
[[ "${package_name}" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$ ]] ||
  fail "--package is not a valid Android package name: ${package_name}"
[[ "${log_lines}" =~ ^[1-9][0-9]*$ ]] || fail "--log-lines must be a positive integer"
(( log_lines <= max_log_lines )) || fail "--log-lines must be <= ${max_log_lines}"
[[ "${timeout_seconds}" =~ ^[1-9][0-9]*$ ]] || fail "--timeout-seconds must be a positive integer"
(( timeout_seconds <= max_timeout_seconds )) || fail "--timeout-seconds must be <= ${max_timeout_seconds}"
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

safe_label="$(printf '%s' "${label}" | tr -c 'A-Za-z0-9._-' '_')"
[[ -n "${safe_label}" ]] || fail "--label did not contain a usable filename character"

timestamp="$(date -u '+%Y%m%dT%H%M%SZ')" || fail "Unable to read host UTC time"
capture_dir="${output_root%/}/${timestamp}-${safe_label}"
mkdir -p -- "${capture_dir}" || fail "Unable to create ${capture_dir}"
status_file="${capture_dir}/capture-status.tsv"
printf 'class\tname\ttimeout_seconds\trc\tcommand\n' > "${status_file}" || fail "Unable to create capture status"

record_status() {
  local operation_class="$1"
  local name="$2"
  local rc="$3"
  shift 3
  local command_text
  printf -v command_text '%q ' "$@"
  printf '%s\t%s\t%s\t%s\t%s\n' "${operation_class}" "${name}" "${timeout_seconds}" "${rc}" "${command_text}" \
    >> "${status_file}" || warn "Unable to append capture status for ${name}"
}

run_bounded adb get-state > "${capture_dir}/adb-state.txt" 2>&1
adb_state_rc=$?
record_status required adb-state "${adb_state_rc}" adb get-state
if [[ "${adb_state_rc}" -ne 0 ]]; then
  cat "${capture_dir}/adb-state.txt" >&2
  fail "ADB device is not ready (rc=${adb_state_rc})"
fi

run_required() {
  local name="$1"
  shift
  local file="${capture_dir}/${name}.txt"
  local rc
  printf 'Capturing %s...\n' "${name}" >&2
  run_bounded "$@" > "${file}" 2>&1
  rc=$?
  record_status required "${name}" "${rc}" "$@"
  if [[ "${rc}" -ne 0 ]]; then
    sed -n '1,120p' "${file}" >&2
    fail "Required capture ${name} failed with rc=${rc}"
  fi
}

run_optional() {
  local name="$1"
  shift
  local file="${capture_dir}/${name}.txt"
  local rc
  printf 'Capturing %s...\n' "${name}" >&2
  run_bounded "$@" > "${file}" 2>&1
  rc=$?
  record_status optional "${name}" "${rc}" "$@"
  if [[ "${rc}" -ne 0 ]]; then
    printf 'optional_capture=%s rc=%d\n' "${name}" "${rc}" >> "${capture_dir}/warnings.txt"
    warn "Optional capture ${name} failed with rc=${rc}; continuing"
  fi
}

{
  printf 'host_utc=%s\n' "${timestamp}"
  printf 'label=%s\n' "${label}"
  printf 'package=%s\n' "${package_name}"
  printf 'timeout_seconds=%s\n' "${timeout_seconds}"
  printf 'log_lines=%s\n' "${log_lines}"
} > "${capture_dir}/manifest.txt" || fail "Unable to create capture manifest"

run_required adb-serial adb get-serialno
run_required package-path adb shell pm path "${package_name}"
if ! grep -q '^package:' "${capture_dir}/package-path.txt"; then
  fail "Package ${package_name} is not installed in the inspected Android user scope"
fi
run_required device-identity adb shell sh -c 'printf "utc="; date -u "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date; id; printf "selinux="; getenforce 2>/dev/null || printf "unknown\n"; printf "boot_id="; cat /proc/sys/kernel/random/boot_id 2>/dev/null || printf "unknown\n"; printf "uptime="; cat /proc/uptime'
run_required build adb shell sh -c 'for key in ro.build.display.id ro.build.version.release ro.build.version.sdk ro.product.manufacturer ro.product.model ro.product.device; do printf "%s=" "$key"; getprop "$key"; done'
run_required wm-size adb shell wm size
run_required wm-density adb shell wm density
run_required activity adb shell dumpsys activity activities
run_required window-displays adb shell dumpsys window displays
run_required window-windows adb shell dumpsys window windows
run_optional surface-layers adb shell dumpsys SurfaceFlinger --list
run_optional surface-organicmaps adb shell sh -c "dumpsys SurfaceFlinger 2>/dev/null | grep -i -A 16 -B 6 '${package_name}'"
run_optional process adb shell sh -c "pidof -s '${package_name}'; ps -A -o USER,PID,PPID,NAME 2>/dev/null | grep '${package_name}'"

logcat_raw="${capture_dir}/logcat-tail.txt"
run_bounded adb logcat -d -v threadtime -t "${log_lines}" > "${logcat_raw}" 2>&1
logcat_rc=$?
record_status optional logcat "${logcat_rc}" adb logcat -d -v threadtime -t "${log_lines}"
if [[ "${logcat_rc}" -eq 0 ]]; then
  filtered_log="${capture_dir}/logcat-window-filtered.txt"
  grep -E 'InCarWindowGeometryCoordinator|InCarVisuals|MapView|ActivityTaskManager|WindowManager|SurfaceFlinger|app\.organicmaps' \
    "${logcat_raw}" > "${filtered_log}"
  grep_rc=$?
  if [[ "${grep_rc}" -gt 1 ]]; then
    warn "Optional logcat filtering failed with rc=${grep_rc}"
    printf 'optional_capture=logcat-filter rc=%d\n' "${grep_rc}" >> "${capture_dir}/warnings.txt"
  fi
else
  warn "Optional logcat capture failed with rc=${logcat_rc}"
  printf 'optional_capture=logcat rc=%d\n' "${logcat_rc}" >> "${capture_dir}/warnings.txt"
fi

if [[ "${warning_count}" -gt 0 ]]; then
  printf 'COMPLETED WITH WARNINGS: %s (%d warning(s))\n' "${capture_dir}" "${warning_count}"
else
  printf 'SUCCESS: %s\n' "${capture_dir}"
fi
