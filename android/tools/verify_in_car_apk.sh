#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: verify_in_car_apk.sh --apk PATH --expected-package PACKAGE [options]

Options:
  --expected-version-name VALUE
  --expected-version-code VALUE
  --expected-min-sdk VALUE       (default: 21)
  --expected-target-sdk VALUE    (default: 36)
  --expected-cert-sha256 HEX
  --proof-dir PATH
  --summary-file PATH
  --github-output PATH
  --signature-only
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

resolve_apksigner() {
  if [[ -n "${APKSIGNER_BIN:-}" && -x "${APKSIGNER_BIN}" ]]; then
    printf '%s\n' "${APKSIGNER_BIN}"
    return 0
  fi
  if [[ -n "${ANDROID_BUILD_TOOLS_DIR:-}" && -x "${ANDROID_BUILD_TOOLS_DIR}/apksigner" ]]; then
    printf '%s\n' "${ANDROID_BUILD_TOOLS_DIR}/apksigner"
    return 0
  fi
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/build-tools" ]]; then
    local candidate
    candidate="$(find "${ANDROID_HOME}/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner -perm -u+x -print 2>/dev/null \
      | sort -V | tail -n1)"
    if [[ -n "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  fi
  return 1
}

resolve_apkanalyzer() {
  if [[ -n "${APKANALYZER_BIN:-}" && -x "${APKANALYZER_BIN}" ]]; then
    printf '%s\n' "${APKANALYZER_BIN}"
    return 0
  fi
  if command -v apkanalyzer >/dev/null 2>&1; then
    command -v apkanalyzer
    return 0
  fi
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/cmdline-tools" ]]; then
    local candidate
    candidate="$(find "${ANDROID_HOME}/cmdline-tools" -type f -name apkanalyzer -perm -u+x -print 2>/dev/null \
      | sort -V | tail -n1)"
    if [[ -n "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  fi
  return 1
}

normalise_sha256() {
  printf '%s' "$1" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'
}

extract_signer_sha256s() {
  local signer_output="$1"
  sed -nE \
    -e 's/^[[:space:]]*Signer( #[0-9]+)? certificate SHA-256 digest:[[:space:]]*([0-9A-Fa-f:]+)[[:space:]]*$/\2/p' \
    -e 's/^[[:space:]]*V[1-4] Signer:[[:space:]]*certificate SHA-256 digest:[[:space:]]*([0-9A-Fa-f:]+)[[:space:]]*$/\1/p' \
    "${signer_output}" \
    | tr -d ':' \
    | tr '[:lower:]' '[:upper:]' \
    | sort -u
}

apk=""
expected_package=""
expected_version_name=""
expected_version_code=""
expected_min_sdk="21"
expected_target_sdk="36"
expected_cert_sha256=""
proof_dir=""
summary_file=""
github_output=""
signature_only=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      require_value "$1" "${2:-}"
      apk="$2"
      shift 2
      ;;
    --expected-package)
      require_value "$1" "${2:-}"
      expected_package="$2"
      shift 2
      ;;
    --expected-version-name)
      require_value "$1" "${2:-}"
      expected_version_name="$2"
      shift 2
      ;;
    --expected-version-code)
      require_value "$1" "${2:-}"
      expected_version_code="$2"
      shift 2
      ;;
    --expected-min-sdk)
      require_value "$1" "${2:-}"
      expected_min_sdk="$2"
      shift 2
      ;;
    --expected-target-sdk)
      require_value "$1" "${2:-}"
      expected_target_sdk="$2"
      shift 2
      ;;
    --expected-cert-sha256)
      require_value "$1" "${2:-}"
      expected_cert_sha256="$(normalise_sha256 "$2")"
      shift 2
      ;;
    --proof-dir)
      require_value "$1" "${2:-}"
      proof_dir="$2"
      shift 2
      ;;
    --summary-file)
      require_value "$1" "${2:-}"
      summary_file="$2"
      shift 2
      ;;
    --github-output)
      require_value "$1" "${2:-}"
      github_output="$2"
      shift 2
      ;;
    --signature-only)
      signature_only=true
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
if [[ "${signature_only}" != true ]]; then
  [[ -n "${expected_package}" ]] || fail "--expected-package is required unless --signature-only is used."
fi
if [[ ! "${expected_min_sdk}" =~ ^[0-9]+$ ]]; then
  fail "Expected minSdk is not numeric: ${expected_min_sdk}"
fi
if [[ ! "${expected_target_sdk}" =~ ^[0-9]+$ ]]; then
  fail "Expected targetSdk is not numeric: ${expected_target_sdk}"
fi
if [[ -n "${expected_cert_sha256}" && ! "${expected_cert_sha256}" =~ ^[0-9A-F]{64}$ ]]; then
  fail "Expected signer SHA-256 is not a 64-digit hexadecimal fingerprint."
fi

if [[ -z "${proof_dir}" ]]; then
  base_tmp="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
  proof_dir="$(mktemp -d "${base_tmp%/}/in-car-apk-proof.XXXXXX")" || fail "Unable to create proof directory."
fi
mkdir -p "${proof_dir}" || fail "Unable to create proof directory: ${proof_dir}"

apksigner="$(resolve_apksigner)" || fail "apksigner was not found in the Android SDK."
signer_output="${proof_dir}/apksigner.txt"
if ! "${apksigner}" verify --verbose --print-certs "${apk}" > "${signer_output}" 2>&1; then
  cat "${signer_output}" >&2
  fail "APK signature verification failed."
fi

mapfile -t apk_cert_sha256s < <(extract_signer_sha256s "${signer_output}")
for digest in "${apk_cert_sha256s[@]}"; do
  if [[ ! "${digest}" =~ ^[0-9A-F]{64}$ ]]; then
    cat "${signer_output}" >&2
    fail "apksigner returned an invalid signer SHA-256 fingerprint: ${digest}"
  fi
done

apk_cert_sha256=""
if [[ -n "${expected_cert_sha256}" ]]; then
  for digest in "${apk_cert_sha256s[@]}"; do
    if [[ "${digest}" == "${expected_cert_sha256}" ]]; then
      apk_cert_sha256="${digest}"
      break
    fi
  done
  if [[ -z "${apk_cert_sha256}" ]]; then
    cat "${signer_output}" >&2
    if [[ "${#apk_cert_sha256s[@]}" -eq 0 ]]; then
      fail "Unable to parse a signer SHA-256 fingerprint from apksigner output for certificate continuity verification."
    fi
    fail "APK signer(s) ${apk_cert_sha256s[*]} do not match the expected release certificate ${expected_cert_sha256}."
  fi
elif [[ "${#apk_cert_sha256s[@]}" -eq 1 ]]; then
  apk_cert_sha256="${apk_cert_sha256s[0]}"
elif [[ "${#apk_cert_sha256s[@]}" -gt 1 ]]; then
  apk_cert_sha256="multiple:${apk_cert_sha256s[*]}"
fi

if [[ "${signature_only}" == true ]]; then
  if [[ -z "${apk_cert_sha256}" ]]; then
    cat "${signer_output}" >&2
    fail "APK signature is valid, but no signer SHA-256 fingerprint could be parsed."
  fi
  printf 'Signer SHA-256: %s\n' "${apk_cert_sha256}"
  if [[ -n "${github_output}" ]]; then
    printf 'cert_sha256=%s\n' "${apk_cert_sha256}" >> "${github_output}"
  fi
  exit 0
fi

apkanalyzer="$(resolve_apkanalyzer)" || fail "apkanalyzer was not found in PATH or the Android SDK."
if ! unzip -tq "${apk}" >/dev/null; then
  fail "APK ZIP integrity verification failed: ${apk}"
fi

manifest="${proof_dir}/manifest.xml"
zip_list="${proof_dir}/zip-list.txt"
dex_entries_file="${proof_dir}/dex-entries.txt"
dex_strings="${proof_dir}/dex-strings.txt"

"${apkanalyzer}" manifest print "${apk}" > "${manifest}" || fail "Unable to decode APK manifest."
unzip -Z1 "${apk}" > "${zip_list}" || fail "Unable to enumerate APK entries."

package_name="$("${apkanalyzer}" manifest application-id "${apk}" | tr -d '\r')" || fail "Unable to read APK application id."
version_name="$("${apkanalyzer}" manifest version-name "${apk}" | tr -d '\r')" || fail "Unable to read APK versionName."
version_code="$("${apkanalyzer}" manifest version-code "${apk}" | tr -d '\r')" || fail "Unable to read APK versionCode."
min_sdk="$("${apkanalyzer}" manifest min-sdk "${apk}" | tr -d '\r')" || fail "Unable to read APK minSdkVersion."
target_sdk="$("${apkanalyzer}" manifest target-sdk "${apk}" | tr -d '\r')" || fail "Unable to read APK targetSdkVersion."

[[ "${package_name}" == "${expected_package}" ]] || fail "Unexpected in-car package id: ${package_name:-not found}; expected ${expected_package}."
if [[ -n "${expected_version_name}" && "${version_name}" != "${expected_version_name}" ]]; then
  fail "Unexpected in-car versionName: ${version_name:-not found}; expected ${expected_version_name}."
fi
if [[ -n "${expected_version_code}" && "${version_code}" != "${expected_version_code}" ]]; then
  fail "Unexpected in-car versionCode: ${version_code:-not found}; expected ${expected_version_code}."
fi
if [[ "${min_sdk}" != "${expected_min_sdk}" ]]; then
  fail "Unexpected in-car minSdkVersion: ${min_sdk:-not found}; expected ${expected_min_sdk}."
fi
if [[ "${target_sdk}" != "${expected_target_sdk}" ]]; then
  fail "Unexpected in-car targetSdkVersion: ${target_sdk:-not found}; expected ${expected_target_sdk}."
fi

forbidden_manifest=(
  'app.organicmaps.car.AndroidAutoService'
  'androidx.car.app.NAVIGATION_TEMPLATES'
  'androidx.car.app.ACCESS_SURFACE'
  'app.organicmaps.editor.EditorActivity'
  'app.organicmaps.editor.ProfileActivity'
  'app.organicmaps.editor.FeatureCategoryActivity'
  'app.organicmaps.editor.ReportActivity'
  'app.organicmaps.editor.OsmLoginActivity'
)
for needle in "${forbidden_manifest[@]}"; do
  if grep -Fq -- "${needle}" "${manifest}"; then
    fail "Forbidden in-car manifest entry remains: ${needle}"
  fi
done

required_manifest=(
  'app.organicmaps.location.TrackRecordingService'
  'app.organicmaps.DownloadResourcesActivity'
)
for needle in "${required_manifest[@]}"; do
  if ! grep -Fq -- "${needle}" "${manifest}"; then
    fail "Required in-car manifest entry is missing: ${needle}"
  fi
done

if ! grep -Fxq -- 'assets/drules_in_car.bin' "${zip_list}"; then
  fail "Required in-car drawing rules asset is missing: assets/drules_in_car.bin"
fi

grep -E '^classes([0-9]+)?\.dex$' "${zip_list}" > "${dex_entries_file}" || true
mapfile -t dex_entries < "${dex_entries_file}"
[[ "${#dex_entries[@]}" -gt 0 ]] || fail "No classes*.dex entries were found in the in-car APK."
: > "${dex_strings}"
for dex_entry in "${dex_entries[@]}"; do
  dex_file="${proof_dir}/$(basename "${dex_entry}")"
  unzip -p "${apk}" "${dex_entry}" > "${dex_file}" || fail "Unable to extract ${dex_entry}."
  strings "${dex_file}" >> "${dex_strings}" || fail "Unable to inspect ${dex_entry} strings."
done

# These integrations are intentionally absent from the direct-display in-car runtime graph.
# Editor implementation bytecode may remain linked; its safety boundary is the removed manifest
# surface and product-gated entry paths above.
forbidden_dex=(
  'Lapp/organicmaps/car/'
  'Landroidx/car/app/'
  'Lapp/organicmaps/intent/GoogleAssistantIntentBridge;'
  'Landroidx/work/'
)
for needle in "${forbidden_dex[@]}"; do
  if grep -Fq -- "${needle}" "${dex_strings}"; then
    fail "Forbidden in-car runtime class/package remains: ${needle}"
  fi
done

mapfile -t abis < <(sed -n 's#^lib/\([^/]*\)/.*#\1#p' "${zip_list}" | sort -u)
if [[ "${#abis[@]}" -ne 1 || "${abis[0]}" != 'arm64-v8a' ]]; then
  fail "Expected arm64-v8a-only in-car APK; found ABI(s): ${abis[*]:-none}"
fi
if ! grep -Fxq -- 'lib/arm64-v8a/liborganicmaps.so' "${zip_list}"; then
  fail "Required native runtime library is missing: lib/arm64-v8a/liborganicmaps.so"
fi

apk_sha256="$(sha256sum "${apk}" | awk '{print toupper($1)}')" || fail "Unable to calculate APK SHA-256."

if [[ -n "${github_output}" ]]; then
  {
    printf 'package_name=%s\n' "${package_name}"
    printf 'version_name=%s\n' "${version_name}"
    printf 'version_code=%s\n' "${version_code}"
    printf 'min_sdk=%s\n' "${min_sdk}"
    printf 'target_sdk=%s\n' "${target_sdk}"
    if [[ -n "${apk_cert_sha256}" ]]; then
      printf 'cert_sha256=%s\n' "${apk_cert_sha256}"
    fi
    printf 'apk_sha256=%s\n' "${apk_sha256}"
  } >> "${github_output}"
fi

if [[ -n "${summary_file}" ]]; then
  {
    echo '### In-car APK proof'
    echo
    echo "- Package: \`${package_name}\`"
    echo "- versionName: \`${version_name}\`"
    echo "- versionCode: \`${version_code}\`"
    echo "- minSdkVersion: \`${min_sdk}\`"
    echo "- targetSdkVersion: \`${target_sdk}\`"
    echo '- ABI: `arm64-v8a` only'
    echo '- Native runtime: `lib/arm64-v8a/liborganicmaps.so` present'
    if [[ -n "${apk_cert_sha256}" ]]; then
      echo "- Signer SHA-256: \`${apk_cert_sha256}\`"
    else
      echo '- APK signature: verified (`apksigner` fingerprint not required for this check)'
    fi
    echo "- APK SHA-256: \`${apk_sha256}\`"
    echo '- Android Auto manifest/runtime: absent'
    echo '- Google Assistant bridge/runtime: absent'
    echo '- WorkManager runtime: absent'
    echo '- OSM editor Activity manifest surface: absent'
    echo '- Track Recording and downloader manifest surfaces: present'
    echo '- In-car drawing rules asset: present'
  } >> "${summary_file}"
fi

printf 'Verified in-car APK: %s\n' "${apk}"
printf 'Package: %s\n' "${package_name}"
printf 'versionName: %s\n' "${version_name}"
printf 'versionCode: %s\n' "${version_code}"
printf 'minSdkVersion: %s\n' "${min_sdk}"
printf 'targetSdkVersion: %s\n' "${target_sdk}"
if [[ -n "${apk_cert_sha256}" ]]; then
  printf 'Signer SHA-256: %s\n' "${apk_cert_sha256}"
else
  printf 'APK signature: verified\n'
fi
printf 'APK SHA-256: %s\n' "${apk_sha256}"
