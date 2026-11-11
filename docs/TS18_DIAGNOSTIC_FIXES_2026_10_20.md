# TS18 diagnostic fixes — 20 October 2026

## Implemented scope

- Active-route crash: the reported `#fe774c10` / `#fe7c4f0f` speed-warning ribbon reaches AndroidX contrast with alpha below 255. Composite the calculation input over opaque card RGB; retain drawn transparency. Test every alpha with actual AndroidX and strengthen the existing render harness so a translucent contrast background throws instead of being silently accepted.
- EGL: the original PowerVR sequence starts with `QuerySurfaceSize` during a window resize, then native-window/buffer acquisition errors and repeated failed swaps. Use SurfaceHolder's supplied pixel dimensions for resize, reject detached/invalid Java surface callbacks, clean up partially attached windows independently of full factory validity, and never terminate the shared display for a failed replacement window. Quarantine a failed-swap surface until a real lifecycle reattachment; Activity resume alone cannot re-admit it. Existing render-thread disable/attach synchronisation remains the owner; no new renderer or route restart.
- Timezone: an empty field in old maps is ordinary missing metadata. Skip deserialisation, clear any previous cached zone when reloading, immediately use the existing optional-zone/system-localtime fallback, and report non-empty malformed metadata at most once per process. Valid location/map-region timezone metadata retains priority.

The system clock is an accepted boot quirk, not a firmware repair target. Fallback reads the current system clock on each operation, so later synchronisation is used. There is no new fixed 2024 timestamp or clock-setting writer.

## Companion PRs

| Repository | Change | Dependency |
|---|---|---|
| [ts-theme #31](https://github.com/cbkii/ts-theme/pull/31) | Guard repeated fallback-button padding mutations; production-panel 1,000-measure regression | Stacked on #30, retaining #26/#27 startup/task work |
| [Auxio-TS #281](https://github.com/cbkii/Auxio-TS/pull/281) | Restore Material icon-button overlay inheritance and `colorOnContainerUnchecked`; API29 day/night inflation tests | Stacked on #278's silent preparation and bounded Play |
| [ts-sysui #10](https://github.com/cbkii/ts-sysui/pull/10) | Optional exact-SystemUI contrast WTF filter with narrow matcher and independent opt-in | Current main; independent of XTService #9 |

Auxio 26.6.8's released resource table identifies the failing `0x7f04011d` as `colorOnContainerUnchecked`. Its custom empty-parent overlay drops the Material selector token. No broad theme replacement, media authority change or protected `com.tw.music` modification is needed.

The historical launcher warnings name most dashboard children. Current #30 already coalesces root geometry and compares unchanged placement. The remaining panel `setPadding` defect is fixed separately; only physical testing can establish that the entire historical storm has stopped. Existing startup recovery is retained, including source-specific readiness, accepted/uncertain claim ownership, bounded single-flight staging, permission/protected-flow yielding and no repeated blind root service start.

## Findings requiring no speculative app mutation

- Input-channel warning: no direct `inputFeatures`/`INPUT_FEATURE_NO_INPUT_CHANNEL` assignment found in the four inspected Android application source sets. The AOSP-era diagnostic can be emitted without the flag actually being set. Exact OEM WindowManager caller/flag attribution remains unproven; do not infer a broken app input contract from the message alone.
- GMS: optional-service reduction remains low priority. Existing summaries establish ANRs, not the responsible blocking thread/component. No component-disable, background throttle or invasive GMS hook is justified by the inspected evidence. Core location and phone integration remain available.
- HAL, Wi-Fi integrity, generic jank/thermal and diagnostic overhead remain explicitly deferred by the owner.

## Limits and qualification

A failed EGL surface is retained as unusable until the normal surface lifecycle supplies a replacement. This prevents repeated submissions but is not proof of automatic driver recovery without a lifecycle transition. Native context-loss recovery and unexplained process disappearance remain separate from the demonstrated Java crash.

The timezone fallback uses the system zone when map metadata is absent. A globally accurate coordinate-to-IANA fallback is **not implemented**: coordinates plus wall-clock time do not determine timezone boundaries, and this runtime has no qualified geographic timezone lookup dataset. Do not substitute a longitude offset or one timezone per country (Australia alone has multiple zones and exceptions). Supplying a compact, licensed geographic lookup is separate work if cross-zone search on legacy maps must be exact.

Physical TS18 tests are NOT_RUN: multiple routed trips with speed warnings, day/night and HOME/fullscreen changes; lost/renewed surfaces; three cold boots; first silent readiness and audible Play with paired Auxio/launcher heads; source handover; repeated source/HOME cycles; permission flows; ACC sleep/wake; SystemUI filter enable/disable and unrelated WTF preservation. A host EGL boundary test does not exercise PowerVR. Native timezone GTests require the retained full-engine test target and are reported separately from Android builds.

## Primary contracts

- [AndroidX ColorUtils](https://developer.android.com/reference/androidx/core/graphics/ColorUtils): contrast backgrounds must be opaque.
- [SurfaceHolder.Callback](https://developer.android.com/reference/android/view/SurfaceHolder.Callback): surface access belongs between creation/destruction callbacks; render threads must stop touching a destroyed surface.
- [Material icon-button styles](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/button/res/values/styles.xml): the base overlay supplies checked/unchecked selector attributes.
