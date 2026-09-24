# InCar screenshot and TS18 qualification bundle

This playbook separates deterministic repository checks from physical TS18 qualification. A green build or emulator/static check is not physical-device proof.

## Prerequisites

- Use the TESTING APK built from the exact PR head being qualified and record its commit SHA/provenance.
- Confirm the target is the intended TS18 before any install or lifecycle action. The current project baseline is Android 10 / API 29 unless current device evidence disproves it.
- Keep the normal Topway launcher/reverse-camera/recovery path available. These checks must not alter MCU/CAN, firmware, panel or vehicle services.
- Install/launch through the normal project TESTING procedure. The capture tools below are read-only: they do not install, grant permissions, change settings/properties or force lifecycle state.
- Do not operate Termux or the head unit while driving. Prepare/capture moving states only with a passenger or in a controlled safe test.

## TS18 Termux collector — preferred physical evidence path

Copy `android/tools/ts18_termux_in_car_ux_capture.sh` to Termux-private storage (for example `$HOME/bin/`) and keep it executable only by the Termux user. The script records ordinary Termux identity first, then uses ordinary Magisk `su` only for bounded read-only Android probes. It deliberately does not use `su -M`, change SELinux, write Android settings/properties, alter packages, or dump last-known precise coordinates.

If Termux does not yet have shared-storage access, run `termux-setup-storage` yourself once. `timeout` is required; `zip` is optional but recommended for a single checksummed result archive.

```text
mkdir -p "$HOME/bin"
cp ts18_termux_in_car_ux_capture.sh "$HOME/bin/"
chmod 700 "$HOME/bin/ts18_termux_in_car_ux_capture.sh"

# Release-like InCar package default:
bash "$HOME/bin/ts18_termux_in_car_ux_capture.sh" baseline

# Override for a different exact installed variant, for example a debug TESTING build:
PACKAGE=app.organicmaps.incar.debug \
  bash "$HOME/bin/ts18_termux_in_car_ux_capture.sh" search-long-name
```

Results are exported under `/storage/emulated/0/Download/OrganicMaps-TS18-UX-Qualification/`. Each run contains the status table, execution-context evidence, exact device/build/display/package/process/window state, selected Topway/DoFun integration evidence, bounded targeted logs, graphics state and a labelled screenshot. When `zip` is available, the script also emits a ZIP and SHA-256 file. A required probe failure makes that run non-zero; optional evidence failures remain WARN/BLOCKED rather than being misreported as feature failures.

Suggested labels/states to capture are:

- `baseline`
- `browse-light-high-glare`
- `browse-dark`
- `my-position-free`
- `my-position-follow`
- `quick-normal`
- `quick-expanded`
- `search-long-name`
- `driver-modal`
- `route-preview`
- `active-nav`
- `compact-window`
- `pip-active`
- `pip-restored`
- `free-drive-straight`
- `free-drive-turn`
- `free-drive-reacquire`
- `post-process-restart`
- `post-reboot`
- `post-acc-wake`
- `post-reverse`

The label is evidence metadata only; prepare the matching UI/runtime state manually before each run. Record the physical observation separately as PASS/FAIL/BLOCKED/NOT_RUN. The collector does not infer visual correctness from a screenshot.

## Host ADB screenshot helper

Where an ADB-connected host is more convenient, prepare each required UI state and capture it with:

```text
python3 android/tools/capture_in_car_validation.py <label> --serial <adb-serial> --package app.organicmaps.incar --output in-car-validation
```

Each invocation creates a timestamped directory containing the PNG plus device fingerprint/API, display geometry, package state, activity state and window state. Keep every directory with the exact-head qualification record.

Canonical ADB labels are enforced by that helper:

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

1. **Exact-device baseline (recommendation 1):** confirm package/version, PID and execution domain, Android release/API, build fingerprint, display size/density, current task/window mode/bounds and current Topway/DoFun integration evidence before judging layout behaviour.
2. **Browsing/light:** roads remain distinguishable in direct/high-glare conditions; stronger road fills/casings do not erase useful local-road or land-use context.
3. **Browsing/dark:** no unintended night-map colour change from the light-only palette work. Shared packed-style geometry does intentionally retain the strengthened road widths, so confirm those widths remain visually balanced at night.
4. **Camera/location controls:** the user-facing duplicate Driving View control remains absent. `+`, `-` and My Position read as one grouped rail; My Position visibly distinguishes free from follow/follow-and-rotate state; track recording remains a separate action.
5. **Route preview:** START remains obvious and the route preview does not clip critical controls.
6. **Active navigation:** END and manoeuvre/speed chrome remain readable and stable; capture both below-threshold and at-or-above-5%-over-limit speed states where safely reproducible using controlled/test data rather than unsafe driving.
7. **Quick Destinations:** normal and expanded states remain right-side vertical, touchable and free of clipping/overlap. The existing responsive 76dp/69dp policy and overflow choice rows are preserved by this change rather than redesigned here.
8. **Search/results:** long destination names may use two lines; description may use two lines; opening/distance metadata stays bounded. Verify alignment remains stable when one metadata field is absent, when names wrap, with enlarged font and with long/localised strings.
9. **Driver modal/action menu:** panel width, hierarchy and touch targets fit the actual display without action buttons or list rows being cut off.
10. **Compact/windowed:** controls remain reachable at the smallest supported runtime bounds and with enlarged font.
11. **PiP:** standard Android PiP, where enabled by the exact build, remains generic rather than TS18-private. Capture PiP and fullscreen restoration separately; verify HOME/fullscreen restoration and recreation. Static code/manifest support is not proof of Topway runtime behaviour.
12. **Free-driving road position/camera (recommendation 11):** with route guidance inactive, capture a straight segment, a turn and safe recovery after a temporary poor-fix/loss condition. Confirm the marker stays on the expected carriageway/road when location accuracy is normal, follow/heading state is stable, touch/free-map -> follow does not oscillate or jump unexpectedly, and reacquisition returns cleanly. Repeat the relevant camera observations separately during active navigation. Do not infer a mapping defect from a known poor GPS fix without recording the location/provider state and surrounding runtime evidence.

## Truncation/cut risk register

The implemented fitting policy deliberately prefers bounded automotive layouts over unbounded growth. The following remaining truncation surfaces must be checked physically before claiming recommendation 9A complete on the TS18:

1. **Search title:** maximum 2 lines with end ellipsis. A relevance-significant suffix (unit, road qualifier or similarly distinguishing tail) can still be cut after two lines.
2. **Search description:** maximum 2 lines with end ellipsis. Category/accessibility/secondary qualifiers can still be cut after the bound is exhausted.
3. **Opening status:** maximum 1 line in an 88–144dp metadata column. Localised `opens/closes in ...` text can truncate at large font or in long locales.
4. **Distance:** maximum 1 line in the same 88–144dp metadata envelope. Very large/localised distance text can truncate; this is usually secondary information but can still affect result comparison.
5. **Quick Destination/choice rows:** `InCarChoiceAdapter` allows 2 lines, then end ellipsis, and retains the existing runtime maximum row height. Long saved destination labels or distinguishing address tails can therefore still truncate in the overflow picker, particularly with enlarged font.
6. **InCar action-menu rows:** maximum 2 lines with end ellipsis. Long/localised action labels can lose their tail; verify any destructive/route-changing action remains unambiguous.
7. **Driver dialog buttons/content:** this PR raises dialog control targets and text size but does not make every upstream dialog body unbounded. Narrow freeform bounds or long translations can still create wrapping/layout pressure; verify the primary/negative action remains fully identifiable.
8. **Quick direct buttons:** these are intentionally icon-only on the map. There is no visible label to truncate; the full text remains in the content description and overflow/choice presentation where applicable.

No additional explicit ellipsis introduced by this PR was found in the changed UX files. That is not a claim that inherited upstream layouts can never clip; screenshot qualification at enlarged font/locales remains the authority for those surfaces.

## Lifecycle qualification

After the immediate checks, repeat the relevant browse/navigation/search/Quick Destination state after:

- activity/process restart;
- reboot/cold boot;
- ACC sleep/wake;
- reverse-camera entry/exit;
- PiP enter/exit where supported.

Record PASS/FAIL/NOT_RUN independently for each boundary. A dependency-blocked check is BLOCKED, not FAIL. Do not claim physical TS18 success unless these steps were actually exercised on the target unit.

## Evidence to return

For the next qualification pass, retain the exact-head APK provenance plus each Termux ZIP (and `.sha256`) or ADB capture directory. Add a short result table with one row per state/lifecycle boundary and PASS/FAIL/BLOCKED/NOT_RUN. For a FAIL, include what was visible, whether the app was route-active/free-driving/PiP/windowed, and the matching capture label. This keeps visual findings tied to the exact runtime state instead of treating screenshots as context-free proof.

## Repository-side checks

Before review/merge, the exact PR head should have its applicable GitHub checks green, including code style, Android/repository scope, InCar touch-target/compact-chrome contracts and generated InCar drawing-rule verification. The generated drawing rules and packaged Android asset must match the exact source style revision. `bash -n android/tools/ts18_termux_in_car_ux_capture.sh` is part of the InCar touch-target workflow so the shipped Termux collector cannot silently regress into invalid shell syntax.
