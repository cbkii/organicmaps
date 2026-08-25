# Building

## Fresh clone

Use a fresh recursive checkout for release-quality evidence:

```bash
git clone https://github.com/cbkii/organicmaps.git
cd organicmaps
git submodule sync --recursive
git submodule update --init --recursive --depth 1
./configure.sh
```

The root CMake project is Android-only; configure native code through Gradle/NDK rather than a desktop CMake target.

Current SDK/NDK/minimum/target values are defined in `android/gradle.properties` and must be treated as the source of truth.

## Repository validation

```bash
python3 tools/ci/verify_android_repo_scope.py
```

This verifies the size budget, forbidden paths, large-asset allow-list, tracked symlink closure and submodule manifest.

## Android validation

The release-gating Android validation surface is InCar-focused:

```bash
cd android
./gradlew lintAllModules detektCheckAll
./gradlew -Parm64 assembleInCarProfileable
./gradlew -Px86_64 app:testInCarDebug sdk:testDebug
```

CI additionally runs the retained SDK connected tests and an x86_64 InCar startup smoke test. Other retained Android flavours and Wear may still be built manually when a change specifically affects those compatibility surfaces, but Web/F-Droid/Google/Huawei/Wear builds are not publication or release CI gates for this fork.

The current CI workflow is authoritative for the exact release-gating command set if Gradle task names change.

## InCar release-equivalent build

CI creates an ephemeral keystore, builds arm64 `InCarRelease` with explicit release version properties, and validates the APK using `android/tools/verify_in_car_apk.sh`. Do not commit test or production signing material.

## Runtime smoke testing

The Android runtime-smoke workflow may use an emulator. API 29 coverage is the compatibility floor for the TS18/InCar target; newer API coverage tests target-SDK behaviour. Neither is a substitute for physical TS18 validation.
