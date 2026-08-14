# Repository Scope

## Policy

This repository contains the Android product and only the shared/runtime/tooling dependencies that the retained Android engineering model still needs.

Forbidden platform/bulk trees include iOS/Xcode, Qt desktop, desktop sandbox/packaging, map-generation borders, inherited large test data and bundled desktop shader compilers. They are available from upstream or the archived legacy fork when needed for archaeology.

Shared C++ source, JNI dependencies and runtime data are not removable merely because they live outside `android/`.

## Size budget

`tools/ci/verify_android_repo_scope.py` enforces an initial maximum tracked-tree budget of **220 MiB**. The operating target is **150–200 MiB**. Exceeding the budget requires dependency evidence, not a disabled check.

Files at or above 10 MiB must be explicitly allow-listed as required Android runtime assets. The allow-list is deliberately small.

## Structural checks

The verifier requires:

- forbidden platform/bulk paths to remain absent;
- every tracked symlink to resolve within the checkout to tracked content;
- `.gitmodules` paths and tracked gitlinks to match exactly;
- tracked blob bytes to remain within budget;
- large files to have an explicit Android runtime justification.

## Upstream imports

Every upstream import must run the scope verifier. Prefer selective cherry-picks or patches with recorded upstream SHAs. A full upstream merge that restores deleted platform trees is not acceptable merely because it resolves cleanly.

## Changing the boundary

A new platform, generator dependency or large asset requires a deliberate repository-policy decision. State the product need, dependency path, byte cost, build/CI impact and rollback before changing the verifier.
