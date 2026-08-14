# Contributing

This fork accepts Android, JNI/shared-runtime and InCar-focused changes. Work for removed iOS, Qt/desktop or desktop-packaging targets belongs upstream unless the repository scope is explicitly reconsidered.

## Before changing code

1. Rebase or branch from the current `master`.
2. Read `AGENTS.md` and, for Android work, `android/AGENTS.md`.
3. Check current open PRs for overlapping work.
4. Identify whether the change affects Android app code, JNI/native code, shared runtime data, CI/release policy or selective upstream import.

## Repository scope

Do not delete shared native/data paths based on names alone. Conversely, do not reintroduce removed platform trees during upstream work. Run:

```bash
python3 tools/ci/verify_android_repo_scope.py
```

after any repository-structure or upstream-import change.

## Validation

Use the smallest relevant checks first, then the Android CI-equivalent checks appropriate to the changed surface. For a broad change:

```bash
git submodule sync --recursive
git submodule update --init --recursive --depth 1
./configure.sh
cd android
./gradlew lintAllModules detektCheckAll
./gradlew -Parm64 assembleWebDebug
./gradlew -Parm32 assembleFdroidDebug
./gradlew -Parm64 assembleInCarProfileable
```

InCar package/release changes must also pass the current release-equivalent CI validation and `android/tools/verify_in_car_apk.sh`.

## Commits and pull requests

Keep commits coherent and reviewable. Describe the controlling layer, affected variant(s), tests run and any remaining physical-device boundary. Do not commit APKs, credentials, signing material, caches, diagnostic archives or generated build output.

Preserve DCO/sign-off requirements where enforced by the repository. Preserve upstream author/provenance information when selectively importing upstream commits.

## Physical TS18 evidence

CI and emulator tests do not prove behaviour on the Topway TS18. Report physical TS18 testing only when it was actually performed, including the build/package tested and relevant lifecycle boundary (launch, relaunch, reboot, window mode or ACC where applicable).
