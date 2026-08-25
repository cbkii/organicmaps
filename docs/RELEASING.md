# Releasing

The supported fork release surface is the signed InCar APK published on this repository's GitHub Releases page. GitHub Releases is the only supported public distribution target for this fork; do not publish this fork through F-Droid, Google Play, Huawei AppGallery, Maven Central, Firebase App Distribution or inherited upstream release infrastructure unless repository policy is deliberately changed.

General Android flavours, Wear and SDK modules may remain buildable for source compatibility or local regression work, but their compile presence does not imply publication support or a release CI obligation.

## Authoritative workflow

Use `.github/workflows/manual-in-car-release.yml` and its validation companion. Do not publish InCar releases from generic upstream Android/iOS release workflows.

The release path must preserve:

- package `app.organicmaps.incar`;
- explicit version name/code resolution;
- arm64 release packaging;
- production signing without exposing key material;
- previous-signer continuity checks;
- APK package/version/certificate verification;
- publication only to GitHub Releases and only after validation succeeds.

`android/tools/verify_in_car_apk.sh` is the current package verifier.

## Lineage-reset continuity

The clean repository must not import legacy release tags because they would make old Git history reachable. Release continuity is instead anchored to the archived legacy release artefact and checksum recorded in `docs/PROVENANCE.md`.

For the first release after lineage cutover, fetch the exact prior APK from the archived release, verify its recorded checksum, extract its certificate and compare it with the configured current signer before publishing.

Never commit APKs, keystores, passwords, certificates containing private material or generated signing configuration.
