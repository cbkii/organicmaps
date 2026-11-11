# Consolidated TS18 integration candidate

PR #78 contains #69/#73/#74/#75/#76 and current master by ancestry. It combines driving camera/routing and bookmark fixes, binary Day/Night, InCar marker contrast, task-preserving HOME/Back and navigation crash fixes with exact-source TESTING notes.

OpenGL compact readiness now requires a successful window presentation. A failed swap revokes readiness through the current Java surface/Activity lease; detach and older generations cannot restore readiness. Invalid native windows stay quarantined until the existing lifecycle owner supplies a new surface. The paired launcher exposes compact retry on readiness loss; this change does not claim autonomous EGL repair or add another task/routing owner. Vulkan retains its existing structural readiness contract.

Production EGL/factory and Java readiness regressions run in the full engineering-tools suite. A debug-only SDK connected test exercises the linked BookmarkManager and KML reader/writer: repeated reload preserves category identity, replaces metadata/bookmarks/tracks, avoids naming collisions, and does not rewrite disk timestamps. Release/profileable libraries exclude the test entry point. Android CI must compile the real native engine, pass SDK connected tests, InCar unit/lint/format checks and release-equivalent packaging/smoke on the final head; host/header tests alone are insufficient.

## Mandatory acceptance before default merge

Human review: exported provider/task admission, root peer contracts and TESTING signing/update boundaries. Physical TS18: qualify the exact signed triplet with Auxio #281 and launcher #31; cold and warm starts, HOME/Back return, route continuation, surface loss and explicit Open fallback, resize, Day/Night/marker contrast, process loss and genuine ACC sleep/wake. No physical success is claimed.

Do not install a high-version TESTING APK without a tested rollback/data-preservation policy. Keep production release publication separate from integration qualification.

## Physical regression corrective revision

The prior triplet failed compact Maps startup/restoration on TS18. Launcher #31 now preserves compact intent in the fallback button, waits for renderer evidence during initial presentation as well as restoration, and re-arms definite pre-dispatch focus refusals only when HOME becomes eligible. Temporary evidence loss preserves task identity. This does not establish the exact earliest failing boundary of the previous hardware cold boot.

The existing authenticated v1 provider adds activity_instance, presentation_required, presented and presentation_generation diagnostic columns after its unchanged original prefix. Structural and presentation fields are captured in one synchronized snapshot. No command, preference write, caller privilege or alternate task authority is added. This exposes whether failure is bootstrap, lifecycle/geometry or native frame presentation rather than reporting only a final timeout.

The actual readiness owner is regression-tested across pause/resume, geometry invalidation/convergence, failed presentation, stale generation, replacement Activity and a new renderer's restarted generation. Maps reports its converged content area; launcher verifies outer task bounds independently and allows caption/inset differences while rejecting oversized stale surfaces.

Repeat exact-head Android/native/connected/release-equivalent checks and qualify the corrected signed triplet on TS18: cold engine/device boot, compact pixels/touch, fullscreen→HOME restoration, app switching/drawer interruption, route continuity, process/surface loss and genuine ACC. The panel's primary fallback is compact retry; fullscreen is an explicit separate action. No new automatic EGL owner, duplicate launch, synthetic tap/key or physical-success claim is introduced.
