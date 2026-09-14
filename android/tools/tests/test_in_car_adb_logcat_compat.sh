#!/usr/bin/env bash
set -u

fail() {
  printf 'FAILED: %s\n' "$*" >&2
  exit 1
}

root="$(mktemp -d)" || exit 1
trap 'rm -rf -- "${root}"' EXIT
fake_adb="${root}/real-adb"
wrapper="$(cd "$(dirname "$0")/.." && pwd)/ci-adb-logcat-window/adb"
state="${root}/state"
log_file="${root}/fake-log.txt"
clear_count="${root}/clear-count.txt"

cat > "${fake_adb}" <<'FAKE'
#!/usr/bin/env bash
set -u
log_file="${FAKE_LOG_FILE:?}"
clear_count="${FAKE_CLEAR_COUNT:?}"
mode="${FAKE_CLEAR_MODE:-success}"

if [[ "${1:-}" == "get-state" ]]; then
  [[ "${FAKE_TRANSPORT_FAIL:-false}" == true ]] && exit 1
  printf 'device\n'
  exit 0
fi

if [[ "${1:-}" == "shell" && "${2:-}" == "log" ]]; then
  marker="${@: -1}"
  printf '09-14 12:00:01.000  100  100 I OrganicMapsInCarSmoke: %s\n' "${marker}" >> "${log_file}"
  exit 0
fi

if [[ "${1:-}" == "logcat" ]]; then
  for arg in "$@"; do
    if [[ "${arg}" == "-c" ]]; then
      count=0
      [[ -f "${clear_count}" ]] && count="$(cat "${clear_count}")"
      count=$((count + 1))
      printf '%s\n' "${count}" > "${clear_count}"
      case "${mode}" in
        success)
          : > "${log_file}"
          exit 0
          ;;
        transient)
          if [[ "${count}" -ge 2 ]]; then
            : > "${log_file}"
            exit 0
          fi
          printf "failed to clear the 'main' log\n" >&2
          exit 1
          ;;
        fail)
          printf "failed to clear the 'main' log\n" >&2
          exit 1
          ;;
      esac
    fi
  done
  cat "${log_file}"
  exit 0
fi

exit 0
FAKE
chmod +x "${fake_adb}"

export IN_CAR_REAL_ADB="${fake_adb}"
export IN_CAR_LOGCAT_STATE_DIR="${state}"
export FAKE_LOG_FILE="${log_file}"
export FAKE_CLEAR_COUNT="${clear_count}"

reset_case() {
  rm -rf -- "${state}"
  rm -f -- "${clear_count}"
  : > "${log_file}"
  unset FAKE_TRANSPORT_FAIL || true
}

reset_case
export FAKE_CLEAR_MODE=success
bash "${wrapper}" logcat -c || fail "immediate clear should succeed"
[[ ! -e "${state}/marker.txt" ]] || fail "successful clear must not retain marker state"

reset_case
export FAKE_CLEAR_MODE=transient
bash "${wrapper}" logcat -c || fail "second clear attempt should recover transient failure"
[[ "$(cat "${clear_count}")" == 2 ]] || fail "transient case should make exactly two clear attempts"
[[ ! -e "${state}/marker.txt" ]] || fail "transient recovery must not retain marker state"

reset_case
export FAKE_CLEAR_MODE=fail
printf '09-14 11:59:59.000  111  111 E AndroidRuntime: FATAL EXCEPTION: stale\n' >> "${log_file}"
bash "${wrapper}" logcat -c || fail "persistent clear failure should establish marker fallback"
[[ -s "${state}/marker.txt" ]] || fail "marker fallback did not persist marker state"
printf '09-14 12:00:02.000  222  222 I App: alive\n' >> "${log_file}"
window="$(bash "${wrapper}" logcat -d -v threadtime -t 4000)" || fail "marker-bounded dump failed"
[[ "${window}" != *"stale"* ]] || fail "pre-marker stale crash leaked into the current window"
[[ "${window}" == *"App: alive"* ]] || fail "post-marker evidence was lost"

printf '09-14 12:00:03.000  222  222 E AndroidRuntime: FATAL EXCEPTION: current\n' >> "${log_file}"
window="$(bash "${wrapper}" logcat -d -v threadtime --pid=222 -t 4000)" || fail "PID-filtered marker dump failed"
[[ "${window}" == *"FATAL EXCEPTION: current"* ]] || fail "post-marker crash evidence was suppressed"
[[ "${window}" != *"OrganicMapsInCarSmoke"* ]] || fail "marker line should not appear in returned evidence"

reset_case
export FAKE_CLEAR_MODE=fail
export FAKE_TRANSPORT_FAIL=true
if bash "${wrapper}" logcat -c >/dev/null 2>&1; then
  fail "transport failure after clear attempts must remain a hard failure"
fi

printf 'PASS: InCar ADB Logcat compatibility shim isolates fallback windows without hiding current failures.\n'
