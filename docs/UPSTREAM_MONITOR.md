# Organic Maps In-Car upstream release intelligence

## Policy

The upstream repository `organicmaps/organicmaps` is **always read-only**. All generated Issues and evidence belong exclusively to `cbkii/organicmaps`. This is **not** an upstream-sync, auto-port, auto-merge, agent-dispatch, Android-build or release-publishing workflow. No agent may write to upstream in later tasks either.

The monitor assesses full upstream changes, including removed platforms, but keeps Android 10/API29 and this fork's curated Android/shared-native/runtime-data boundaries. Source ancestry is **not** code equivalence: earlier history-only merges may record upstream commits while retaining a distinct working tree.

## Running

In the fork's **Actions** tab, select **Upstream Release Intelligence (fork-only)** > **Run workflow**.

- `analyse`: blank tag = discover published Android releases not yet represented by Issues; specific tag = analyse only if its Issue does not exist.
- `reanalyse`: requires one exact tag and refreshes the machine-owned section of the Issue using the current fork `master` (preserves human notes and closed state).
- `dry-run`: generate sealed analysis artifacts but **never** publish Issues or evidence. Use a specific tag to include already-reviewed versions.

The schedule is **monthly, day 2 at 03:37 UTC**. GitHub scheduled runs can be delayed or skipped, so each run discovers all missed releases. The first automatic check uses **19 October 2026** as the historical start boundary; manual tags can target earlier published releases. Each run handles up to 12 releases (oldest first), retaining remaining work for the next dispatch. Unknown tags, non-Android releases, drafts/prereleases and too many unreviewable objects are not silently included.

## Analysis semantics and limits

Inputs come from the upstream published-release API and Git source tagged commits, not merely release notes. The scanner requires tag ancestry coherence and an exact fork commit SHA. It inventories **every commit (including first-parent per-commit changed paths), changed path and file hash** between the previous published Android release and the target. Native runtime, Android and removed-platform paths all appear in JSON. It also records the October curated audit checkpoint and its decisions where applicable.

The monitor uses Git's `--filter=blob:none` fetch and no Git push operations. Source patches are limited to 100 relevant files, 320 KB per patch and 8 MB per release to avoid enormous binary/generated assets. Omitted patches are explicitly listed in `patch-coverage.json` with retrievable immutable commit identities. The **commit/file inventory is exhaustive within the reported successful run, but the patch bundle is intentionally not exhaustive**. `ALREADY_PRESENT` means an exact Git blob match at the identical path only; semantic equivalence is a human/agent investigation. Non-Android paths are still inventoried and classified; exclusions are review proposals, not silently applied decisions.

Safety caps (10,000 commits, 200,000 files, timeouts, Git output size, GitHub API pagination) fail closed with `BLOCKED` rather than generating a falsely complete Issue. Per-commit rename moves are represented as changes to old/new paths, not falsely claimed as detected renames. Agents must reconcile moves and renamed classes.

## Evidence and Issues

One Issue per upstream GitHub release ID; reanalysis rewrites only a clearly delimited machine-owned section and retains manual comments/notes, Issue state and existing PR links. Closed Issues are never reopened automatically. Agent dispatch is always manual.

Durable evidence is stored in the **orphan fork-only branch** `automation/upstream-review-evidence` under:

`releases/<release-id>-<tag>/<assessed-fork-sha-prefix>/`

Each snapshot contains:

- `metadata.json` — immutable source/ref identity, counts, scope and coverage
- `commits.json` — all upstream changes, parents, per-commit file paths, review decisions and direct links
- `files.json` — all changed release paths, exact upstream/fork blob identities and static triage
- `report.md` — readable prioritisation and agent instructions
- `selected-source-diffs.patch` — bounded relevant patch evidence
- `patch-coverage.json` — every omitted patch and reason
- `MANIFEST.sha256` — sealed integrity manifest

GitHub Actions artifacts are temporary convenience copies (30-day retention). The evidence branch is the durable source. The publisher verifies the sealed SHA-256 manifest *before* publication; updates are new commits on the evidence branch, not writes to fork `master`. The branch deliberately starts with no application source or prior ancestry. Repository-size growth should be reviewed periodically before lifting patch-size caps.

## Ownership, tokens and permissions

- Analysis job: `contents: read`, `issues: read`, no stored checkout credentials. Its upstream remote is HTTPS read-only with a disabled push URL.
- Publisher job: `contents: write` and `issues: write`, with no upstream remote and hardcoded `cbkii/organicmaps` API host/path allowlists.
- Analysis artifacts are untrusted until manifest and provenance verification. No workflow invokes upstream scripts, dependency installation from upstream or code execution from a release.
- No PAT, external LLM token, production signing secret or GitHub App installation is required.

These controls prevent **this implementation** from writing to upstream. Agents subsequently assigned an Issue must separately obey the same owner allowlist, and all their commits/PRs must remain in the fork.

## Handoff checklist

1. Read current `AGENTS.md`, `docs/UPSTREAM.md`, existing upstream decision ledgers and the complete linked JSON/patch evidence.
2. Re-fetch *current* fork `master`/PRs and pinned upstream SHA; the snapshot's fork SHA is historical when master advances.
3. Group commits into dependent features and verify owner, current implementation, Android native/JNI/data compatibility, tests, prior decisions and actual value.
4. Create separate candidate implementation PRs in **this fork only** when approved. Avoid bulk upstream merges and excluded platform resurrection.
5. Record code/test/CI state separately from install/physical TS18 validation and subsequent lifecycle tests.

## Troubleshooting and rollback

A release-discovery/API/ancestry/checkout/analysis failure is `BLOCKED`: no Issue is generated for incomplete evidence. A publication failure can leave sealed evidence committed without an Issue; rerun `reanalyse` with the exact tag to finish safely. Repeating an identical evidence snapshot does not create another commit. A pre-existing Issue with ambiguous/missing machine delimiters blocks an automated overwrite to protect human notes.

To disable the monitor, disable its scheduled workflow in **this fork** or revert the monitor PR. The evidence branch and existing Issues remain as archived audit material until explicitly deleted by the owner; the workflow never purges them. Do not run a monitor workflow from an untrusted PR with write permissions.
