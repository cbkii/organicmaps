# BootTrace v3 scoped repair — 23 October 2026

The physical candidate at `1aaec09a26ac82a41abd3ae4abce24ff5cf1d2bc`
showed unreadable compact planning time/distance on the black START rail.
The summary copied mobile-theme `ForegroundColorSpan` values. The rail now
copies the text/formatting and replaces those foreground spans with white,
without mutating the ordinary routing text or changing route ownership.

The read-only compact startup provider now distinguishes protocol, boot,
observer and locked-user refusals. Authentication, explicit consent and the
existing v1 cursor prefix remain unchanged. These diagnostics do not claim
to have identified the captured early `IllegalArgumentException` cause.

`android/tools/tests/test_route_summary_spans.py` executes the production
sanitiser with framework value doubles, including RTL, long/empty text and
source immutability. Actual Android span rendering, day/night, large fonts,
compact/fullscreen transitions and the countdown/active-route flow still
need the signed exact-head TESTING APK on TS18.

Pair with ts-theme #31 and Auxio-TS #281. Keep this PR draft. Unprimed first
HOME, full-power cold boot, ACC, guidance audio and ducking are NOT_RUN for
the repaired candidate. No stock Topway, SystemUI, MCU or firmware change.
