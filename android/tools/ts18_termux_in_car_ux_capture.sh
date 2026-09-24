#!/data/data/com.termux/files/usr/bin/bash
# Read-only TS18 Organic Maps UX/UI evidence capture for Termux + Magisk root.
# Usage: bash ts18_termux_in_car_ux_capture.sh <label>

umask 077

PACKAGE="${PACKAGE:-app.organicmaps.incar}"
PROJECT="${PROJECT:-OrganicMaps-TS18-UX-Qualification}"
EXPORT_BASE="${EXPORT_BASE:-/storage/emulated/0/Download/${PROJECT}}"
LABEL="${1:-baseline}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-12}"
MAX_LOG_LINES="${MAX_LOG_LINES:-1400}"

case "$LABEL" in
  *[!A-Za-z0-9._-]*|'')
    printf 'FAIL: label must contain only letters, numbers, dot, underscore or hyphen.\n' >&2
    exit 2
    ;;
esac
case "$PACKAGE" in
  *[!A-Za-z0-9._-]*|'')
    printf 'FAIL: PACKAGE contains unsafe characters: %s\n' "$PACKAGE" >&2
    exit 2
    ;;
esac

for required in date mkdir cat printf id readlink find sort sha256sum; do
  if ! command -v "$required" >/dev/null 2>&1; then
    printf 'FAIL: required Termux command not found: %s\n' "$required" >&2
    exit 3
  fi
done

TIMEOUT_BIN="$(command -v timeout 2>/dev/null || true)"
if [ -z "$TIMEOUT_BIN" ]; then
  printf 'FAIL: Termux timeout is required so dumpsys/logcat probes are bounded.\n' >&2
  exit 3
fi

TS="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="${EXPORT_BASE}/${TS}-${LABEL}"
TMP_BASE="${TMPDIR:-$HOME/.tmp}"
TMP_RUN="${TMP_BASE}/omaps-ts18-ux-${TS}-$$"
STATUS_FILE="${RUN_DIR}/STATUS.txt"

if ! mkdir -p "$RUN_DIR" "$TMP_RUN"; then
  printf 'FAIL: cannot create output/temp directories. Run termux-setup-storage if shared storage is unavailable.\n' >&2
  exit 4
fi
chmod 700 "$TMP_RUN" 2>/dev/null || true

PASS=0
FAIL=0
WARN=0
BLOCKED=0
INFO=0
ROOT_OK=0
ROOT_UID=""
SU_BIN="$(command -v su 2>/dev/null || true)"

record_status() {
  local status="$1" name="$2" detail="${3:-}"
  printf '%s\t%s\t%s\n' "$status" "$name" "$detail" >> "$STATUS_FILE"
  case "$status" in
    PASS) PASS=$((PASS + 1)) ;;
    FAIL) FAIL=$((FAIL + 1)) ;;
    WARN) WARN=$((WARN + 1)) ;;
    BLOCKED) BLOCKED=$((BLOCKED + 1)) ;;
    INFO) INFO=$((INFO + 1)) ;;
  esac
}

run_bounded() {
  local seconds="$1" stdout_file="$2" stderr_file="$3"
  shift 3
  "$TIMEOUT_BIN" -k 2s "${seconds}s" "$@" >"$stdout_file" 2>"$stderr_file"
  return $?
}

write_capture_header() {
  local file="$1" name="$2" authority="$3" timeout="$4" command_text="$5"
  {
    printf 'name=%s\n' "$name"
    printf 'utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'authority=%s\n' "$authority"
    printf 'timeout_seconds=%s\n' "$timeout"
    printf 'command=%s\n' "$command_text"
    printf '%s\n' '--- stdout ---'
  } > "$file"
}

capture_normal() {
  local name="$1" class="$2" seconds="$3"
  shift 3
  local body="${TMP_RUN}/${name}.out" err="${TMP_RUN}/${name}.err" final="${RUN_DIR}/${name}.txt"
  write_capture_header "$final" "$name" "ordinary-termux" "$seconds" "$*"
  run_bounded "$seconds" "$body" "$err" "$@"
  local rc=$?
  cat "$body" >> "$final" 2>/dev/null || true
  {
    printf '\n%s\n' '--- stderr ---'
    cat "$err" 2>/dev/null || true
    printf '\nexit_status=%s\n' "$rc"
  } >> "$final"
  if [ "$rc" -eq 0 ]; then
    record_status PASS "$name" "$class"
  elif [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; then
    if [ "$class" = REQUIRED ]; then
      record_status FAIL "$name" "timeout"
    else
      record_status WARN "$name" "timeout"
    fi
  elif [ "$class" = REQUIRED ]; then
    record_status FAIL "$name" "exit=$rc"
  else
    record_status WARN "$name" "exit=$rc"
  fi
  return "$rc"
}

capture_root_sh() {
  local name="$1" class="$2" seconds="$3" script_body="$4"
  local final="${RUN_DIR}/${name}.txt" body="${TMP_RUN}/${name}.out" err="${TMP_RUN}/${name}.err"
  if [ "$ROOT_OK" -ne 1 ]; then
    write_capture_header "$final" "$name" "magisk-root" "$seconds" "$script_body"
    printf 'BLOCKED: root prerequisite unavailable.\n' >> "$final"
    record_status BLOCKED "$name" "root unavailable"
    return 125
  fi

  local helper="${TMP_RUN}/root-${name}.sh"
  {
    printf '%s\n' '#!/system/bin/sh'
    printf '%s\n' 'PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin'
    printf '%s\n' 'HOME=/'
    printf '%s\n' 'export PATH HOME'
    printf '%s\n' 'unset LD_PRELOAD LD_LIBRARY_PATH'
    printf '%s\n' "$script_body"
  } > "$helper"
  chmod 700 "$helper"

  write_capture_header "$final" "$name" "magisk-root" "$seconds" "$script_body"
  run_bounded "$seconds" "$body" "$err" "$SU_BIN" -c "/system/bin/sh '$helper'"
  local rc=$?
  cat "$body" >> "$final" 2>/dev/null || true
  {
    printf '\n%s\n' '--- stderr ---'
    cat "$err" 2>/dev/null || true
    printf '\nexit_status=%s\n' "$rc"
  } >> "$final"
  if [ "$rc" -eq 0 ]; then
    record_status PASS "$name" "$class"
  elif [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; then
    if [ "$class" = REQUIRED ]; then
      record_status FAIL "$name" "timeout"
    else
      record_status WARN "$name" "timeout"
    fi
  elif [ "$class" = REQUIRED ]; then
    record_status FAIL "$name" "exit=$rc"
  else
    record_status WARN "$name" "exit=$rc"
  fi
  return "$rc"
}

finalise() {
  local rc=$?
  trap - EXIT INT TERM

  {
    printf '\nsummary\n'
    printf 'PASS=%s\nFAIL=%s\nWARN=%s\nBLOCKED=%s\nINFO=%s\n' "$PASS" "$FAIL" "$WARN" "$BLOCKED" "$INFO"
    printf 'script_exit=%s\n' "$rc"
  } >> "$STATUS_FILE" 2>/dev/null || true

  if [ -d "$RUN_DIR" ]; then
    (
      cd "$RUN_DIR" || exit 1
      find . -type f ! -name MANIFEST.sha256 -print | LC_ALL=C sort | while IFS= read -r file; do
        sha256sum "$file"
      done > MANIFEST.sha256
      sha256sum -c MANIFEST.sha256 > MANIFEST.verify.txt 2>&1
    )
    local manifest_rc=$?
    if [ "$manifest_rc" -ne 0 ]; then
      printf 'WARN: manifest verification failed; directory preserved at %s\n' "$RUN_DIR" >&2
    fi

    local zip_bin
    zip_bin="$(command -v zip 2>/dev/null || true)"
    if [ -n "$zip_bin" ]; then
      local archive="${RUN_DIR}.zip"
      (
        cd "$RUN_DIR" || exit 1
        find . -type f -print | LC_ALL=C sort | "$zip_bin" -q -X "$archive" -@
      )
      local zip_rc=$?
      if [ "$zip_rc" -eq 0 ]; then
        sha256sum "$archive" > "${archive}.sha256"
        printf 'Archive: %s\n' "$archive"
        printf 'Archive SHA-256: '
        cut -d' ' -f1 "${archive}.sha256"
      else
        printf 'WARN: zip packaging failed; result directory preserved at %s\n' "$RUN_DIR" >&2
      fi
    else
      printf 'WARN: zip not installed; result directory preserved at %s\n' "$RUN_DIR" >&2
    fi
  fi

  rm -rf "$TMP_RUN" 2>/dev/null || true
  printf 'Results: %s\n' "$RUN_DIR"
  exit "$rc"
}
trap finalise EXIT INT TERM

: > "$STATUS_FILE"
{
  printf 'Organic Maps TS18 UX/UI qualification capture\n'
  printf 'label=%s\npackage=%s\nutc=%s\n' "$LABEL" "$PACKAGE" "$TS"
  printf 'read_only=true\n'
  printf 'note=No settings/property/package/window mutations are performed.\n'
} > "${RUN_DIR}/RUN.txt"

# Ordinary Termux identity first: UID/domain/namespace must not be conflated with root or ADB shell.
capture_normal termux-context REQUIRED 5 /data/data/com.termux/files/usr/bin/bash -c \
  'id; printf "selinux="; cat /proc/self/attr/current 2>/dev/null; printf "mount_ns="; readlink /proc/self/ns/mnt; printf "pwd="; pwd; printf "PATH=%s\n" "$PATH"'

if [ -n "$SU_BIN" ]; then
  ROOT_UID="$("$TIMEOUT_BIN" -k 1s 5s "$SU_BIN" -c 'id -u' 2>/dev/null | tr -d '\r\n')"
fi
if [ "$ROOT_UID" = "0" ]; then
  ROOT_OK=1
  record_status PASS root-prerequisite "Magisk/root UID 0 available"
else
  record_status FAIL root-prerequisite "su unavailable or did not return UID 0"
fi

capture_root_sh root-context REQUIRED 5 \
  'id; printf "selinux="; cat /proc/self/attr/current 2>/dev/null; printf "mount_ns="; readlink /proc/self/ns/mnt; printf "android_user="; am get-current-user 2>/dev/null; printf "PATH=%s\n" "$PATH"'

capture_root_sh device-baseline REQUIRED 8 \
  'printf "model="; getprop ro.product.model; printf "device="; getprop ro.product.device; printf "fingerprint="; getprop ro.build.fingerprint; printf "release="; getprop ro.build.version.release; printf "sdk="; getprop ro.build.version.sdk; printf "display="; getprop ro.build.display.id; printf "security_patch="; getprop ro.build.version.security_patch; printf "boot_id="; cat /proc/sys/kernel/random/boot_id'

capture_root_sh display REQUIRED 8 \
  'wm size; wm density; dumpsys display | grep -E "DisplayDeviceInfo|mBaseDisplayInfo|mOverrideDisplayInfo|state |FLAG_|DisplayInfo" | head -n 260'

capture_root_sh organicmaps-package REQUIRED 10 \
  "dumpsys package '$PACKAGE' | head -n 700"

capture_root_sh organicmaps-process REQUIRED 8 \
  "printf 'pid='; pidof '$PACKAGE' || true; ps -A -Z -o USER,PID,PPID,NAME,LABEL 2>/dev/null | grep -E 'organicmaps|topway|dofun|variety|twservice|system_server' | head -n 220"

capture_root_sh activity-window REQUIRED "$TIMEOUT_SECONDS" \
  "dumpsys activity activities | grep -E -A8 -B4 '$PACKAGE|mResumedActivity|mFocusedActivity|TaskRecord|RootTask|windowingMode|bounds=' | head -n 900; echo '--- window ---'; dumpsys window windows | grep -E -A8 -B4 '$PACKAGE|mCurrentFocus|mFocusedApp|Window\{|mFrame=|Requested w=|mAttrs=' | head -n 900"

capture_root_sh topway-integration OPTIONAL 10 \
  "echo '--- packages ---'; pm list packages | grep -Ei 'organicmaps|dofun|topway|tw[._]|variety' | head -n 260; echo '--- selected properties ---'; getprop | grep -Ei '(^|\\[)(persist\\.)?(tw|topway|dofun|.*pip|.*freeform|.*multi.?window|.*display)' | head -n 320"

capture_root_sh power OPTIONAL 8 \
  'dumpsys power | grep -E "mWakefulness=|mInteractive=|Display Power|mScreenBrightness|mUserActivitySummary|mIsPowered=" | head -n 160'

capture_root_sh graphics OPTIONAL 10 \
  "dumpsys gfxinfo '$PACKAGE' | head -n 500"

capture_root_sh targeted-logcat OPTIONAL 12 \
  "logcat -d -v threadtime -t '$MAX_LOG_LINES' 2>/dev/null | grep -E 'AndroidRuntime|ActivityTaskManager|WindowManager|OrganicMaps|MwmActivity|InCar|LocationState|Routing|FATAL EXCEPTION|ANR' | tail -n '$MAX_LOG_LINES'"

capture_root_sh crash-metadata OPTIONAL 8 \
  "echo '--- ANR names only ---'; ls -l /data/anr 2>/dev/null | tail -n 80; echo '--- tombstone names only ---'; ls -l /data/tombstones 2>/dev/null | tail -n 80"

# Location enable/provider state only. Do not dump last-known coordinates or precise location history.
capture_root_sh location-state OPTIONAL 8 \
  'printf "secure_location_mode="; settings get secure location_mode 2>/dev/null; printf "location_providers_allowed="; settings get secure location_providers_allowed 2>/dev/null'

# Screenshot is the direct evidence for layout, clipping, rail state, road/camera placement and glare review.
SCREENSHOT_PATH="${RUN_DIR}/screen-${LABEL}.png"
capture_root_sh screenshot REQUIRED 10 \
  "screencap -p '$SCREENSHOT_PATH'; test -s '$SCREENSHOT_PATH'"

# Record file metadata without reading user/private app data.
capture_root_sh screenshot-metadata OPTIONAL 5 \
  "ls -l '$SCREENSHOT_PATH'; sha256sum '$SCREENSHOT_PATH'"

record_status INFO physical-observation \
  "Label the matching playbook row PASS/FAIL/BLOCKED manually; this collector does not infer visual correctness."

# Core evidence failures make the run non-zero; optional WARN/BLOCKED still preserve a usable bundle.
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
