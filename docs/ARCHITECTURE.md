# Architecture

The repository is intentionally Android-focused, but the Android APK is not implemented solely under `android/`.

## Retained layers

1. **Android product** — application, SDK, Wear and Android feature modules under `android/`.
2. **JNI/NDK bridge** — `android/sdk/src/main/cpp/` and root Android CMake integration.
3. **Shared C++ runtime** — `libs/` and required shared source used by the Android native library.
4. **Third-party dependencies** — the minimum `3party/` and submodule closure required by the Android/native build plus narrowly retained tooling dependencies.
5. **Runtime data** — `data/` assets required by packaged Android variants, including world maps, fonts, classifications, drawing rules, symbols, patterns and other resources consumed at runtime.
6. **Engineering surface** — Android-focused CI, release verification, repository-scope checks and selective upstream tooling.

The root CMake project rejects non-Android configuration. Android Gradle drives CMake through the SDK external-native build and links the shared native `map` dependency closure.

## Variant boundary

`inCar` is a distinct product flavour, not a replacement for the whole Android architecture. General Android flavours remain useful compile/regression references. InCar-specific behaviour must stay source-set or feature-policy isolated where practical.

## Authority boundaries

Repository cleanup is not a reason to add or move runtime authority. In particular, avoid creating duplicate navigation services, notifications, audio-focus owners, media sessions or device-vendor dependencies. Preserve the app/native framework lifecycle and existing Android authority model.

## Data boundary

Large files are retained only when they are required Android runtime assets. Map-generation borders, inherited large test fixtures and desktop shader compiler binaries are deliberately absent. `tools/ci/verify_android_repo_scope.py` enforces the allowed boundary and checks tracked symlinks/submodules.
