# Longstanding black map after window resizing

This scope begins at consolidated fork master `dedfe2be5f1023dad3885efadb9d3e7d548ff29c`.
It is separate from the earlier launcher/Auxio remediations. The owner reports the black-map
symptom since the first Magisk-module validation campaigns. That chronology does not establish
either a recent regression or the collector as its cause.

## Confirmed source gaps and changes

1. The OpenGL presentation observation was reset on surface attachment/replacement but not a
   dimension change. A previous successful swap could therefore remain the readiness evidence
   after resizing. Changed positive dimensions now invalidate it; redundant/invalid sizes do not.
2. A swap already in flight could finish after invalidation. Each swap now carries the factory's
   surface-generation token. Its completion cannot certify a different generation. State-edge
   ordering tokens remain separate so late observer snapshots cannot supersede newer callbacks.
3. The InCar geometry coordinator returned once layout, SurfaceHolder and applied native sizes
   matched, even when supported presentation observation reported no frame. It now waits 1.5 s,
   reapplies the existing native viewport once, waits until 3 s for one configuration-style
   detach/attach against the same Surface, then stops checking at 4.5 s. These are conservative
   engineering budgets, not measured TS18 driver timings. Existing geometry repair attempts share
   the same limits. A new geometry epoch or resumed lifecycle can re-arm the existing owner.

Recovery requires the exact current Activity, initialized native renderer, resumed/attached host,
valid Surface, and supported pending presentation evidence. Unsupported observation is UNKNOWN.
Presentation/native-initialization callbacks re-enter the same coordinator; they do not create
another task, route, renderer or retry authority. Healthy frame evidence stops recovery. Window
bounds remain owned by Android/the launcher. Protected Topway/SystemUI are unchanged.

## Evidence boundary

The supplied Boot 3 capture shows surviving route UI and the same Maps task during black content.
Settled snapshots can show both Maps and HOME resumed. That is contrary evidence to treating every
black frame as an intentionally paused Activity. No pause-to-stop lifecycle refactor is included.
The old capture lacks a sufficient EGL/surface correlation to prove the original driver failure.

A successful eglSwapBuffers is evidence of submission, not proof of useful map pixels or visibility.
Black-but-successfully-swapped or occluded content remains a separate physical question. No native
pixel probe, map-data assumption, task kill, route rebuild or fullscreen relaunch is used as a cure.

## Qualification

Host tests compile the actual EGL factory/context with controlled boundary failures, including a
resize during a swap, and run the complete geometry coordinator with a deterministic View queue
and clock. They cover delayed frames, bounded method changes, unsupported observation, healthy
recovery, initialization, pause, invalid Surface, release/destruction and Activity replacement.
Android unit tests cover the recovery timing policy as well as existing geometry policies.
These tests do not exercise a PowerVR driver or certify physical recovery.

Run canonical Android lint/build/native/unit/connected/API29/API30 smoke on the final PR head.
For one final signed candidate, preserve the previous signed Maps APK, data and paired launcher/
Auxio rollback set. Qualify 10 compact/fullscreen/HOME cycles and 10 BACK sequences, with actual
map pixels/touch, task/route identity, START/active/END and permission/focus changes. Capture the
existing Map/MapView/coordinator/presentation provider evidence and video. Report reboot, cold-power
procedure and ACC separately. All physical qualification remains NOT_RUN until performed.
