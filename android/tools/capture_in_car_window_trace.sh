#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: capture_in_car_window_trace.sh --label NAME [options]

Capture one read-only TS18/Android window-state snapshot after a user-performed transition.
Run it once for each labelled state/transition rather than leaving broad diagnostics running.

Options:
  --label NAME       Required short transition/state label.
  --package NAME     App package (default: app.organicmaps.incar).
  --out DIR          Export root (default: ./ts18-window-trace).
  --log-lines N      Tail this many logcat lines before filtering (default: 4000).
  -h, --help         Show this help.
USAGE
}

fail() {
  printf 'FAILED: %s\n' "$*" >&2
  exit 1
}

warn() {
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
[[ "${log_lines}" =~ ^[1-9][0-9]*$ ]] || fail "--log-lines must be a positive integer"
command -v adb >/dev/null 2>&1 || fail "adb was not found"

safe_label="$(printf '%s' "${label}" | tr -c 'A-Za-z0-9._-' '_')"
[[ -n "${safe_label}" ]] || fail "--label did not contain a usable filename character"

timestamp="$(date -u '+%Y%m%dT%H%M%SZ')" || fail "Unable to read host UTC time"
capture_dir="${output_root%/}/${timestamp}-${safe_label}"
mkdir -p -- "${capture_dir}" || fail "Unable to create ${capture_dir}"

if ! adb get-state > "${capture_dir}/adb-state.txt" 2>&1; then
  cat "${capture_dir}/adb-state.txt" >&2
  fail "ADB device is not ready"
fi

run_required() {
  local name="$1"
  shift
  local file="${capture_dir}/${name}.txt"
  local rc
  printf 'Capturing %s...\n' "${name}" >&2
  "$@" > "${file}" 2>&1
  rc=$?
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
  "$@" > "${file}" 2>&1
  rc=$?
  if [[ "${rc}" -ne 0 ]]; then
    printf 'optional_capture=%s rc=%d\n' "${name}" "${rc}" >> "${capture_dir}/warnings.txt"
    warn "Optional capture ${name} failed with rc=${rc}; continuing"
  fi
}

{
  printf 'host_utc=%s\n' "${timestamp}"
  printf 'label=%s\n' "${label}"
  printf 'package=%s\n' "${package_name}"
} > "${capture_dir}/manifest.txt" || fail "Unable to create capture manifest"

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
if adb logcat -d -v threadtime -t "${log_lines}" > "${logcat_raw}" 2>&1; then
  filtered_log="${capture_dir}/logcat-window-filtered.txt"
  grep -E 'InCarWindowGeometryCoordinator|InCarVisuals|MapView|ActivityTaskManager|WindowManager|SurfaceFlinger|app\.organicmaps' \
    "${logcat_raw}" > "${filtered_log}"
  grep_rc=$?
  if [[ "${grep_rc}" -gt 1 ]]; then
    warn "Optional logcat filtering failed with rc=${grep_rc}"
    printf 'optional_capture=logcat-filter rc=%d\n' "${grep_rc}" >> "${capture_dir}/warnings.txt"
  fi
else
  warn "Optional logcat capture failed"
  printf 'optional_capture=logcat rc=1\n' >> "${capture_dir}/warnings.txt"
fi

printf 'SUCCESS: %s\n' "${capture_dir}"
