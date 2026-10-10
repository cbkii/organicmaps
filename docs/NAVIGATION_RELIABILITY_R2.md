# R2: InCar startup and configuration handoff

This follow-up to PR #55 addresses app-owned lifecycle defects found during the
launcher R1 investigation. It does not depend on a Topway service or request a
window mode. Apply/qualify launcher R1 first, then test this snapshot with it.

## Startup authority

Previously, the native asynchronous completion callback could call
`SplashActivity.processNavigation()` after Splash paused. Repeated resumes could
also call initialization again; API-result handoff leaves Splash alive and had
no dispatch latch. InCar now owns one initialization and one dispatch claim per
Splash instance. Completion while paused retains readiness and waits for resume.
Destroy/fatal-error closes that instance; a late callback cannot launch another
Activity. Core callbacks enter the UI thread before reading this state.

This leaves the permission request, original intent payload, URI grant subset,
API-result delivery and first-run camera policy in their existing owners. It does
not bypass resource downloads or claim that Splash completion means a drawn map.
New startup logs identify the task and whether handoff dispatched or deferred.

## Configuration authority

InCar records its UI-mode baseline on the map Activity instance. A configuration
callback with unchanged mode no longer immediately destroys the map after the
base Activity invokes the existing geometry coordinator. Day/night changes and
non-car mode-type changes still recreate; car-mode-only changes retain the
existing exemption. Other flavours retain their existing configuration path.

No `configChanges` expansion, launchMode/taskAffinity change, renderer reset,
fixed TS18 dimensions or OEM call is added. Size changes which Android handles
through recreation still do so. Expanding in-place size handling requires actual
map/surface/native/touch evidence; the existing clean-emulator resize smoke can
pass while resource bootstrap prevents reaching the map.

## Validation and limits

The new JVM tests exercise completion while paused, return before completion,
repeated/API-result handoff, fatal/destroyed callbacks, recreated-instance
ownership, unchanged/car UI-mode transitions and theme changes. Existing tests
cover original-intent consumption and geometry recovery. Repository Android Check
must pass lint/detekt, app/SDK JVM tests, API29/API30 emulator smoke, the retained
SDK connected suite and arm64 release-equivalent APK verification for the exact
head. Android Check now runs the existing scope verifier after recursive checkout
and detekt alongside lint, so these required checks also cover this stacked PR
whose base is not master. TESTING signing is separately requested for physical installation.

Local Android/Gradle/JUnit builds are NOT_RUN: this host has no Android SDK or
Gradle. The Java compiler module is available despite the missing javac wrapper;
10 committed pure-policy tests passed in a JVM adapter with minimal Android
constants and assertion stubs. This does not exercise the Android Activity runtime. Local scope verification was attempted, but the inherited
sparse object cache cannot provide every tracked blob; canonical full recursive
CI is the authority. Do not weaken the scope guard to hide that limitation.

Physical TS18 qualification is NOT_RUN. Pair the exact signed R1/R2 APKs and
record boot state, current user, task/component/stack, root phase/deadline,
Activity lifecycle, bounds, visible/drawn window and map/surface/native dimensions.
Run 20 cold starts, 30 returns from other apps and 20 fullscreen-to-HOME returns.
Each stable state needs one owned task, rendered map, aligned touch, no unintended
foreground theft, crash or duplicate handoff. Keep the permission/download stages
in the trace and exclude them from map-ready claims. See [TS18 validation](TS18_VALIDATION.md) and the
[launcher R1 matrix](https://github.com/cbkii/ts-theme/blob/fe57a7f035485632dc06d749fcc3d97b21aaddce/docs/NAVIGATION_RELIABILITY_R1.md)
for the wider acceptance matrix.

R3 remains conditional on those physical observations proving an OEM/window
contract defect after R1/R2. No protected DoFun/TW/SystemUI or platform change is
part of this PR. Roll back this PR's app changes and use the retained PR #55
TESTING snapshot if it regresses startup or UI mode handling.
