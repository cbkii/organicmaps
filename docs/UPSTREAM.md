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
