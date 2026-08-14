# Provenance and lineage reset

This repository is a derivative of `organicmaps/organicmaps`.

## Current cleanup baseline

The Android-only cleanup started from legacy fork `cbkii/organicmaps` at:

- legacy `master`: `b95989efa310dec14a3a8ec80f3594ce8be3d679`;
- upstream repository: `organicmaps/organicmaps`;
- upstream master observed at cleanup start: `3655a7033e65c2460150ca9e3fbc07754fe2d10b`;
- live merge base at cleanup start: `94f6abb59bb6c2a03028e70cdd56f7bd38932689`.

These values document the lineage-reset input; future upstream imports must record their own source SHAs.

## Planned clean lineage

After the cleanup branch is validated, the intended administrative cutover is to preserve the current fork as a recoverable archive and create a standalone repository whose root tree exactly matches the validated Android-only candidate. No pre-fork history or legacy tags should be reachable from the new primary repository.

Repository rename/archive/create operations are intentionally outside ordinary PR work and require explicit approval of the exact cutover manifest.

## Legacy InCar release baseline

The historical baseline supplied for release continuity is:

- tag: `in-car-v1.0.0`;
- APK SHA-256: `cb702d3f23ad4ba13e8117ffbc2a7127e786e7479740f0ea60a9915f45e96d95`.

Before the first clean-lineage release, fetch the exact prior APK from the archived repository release, verify this checksum and compare its signing certificate with the current configured release signer. Do not copy legacy tags into the new repository and do not commit the APK or signing material.
