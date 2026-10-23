# Signed InCar cold-consent contract v1

23 October 2026. Companion: ts-theme PR #31. Physical TS18 acceptance: NOT_RUN.

The v3.1 capture shows the installed startup provider cannot be acquired during clean boots,
although the registered authority and signature are correct. Provider queries remain the live
preference and renderer authority. This change advertises an explicitly supported **read-only**
preference fallback for the already authorised root task broker, without adding a component,
permission, service, boot launcher, preference mirror or renderer authority.

Only the `inCar` manifest advertises
`app.organicmaps.incar.COMPACT_CONSENT_PREFS_PROTOCOL=1`. The contract is the existing
credential-encrypted private `shared_prefs/OrganicMapsPrefs.xml`, Boolean key
`InCarCompactStartup`, whose unset/default value is true. An explicit false is authoritative.
The paired launcher checks the installed independent signer, unique package UID, user,
enabled state, advertised protocol and package-update identity before/after reading.

The root broker checks the actual application UID/data directory through PackageManager,
requires no running process for that UID before and after the read, verifies file ownership
and canonical containment, refuses an outstanding `.bak`, bounds bytes and parses XML
structurally without DTD/entity access. Corrupt, changing, oversized, unavailable or wrongly
owned state remains unknown. It does not read a live process's potentially stale disk cache.
No preference file is created or modified. The new-install default is used only for an
explicitly opted-in v1 peer with an owned data directory and no saved preference file.

This result can authorise only the existing single cold-start admission under fresh HOME/
window/unlock/audio/user checks. It cannot qualify any prior task, Activity, renderer, surface
or boot. Once Maps exists, ordinary authenticated provider observation must establish the
current task/native/presentation generation before HOME reports ready. Saved OFF and live
preference invalidations continue to gate automatic admission; explicit user opening retains
its existing semantics. Unknown never implies consent.

Validate both apps as a pair. Check unset, saved OFF/ON, package replacement, corrupt XML,
backup/race/symlink, other user, unavailable root, live process and later provider acquisition.
The exact TS18 fallback/Binder/SELinux behaviour and first-HOME pixels/touch remain physical
gates. Keep PR #78 draft and unmerged pending exact-head CI and that qualification.
