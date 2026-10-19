# Standalone binary appearance and quiet location UI

Owner decisions of 20 October 2026 supersede the earlier theme audit roadmap.
Source base: PR #69 `07340fca1e5e603e0f40aa576204b1ec39342c14`.
Primary target: InCar on confirmed sensorless TS18 Android 10/API29; retain runtime geometry.

## Implemented policy

- Organic Maps owns its settings independently of the launcher and global Android settings.
  Only explicit Follow system uses Android's system policy. Manual Light/Dark cannot be
  overridden by navigation; the conflicting legacy auto-dark navigation option is hidden.
- InCar defaults to Scheduled when no preference exists. Explicit existing preferences remain.
  Legacy `nav_auto` resolves to the product default; other flavours retain their default policy.
- Scheduled resolves only to LIGHT or DARK at sunrise/sunset, inclusive dawn and exclusive dusk.
  The 15-minute hold-current-theme transition windows are removed.
- Missing location immediately uses 07:00/19:00 local clock anchors. Invalid/inverted native
  astronomical output, including invalid next sunrise after sunset, immediately uses the same
  fallback and retries within a minute or at an earlier fallback boundary. No abandoned timer.
- Polar day/night get a bounded recheck. Long journeys reconcile astronomical boundaries at most
  every 15 minutes; current boundaries schedule earlier. Time/time-zone/screen-on and foreground
  reconciliation handle clock changes and wake. Timers and first-location listeners are cancelled
  when leaving Scheduled. Repeated identical themes do not reapply AppCompat/platform mode.
- Native map styling remains owned by the existing ThemeSwitcher/renderer path. The current
  branch already has the light high-glare road/casing/blue-route palette: preserve it and the
  approved POI colours. Day camera-rail opacity is increased and secondary navigation text darkened.
- Remove the automatic floating GPS acquisition/fix banner and its two-second polling loop.
  Remove retry and InCar location/compass status toasts. Location details/retry remain available
  through the existing explicit settings action. Location subscriptions, route/recording state,
  Android permission UI and required foreground-service notifications retain their existing owners.

## Verification and remaining gates

Production scheduler host tests execute exact fallback and sunrise/sunset boundaries, invalid
sunrise/sunset/next sunrise, polar states, first fix, single timer, stop/stale callbacks and a
DST-date clock. The existing InCar day/night contrast verifier covers semantic chrome roles.
These are source/host checks, not physical or native-astronomy validation.

Before acceptance, run final-head Android Check and the retained tests, then qualify Day/Night
switching in compact and fullscreen Maps, Settings/dialogs, active navigation and recording,
process loss, time-zone/DST changes, no/stale GNSS and ACC wake on the physical TS18. Confirm no
floating fix notifications, loss of route/camera state or visible theme divergence within Maps.
Measure frames/CPU/memory before claiming a performance gain. Full engine/GPU and inherited
formatter gaps remain separate from this patch. No release/merge or device installation is implied.

Future only: lux-driven policy on validated sensor-equipped hardware. No launcher integration,
global theme authority, Leaflet filter change or extra effective-mode status UI is planned here.
