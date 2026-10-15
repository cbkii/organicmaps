# TS18 post-merge review remediation and qualification

**Target:** TS18 Android 10/API 29, arm64 InCar testing variant, `app.organicmaps.incar`.
**Baseline master merge:** `9664fe8a8445155437fb3dea477c6265ec0052cf` (merged #58 followed by #59 using merge commits).
**Scope:** This branch makes narrow follow-up fixes; it does not introduce a new navigation, renderer, task, startup, or retry authority.

## Source fixes

1. **Renderer redundant flat-camera resets:** Only reconcile camera presentation when the policy changed, a manual override was cleared, or the actual screen is still in perspective. Do not repeatedly reset map-plane animations and emit frames for identical already-flat policy updates. The native renderer and its queued event ownership remain unchanged.
2. **Place-page redraw caching:** Restrict the same-selection short-circuit to the InCar flavour. Other Android flavours keep the upstream-style rebuild behaviour. InCar selection-generation and window-change invalidation remain in place.
3. **Countdown localisation:** Route visible GO TO countdown suffix and START countdown label through translatable Android resources. Existing display order, triggering behaviour and timings remain unchanged.

Regression contracts are added to `android/tools/tests/test_in_car_controls.py`, already executed by the InCar Touch Target Check workflow. They are static code contracts; Android build/lint, device tests and performance traces remain independent requirements.

## Acceptance and release gates

- Build/check exact PR head: Android Lint, Android Check, generated InCar styles, routing/JNI contracts, touch target checks, Android API 29/30 smoke and an arm64 release-equivalent APK.
- Source test: verify duplicate disallowed-camera policy messages skip redundant presentation, while a changed policy/manual override/perspective screen still reconciles.
- Source/variant test: verify non-InCar place pages continue to rebuild and InCar route action slots/countdowns remain correct.
- Localisation: verify formatted labels in English and one non-English locale; check long text/RTL layout behaviour on the limiting display.
- **Physical TS18 qualification remains NOT_RUN until performed.** With downloaded offline maps, test real camera recenter/perspective changes, repeated policy events, START/GO TO cancellation and stage handoff, route changes, shallow windows, task/HOME returns, GPS loss/reacquisition and ACC sleep/wake. Capture profile traces, frame times, CPU/RAM and operating conditions. See `docs/TS18_CONSOLIDATED_UI_VALIDATION.md`.
- Cross-repository dependence: qualify `cbkii/ts-theme#16` (launcher R1) against the fixed Maps baseline before attributing task/window failures to Maps R2/R3.
- Do not claim performance improvements or production suitability from source-only tests. Keep a compatible signed rollback APK and verify package/version/signature continuity before physical installation.

**Publication:** A TESTING APK may be requested through the repository's established protected `testing-apk` workflow only after source completion and available CI checks. Its `BUILD_INFO` must identify the exact committed PR head and built SHA. Do not merge this PR automatically or publish a production release.
