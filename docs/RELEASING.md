# Releasing

The supported fork release surface is the InCar APK. General Android flavours remain buildable for compatibility/regression purposes but their compile presence does not imply store publication by this fork.

## Authoritative workflow

Use `.github/workflows/manual-in-car-release.yml` and its validation companion. Do not publish InCar releases from generic upstream Android/iOS release workflows.

The release path must preserve:

- package `app.organicmaps.incar`;
- explicit version name/code resolution;
- arm64 release packaging;
- production signing without exposing key material;
- previous-signer continuity checks;
- APK package/version/certificate verification;
- publication only after validation succeeds.

`android/tools/verify_in_car_apk.sh` is the current package verifier.

## Lineage-reset continuity

The clean repository must not import legacy release tags because they would make old Git history reachable. Release continuity is instead anchored to the archived legacy release artefact and checksum recorded in `docs/PROVENANCE.md`.

For the first release after lineage cutover, fetch the exact prior APK from the archived release, verify its recorded checksum, extract its certificate and compare it with the configured current signer before publishing.

Never commit APKs, keystores, passwords, certificates containing private material or generated signing configuration.
