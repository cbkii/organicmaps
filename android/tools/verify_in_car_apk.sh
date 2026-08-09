#!/usr/bin/env bash

usage() {
  cat <<'USAGE'
Usage: verify_in_car_apk.sh --apk PATH --expected-package PACKAGE [options]

Options:
  --expected-version-name VALUE
  --expected-version-code VALUE
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

extract_signer_sha256() {
  local signer_output="$1"
  local line
  line="$(grep -i -m1 -E '^[[:space:]]*Signer #1 certificate SHA-256 digest:[[:space:]]*[0-9a-f:]+' "${signer_output}" || true)"
  [[ -n "${line}" ]] || return 1
  line="${line#*:}"
  normalise_sha256 "${line}"
}

apk=""
expected_package=""
expected_version_name=""
expected_version_code=""
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

apk_cert_sha256="$(extract_signer_sha256 "${signer_output}" || true)"
if [[ ! "${apk_cert_sha256}" =~ ^[0-9A-F]{64}$ ]]; then
  cat "${signer_output}" >&2
  fail "Unable to parse the APK signer SHA-256 fingerprint from apksigner output."
fi
if [[ -n "${expected_cert_sha256}" && "${apk_cert_sha256}" != "${expected_cert_sha256}" ]]; then
  fail "APK signer ${apk_cert_sha256} does not match the expected release certificate ${expected_cert_sha256}."
fi

if [[ "${signature_only}" == true ]]; then
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

[[ "${package_name}" == "${expected_package}" ]] || fail "Unexpected in-car package id: ${package_name:-not found}; expected ${expected_package}."
if [[ -n "${expected_version_name}" && "${version_name}" != "${expected_version_name}" ]]; then
  fail "Unexpected in-car versionName: ${version_name:-not found}; expected ${expected_version_name}."
fi
if [[ -n "${expected_version_code}" && "${version_code}" != "${expected_version_code}" ]]; then
  fail "Unexpected in-car versionCode: ${version_code:-not found}; expected ${expected_version_code}."
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

apk_sha256="$(sha256sum "${apk}" | awk '{print toupper($1)}')" || fail "Unable to calculate APK SHA-256."

if [[ -n "${github_output}" ]]; then
  {
    printf 'package_name=%s\n' "${package_name}"
    printf 'version_name=%s\n' "${version_name}"
    printf 'version_code=%s\n' "${version_code}"
    printf 'cert_sha256=%s\n' "${apk_cert_sha256}"
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
    echo '- ABI: `arm64-v8a` only'
    echo "- Signer SHA-256: \`${apk_cert_sha256}\`"
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
printf 'Signer SHA-256: %s\n' "${apk_cert_sha256}"
printf 'APK SHA-256: %s\n' "${apk_sha256}"
