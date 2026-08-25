# Android Engineering Instructions

These instructions supplement the root `AGENTS.md`.

## Modules and variants

- `app/`: Android application and product flavours.
- `sdk/`: JNI-backed SDK and Android/native integration.
- `libs/`: Android feature libraries, including retained car/compatibility modules.
- `wear/`: retained Wear build.
- Flavours currently include `google`, `web`, `fdroid`, `huawei` and `inCar`; build types include `debug`, `release`, `beta` and `profileable` where configured.
- Do not infer publication support from a compile flavour. This fork's only supported public release surface is the InCar APK on GitHub Releases unless repository policy is deliberately changed.
- Retained Web/F-Droid/Google/Huawei/Wear/SDK buildability may still be useful for local compatibility work, but those surfaces are not release CI gates.

## Compatibility

Read `gradle.properties` for the current compile SDK, target SDK, min SDK and NDK. The InCar source set must continue to run on Android 10/API 29; API-gate newer platform behaviour.

Preserve the InCar application identity and source-set isolation. Do not move general Android integrations into InCar merely to share code, and do not introduce additional service, notification, navigation or media authorities.

## JNI/native lifecycle

Java/Kotlin native declarations map into `sdk/src/main/cpp/`. Respect framework creation/destruction boundaries: do not call code that requires a native Framework before creation has completed. Lifecycle bridges must be idempotent and fail open when native state is unavailable.

Keep blocking I/O off the main thread and bounded. Treat startup, storage, rendering, removable media and OEM framework interactions as crash-sensitive.

## UI

Use runtime window bounds and insets. Support normal, split/windowed and InCar layouts without fixed assumptions about a particular panel's reserved pixels.

## Kotlin/Java

- Java 17 source/target.
- Prefer `val` and clear Kotlin/Java interop; preserve public Java call sites when porting code.
- Run `./gradlew detektCheckAll` for Kotlin static analysis.
- Run the current ktlint workflow or repository formatting helper for edited Kotlin.
- Preserve behaviour unless the task explicitly changes product semantics.

## Validation

Release-gating local/fresh-clone commands are:

```bash
cd android
./gradlew lintAllModules detektCheckAll
./gradlew -Parm64 assembleInCarProfileable
./gradlew -Px86_64 app:testInCarDebug sdk:testDebug
```

The CI InCar lane additionally runs the retained SDK connected tests, creates an ephemeral signer, builds release-equivalent `InCarRelease`, verifies package/version/certificate properties with `android/tools/verify_in_car_apk.sh`, and performs an x86_64 startup smoke test.

Other retained flavours and Wear may be compiled manually when a change affects those compatibility surfaces, but they are not required publication or release CI lanes for this fork.

An emulator validates Android behaviour only. Physical TS18 launch, windowing, audio and vehicle lifecycle claims require separate device evidence.
