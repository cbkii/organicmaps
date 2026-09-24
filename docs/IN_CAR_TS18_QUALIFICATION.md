# InCar screenshot and TS18 qualification bundle

This playbook separates deterministic repository checks from physical TS18 qualification. A green build or emulator/static check is not physical-device proof.

## Prerequisites

- Use the TESTING APK built from the exact PR head being qualified and record its commit SHA/provenance.
- Confirm the attached target is the intended TS18 before mutation or install. The current project baseline is Android 10 / API 29 unless current device evidence disproves it.
- Keep the normal Topway launcher/reverse-camera/recovery path available. These checks do not require root and must not alter MCU/CAN, firmware, panel or vehicle services.
- Install/launch through the normal project TESTING procedure. This capture tool is read-only; it does not install, grant permissions, change settings or force lifecycle state.

## Capture tool

From the repository root, prepare each required UI state and capture it with:

```text
python3 android/tools/capture_in_car_validation.py <label> --serial <adb-serial> --package app.organicmaps --output in-car-validation
```

Each invocation creates a timestamped directory containing the PNG plus device fingerprint/API, display geometry, package state, activity state and window state. Keep every directory with the exact-head qualification record.

Canonical labels are enforced by the script:

- `browse-light`
- `browse-dark`
- `route-preview`
- `active-navigation`
- `speeding-at-or-above-5pct`
- `speeding-below-5pct`
- `compact-windowed`
- `quick-destinations-expanded`
- `quick-destinations-normal`
- `my-position-free`
- `my-position-follow`
- `search-results`
- `driver-modal`
- `pip-active`
- `pip-restored-fullscreen`

## Acceptance checks

For the clarity/high-glare change, verify on the physical TS18:

1. **Browsing/light:** roads remain distinguishable in direct/high-glare conditions; stronger road fills/casings do not erase useful local-road or land-use context.
2. **Browsing/dark:** no unintended night-map colour change from the light-only glare work.
3. **Camera/location controls:** the user-facing duplicate Driving View control remains absent. `+`, `-` and My Position read as one grouped rail; My Position visibly distinguishes free from follow/follow-and-rotate state; track recording remains a separate action.
4. **Route preview:** START remains obvious and the route preview does not clip critical controls.
5. **Active navigation:** END and manoeuvre/speed chrome remain readable and stable; capture both below-threshold and at-or-above-5%-over-limit speed states where safely reproducible using controlled/test data rather than unsafe driving.
6. **Quick Destinations:** normal and expanded states remain right-side vertical, touchable and free of clipping/overlap.
7. **Search/results:** long destination names may use two lines; description may use two lines; opening/distance metadata stays bounded. The deliberate remaining truncation risk is end ellipsis on unusually long title/description/metadata strings after those bounds are exhausted. Verify enlarged-font/localised strings do not hide the primary action or row identity.
8. **Driver modal/action menu:** panel width, hierarchy and touch targets fit the actual display without action buttons or list rows being cut off.
9. **Compact/windowed:** controls remain reachable at the smallest supported runtime bounds and with enlarged font.
10. **PiP:** standard Android PiP, where enabled by the current exact build, remains generic rather than TS18-private. Capture PiP and fullscreen restoration separately; verify HOME/fullscreen restoration and recreation. Do not treat PiP support in static code or the APK manifest as proof of Topway runtime behaviour.

## Lifecycle qualification

After the immediate checks, repeat the relevant browse/navigation/search/Quick Destination state after:

- activity/process restart;
- reboot/cold boot;
- ACC sleep/wake;
- reverse-camera entry/exit;
- PiP enter/exit where supported.

Record PASS/FAIL/NOT_RUN independently for each boundary. A dependency-blocked check is BLOCKED, not FAIL. Do not claim physical TS18 success unless these steps were actually exercised on the target unit.

## Repository-side checks

Before review/merge, the exact PR head should have its applicable GitHub checks green, including code style, Android/repository scope, InCar touch-target/compact-chrome contracts and generated InCar drawing-rule verification. The generated drawing rules and packaged Android asset must match the exact source style revision.
