# Release Plan — 0.1.0 "First CI-verified scaffold"

> Status: Proposed · Target branch: `main` · Owner: docs/backed by repo admin
> Scope decision (recorded): **Code + CI only**. Container images and k8s cluster smoke are deferred
> to 0.2.0 — this release is a *source + verification* release, not yet an artifact distribution.

## 1. Goal

Prove the WP0–WP7 scaffold actually builds, passes its own contract gates, runs locally per the
docs, and is cut as a versioned, tagged, reviewable commit. This is the baseplate every later
release builds on, so its definition of done is deliberately strict and its surface deliberately
small.

## 2. Scope

**In**
- Turn the directory into a git repo; establish branching/tagging conventions.
- First green run of `.github/workflows/ci.yml` (contract gates + full `gradle build` incl. the 4
  embedded-Kafka ITs) on `main`.
- Reproducibility audit: the local quickstart executed verbatim on a clean machine.
- Version 0.1.0, tag `v0.1.0`, release notes.

**Out (deferred to 0.2.0)**
- Container image builds + `ghcr.io/logistics-os/*:0.1.0` publishing.
- `kubectl apply -k ops/k8s` smoke on a kind cluster; otel-to-collector trace validation.
- Schema Registry / MSK client auth wiring. Gradle wrapper + `gradle.properties`.
- Any new functionality (WMS payload completeness, orchestrator stub).

## 3. Definition of Done (exit criteria)

Checklist to be ticked in the 0.1.0 release notes:

- [ ] Repo is a git repo with clean, reviewed history (no secrets; `.gitignore` honored incl.
      wrapper-jar exclusion).
- [ ] CI green on `main` for a real push: `contracts` job then `build` job all pass.
- [ ] `gradle build` green: every module unit test group + `KafkaIngestPublisherIT`,
      `TmsPipelineIT`, `ReadModelTopologyIT`, `MdmPublishIT`.
- [ ] Contract gates drift-free: `validateSchemas` OK, `reconcile.sh` OK (WARN-only backlog
      documented, not blocking), baselines still at **68** (or bumped with a reviewed schema change).
- [ ] `ops/local-compose` quickstart executed verbatim on a clean Docker host: bootstrap → services
      bootRun → smoke curls return expected payloads; no undocumented steps.
- [ ] Release version 0.1.0 (`-SNAPSHOT` dropped), annotated tag `v0.1.0` pushed.
- [ ] Release notes published: working scope, run instructions link, known limitations, 0.2.0
      backlog pointer.

## 4. Milestones & gates

Timeline assumes a GitHub repo with Actions enabled and a machine with Docker to run the audit;
no local JDK/Gradle required for anything except M2's optional host runs.

| # | Milestone | Work | Gate to pass | Est. |
|---|-----------|------|--------------|------|
| M0 | VCS baseline | `git init`, initial `.gitignore` review (wrapper jar, `ops/terraform/*.tfstate*`, compose volumes), seed branch/tag conventions, first commit, secret scan. | Clean history; no secrets; `.github/workflows/ci.yml` present. | 0.5 d |
| M1 | First CI green | Push; run workflow; **fix-forward** until both jobs pass. Keep `gradle/actions/setup-gradle` `gradle-version: 8.12.1` + Temurin 21; bump JVM heap inside `GRADLE_OPTS` if an IT OOMs. Log every first-build fix in commit messages (or an appendix note). | Green `contracts` + `build` on `main`; baseline drift none. | 2–3 d |
| M2 | Reproducibility audit | On a clean Docker host, execute `docs/engineering-build.md` §4 verbatim; record smoke output; fix doc/script drift found; also re-run contract gates locally (bash-only, no JDK). | Quickstart is verbatim-repeatable; smoke curls healthy; gates OK outside CI. | 1–2 d |
| M3 | Release cut | Freeze main; align version to 0.1.0; confirm baselines; annotated tag `v0.1.0`; write + publish release notes; close out with sign-off. | DoD checklist fully ticked. | 0.5–1 d |

**Sequence:** M0 → M1 → M2 → M3 (M2 can start in parallel with M1 tail).

## 5. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| First-build errors (never compiled anywhere) | High | M1 fix-forward budget; jobs isolated so gate failures don't mask build failures. |
| Embedded-Kafka IT memory/flakiness on runners | Med | `-Xmx2g` in `GRADLE_OPTS`; ITs are ID-agnostic (fresh cluster per run). |
| No wrapper → contributor/runner gradle drift | Med | CI pins `gradle-version: 8.12.1`; M3 notes wrapper as 0.2.0 item. |
| Local vs CI drift (docs quickstart rots) | Med | M2 verbatim audit; gate = repeatability, not just green CI. |
| Baseline 68 accidentally drifts | Low | `snapshot-baselines.sh` fails CI on drift; any schema change requires reviewed `--write`. |
| Scope creep (images/k8s pulled into 0.1.0) | Med | Recorded scope decision; release notes restate Out-of-scope. |

Rollback: source-level release only — revert = revert tag/commit; no distribution to revoke.

## 6. Release process checklist (M3)

1. `main` frozen; CI green twice consecutively (contracts + build).
2. Contract baseline confirmed drift-free (or bumped deliberately + `--write`).
3. Root/version set to `0.1.0` (no `-SNAPSHOT`).
4. Commit `release: 0.1.0`; annotated tag `v0.1.0`; push tag.
5. Release notes: working scope + `docs/engineering-build.md` + `ops/local-compose/README.md` links,
   known limitations, 0.2.0 backlog section, DoD checklist.
6. Sign-off by docs owner + CI owner.

## 7. Backlog hand-off → 0.2.0 (candidate)

- Container image builds (jib/kaniko) + `ghcr.io/logistics-os/*` publish + SBOM/attestation.
- k8s smoke: kind cluster, `kubectl apply -k ops/k8s`, otel collector trace validation,
  ingest→tower E2E in-cluster.
- MSK hardening: per-cluster Streams RF, optional Schema Registry, MSK client auth.
- Gradle wrapper + `gradle.properties` (release reproducibility).
- Observability: attach otel java agent in images.
- Phase-2 scoping: WMS payload completeness, orchestrator (L5) stub.
- Terraform cleanup: destroy/module pin `~> 4.0` lockfile hygiene.