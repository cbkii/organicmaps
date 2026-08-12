#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: smoke_test_in_car_apk.sh --apk PATH --package PACKAGE [options]

Options:
  --wait-seconds SECONDS   Time each launched process must remain alive (default: 10)
  --proof-dir PATH         Directory for install, launch and logcat evidence
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
  fail "Unable to install the signed InCar APK."
fi

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

  if grep -E \
      'FATAL EXCEPTION|Fatal signal [0-9]+ \((SIGABRT|SIGSEGV|SIGBUS)\)|Abort message:' \
      "${app_logcat_file}" >/dev/null ||
     grep -E 'tombstoned.*received crash request' "${system_logcat_file}" >/dev/null; then
    tail -n 300 "${system_logcat_file}" >&2
    fail "Crash evidence was found after launch ${attempt}."
  fi

  printf 'Launch %s remained alive as pid %s for %s seconds.\n' "${attempt}" "${pid}" "${wait_seconds}"
}

launch_once 1
launch_once 2
adb shell am force-stop "${package_name}" ||
  fail "Unable to force-stop ${package_name} after the smoke test."

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '### In-car signed APK launch smoke'
    echo
    printf -- '- Package: `%s`\n' "${package_name}"
    printf -- '- Launcher: `%s`\n' "${component}"
    echo '- Cold launches: `2`'
    printf -- '- Alive window per launch: `%s seconds`\n' "${wait_seconds}"
    echo '- Fatal Java/native crash and tombstone scan: `passed`'
  } >> "${GITHUB_STEP_SUMMARY}"
fi
