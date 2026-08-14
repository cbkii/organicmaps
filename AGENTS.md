# Repository Agent Policy

## Product scope

This repository is an Android-focused derivative of Organic Maps. Treat the retained product as:

1. Android application/modules under `android/`;
2. JNI/NDK bridge and Android native build configuration;
3. shared C++ runtime libraries linked into the Android native library;
4. minimum third-party and runtime-data closure required by those layers;
5. Android-focused build, validation, release and selective-upstream tooling.

Android-only does not mean InCar-only. The `inCar` flavour is the primary fork-specific product, while useful general Android flavours, Wear and SDK modules remain valid unless current evidence proves otherwise.

## Source of truth

- Current repository code and CI state override stale documentation, old PR descriptions and historical audit notes.
- Read the complete relevant files and current PR/review state before editing.
- Prove removability through Gradle, CMake, JNI, symlink, runtime-data, submodule, workflow and release dependencies; directory names are not proof.
- Required runtime assets may live outside `android/`.

## Android constraints

- Current compile/target/min SDK and NDK values are defined in `android/gradle.properties`.
- Preserve Android 10/API 29 runtime compatibility for the InCar product unless an explicitly approved product change says otherwise.
- Preserve `app.organicmaps.incar` release identity and release signer continuity.
- Preserve arm64; retain 32-bit Android compatibility where the current build/dependency graph requires it.
- Use runtime bounds/insets. Do not introduce fixed TS18 display assumptions into general Android code.
- Do not add another navigation, playback, MediaSession, notification, audio-focus or vendor-service authority as part of repository maintenance.

## Build and validation

Use a fresh recursive clone for release-quality validation. Minimum repository checks:

```bash
python3 tools/ci/verify_android_repo_scope.py
git submodule sync --recursive
git submodule update --init --recursive --depth 1
./configure.sh
cd android
./gradlew lintAllModules detektCheckAll
./gradlew -Parm64 assembleWebDebug
./gradlew -Parm32 assembleFdroidDebug
./gradlew -Parm64 assembleInCarProfileable
```

For InCar release-equivalent validation, use the current CI workflow and `android/tools/verify_in_car_apk.sh`; never commit signing material.

Emulator/CI success is not physical TS18 validation. Record physical validation separately.

## Repository scope guard

`python3 tools/ci/verify_android_repo_scope.py` is mandatory after cleanup or upstream import. Do not weaken its forbidden-path, symlink/submodule closure, large-asset or size-budget checks merely to make CI pass. Fix the dependency or document a genuinely required Android runtime asset.

## Upstream imports

Import selectively from `organicmaps/organicmaps` and record upstream commit provenance. Do not use an unrestricted upstream merge that silently restores iOS, Qt/desktop, removed generator bulk or obsolete workflows. Run the repository-scope verifier after every import.

## Changes and safety

- Keep changes narrow, reviewable and reversible.
- Do not commit generated APKs, signing keys, credentials, diagnostic archives, caches or local build output.
- Do not rewrite public history, delete release tags, change branch protection/rulesets, or perform repository lineage cutover without explicit approval for that exact administrative operation.
- Preserve licences, notices and third-party attribution for retained code/data.

See `android/AGENTS.md` for Android implementation conventions and `docs/` for architecture, building, release, scope, upstream and TS18 validation policy.
