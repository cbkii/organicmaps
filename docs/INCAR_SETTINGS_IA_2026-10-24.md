# InCar settings and main-menu information architecture

Scope: InCar UI only, stacked on PR #79 (which itself depends on #78).
The previous main-menu map controls, search and routing navigation actions remain owned by their existing controllers.

## Main menu

The InCar menu contains, in order: Saved Places, Offline Maps, Map Layers, Track Recording (existing presenter-owned live switch), Settings, More Actions. More Actions contains Add a Place, Share Location, and the conditional Donate command. There is no duplicate recorder in More Actions. All actions reuse the original callbacks; mobile is unchanged.

## Settings paths

| New section | Original owners |
| --- | --- |
| Navigation | route perspective, auto-zoom, road avoidance, action countdown, route resume, voice, fallback sounds, overspeed warning |
| Driving & Startup | Driving View, return after browsing, auto-follow, initial map area, ts-theme compact admission, location permissions/diagnostics |
| Map & Display | appearance, optimised visuals, layer options, custom tile editor, downloaded boundaries, map language, transliteration, large labels, bookmark label placement, units |
| Places & Tracks | existing Quick Destinations editor, track auto-resume |
| Offline Maps | auto-download, mobile-data policy, map-age notice, storage |
| Controls & Performance | handedness, power scheme, budget rendering, 3D buildings, screen wake/lock-screen policy |
| Privacy & Support | search history, Google location-services choice, logging, Help |

The Android preference keys, owners, persisted values and defaults are unchanged. Nested editors (route avoidance, voice, storage, language, tiles, Quick Destinations) retain their existing fragments and task/back-stack semantics.

## Source-level dependencies

- Initial map area is disabled without automatic follow. Compact auto-start is disabled if authenticated HOME admission is unavailable.
- 3D buildings remain subject to both the power scheme and InCar rendering-budget guard; a disabled option must show its controlling reason.
- Satellite availability requires a configured tile URL. Do not surface dormant start-collapsed Quick Destinations storage as a new switch without establishing its live runtime consumer.
- Manual appearance is app-owned. Spoken guidance, speech-engine settings, fallback clips/tones and camera alerts retain their independent owners.
- Reorganisation does not change RHD/LHD, action countdown, driving hysteresis, speed warning thresholds, actual GPS availability or OEM display state.

## Deliberately hidden in the InCar hierarchy

The OSM profile, old navigation automatic-night override, bad-storage emulation and general zoom-button preference are not applicable/are already hidden/ineffective in InCar. They remain in the mobile/default XML. Section keys are navigation metadata, not persisted settings.

## Qualification

1. Run the static InCar menu/navigation test and normal InCar touch-target, scope and Android CI gates against the exact final PR head.
2. Verify settings upgrade preservation by recording values before/after on a disposable or backed-up InCar installation; do not clear app data.
3. Walk all seven parent sections, all 43 child options, and deep editors. Check Back, selected values, disabled-state summaries, 76dp touch regions, large-font/scroll behaviour and compact/fullscreen task restoration.
4. Test all main-menu and More Actions commands; verify Track Recording has one state authority.
5. TS18 cold boot, location acquisition, route, process restart, reboot and ACC transitions require separate physical validation.

Note: base PR #79 has pending review comments. This stacked branch does not claim those inherited issues resolved.

CI/PACKAGE/TS18 installation/physical testing is not implied by this source change.
