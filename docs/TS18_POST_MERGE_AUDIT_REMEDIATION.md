# TS18 post-merge review remediation and qualification

**Target:** TS18 Android 10/API 29, arm64 InCar testing variant, `app.organicmaps.incar`.
**Baseline master merge:** `9664fe8a8445155437fb3dea477c6265ec0052cf` (merged #58 followed by #59 using merge commits).
**Scope:** This branch makes narrow follow-up fixes; it does not introduce a new navigation, renderer, task, startup, or retry authority.

## Source fixes

1. **Renderer redundant flat-camera resets:** Always discard pending auto-perspective events when the active route explicitly disallows perspective (even when the camera is already flat), without unnecessarily resetting animations and emitting frames on repeated no-op messages. The native renderer remains the sole owner of presentation and the guarded queue. The C++ seam probe checks the production permission method; actual frame-time savings are NOT measured.
2. **Place-page redraw caching:** Restrict the same-selection short-circuit to the InCar flavour. Also include the native Save/Restore label state in InCar cache invalidation; a button's title can change without a button-type or selection change. Other Android flavours keep upstream-style rebuilding. Window-resize/generation invalidation remain in place.
3. **Countdown localisation:** Route visible GO TO countdown suffix and START countdown label through translatable Android resources. Existing display order, triggering behaviour and timings remain unchanged.

Regression checks in `android/tools/tests/test_in_car_controls.py` include source assertions and a compiled, executed seam test of the actual C++ renderer permission method. The queue implementation is separately inspected as source; these checks do not simulate the real GPU, thread scheduler, navigation task, or TS18 hardware. Android build/lint, device tests and performance traces remain independent requirements.

## Second-pass exact-device boundaries (TS Project sources S01/S03/S06/S15/S17)

- The last exact-device runtime capture (12 September 2026) identifies Android 10/API29, arm64, 1280×720 physical display, dynamic top/right SystemUI regions and custom HOME `com.cbkii.ts18launcher/.HomeAlias`. These are last-observed, not proof of the present session. Respect runtime insets/bounds; preserve HOME and protected DoFun/Topway packages.
- Organic Maps owns map rendering, place-page controls, route build/START and active navigation. The standalone launcher (`cbkii/ts-theme`) owns launch/task/window presentation and HOME handoff; SystemUI (`cbkii/ts-sysui`) owns system bars/insets. Do not use root, Magisk, Topway private calls or force-start launcher apps to paper over an Organic Maps render issue.
- Establish location failures in order: permission → AppOps → enabled provider/GNSS → fix delivery → routing use. Do not infer a location failure from a blank task/focus parse; inspect owning process/UID and recheck boundedly.
- Regression-check activity/fragment destruction and current selection before dispatching delayed GO TO/START. Preserve valid task identity, existing navigation progress, user permission flows and click cancellation. Don't assume a green API29 emulator test establishes real ACC/window behaviour.
- Source and CI qualification are Q0/Q1. Physical install is Q2, immediate runtime Q3, process restart Q4, reboot Q5, cold boot Q6, ACC sleep/wake Q7; report them only after direct validation.

## Acceptance and release gates

- Build/check exact PR head: Android Lint, Android Check, generated InCar styles, routing/JNI contracts, touch target checks, Android API 29/30 smoke and an arm64 release-equivalent APK.
- Source test: verify duplicate disallowed-camera policy messages skip redundant presentation, while a changed policy/manual override/perspective screen still reconciles.
- Source/variant test: verify non-InCar place pages continue to rebuild and InCar route action slots/countdowns remain correct.
- Localisation: verify formatted labels in English and one non-English locale; check long text/RTL layout behaviour on the limiting display.
- **Physical TS18 qualification remains NOT_RUN until performed.** With downloaded offline maps, test real camera recenter/perspective changes, repeated policy events, START/GO TO cancellation and stage handoff, route changes, shallow windows, task/HOME returns, GPS loss/reacquisition and ACC sleep/wake. Capture profile traces, frame times, CPU/RAM and operating conditions. See `docs/TS18_CONSOLIDATED_UI_VALIDATION.md`.
- Cross-repository dependence: qualify `cbkii/ts-theme#16` (launcher R1) against the fixed Maps baseline before attributing task/window failures to Maps R2/R3.
- Do not claim performance improvements or production suitability from source-only tests. Keep a compatible signed rollback APK and verify package/version/signature continuity before physical installation.

**Publication:** A TESTING APK may be requested through the repository's established protected `testing-apk` workflow only after source completion and available CI checks. Its `BUILD_INFO` must identify the exact committed PR head and built SHA. Do not merge this PR automatically or publish a production release.


## CI qualification caveat

The existing whole-tree `tools/unix/clang-format.sh` code-style job reports extensive formatting drift in approximately 30 files inherited from merged baseline #58/#59, including files outside this remediation. It is a real red CI job even when source-contract tests pass. This PR must not suppress that workflow or bulk-reformat unrelated files to hide the baseline difference. Distinguish inherited drift from formatting of lines changed by this branch; resolve the formatter baseline in a separately scoped review if required before merge.
