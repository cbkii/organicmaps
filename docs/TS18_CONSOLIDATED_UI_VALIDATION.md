# Consolidated In-Car UI physical validation

This checklist qualifies the actual TS18 and exact candidate, separately from CI. All physical rows begin NOT_RUN. Work while parked; never drive to trigger speed thresholds.

## Install and rollback

1. Keep the previous known-working TESTING APK and its recorded source SHA, version, package, signer certificate SHA-256 and APK SHA-256. Back up any user data through supported app features before installing.
2. Obtain the candidate from the PR's exact-head supported workflow. Match its source commit/build metadata, APK SHA-256, package (`app.organicmaps.incar` for release-like builds), versionCode/versionName and signer against the qualification record. Debug/profileable suffixes are separate packages. Do not treat an older rolling TESTING asset as the new head.
3. Use Android's normal APK installer. If a signer/package mismatch or downgrade refusal occurs, stop and record BLOCKED. Do not uninstall/clear data or change protected Topway packages to bypass it. Open Organic Maps manually first, then through the existing launcher; preserve current HOME.
4. To roll back, use the retained compatible same-package/same-signer APK through the supported installer when version policy permits. A lower versionCode may be refused: obtain a rollback build through the supported repository workflow rather than deleting data. Confirm settings and route behaviour after rollback. Firmware, root modules and OEM packages are outside this procedure.

## One-command walkthrough

Download both `android/tools/ts18_in_car_ui_walkthrough.sh` and `android/tools/ts18_termux_in_car_ux_capture.sh` into the head unit's Download folder. In Termux, run `termux-setup-storage` once if needed, then paste:

```bash
mkdir -p "$HOME/bin"
cp /storage/emulated/0/Download/ts18*in_car*sh "$HOME/bin/"
chmod 700 "$HOME/bin/ts18_in_car_ui_walkthrough.sh" "$HOME/bin/ts18_termux_in_car_ux_capture.sh"
bash "$HOME/bin/ts18_in_car_ui_walkthrough.sh"
```

Keep scripts in private Termux storage. Each prompt describes the manual action: switch to Organic Maps, do it, return to Termux, enter **P** (pass), **F** (fail), or **N/Enter** (not run). Optional comments need no typing. **Q** finishes safely and explicitly records all remaining rows NOT_RUN. Invalid input is rejected. The walkthrough never launches apps, changes Android settings or injects touch/input. Settings/lifecycle changes described by the prompt are performed by the tester.

Unexpected terminal end-of-input returns an error (exit 1) and still seals the
results, including remaining NOT_RUN rows. This is an input/collector failure,
not evidence that an app feature failed. Intentional **Q** retains exit 0.

For a failed row, **Y** optionally captures the current state. Leave the failing UI visible before invoking the collector separately if returning to Termux changes focus:

```bash
bash "$HOME/bin/ts18_termux_in_car_ux_capture.sh" camera-failure
```

The snapshot identifies its actual focus/task and time: it cannot prove a previous transient screen. Screenshots may include destinations; review/redact before sharing. Logs are targeted, finite snapshots, with no live writer. Root-unavailable captures retain partial evidence and mark root-dependent sections BLOCKED; a collector failure does not change the manual feature verdict. Manual results live in `Download/OrganicMaps-TS18-UX-Qualification/<time>-walkthrough-<pid>/MANUAL.tsv`, with a verified manifest. Individual captures provide separate checksummed ZIPs when `zip` exists. Share the manual results plus relevant failure captures.

## Covered boundaries and interpretation

The prompts cover handedness persistence, four-cell rail and native Follow states, real perspective/recenter and location recovery, search/menu/overflow, primary GO TO, two independent countdowns and cancellation, all delay settings, day/night HUD hierarchy, warning thresholds, adaptive footer/cog/END, rendered map style/exclusions, launcher window/fullscreen transitions and app/reboot/ACC boundaries.

For Search/Menu, check browsing immediately after cold launch, after returning from
navigation, and after a resize or Activity recreation. The persistent group must
contain **Search + Main Menu only**; Saved Places must remain available inside Main
Menu and must not appear or receive accessibility focus as a separate map button.
In active navigation, use Quick Search and confirm there is no second Search.

Where an accessibility service is already available, start each countdown and move
accessibility focus to another control or scroll with its accessibility action.
The countdown must cancel while the normal focus/scroll action still works.
Countdown text updates alone must not cancel. Manual GO TO/START must remain usable.

Speed warning qualification requires an already approved, parked-only test-data facility. The walkthrough supplies no mock-location injection and no speed simulation. Without approved data, record NOT_RUN. Stationary lack of usable bearing may also leave some native Follow/camera states NOT_RUN. Note individual untested delay choices, route alternatives, locality categories, localisations, cold boot, reverse transitions and physical glare in comments: passing one representative row does not qualify every variant.

After any failure, record the earliest observed cause and relevant screenshot/log snapshot; do not repeatedly retry the same launch/camera command or label dependent tests as independent failures. Re-run a new walkthrough after a new candidate; preserve old directories for comparison. CI, capture-command PASS, APK installation and screenshot existence are not physical behaviour PASS.
