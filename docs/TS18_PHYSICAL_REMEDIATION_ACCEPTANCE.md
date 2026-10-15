# TS18 physical remediation: two TESTING checkpoints

Target: Android 10/API29, arm64, `app.organicmaps.incar`. No firmware, Topway,
SystemUI, launcher or upstream writes. Do not merge these PRs during qualification.

## Build identity and rollback

Use the signed PR #62 snapshot first (recenter isolation), then the signed PR #64
snapshot (combined stack). PR #63 is the intermediate UI dependency. The rolling
TESTING draft retains only two complete snapshot groups: download both groups,
including BUILD_INFO, signer and SHA256SUMS, before requesting another snapshot.
Match each APK's built/source commit to its PR's current head, package, version,
signer and SHA-256. A workflow marked successful without a completed build and
publication is not an APK. Keep the previously installed compatible signed APK.

Install using the same package/signing identity without clearing application data
or uninstalling. If Android rejects an older version, do not uninstall to force
rollback: preserve offline maps/settings and use a compatible monotonic-version
rollback build. Do not reset the user's selected countdown or theme preference.
The missing-preference START default is already 10 seconds.

## Run each case

Park safely with downloaded local maps and a current GPS fix. For every row record
**P** (pass), **F** (fail), or **N** (not run), plus an optional short comment. Use a
passenger or replay for travel cases; the driver must not operate the test UI.
Record the installed APK/build identity and current HOME once at the start.
A source check, emulator run or install is not physical acceptance.

| Case | Action | Pass condition |
|---|---|---|
| 1: P0 warm recenter | Use Follow to establish location. Pan away; tap `•`. Repeat after remaining stationary and after a low-speed fix. | Same correct locality as Follow; requested camera perspective and Follow mode preserved; no delayed jump. |
| 2: P0 cold recenter | Restart the app; tap `•` before a fresh fix, then wait for GPS. Pan manually while waiting. | No unrelated or stale jump; a later fix does not override the newer manual action. |
| 3: day/night | Select Light, Dark, then System/scheduled as applicable; repeat during free driving, planning and active navigation, including after app restart and window resize. | Map and all overlay rails/buttons/banner follow the chosen mode together. No retained light surface on a dark map or inverse. |
| 4: rails | Check both handedness settings, normal and shallow windows; press each camera and Quick Destinations cell. | Matching continuous translucent rails, equal cells, original-size icons, blue Quick glyphs; no persistent circular cell fills or stretched shared surface. |
| 5: warning replay | Using a replay/passenger observation, compare normal, orange and severe red warnings in Light and Dark. Check with and without lanes. | NOW, road text, AFTER and lanes all receive warning colour; text/glyphs remain readable. Speed circles keep their separate warning/limit styling. Day severe speed is black on bright red. |
| 6: START | Build a vehicle route. Observe the one-row summary, MORE and START; rebuild/change destination; check a clean/default preference and a deliberate saved override. | One responsive row; no stale summary during rebuild/error; default 10s, saved overrides retained; manual START/cancellation still works. |
| 7: add stop/END | During guidance, choose cog → Search/Add stop. First cancel; then choose a result and add a stop. End guidance. | Cancel preserves the original route. Selected stop rebuilds via the existing routing owner. END stays inside the rail and cancels navigation. |
| 8: road continuity | Replay/observe cruise → crawl → stop → resume, sharp connected bends and multi-edge roundabouts, including roads beside parking. | Existing road lock does not release because speed drops. No unrelated parallel-road jump. |
| 9: parking/off-road | Observe a genuine low-speed turn into parking, parking exit, mapped parking aisles, and real off-road departure. | Parking proximity/compass jitter alone does not release a plausible public road. A trusted approximately 70° departure or sustained geometry mismatch can release; road reacquisition remains available. |
| 10: speed display | Observe known/replayed speeds below 3km/h, exactly 3km/h and above, plus missing/stale speed. | Below 3km/h displays zero; threshold and above remain normally formatted. Missing/stale speed is unavailable, not a fabricated zero. |
| 11: lifecycle | Return via HOME, resize/fullscreen, app restart, Android reboot, then genuine ACC sleep/wake. | Same task/navigation authority, current position, correct theme, no stale snap or camera command. Record each boundary separately. |

## Evidence and remaining limits

Capture raw/provider fix age and accuracy, native held/camera/display coordinates,
Follow mode, matcher state/edge and transition reason, task/window bounds/focus,
route state and a short labelled screen recording. Keep each layer separate:
raw GPS -> road projection -> camera position -> window presentation.

Static causes corrected: held-position/freshness mismatch; out-of-order held
observations; stale held accuracy/zoom; landscape summary omission/invalidation;
opaque child navigation surfaces; warning contrast; inverted nested navigation
foreground; implicit rail-width expansion; and parking gates tied to the winning
candidate/per-fix displacement rather than the retained road/accumulated course.

The 70° threshold is a device-report hypothesis, not a measured optimum. Trusted
turn evidence excludes stationary holds; an extremely slow entry may require
sustained road mismatch to release. Display retention remains distance bounded
(35m Good / 55m Moderate, or an already larger normal limit), age bounded (5s),
and suppressed for building/open-area signals. It never changes active routing.
GPS traces, real rendered screenshots, frame timing, long-session resource use,
reboot and ACC behaviour remain NOT_RUN until tested on the unit.
