# InCar location acquisition and optional background permission

## Ownership and compatibility

The InCar application (`app.organicmaps.incar`, Android 10/API 29 supported, target SDK 36)
uses Android LocationManager, with one LocationHelper and one native provider owner.
No Topway private command, root operation, GNSS reset, aiding-data deletion, service,
permanent tracking lease or new foreground-service authority is introduced.

This is a standalone change based on master `9664fe8a8445155437fb3dea477c6265ec0052cf`.
At inspection the open UI/camera stack was #61 through #69, with #69 at
`5a88adc1146cccc96dca15e72f03d5b661399fcb`. Its SDK location files were unchanged;
its settings lifecycle/UI changes are independent integration surfaces to recheck before merging.

The exact TS18 reference establishes Android 10, UIS8581A/SP9863A and
`ro.vendor.gnsschip=ge2`. It does not establish a supported private vendor GPS reset API.
Standard Android subscriptions and GNSS callbacks are therefore the integration contract.
Working hardware cannot obtain a satellite fix through arbitrary shielding. Recovery must
continue when reception returns, without repeatedly resetting healthy acquisition.

## Behaviour

- Successful subscriptions are recorded only after registration returns. Failure of GPS does
  not abort registration of network/fused providers. Coarse-only access does not request GPS.
- Acquisition demand and active registration are distinct. A failed registration retains a
  cancellable recovery session, without falsely claiming a registered provider.
- The InCar watchdog checks every five seconds. Failed/missing subscriptions become eligible
  for repair after 30 seconds; subsequent repairs wait 60, 120 and then 300 seconds.
  Monitoring continues beyond ten minutes, for the existing lifecycle lease.
- A registered receiver with no fix or satellites keeps running. Lack of GNSS telemetry alone
  is not proof of failure. Telemetry registration retries are limited to once per minute.
  A transient vendor inspection exception cannot cancel the recurring watchdog.
  An observed GNSS stop lasting 90 seconds can justify repair;
  subsequent GNSS progress or GPS delivery cancels that evidence.
- Manual retry shares this cooldown. It cannot re-enable a stopped location session.
- Repair re-registers the provider without resetting native free-driving state, routes,
  the camera, saved location or Activity/task ownership. Queued callbacks from an old
  provider registration are invalidated before unregistration.
- Live observations must have finite coordinates/accuracy, valid monotonic timestamps and
  age at most 30 seconds. Duplicate/older observations cannot repeatedly confirm a fix.
  Stale saved positions cannot become a new route origin or be promoted on first-run/listener
  replay. Existing native filtering remains authoritative after Java acceptance.
- Recent system cache (at most 120 seconds, reported accuracy at most 2 km) is separate.
  It can be previewed explicitly from acquisition details while no route is being planned
  or navigated. It is never passed to nativeLocationUpdated or saved as a route origin.
- A-GPS commands are rate limited to once per two minutes when assistance is due and network
  policy permits. Only acceptance of both commands advances the 16-hour timestamp.
  Acceptance is not proof of download or improved time to first fix. No aiding data is cleared.
- InCar's 30-second no-update notification recurs and does not open battery-saver prompts.
  Permission/settings coordination continues to own disabled Location and permission UI.
- Explicit stop, permission loss, global Location off and an unused background map stop the
  acquisition supervisor. Active navigation/recording keep their existing lifecycle lease.
  Merely granting background permission does not start acquisition or keep an unused map alive.

## Permission/UI

Only the InCar manifest and its exact APK permission allowlist add
ACCESS_BACKGROUND_LOCATION. On Android 10 the foreground permission request includes
background access, permitting the native **Allow all the time** choice. Foreground permission
alone remains sufficient for foreground use. Declining background access does not report
ERROR_DENIED, stop navigation or automatically reopen Settings.

Settings → InCar → Location access reads the real Android grant state on every resume and
permission/settings return. Existing installations can request the Android 10 upgrade when
rationale is available, or open app permission settings. Android 11+ uses a separate explained,
user-initiated app-settings transition; foreground/background are never requested together.
Request-in-flight state survives Settings fragment recreation. The map's existing permission
coordinator still owns its combined request, so no parallel startup permission authority exists.

A non-modal map label distinguishes acquiring, approximate, confirmed GPS, stale GPS,
restoring a subscription, stopped, missing permission and disabled Location. It follows the
visible top HUD bounds and hides if the compact window cannot fit it. Tapping it or opening
InCar → GPS acquisition details shows a coordinate-free snapshot and the shared retry action.
Confirmation means a fresh GPS observation accepted by the Java layer; GNSS first-fix,
native handoff and road matching remain separate boundaries.

Details include session duration, provider and GPS registration, fix age/accuracy,
callback/accepted ages, GNSS first-fix duration, first accepted app observation duration,
satellites seen/used, telemetry subscription/event age, first native handoff, rejection/recovery
counts and reasons, and
assistance-command acceptance. There are no precise coordinates in these new diagnostics.

## Automated validation

`python3 -m unittest discover -s android/tools/tests -p test_location_acquisition.py -v`
compiles and executes the production policies, AndroidNativeProvider and LocationHelper
against deterministic Android/framework doubles. It exercises partial/failed registration,
coarse-only access, callbacks from stopped registrations, long poor reception, repair,
stale/duplicate rejection, manual cooldown, retained native state, explicit stop and permission/
Location revocation. Policy JUnit tests also run in the normal app/SDK unit-test lane.
These are source-level behavioural tests, not Android runtime or physical-device evidence.

The existing Android Check workflow runs the deterministic harness, lint, InCar/SDK tests,
API 29/30 emulator startup/warm/resize smoke, API 35 SDK connected tests and
release-equivalent build/package verification.
API 30 permission interaction and physical TS18 acquisition remain separate qualification.

## Physical qualification — NOT RUN

Use one exact-head signed TESTING build, retaining its BUILD_INFO, signer report and SHA-256.
Do not substitute a newer unrelated APK or a merge-ref build without identifying that SHA.
With the car parked, compare against the previous build in comparable reception conditions:

1. Fresh install: Android 10 offers Always / While using / Deny. Test all three independently.
2. Upgrade with foreground permission already granted: use Location access to grant Always;
   return from Settings and verify displayed state. Revoke just background; foreground works.
3. Cold/warm launch with good sky: record GNSS TTFF and first accepted app observation.
4. Launch in poor reception for more than ten minutes. Record satellite/registration state;
   a quiet but registered receiver must not cycle repeatedly. No false fix confirmation.
5. Move into good reception: a fresh GPS fix is accepted automatically without app restart,
   Location toggling or reopening permission/settings screens.
6. After a fix, lose reception for more than 30 seconds, then restore it. Verify stale/current
   states, recovery and preserved route START → active navigation → END.
7. Recreate the Activity during permission flow, return from app settings, and exercise HOME/
   foreground navigation. No overlapping permission flow or route/task theft.
8. Explicitly stop location and leave an unused map in the background. No continuing retries.
   Exercise active foreground-service navigation separately with While using and Always.
9. Test app/process restart, reboot/cold boot and ACC sleep/wake. Record each boundary separately.
10. Repeat native permission interactions on API 30+; grant foreground first, then use Settings.

Record monotonic elapsed times, fix ages, satellite counts, actual provider registrations,
rejections/recoveries and whether navigation accepted the position. Correlate with bounded
`dumpsys location`, package grants/AppOps and process lifecycle if available. Raw dumps can
contain coordinates: redact them before sharing. Collector failures are BLOCKED/UNKNOWN,
not evidence of GPS failure. Do not clear protected Topway data or reset GNSS firmware.

## Android references

- https://developer.android.com/develop/sensors-and-location/location/permissions/background
- https://developer.android.com/about/versions/11/privacy/location
- https://developer.android.com/reference/android/location/LocationManager
