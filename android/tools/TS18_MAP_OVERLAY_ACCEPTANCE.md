# TS18 InCar map-overlay acceptance — PR #66

## Source and safety

This checklist qualifies **app-side** map UI for the exact installed `app.organicmaps.incar` TESTING APK. GitHub build/check success is **not** TS18 acceptance. Verify the test APK's release-note PR/head/built SHA, package, signer and SHA-256 before interpreting screenshots; reject stale/mixed snapshots. Stop if the installed package is a different build. Do not remove or reset stock Topway packages, edit SystemUI, or test manoeuvres while driving. Park the vehicle safely.

For the canonical **install, provenance and rollback** procedure, follow [TS18 consolidated UI validation — Install and rollback](../../docs/TS18_CONSOLIDATED_UI_VALIDATION.md#install-and-rollback). Before collecting results, record the exact PR/head and **built SHA** from the draft TESTING release notes, plus APK filename, SHA-256, installed package identity, versionCode/versionName and signing-certificate SHA-256. On a host with Android build tools, `sha256sum candidate.apk` and `apksigner verify --print-certs candidate.apk` can independently confirm the archive and signing identity. Do not infer the installed signer from the filename. Keep the manifest together with the screenshot/result set; if the installed build cannot be matched, mark the run **BLOCKED**.

Use the existing read-only Termux collector (`android/tools/ts18_termux_in_car_ux_capture.sh`), or the ADB-side `capture_in_car_validation.py`. Both collect a labelled screenshot plus window/device evidence; neither infers a test result. If a probe cannot run, mark it **BLOCKED** rather than treating missing data as a UI failure.

### One-time preparation

1. Start the installed Organic Maps InCar APK from its existing task or normal launcher.
2. Enable Developer Options **Show taps** if desired and leave Android display/density in its known normal TS18 configuration. Record the currently selected physical control side (RHD/LHD) and map theme.
3. In Termux, `cd` to the folder where the collector script actually resides. Avoid moving or editing the script while testing.
4. For every row below, perform the **manual action first**, then run `bash ts18_termux_in_car_ux_capture.sh LABEL` using the label shown. Tap **P** (pass), **F** (fail), **N** (not run), or **B** (blocked) in your own result sheet/notes; optionally record a few words describing the failure. Each capture is exported under `/storage/emulated/0/Download/OrganicMaps-TS18-UX-Qualification/`.

For non-ADB Termux captures that cannot access another UID or screenshot service, do not change global SELinux or chmod protected paths. Read the collector's `STATUS.txt` for prerequisite failure vs actual app failure.

## Manual scenarios and expected result

| # | Label | Manual action, parked | PASS when |
|---|---|---|---|
| 1 | browse-light | Open map, browse without a selected POI in day theme | Camera/Quick rails have consistent surfaces; Search/Menu footer is separated, all touches work |
| 2 | browse-dark | Switch to night theme and return to browse | Icon, borders, rail backgrounds remain visible; no double circles |
| 3 | quick-destinations-normal | Set each available home/work/fuel shortcut; open More | Shortcuts and More do not cover footer; More dialog has reachable items/settings |
| 4 | driver-modal | Open More, then dismiss by its intended close mechanism | No underlying Quick tap succeeds while modal is open; Quick restores after dismiss |
| 5 | search-results | Open Search, type a short non-sensitive query, show keyboard/results | Search keyboard and result actions reachable; competing Quick controls absent |
| 6 | route-preview | Select a place and request a route, but do not START yet | Place/route controls including START unobstructed; optional Quick hidden |
| 7 | active-navigation | Start a stationary safe test route or suitable route simulation | Navigation ribbon, lane/speed state and END readable; secondary Quick never covers END |
| 8 | compact-windowed | Use intended TS18 map-window mode; repeat browse and navigation, especially near 360dp height | Camera rail changes orientation when needed; Quick fits or collapses without overlap; at zero capacity no optional cell intercepts taps |
| 9 | my-position-free | Pan/zoom/rotate map manually | Gestures reach map outside controls; camera control remains responsive |
| 10 | my-position-follow | Tap Follow and recenter, then pan and restore Follow | Correct mode/label changes; single action per tap; no disappearing 55dp cell |
| 11 | pip-active | Enter the supported task/window reduced or PiP presentation, if available | No malformed hit targets or overlay crash; if app PiP unsupported, mark N |
| 12 | pip-restored-fullscreen | Return to full map task without changing HOME | Geometry converges and all controls respond, no stale prior task/window bounds |

For each row, inspect **the visual pixels and the matching window/activity dump**. Measure rail/Quick/search/menu/START/END rectangles and safe content boundaries in one consistent screen coordinate system; note x/y/w/h at actual device density. A missing screenshot due to failed root screenshot transport is a collector failure, not proof of a UI regression.

## Additional acceptance sweeps

- **Control-side mirror:** repeat rows 1, 6, 7 and 8 with the opposite RHD/LHD layout; do not infer physical side from RTL language.
- **Insets:** cover app full-screen, the actual windowed task, status/navigation bars, safe cutouts and IME. Do not apply Android insets twice to content already clipped by its parent.
- **Native map:** confirm GPS/location dot and selected POI are not hidden by panels when feasible; route polyline/turns remain visible. Report native camera/selection separately from Android control placement.
- **Touch and accessibility:** each action remains at least 48dp in each dimension (55dp minimum planned for extra-compact); verify touchscreen hit areas, clear content descriptions, selected Follow state, visible keyboard action, and hidden views absent from TalkBack traversal.
- **Lifecycle:** repeat normal/warm resume, app process restart and size transitions. Reboot/cold boot/ACC sleep–wake are separate higher qualification levels; record only those actually performed.
- **Legibility:** review both themes over very bright/light and very dark map tiles, and long-language text. Do not claim 4.5:1/3:1 rendered contrast from palette hex values alone.

### Sign-off gate

No unintended overlap among **actionable** controls; START/END, active guidance and modal actions always win over optional Quick buttons; no missed taps/gesture interception in clear map areas; all screenshots tied to the **same installed final-head TESTING APK**. Mark native selected-POI camera padding, road geometry and SystemUI-owned insets **NOT_RUN/UNKNOWN** until those owning contracts are measured.

**Privacy gate before sharing:** these captures may contain saved destinations, precise coordinates, recent searches, personal addresses, serials, account details, notification text and logcat context. Review files locally, redact or omit sensitive screenshot regions and logs, and share only the necessary scoped evidence. Keep original private capture hashes separate from hashes of any redacted copies; never publish unreviewed raw diagnostic archives. This applies even when the collector succeeded.

Bring the capture directory, screenshot(s), results and build provenance back into the PR review. A green CI check alone does not authorise merging.
