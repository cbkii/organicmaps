# Consolidated automotive controls and navigation presentation

This change is stacked on the lifecycle candidate in PR #58. It retains that
candidate's location, startup, task/window and route ownership, including the
curated upstream ancestry inherited through PR #55. It does not modify HOME,
Topway, DoFun, SystemUI, firmware or the routing algorithm.

## Control and camera ownership

The four rail cells are Zoom In, Zoom Out, Follow Mode, and Camera View/Recentre.
Follow dispatches the existing native location state machine, with actual mode
icons and accessibility descriptions. Camera View sends one ordered render-thread
operation to toggle the screen between top-down and 45-degree perspective and
recenter through the existing accepted-position/freshness controller. A separate
render-thread query reports actual presentation; Android never guesses a toggle
state. An unavailable position retains the existing pending recenter and bounded
Application/current-Activity recovery relay.

Repeated Driving View updates preserve a manual camera choice. A new Driving View
or routing session establishes its camera policy; route END clears the manual
session override. Routing perspective eligibility remains native-owned. No global
3D preference or persisted tracking intention is used as a per-tap toggle.

Control position defaults to right-hand drive/physical left; left-hand drive uses
physical right independently of text direction. Map, quick, search, planning,
place-page and footer owners share that setting. The information banner stays
in place. The 76/69/55dp tiers remain; a shallow window uses a horizontal four-cell
rail while keeping all camera/zoom actions and three dividers accessible.

## Destination and route actions

GO TO reserves a direct slot before secondary-action overflow. Both visible and
overflow menus consume the same projection. Manual handlers remain authoritative.

The shared automatic-action delay offers 2, 3, 5, 10, 15, 20 or 30 seconds,
defaulting to 10. A lifecycle-owned monotonic countdown uses independent destination
selection and route-build generations. It waits for full interactive presentation,
then revalidates before performing the existing button click. Cancellation consumes
that generation, so redraws cannot silently re-arm it. Manual interaction, page/
route changes, dialogs, loss of focus, pause/destruction, screen-off and task/window
changes invalidate pending dispatch. Required notices and permissions stay manual.

## Readability

NOW, LANES and NEXT/AFTER use distinct day/night semantic surfaces. Speed WARNING
enters at 105% and clears below 103%; SEVERE enters at 110% and clears below 108%.
Existing tolerance and speed-source freshness apply. A full-opacity 4dp orange/red
line sits under the application banner without shifting layout. It is unrelated
to the system navigation bar and footer progress indicator.

The footer targets 460dp, expands towards 540dp for longer content, and caps itself
to actual safe parent bounds. Distance/time use whole-expression auto-sizing and
content-dependent allocation. The cog retains the existing navigation-options menu.

In-Car compiled styles use day route core/casing #00B5D1/#003D77 and night
#55BAC8/#103A60. Eligible feature captions use #F6DEA0 day/#D5C18E night with
#252A2E charcoal halo. Road captions, locality captions, routing shields and
house-number colours remain unchanged. The compiler stores integer text heights:
nearest address sizes 12/13/11 achieve approximately 7–8% reduction, rather than
an exact 10%. The compiler encodes halo enablement; actual halo width is renderer
defined, so source radius 1.75 does not prove a 1.75px rendered halo.

## Qualification and rollback

Source and compiled-style checks are separate from Android packaging and physical
validation. Only final-head workflow results qualify the candidate APK. Missing
SDK/build, renderer screenshots, GPS, glare and vehicle lifecycle evidence must be
reported as BLOCKED or NOT_RUN, never inferred from source tests.

Use [the physical walkthrough](TS18_CONSOLIDATED_UI_VALIDATION.md) for installation,
signer/version compatibility, P/F/N results, failure captures and same-package
rollback. Keep a prior compatible APK. No merge or production release is part of
this candidate.
