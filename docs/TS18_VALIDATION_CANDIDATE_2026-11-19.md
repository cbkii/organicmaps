# TS18 validation candidate: 19 November 2026

This is a documentation-only candidate ledger. No application, native, workflow or
release code changes are included. The audited fork baseline is
`b0a8d9cb39e403e44b83c125d8f15cc82884b880` (`master`, merged PR #85). Its source
tree is identical to PR #85 head `fd09013bdbd2ffe2d328fe6d4efd2cac15cca190`.
Any subsequent documentation commit has its own source/build identity; record that
exact identity in the signed TESTING artefact rather than relabelling this baseline.
`organicmaps/organicmaps` remains read-only.

## N1–N4 disposition

| ID | Disposition and remaining boundary |
| --- | --- |
| N1: compact Maps sometimes absent | Not physically resolved. Launcher admission, task/window presentation, native readiness and frame evidence remain separate gates. The inspected Maps owners do not justify another launch/retry authority. |
| N2: fullscreen helper refusal | The captured `resizeTask_not_allowed_on_task` belongs to the launcher bridge/window owner. Maps must consume the resulting bounds without mutating tasks. Qualify the paired launcher candidate; this ledger claims no Maps-side fix for that exception. |
| N3: black map with surviving navigation controls | PR #85 fixes stale pre-resize presentation evidence and adds bounded same-Surface recovery. These source fixes do not establish the longstanding black-map cause or prove visible recovery. A successful swap proves submission, not useful pixels or visibility. |
| N4: inaccurate controls and mixed HOME/BACK procedure | Use only controls present in the installed candidate. Record HOME and BACK as separate actions and test sequences; neither result substitutes for the other. The guide/collector owner is the paired launcher project. |

The audit retained the active renderer, geometry coordinator, authenticated startup
provider and task-preserving Back/lifecycle paths. No obsolete or redundant code was
proven removable in the inspected scope. No speculative task kill/relaunch, route
rebuild, pause-policy change or protected-platform workaround is warranted by the
capture. The symptom predates this candidate; no module cause is established.

## Retained recovery contract

Changed positive Surface dimensions invalidate presentation evidence. Each swap
carries a surface-generation token, so an older in-flight swap cannot certify the
new dimensions. State-edge tokens and Java observation leases reject stale callbacks.

For converged geometry with supported pending presentation evidence, the existing
coordinator starts an uptime budget at its first pending-frame check. It may reapply
the current viewport once after 1.5 seconds and reattach the same valid Surface once
after 3 seconds. At or after 4.5 seconds it performs neither presentation recovery
action and stops its scheduled frame-wait loop. Checks use a 250 ms delay followed by
animation scheduling: these are thresholds, not guaranteed execution times, an
end-to-end startup deadline or a timeout on synchronous native calls.

Recovery requires the current Activity's native-ready, attached presentation state,
a resumed/attached host and valid Surface. Unsupported evidence remains UNKNOWN.
Geometry repair shares the attempt flags; notifications do not replenish the budget.
A changed geometry/task/Activity epoch or RESUME can re-arm recovery. Task, route,
location and native Framework ownership remain unchanged.

## Post-capture scope and verification

The captured Maps source `ab7ecd018d7d80034fbdfd0587f30dd00899ae9f` is an ancestor
of the baseline. Subsequent first-parent merges are #83 (release-signer continuity
anchor), #84 (fork-only upstream monitoring) and #85 (presentation invalidation and
recovery). #83 requires artefact/signing evidence and #84 workflow evidence; neither
adds a device runtime behaviour to qualify. #85 requires physical surface/lifecycle
qualification. Earlier captures do not qualify its changes.

On the baseline, `python3 -m unittest discover -s android/tools/tests -p 'test_*.py'`
passed all **119 tests** in 26.612 seconds. The three focused actual-owner EGL,
coordinator and readiness tests also passed. `git diff --check` passed. These host
tests are controlled-boundary checks, not a PowerVR or TS18 pixel test.

PR-head Android/style/touch/scope CI passed. Baseline Android Check run
`44308048409` passed lint analysis, native/package builds, tests and API29/API30
smoke, but its lint reporting job failed on a reviewdog check-run PATCH HTTP 500.
A bounded rerun of that failed job succeeded; the final workflow is green on
the same baseline. This was an external annotation-service failure, not an app
failure. No new local full Gradle/native/emulator qualification is claimed.

## Physical handoff

Match the final signed TESTING source/build SHA, package `app.organicmaps.incar`,
version, signer and APK SHA-256; retain the compatible rollback APK/data and paired
launcher/Auxio set. Do not uninstall or clear data to bypass installation refusal.

With downloaded maps, record ten compact/fullscreen/HOME cycles and ten separate
BACK sequences. Observe actual map pixels and touch, exact task and active route,
START/active/END, and permission/focus changes. Capture video with the existing
Map/MapView/coordinator/provider evidence around failures. Distinguish absent map
data, failed submission and occlusion rather than treating dimensions or
`renderer=1` as visual success. Report app/process restart, reboot, electrical cold
power and ACC sleep/wake separately. All current-candidate physical gates remain
**NOT_RUN** until the owner performs them.
