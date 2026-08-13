# Organic Maps — Android-focused fork

This repository is an Android-focused derivative of [Organic Maps](https://github.com/organicmaps/organicmaps). It retains the Android application, JNI/NDK bridge, shared C++ runtime and Android runtime data needed to build and operate the product, while deliberately excluding iOS, Qt/desktop packaging and large map-generation/test inputs.

The upstream project remains the authoritative source for removed platforms and historical material.

## Supported Android surfaces

- `inCar`: the fork's primary direct-display/in-car product; release package `app.organicmaps.incar`.
- `web`, `fdroid`, `google`, `huawei`: retained Android compile/regression flavours while they remain useful. Their presence does not imply that this fork publishes to the corresponding stores.
- Wear and Android SDK modules are retained where they continue to build against the shared Android/core tree.

The InCar profile is designed to remain compatible with Android 10/API 29 devices even though the repository compiles and targets newer Android SDKs. Current toolchain values live in `android/gradle.properties`; do not duplicate them in documentation.

## Build

From a fresh clone:

```bash
git submodule sync --recursive
git submodule update --init --recursive --depth 1
./configure.sh
cd android
./gradlew -Parm64 assembleWebDebug
./gradlew -Parm64 assembleInCarProfileable
```

See [docs/BUILDING.md](docs/BUILDING.md) for the supported validation commands and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the retained dependency layers.

## Repository scope

This is **Android-only, not InCar-only**. Shared native code and required runtime data intentionally remain outside `android/`. Do not delete a path merely because it is not under `android/`.

Every change and selective upstream import must pass:

```bash
python3 tools/ci/verify_android_repo_scope.py
```

The verifier enforces the Android-only path boundary, submodule/symlink closure, large-asset allow-list and tracked-tree size budget. See [docs/REPOSITORY_SCOPE.md](docs/REPOSITORY_SCOPE.md).

## InCar release

The supported release path is `.github/workflows/manual-in-car-release.yml`. It must preserve package identity, signer continuity and release validation. See [docs/RELEASING.md](docs/RELEASING.md).

CI and emulator results are not evidence of physical TS18 behaviour; see [docs/TS18_VALIDATION.md](docs/TS18_VALIDATION.md).

## Upstream and provenance

Upstream changes are imported selectively from `organicmaps/organicmaps`; broad merges must not restore deleted platforms. See [docs/UPSTREAM.md](docs/UPSTREAM.md) and [docs/PROVENANCE.md](docs/PROVENANCE.md).

## Licence and attribution

Organic Maps code and retained third-party components remain subject to their original licences and notices. See `LICENSE`, `NOTICE`, `COPYING`, `DATA_LICENSE.txt`, `.reuse/dep5` and `data/copyright.html`.

This fork is not the upstream Organic Maps project. The upstream project and contributors remain credited as required by the retained licences and user-visible attribution.
