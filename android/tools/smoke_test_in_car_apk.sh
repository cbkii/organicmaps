#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: smoke_test_in_car_apk.sh --apk PATH --package PACKAGE [options]

Options:
  --wait-seconds SECONDS   Time each launched process must remain alive (default: 10)
  --proof-dir PATH         Directory for install, launch and logcat evidence
  --skip-route-entry       Skip the landscape route-plan entry regression smoke
USAGE
}

fail() {
  printf '::error::%s\n' "$*" >&2
  exit 1
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "${value}" ]] || fail "${option} requires a value."
}

apk=""
package_name=""
wait_seconds="10"
proof_dir=""
route_entry_smoke="true"
original_accelerometer_rotation=""
original_user_rotation=""
rotation_settings_captured="false"

restore_rotation_settings() {
  if [[ "${rotation_settings_captured}" != "true" ]]; then
    return
  fi

  if [[ -z "${original_accelerometer_rotation}" || "${original_accelerometer_rotation}" == "null" ]]; then
    adb shell settings delete system accelerometer_rotation >/dev/null 2>&1 || true
  else
    adb shell settings put system accelerometer_rotation "${original_accelerometer_rotation}" >/dev/null 2>&1 || true
  fi

  if [[ -z "${original_user_rotation}" || "${original_user_rotation}" == "null" ]]; then
    adb shell settings delete system user_rotation >/dev/null 2>&1 || true
  else
    adb shell settings put system user_rotation "${original_user_rotation}" >/dev/null 2>&1 || true
  fi
}

trap restore_rotation_settings EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      require_value "$1" "${2:-}"
      apk="$2"
      shift 2
      ;;
    --package)
      require_value "$1" "${2:-}"
      package_name="$2"
      shift 2
      ;;
    --wait-seconds)
      require_value "$1" "${2:-}"
      wait_seconds="$2"
      shift 2
      ;;
    --proof-dir)
      require_value "$1" "${2:-}"
      proof_dir="$2"
      shift 2
      ;;
    --skip-route-entry)
      route_entry_smoke="false"
      shift
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

[[ -n "${apk}" ]] || fail "--apk is required."
[[ -f "${apk}" ]] || fail "APK not found: ${apk}"
[[ -n "${package_name}" ]] || fail "--package is required."
[[ "${wait_seconds}" =~ ^[1-9][0-9]*$ ]] || fail "--wait-seconds must be a positive integer."
command -v adb >/dev/null 2>&1 || fail "adb was not found."

if [[ -z "${proof_dir}" ]]; then
  base_tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
  proof_dir="$(mktemp -d "${base_tmp%/}/in-car-launch-smoke.XXXXXX")" ||
    fail "Unable to create a smoke-test proof directory."
fi
mkdir -p "${proof_dir}" || fail "Unable to create proof directory: ${proof_dir}"

adb wait-for-device || fail "No Android test device became available."

installed_path="$(adb shell pm path "${package_name}" 2>/dev/null | tr -d '\r')"
if [[ -n "${installed_path}" ]]; then
  adb uninstall "${package_name}" > "${proof_dir}/uninstall.log" 2>&1 ||
    fail "Unable to remove the previously installed ${package_name}."
fi

if ! adb install --no-streaming "${apk}" > "${proof_dir}/install.log" 2>&1; then
  sed -n '1,200p' "${proof_dir}/install.log" >&2
  fail "Unable to install the InCar APK."
fi

# Organic Maps requests location immediately on a clean install. Pre-grant it so an Android permission-controller
# Activity cannot survive the app force-stop between launch attempts and turn the relaunch check into a UI-dialog test.
# Application.onCreate() still runs from a clean data state, which is the lifecycle boundary this smoke test targets.
permission_log="${proof_dir}/permissions.log"
: > "${permission_log}" || fail "Unable to create permission evidence log."
for permission in android.permission.ACCESS_COARSE_LOCATION android.permission.ACCESS_FINE_LOCATION; do
  if ! adb shell pm grant "${package_name}" "${permission}" >> "${permission_log}" 2>&1; then
    sed -n '1,200p' "${permission_log}" >&2
    fail "Unable to grant ${permission} before launch smoke."
  fi
done

component="$(
  adb shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    "${package_name}" 2>/dev/null \
    | tr -d '\r' \
    | tail -n1
)"
[[ "${component}" == "${package_name}/"* ]] ||
  fail "Unable to resolve the launcher activity for ${package_name}: ${component:-not found}"

has_crash_evidence() {
  local app_logcat_file="$1"
  local system_logcat_file="$2"

  grep -E \
      'FATAL EXCEPTION|Fatal signal [0-9]+ \((SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:' \
      "${app_logcat_file}" >/dev/null ||
    grep -E 'tombstoned.*received crash request' "${system_logcat_file}" >/dev/null
}

launch_once() {
  local attempt="$1"
  local launch_log="${proof_dir}/launch-${attempt}.txt"
  local app_logcat_file="${proof_dir}/logcat-app-${attempt}.txt"
  local system_logcat_file="${proof_dir}/logcat-system-${attempt}.txt"
  local pid

  adb shell am force-stop "${package_name}" ||
    fail "Unable to force-stop ${package_name} before launch ${attempt}."
  adb logcat -c || fail "Unable to clear logcat before launch ${attempt}."

  if ! adb shell am start -W -n "${component}" > "${launch_log}" 2>&1; then
    sed -n '1,200p' "${launch_log}" >&2
    adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 || true
    fail "am start failed for launch ${attempt}."
  fi

  if grep -Eq '^Error:|^Error type [0-9]+|^Exception' "${launch_log}" ||
     ! grep -Eq '^Status:[[:space:]]+ok$' "${launch_log}"; then
    sed -n '1,200p' "${launch_log}" >&2
    adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 || true
    fail "am start did not report a successful launch for attempt ${attempt}."
  fi

  sleep "${wait_seconds}"
  pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r[:space:]')"
  if [[ -z "${pid}" ]]; then
    adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 || true
    sed -n '1,200p' "${launch_log}" >&2
    tail -n 300 "${system_logcat_file}" >&2
    fail "${package_name} did not remain alive after launch ${attempt}."
  fi

  adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 ||
    fail "Unable to capture system logcat after launch ${attempt}."
  if ! adb logcat -d -v threadtime --pid="${pid}" > "${app_logcat_file}" 2>&1; then
    cp "${system_logcat_file}" "${app_logcat_file}" ||
      fail "Unable to preserve fallback logcat after launch ${attempt}."
  fi

  if has_crash_evidence "${app_logcat_file}" "${system_logcat_file}"; then
    tail -n 300 "${system_logcat_file}" >&2
    fail "Crash evidence was found after launch ${attempt}."
  fi

  printf 'Launch %s remained alive as pid %s for %s seconds.\n' "${attempt}" "${pid}" "${wait_seconds}"
}

route_entry_once() {
  local route_uri='om://route?sll=-35.2809,149.1300&saddr=Start&dll=-35.2819,149.1320&daddr=Destination&type=vehicle'
  local route_launch_log="${proof_dir}/route-entry-launch.txt"
  local app_logcat_file="${proof_dir}/logcat-app-route-entry.txt"
  local system_logcat_file="${proof_dir}/logcat-system-route-entry.txt"
  local window_log="${proof_dir}/route-entry-window.txt"
  local before_pid
  local after_pid

  before_pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r[:space:]')"
  [[ -n "${before_pid}" ]] || fail "${package_name} was not alive before route-entry smoke."

  original_accelerometer_rotation="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r[:space:]')"
  original_user_rotation="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r[:space:]')"
  rotation_settings_captured="true"

  adb shell settings put system accelerometer_rotation 0 ||
    fail "Unable to disable emulator auto-rotation for landscape route-entry smoke."
  adb shell settings put system user_rotation 1 ||
    fail "Unable to request landscape rotation for route-entry smoke."
  sleep 2

  adb logcat -c || fail "Unable to clear logcat before route-entry smoke."
  if ! adb shell "am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d '${route_uri}' -p '${package_name}'" \
      > "${route_launch_log}" 2>&1; then
    sed -n '1,200p' "${route_launch_log}" >&2
    adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 || true
    fail "Unable to start the InCar route deep link."
  fi

  if grep -Eq '^Error:|^Error type [0-9]+|^Exception' "${route_launch_log}" ||
     ! grep -Eq '^Status:[[:space:]]+ok$' "${route_launch_log}"; then
    sed -n '1,200p' "${route_launch_log}" >&2
    adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 || true
    fail "Route-entry deep link did not report a successful launch."
  fi

  sleep "${wait_seconds}"
  after_pid="$(adb shell pidof -s "${package_name}" 2>/dev/null | tr -d '\r[:space:]')"
  adb logcat -d -v threadtime > "${system_logcat_file}" 2>&1 ||
    fail "Unable to capture system logcat after route-entry smoke."
  if [[ -n "${after_pid}" ]] && ! adb logcat -d -v threadtime --pid="${after_pid}" > "${app_logcat_file}" 2>&1; then
    cp "${system_logcat_file}" "${app_logcat_file}" ||
      fail "Unable to preserve fallback route-entry logcat."
  elif [[ -z "${after_pid}" ]]; then
    cp "${system_logcat_file}" "${app_logcat_file}" || true
  fi
  adb shell dumpsys window windows > "${window_log}" 2>&1 || true

  if [[ -z "${after_pid}" ]]; then
    tail -n 300 "${system_logcat_file}" >&2
    fail "${package_name} died during landscape route entry."
  fi
  if [[ "${after_pid}" != "${before_pid}" ]]; then
    tail -n 300 "${system_logcat_file}" >&2
    fail "${package_name} changed pid during landscape route entry (${before_pid} -> ${after_pid})."
  fi
  if has_crash_evidence "${app_logcat_file}" "${system_logcat_file}"; then
    tail -n 300 "${system_logcat_file}" >&2
    fail "Crash evidence was found during landscape route entry."
  fi

  printf 'Landscape route entry remained alive as pid %s for %s seconds.\n' "${after_pid}" "${wait_seconds}"
}

launch_once 1
launch_once 2
if [[ "${route_entry_smoke}" == "true" ]]; then
  route_entry_once
fi
adb shell am force-stop "${package_name}" ||
  fail "Unable to force-stop ${package_name} after the smoke test."

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '### In-car APK launch smoke'
    echo
    printf -- '- Package: `%s`\n' "${package_name}"
    printf -- '- Launcher: `%s`\n' "${component}"
    echo '- Clean app-data state with location pre-granted for deterministic relaunch: `yes`'
    echo '- Cold launches: `2`'
    printf -- '- Alive window per launch: `%s seconds`\n' "${wait_seconds}"
    if [[ "${route_entry_smoke}" == "true" ]]; then
      echo '- Landscape route-entry deep link: `passed without process replacement`'
    else
      echo '- Landscape route-entry deep link: `skipped`'
    fi
    echo '- Fatal Java/native crash and tombstone scan: `passed`'
  } >> "${GITHUB_STEP_SUMMARY}"
fi
