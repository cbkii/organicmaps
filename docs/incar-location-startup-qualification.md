# InCar location and Driving View startup

The existing `AutoStartLocationFollowAndRotate` preference is now labelled **Start in Driving View** in In-Car Settings. Its stored value is retained: a prior OFF stays OFF; the existing native default of ON applies to a fresh InCar configuration. No second startup boolean is introduced. The separate speed-based Automatic Driving View setting continues to govern later entry, while a launcher session starts immediately at any speed and does not use automatic low-speed exit.

Only a plain MAIN/LAUNCHER entry, as classified by `InCarStartupCameraPolicy`, starts the route-independent session. Routing and explicit map targets retain camera precedence. The InCar controller owns persistent native Driving View; the bounded native startup helper only frames a pre-fix local area. A `NOT_FOLLOW_NO_POSITION` value alone does not establish explicit user location-off intent. The InCar My Position control records an explicit off action while waiting for a fix; turning it back on clears that marker. Previously persisted native camera modes are therefore recoverable without overriding a recorded user stop. The marker and the location-disabled warning preference are InCar-only application preferences.

When permission is granted but Android Location reports OFF, InCar waits up to ten seconds, checking at most every 1.5 seconds. The warning is OFF by default and appears at most once after the window if enabled. Runtime permission requests remain independent. The Location Settings action in In-Car Settings remains available manually. A master-switch broadcast or return from Settings can restart an inactive provider after the bounded window without continuous polling. Other flavours retain their existing prompt path.

## Physical TS18 qualification (exact built SHA required)

- Cold process launch while stationary and while already moving: Driving View and heading follow begin without a route or first-fix mode bounce.
- Delayed first fix, GPS lost/restored, launcher warm re-entry, and full-screen/launcher-hosted transitions.
- Route planning/building, START, active navigation and END; saved route and deep-link camera precedence.
- Start in Driving View OFF, explicit in-app location off/on, and Settings/Search return.
- Android Location ON at launch and after a process relaunch: no settings popup; a fresh fix appears without opening Settings.
- Android Location OFF with warning OFF: no blocking popup; manual Settings action works. With warning ON: one warning after the settling window. Revoked runtime permission still requests Android permission.
- Reboot/cold boot and ACC sleep/wake separately.

CI/build and emulator results do not establish these physical outcomes.
