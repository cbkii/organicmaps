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

## Windowing authority

The Topway/DoFun desktop navigation window must not be assumed to be Android's standard split-screen or pinned PiP mode. The system/launcher owns outer task/window bounds. Organic Maps should react to the actual content, `MapView`, `SurfaceHolder` and native renderer dimensions it receives; `isInMultiWindowMode()` and `isInPictureInPictureMode()` are diagnostic annotations rather than geometry authority.

Do not hard-code dimensions recovered from one DoFun theme or panel. Equivalent Topway Window-* units are only covered when their observed task/surface behaviour matches the tested path.

## Window trace capture

For a windowing regression, use the bounded read-only host-side capture after each user-performed transition:

```bash
bash android/tools/capture_in_car_window_trace.sh \
  --package app.organicmaps.incar \
  --label desktop-to-full \
  --out ./ts18-window-trace
```

The capture correlates current Activity/task/window dumps with Organic Maps' structured app-side geometry logs. Run a separate labelled capture for each transition rather than leaving broad logging active.

The exact DoFun outer-window implementation remains unverified until a physical capture establishes whether the firmware is using standard Android freeform/split/PiP, a vendor task-windowing path, a virtual display/container, or another Topway mechanism. Do not change `MwmActivity` task/launch semantics merely to guess around that uncertainty.

## Window transition acceptance matrix

For window/surface changes, identify the exact APK/commit and test at least:

1. cold start in the DoFun desktop navigation window;
2. desktop window -> full -> desktop;
3. full -> Home -> desktop;
4. 10 repeated full/window/full cycles;
5. Android split-screen enter, multiple divider sizes and exit where exposed by the firmware;
6. split/window -> Home -> return;
7. reverse-camera enter/exit while full and compact;
8. status/navigation-bar visibility changes;
9. route planning -> Start -> active navigation -> End in both stable sizes;
10. Search, Place Page and Settings return;
11. process relaunch;
12. reboot/cold boot;
13. ACC sleep/wake where applicable.

For every stable state require:

- no unintended fullscreen transition;
- no persistent black unused region beyond intentional launcher decoration;
- no clipped or stale renderer;
- map and touch coordinates agree;
- no duplicate activity/task/rendering authority;
- no crash or process death;
- content/map/surface/native dimensions converge in a bounded interval.

## Package identity

Physical tests must identify the APK variant/package and commit. InCar release validation should preserve `app.organicmaps.incar`; profileable/debug package suffixes must not be confused with release evidence.

## Fail-open rule

InCar-specific integration should not make ordinary Android startup dependent on unavailable OEM/vendor services. Native-framework lifecycle calls must remain safe before framework creation and across detach/recreate boundaries.
