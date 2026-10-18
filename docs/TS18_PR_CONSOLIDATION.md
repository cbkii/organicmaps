# TS18 PR consolidation checkpoint

## History and scope

The checkpoint combines master `9664fe8a8445155437fb3dea477c6265ec0052cf`
with PR #68 `986786b1d3c0bd1e064c484563f33b26b125e07a` using two parents.
The original #61–#68 commits must remain reachable; do not squash or rebase
this checkpoint. PR #69 and independent PR #70 belong in the remaining
camera/location candidate, not in this default-branch checkpoint.

## Review disposition

| Finding | Disposition in checkpoint |
| --- | --- |
| #61 disallowed auto-perspective remains armed | Already fixed by #67; production seam exercises flat-but-auto state and guard now has full source coverage. |
| #61 English countdown suffix | Reuse the existing translated `second` resource instead of hard-coded `s`. START already supplies translated `p2p_start`; the label/number format remains resource-overridable. No new translations are invented. |
| #66 whole-width footer occupancy | Already fixed by #67: measure Search and Menu children. |
| #67 rail clips padded panels / wrong anchor | Clamp to content width and set both physical gravity and anchor gravity. Production-method regression covers padding, resize and stale anchor repair. |
| #67 positional child access | Stable divider/content IDs in both orientation layouts. |
| #67 validates shadowed speed overlay | Touch-target verifier now inspects the InCar source set that ships. |
| #67 missing shared resources | Already fixed in #68; shared fallback drawable and dimensions retained. |
| #67 atlas pixels | Existing final #67 pixel checks and regenerated atlases retained; verify all twelve atlases. |
| #67 Python optimisation disables checks | Replace bare asserts with explicit failure; CI runs verifier under `-O`. |
| #67 XML parsing / duplicate security reports | Reject DTD/entity declarations before UTF-8 parsing, permitting only the generator's exact inert `<!DOCTYPE skin>` declaration. Regression covers external entities, internal subsets and encoding bypass. No new dependency. |
| #67 colour substring matching | Match parsed fill attributes/style properties, not arbitrary SVG substrings. |
| #67 symbols symlink trigger absent | Add packaged symbols path to both style workflow filters. |
| #67 Java unavailable silently passes | Explicit NOT_RUN skip for compass test. |
| #67 compass radius larger than artwork | Intentional full automotive touch-area clearance; documented at owning call site. |
| #67 no-water/cultural dark colours | Preserve the supplied, approved per-symbol palette and its deliberate lower-priority roles. Category summary colours do not override per-symbol values. No unsolicited recolouring or generated asset churn. Physical contrast remains unqualified. |
| #67 opaque START surface | Preserve requested matching START/END treatment; adaptive recolouring is a separate design change. Physical glare/layout remain unqualified. |

## Acceptance boundaries

Local source, compiled production-method harnesses and generated-asset checks
are not physical TS18 evidence. Exact-head Android CI must also qualify lint,
arm64/x86 builds, unit/connected SDK tests, release-equivalent package checks
and API29/API30 startup/window smoke tests.

The exact checkpoint formatter initially reported 48 files. Formatting corrections
are confined to the 33 files modified by #61–#68 or the checkpoint; 15 untouched
files are excluded. The remaining repository-wide baseline must be reported with
its actual failure diff; do not bulk-format unrelated files or suppress the check.
Branch protection may be unavailable to the connector and must not be assumed absent.

The approved execution plan requires focused physical recenter, road snap,
START/END, occupancy and camera/Quick checks before merging this checkpoint
into master. Those checks are NOT_RUN in this environment. Keep the checkpoint
open until that gate is satisfied or the maintainer explicitly changes it.

After an accepted merge, merge master normally into #69, merge #70 normally
while preserving both compact-startup and location-permission settings setup,
retarget #69 to master and close superseded PRs only after proving inclusion.
Qualify the final camera/location head separately; request its signed TESTING
snapshot from that exact SHA and preserve rollback provenance. No device
installation or production publication is part of this checkpoint.

## Combined camera/location candidate

PR #69 incorporates the checkpoint and independent #70 by ordinary merge
ancestry. Its review base is the checkpoint branch while the physical master
merge gate is pending; retarget to master after the checkpoint is accepted.
The settings conflict retains compact capability-generation checks and both
location permission launchers, saved pending state, explanation and diagnostics.
Manifest permissions, renderer evidence and location-supervisor ownership coexist.

A compiled production-method regression exercises location loss, first accepted
stationary fix, first reliable moving course, one-shot follow events, manual
interruption and routing/disabled-session exclusions. Together with the actual
LocationHelper/provider registration/stall/quiet-receiver harness this qualifies
those source boundaries, not GNSS hardware, JNI timing or rendered TS18 latency.
The final signed TESTING candidate must report source/built SHA, package,
version, signer and APK checksum before physical testing.

The combined regression exposed a silent-repair gap: provider repair preserves
native state and does not emit LoseLocation. Rapid acquisition now also re-arms
before a fresh, strictly newer native observation replaces evidence stale for
30 seconds (or marked obsolete). BOOTTIME evidence covers suspended rendering.
Routing, ordinary non-driving mode, manual pan/zoom cooldown, stale/replayed/future
input and invalid clocks do not gain rapid camera ownership. The production-method
regression fails on the prior candidate and passes after this targeted native fix.
