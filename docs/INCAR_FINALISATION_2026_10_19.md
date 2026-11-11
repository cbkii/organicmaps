# Paired Android 10 InCar finalisation — 19 October 2026

The owner's section 16 prompt targets #72 → #69 with merge ancestry preserved, then keeps
#69 → master and launcher #26 → main open for final physical qualification. No default merge.

| Requirement | Implementation / evidence boundary |
|---|---|
| IC-04 course quality | One native owner predicate supplies rapid acquisition and direction assignment. Available speed/bearing/accuracy must be finite; speed lower-confidence bound clears 5 km/h; bearing accuracy <=30 degrees. Absent OEM accuracy remains supported. Fresh, strictly newer evidence is required; stale/replayed/out-of-order fixes cannot consume the first-course adjustment. Float bit checks survive native `-ffast-math`. |
| IC-05 status work | Changed-only resource text, max width, margin, translation and visibility; reused geometry scratch arrays/Rect. UI reads lightweight availability snapshots while fix age remains live. Existing five-second owner watchdog refreshes permission/provider evidence; explicit diagnostics refresh immediately. Periodic eager verbose diagnostics removed; recovery/event logs retained. No CPU/frame improvement is claimed without measurement. |
| IC-03 native owners | New headless tests compile complete production AsyncRouter, MessageQueue, EngineContext and TileInfo translation units. Controlled domain/platform doubles avoid Qt/generator restoration. Actual retained queue and driving-policy tests also execute, including `-ffast-math`. This is stronger worker/read/queue evidence, not full engine/GPU/bookmark integration. |
| Additional confirmed defect | The full AsyncRouter test exposed queued progress from a superseded delegate reaching GUI. GUI execution now rechecks delegate cancellation using owned lifetime; late progress cannot update the replacement route. |

## Qualification limits

Retain the six fast production-method probes, controls/camera/location/compact harnesses and
full Android build/emulator lanes. CI now invokes all these regardless of ancillary path filters.
The complete BookmarkManager reload/compilation integration and real GPU/backend tests still
require a supported engine-linked native runner: this Android-only checkout disables host CMake
tests and the inherited native tests link removed/unqualified Qt/generator test support. Do not
claim these as run or restore desktop/generator bulk merely to make them runnable. Compilation
cleanup has the retained native integration source and executed narrower boundary regression.

## Requirements retained from the consolidation audit

27 of 29 October closed heads were retained; launcher #21/#22 remain intentionally superseded.
Maps #70 is part of #69, and #60 was a retired TESTING carrier. The previous merge checkpoints
retain their original trees and ancestry.

Preserve all of the following in the final combined diff:

- Four camera controls, freshness/held-position recovery, native one-shot recenter, route camera
  precedence, manual gesture protection, the 650 ms acquisition cap and distant-fix shortcut.
- Menu Track Recording using actual state/save-and-stop; icon/text rows; Search+Menu browsing
  dock without duplicate Driving/recording/camera FAB or persistent browsing Bookmarks button.
- RHD-left/LHD-right controls, compact/Quick collision handling and one inset/viewport authority.
- Existing measured search/place/route-sheet viewport and native ruler/copyright attribution
  offsets, readiness gating and full viewport restoration. These are implemented, not rebuilt.
- GO TO/START shared saved 2/3/5/10/15/20/30 s countdown with missing-only 10 s default,
  exactly-once route actions, generation identity, full touch/key/accessibility/focus/lifecycle
  cancellation and no accepted permission/disclaimer.
- NOW/AFTER/LANES hierarchy, warning/hysteresis, adaptive footer/START/END rails, approved
  marker palette/atlases/drules and cyan/blue route treatment/label exclusions.
- Fresh persistent road snap/parking release, active-route isolation, speed-limit provenance,
  idempotent permissions/providers, supervised GNSS recovery, TTS containment and default arrow.
- All 20 curated upstream selections; no unrelated upstream style/platform/generator tree.
  Preserve upstream `e78a075b7ae77bb31510918a26545d77ea4ae690` as an ancestor through normal merges.

## Final status authority

PR metadata records exact source/CI/APK identity after push and integration. The final signed
pair supersedes isolated snapshots; retained rollback assets must be observed, not assumed.
Physical TS18, installed identity, audible playback, real map pixels/input, camera latency,
frame/CPU/memory/thermal and every process/reboot/cold boot/ACC campaign remain **NOT_RUN**.
The 64-shape batch is a shape-count bound, not a byte/memory/time bound. No speculative cache,
concurrency, graphics backend or quality changes are made.

## Consolidation and formatting

PR #72 was merged into #69 with merge commit `80538742a363c8901e767ae672be5d15a2dcc07a`, retaining both original parents and exactly the tested `d43770dbfbc9869d900e5cff81f4fa7ce368ffc1` tree. The acknowledged upstream `e78a075` and original #69 head remain ancestors. Android Check 39186090471 passed lint, arm64/x86 builds, unit/connected tests, release-equivalent packaging and API29/API30 cold/warm/resize smoke before consolidation.

The #69 whole-tree formatter run 39188430632 reported 23 files. Eight changed integration files receive its formatting-only corrections. The remaining 15 are byte-unchanged from default `f13716f7b1dad1d3efc8cf1191e533bf75da439b`: InCarAccessibilityDelegate, InCarControlPositionPolicy, InCarNavigationCountdown, InCarNavigationCountdownPolicy, InCarUiModePolicy, SearchFragmentController, PlacePageActionProjection, PlacePageViewModel, InCarControlPositionPolicyTest, InCarNavigationCountdownPolicyTest, MapButtonsControllerPolicyTest, PlacePageActionProjectionTest, LocationState.cpp, RoutingController.java and drape_engine_driving_view.cpp. The inherited failed check remains visible; no formatter or permissions policy is weakened and unrelated files are not bulk-formatted. New-head software qualification must be checked after the formatting commit.

Physical acceptance and full-engine BookmarkManager/GPU qualification remain as bounded above; consolidation does not clear them.
