#!/usr/bin/env bash
set -euo pipefail

repo_dir="${1:-.}"
out_dir="${2:-testing-input}"

repo_dir="$(cd "$repo_dir" && pwd)"
mkdir -p "$out_dir"
out_dir="$(cd "$out_dir" && pwd)"

cd "$repo_dir"

test -f android/app/build.gradle || {
  echo "Selected source does not contain the Organic Maps Android app module." >&2
  exit 1
}
test -x configure.sh || {
  echo "Selected source does not contain an executable configure.sh." >&2
  exit 1
}

: "${RUNNER_TEMP:?RUNNER_TEMP must be set}"
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT must be set}"

base_version_name="0.0.0"
version_name="0.0.0-InCar"
# Organic Maps In-Car production codes occupy 1,000,000,000..2,099,999,999.
# Keep the fixed engineering lane at the top of that supported range so a
# TESTING snapshot can update any normal current release without -d/uninstall.
version_code="2099999999"
keystore_file="${ORGANICMAPS_KEYSTORE_FILE:-$RUNNER_TEMP/temporary-testing-validation.jks}"
keystore_password="${KEYSTORE_PASSWORD:-temporary-testing-validation}"
key_alias="${KEY_ALIAS:-temporary-testing-validation}"
key_password="${KEY_PASSWORD:-temporary-testing-validation}"

umask 077
rm -rf "${out_dir:?}"/*

if [[ ! -s "$keystore_file" ]]; then
  keytool -genkeypair -noprompt \
    -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 1 \
    -keystore "$keystore_file" \
    -storepass "$keystore_password" -keypass "$key_password" \
    -dname "CN=Temporary Organic Maps testing build"
fi

python3 tools/ci/verify_android_repo_scope.py
bash ./configure.sh

secure_properties="$repo_dir/android/app/secure.properties"
: > "$secure_properties"
cleanup() {
  rm -f "$secure_properties"
}
trap cleanup EXIT

export ORG_GRADLE_PROJECT_spropStoreFile="$keystore_file"
export ORG_GRADLE_PROJECT_spropStorePassword="$keystore_password"
export ORG_GRADLE_PROJECT_spropKeyAlias="$key_alias"
export ORG_GRADLE_PROJECT_spropKeyPassword="$key_password"

cd android
chmod +x ./gradlew

./gradlew --no-daemon --stacktrace \
  -Parm64 \
  app:testInCarDebug assembleInCarDebug

./gradlew --no-daemon --stacktrace \
  -Parm64 \
  -PinCarReleaseVersionName="$base_version_name" \
  -PinCarReleaseVersionCode="$version_code" \
  assembleInCarRelease

cd "$repo_dir"

mapfile -t debug_apks < <(find android/app/build/outputs/apk/inCar/debug -maxdepth 1 -type f -name '*.apk' -print | sort)
mapfile -t release_apks < <(find android/app/build/outputs/apk/inCar/release -maxdepth 1 -type f -name '*.apk' -print | sort)
[[ "${#debug_apks[@]}" -eq 1 ]] || {
  echo "Expected exactly one InCarDebug APK; found ${#debug_apks[@]}." >&2
  printf '%s\n' "${debug_apks[@]:-}" >&2
  exit 1
}
[[ "${#release_apks[@]}" -eq 1 ]] || {
  echo "Expected exactly one InCarRelease APK; found ${#release_apks[@]}." >&2
  printf '%s\n' "${release_apks[@]:-}" >&2
  exit 1
}

debug_apk="${debug_apks[0]}"
release_apk="${release_apks[0]}"
test -s "$debug_apk"
test -s "$release_apk"

apksigner="${ANDROID_SDK_ROOT}/build-tools/36.0.0/apksigner"
test -x "$apksigner"
"$apksigner" verify --verbose "$release_apk" >/dev/null

apkanalyzer="$(command -v apkanalyzer || true)"
if [[ -z "$apkanalyzer" ]]; then
  apkanalyzer="$(find "$ANDROID_SDK_ROOT/cmdline-tools" -type f -name apkanalyzer -perm -u+x -print 2>/dev/null | sort -V | tail -n1)"
fi
test -x "$apkanalyzer"

actual_package="$($apkanalyzer manifest application-id "$release_apk")"
actual_version_name="$($apkanalyzer manifest version-name "$release_apk")"
actual_version_code="$($apkanalyzer manifest version-code "$release_apk")"
[[ "$actual_package" == "app.organicmaps.incar" ]]
[[ "$actual_version_name" == "$version_name" ]]
[[ "$actual_version_code" == "$version_code" ]]

cp "$debug_apk" "$out_dir/incar-debug.apk"
cp "$release_apk" "$out_dir/incar-release.apk"

field() {
  local key="$1" value="${2:-}"
  value="${value//$'\n'/ }"
  value="${value//$'\r'/ }"
  printf '%s=%s\n' "$key" "$value"
}

{
  field repository "${GITHUB_REPOSITORY:-}"
  field source_kind "${TESTING_SOURCE_KIND:-manual}"
  field requested_source "${TESTING_REQUESTED_SOURCE:-}"
  field source_sha "${TESTING_SOURCE_SHA:-$(git rev-parse HEAD)}"
  field built_sha "$(git rev-parse HEAD)"
  field source_ref "${TESTING_SOURCE_REF:-}"
  field pr_number "${TESTING_PR_NUMBER:-}"
  field pr_head_sha "${TESTING_PR_HEAD_SHA:-}"
  field pr_base_ref "${TESTING_PR_BASE_REF:-}"
  field pr_base_sha "${TESTING_PR_BASE_SHA:-}"
  field trigger_type "${TESTING_TRIGGER_TYPE:-manual}"
  field trigger_actor "${TESTING_TRIGGER_ACTOR:-${GITHUB_ACTOR:-}}"
  field testing_package "app.organicmaps.incar"
  field testing_version_name "$version_name"
  field testing_version_code "$version_code"
  field testing_abi "arm64-v8a"
  field build_run_id "${GITHUB_RUN_ID:-}"
  field build_run_number "${GITHUB_RUN_NUMBER:-}"
  field build_run_url "${TESTING_BUILD_RUN_URL:-https://github.com/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}}"
} > "$out_dir/BUILD_INFO.txt"
