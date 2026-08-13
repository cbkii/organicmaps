# TS18 Validation

The InCar product targets direct-display Android devices including the Topway TS18. Repository CI must keep Android 10/API 29 compatibility in view, but CI and emulator results are not physical-device proof.

## Evidence classes

- **CI/build:** source compiles, tests pass, APK package/signing checks pass.
- **Emulator:** Android framework/runtime behaviour in the configured emulator image.
- **Physical TS18:** behaviour observed on the actual head unit and exact APK/build.

Never report the first two as the third.

## Physical validation boundaries

When a change can affect startup, rendering, navigation audio, storage, lifecycle or windowing, record the applicable boundaries explicitly:

1. initial install/launch;
2. force-stop and relaunch;
3. full-screen to split/windowed/PiP transitions where supported;
4. Android reboot/cold boot where startup integration changed;
5. ACC sleep/wake where the feature depends on automotive lifecycle;
6. audible/vehicle interaction only when it was actually tested.

## Package identity

Physical tests must identify the APK variant/package and commit. InCar release validation should preserve `app.organicmaps.incar`; profileable/debug package suffixes must not be confused with release evidence.

## Fail-open rule

InCar-specific integration should not make ordinary Android startup dependent on unavailable OEM/vendor services. Native-framework lifecycle calls must remain safe before framework creation and across detach/recreate boundaries.
