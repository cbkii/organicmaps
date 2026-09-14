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

## Testing-only rolling draft

The fixed draft **000 Testing Only Version** is a separate engineering/physical-validation channel. It is not a production release and is not refreshed automatically by every successful PR or CI run.

Request a snapshot in any of four ways:

1. add the `testing-apk` label to a same-repository PR;
2. put `/testing-apk` in the newest PR commit message;
3. comment `/testing-apk` in the PR conversation as an owner/member/collaborator;
4. manually run **Refresh Testing APK Draft** from the default branch and set `source_to_build` to a PR number, branch, tag or commit SHA.

For manual runs, keep GitHub's **Use workflow from** selector on `master`. The `source_to_build` input selects the source to build. This avoids the GitHub Actions dispatch/ref ambiguity that otherwise causes PR-number-like values to be treated as workflow refs.

The workflow resolves the requested source to an immutable commit, uses the trusted build helper from `master`, builds the arm64 InCar product, then publishes through the repository release signer. Automatic signed PR snapshots are intentionally limited to same-repository PR heads; fork heads are rejected.

The install APK keeps the production package identity `app.organicmaps.incar` and uses a fixed TESTING update lane:

```text
versionName=0.0.0-InCar
versionCode=999999
ABI=arm64-v8a
```

`0.0.0-InCar` is the Organic Maps equivalent of the ts-theme fixed testing version: the existing InCar flavour appends `-InCar` to the base testing version `0.0.0`, avoiding a special product-code path solely for testing.

Each snapshot is identified as `PR<number>-<sha7>` or `SHA-<sha7>`. The draft retains the newest two complete snapshot groups. Every group contains:

- the production-package TESTING APK signed with the repository InCar key;
- a separate DEBUG APK for diagnostics;
- `BUILD_INFO` with requested source, resolved/built SHA, PR/base provenance and workflow run;
- signer information;
- SHA-256 checksums.

`BUILD_INFO` is uploaded last and acts as the visible commit marker for a complete snapshot group. Interrupted/incomplete managed groups are removed before retention is evaluated. The draft release notes are the authoritative mapping between an APK and the PR/head/built SHA used for a physical test.

The draft must remain a draft. Do not publish or promote it into the normal InCar release stream.

## Lineage-reset continuity

The clean repository must not import legacy release tags because they would make old Git history reachable. Release continuity is instead anchored to the archived legacy release artefact and checksum recorded in `docs/PROVENANCE.md`.

For the first release after lineage cutover, fetch the exact prior APK from the archived release, verify its recorded checksum, extract its certificate and compare it with the configured current signer before publishing.

Never commit APKs, keystores, passwords, certificates containing private material or generated signing configuration.
