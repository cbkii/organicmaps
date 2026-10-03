# Upstream Imports

Upstream: `organicmaps/organicmaps`.

This fork deliberately does not mirror upstream's full multi-platform repository. Import changes selectively and preserve the upstream commit SHA in the commit message or PR description.

## Process

1. Resolve the current fork `master`, upstream target SHA and any overlapping open PRs.
2. Inspect the complete upstream diff before applying it.
3. Apply only the Android/shared-native/runtime-data portion required by this fork.
4. Do not restore iOS, Xcode, Qt/desktop packaging, removed bulk data or obsolete multi-platform workflows as collateral changes.
5. Reconcile moved files against the current Android-only tree rather than recreating deleted paths for convenience.
6. Run `python3 tools/ci/verify_android_repo_scope.py` and the relevant Android CI/build matrix.
7. Record the upstream source SHA and any deliberately omitted upstream files in the PR.

Broad upstream merges require an explicit repository-scope review because they can make deleted platforms and large history/bulk content reachable again.

The archived legacy fork remains the historical reference after lineage cutover; it is not a source to mirror back into the clean repository.

## Work package A audit — 3 October 2026

Previously acknowledged upstream: `74668b3a159124a88f161f9a58a694da6dce2a99`.
New upstream target: `3f26ce8979f044f14664d3a1e88160e105110bb6`.
The eleven intervening commits were reviewed against the retained Android automotive product.

| Upstream commit | Decision | Reason / adaptation |
|---|---|---|
| `afa0f91dea9091ec8f518517e80455f3fcd9060f` | Import | Keep START disabled when missing maps have not produced a built route; retain an already built route. Preserve this fork's routing/intermediate-stop/lease behaviour. Move the new regression test into the existing app Mockito test harness; the SDK has no Mockito dependency. |
| `80e57b2617bcf57f2546583f19fc617571452f30` | Import | Keep Android Auto place preview usable after declining missing-map downloads. |
| `006da2ba61b312bcf4d1c4964f3faa52f6d9036d` | Import | Contain car sensor registration/removal permission failures; use stable listener instances and ignore invalid/unavailable samples. |
| `3f26ce8979f044f14664d3a1e88160e105110bb6` | Exclude | Unused bookmark server-ID removal spans KML/shared APIs and removed iOS paths. No demonstrated automotive defect requires the format/API cleanup in this batch. |
| `b90c786f839d930dcf16590acd9f97201cc988b4` | Exclude | iOS voice collation; platform is outside the retained product. |
| `b46e9542f3b209d233ecd172e3bf2ce18ad261cf` | Exclude | Upstream markdown-only CI skip policy; this fork owns different Android validation workflows. |
| `7c047227df82fbb1fac2839820ef841786510ae5` | Exclude | Upstream multi-platform markdown CI skip policy; retain fork-owned gates. |
| `6c6c764f1a63424650928b2419bd90fda9ae2d0e` | Exclude | Qt/Windows runtime handling; platform is removed. |
| `6a82d4bbf6d1d53af0b14405f70145e173bafe07` | Exclude | Upstream build-directory agent example; retained fork build policy already uses its own paths. |
| `5081d9393a02edc0cf2087de992d7c607a985eb5` | Exclude | Upstream agent-policy corrections; retained automotive policy is independently maintained. |
| `751319bc449fef68bd5bc2fdce58d8294a5d61aa` | Exclude | iOS CarPlay bookmark repair; platform is removed. |

The curated source commit is followed by a history-only merge whose first parent is that source commit and second parent is the upstream target. Its tree must equal the curated first-parent tree. This records considered upstream ancestry without importing excluded source or restoring removed platforms. Tree equality and upstream ancestry are separate required verification facts. Preserve this lineage when eventually merging PR #56: **Create a merge commit**, never squash or rebase. This work package leaves the PR unmerged.
