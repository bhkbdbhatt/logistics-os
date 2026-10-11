# Release Plan — 0.2.0 "Distributable & deploy-verified"

> Status: Proposed · Target branch: `main` · Owner: docs/backed by repo admin
> Progress: M0 (toolchain) landed ahead of the plan: committed Gradle 8.12.1 wrapper
> (jar + gradlew/gradlew.bat + pinned `distributionSha256Sum`), repo-level `gradle.properties`, CI
> switched to `./gradlew` with `gradle/wrapper-validation-action`. M1 is on track: the
> `core:phase2-stubs:orchestrator` module landed (stub boots, emits `orch.command.v1`, unit +
> embedded-Kafka IT); WMS payload completeness done — `wms.v1.*` promoted to `canonical` with mapper
> coverage for the previously-omitted `inventoryRef`/`referenceRef` fields; baselines re-snapshot to
> match. Remaining: M2 images, M3 kind smoke, M4 MSK/Terraform.
> M2 drafted: `.github/workflows/images.yml` (tag `v*` + `workflow_dispatch`) builds the 8 service
> images via `ops/containers/service.Dockerfile` with the OTel java agent baked in and OCI
> provenance/SBOM attestations; `ops/k8s/apps/*` already referenced `:0.2.0`. Steps still needing a
> live run: push a `v0.2.0` tag, verify digests + attestations, then `kind` smoke (M3).
> M3 drafted: `ops/kind/` (cluster config, single-broker KRaft Kafka, topic catalog, `topic-bootstrap`
> + `pipeline-probe` Jobs, `smoke.sh`) rehearses `kubectl apply -k ops/k8s` with an in-cluster E2E
> (`legacy-simulator -> acl-tms -> canonical -> read-model`) and OTel trace check — needs a
> Docker-capable host to execute.
> M4 done (config + docs): MSK client auth parameterized (`client_auth_sasl_scram|iam|tls`);
> `read-model-builder` Streams RF now `STREAMS_REPLICATION_FACTOR`-overridable and pinned in `ops/k8s`;
> Schema-Registry switch documented; Terraform lockfile/destroy hygiene notes in place.
> Scope decision (recorded): 0.1.0 proved the scaffold *builds and passes its own gates*; 0.2.0 turns
> it into a **distributable artifact** — signed container images, an in-cluster smoke on `kind`, a
> reproducible toolchain (Gradle wrapper), and the first Phase-2 surface (WMS payload completeness +
> a real L5 orchestrator stub). It also retires the drift between `settings.gradle.kts` and
> `docs/engineering-build.md` for the orchestrator module.
> M3 wired: `.github/workflows/smoke.yml` runs `ops/kind/smoke.sh` on **manual dispatch + nightly**,
> building images locally and `kind load`-ing them (published-image mode via GHCR login optional).
> M2 baked into a release path: `.github/workflows/release.yml` builds an **edition's** service subset
> (`editions/<edition>.yaml`, schema-validated), pushes to GHCR, and **cosign**-signs by digest; a base
> run also publishes the GitHub release from `docs/releases/<version>.md`.
> Productization slice (preview, beyond the 0.2.0 gates): a sellable SKU catalog (`editions/`),
> commercial docs (`docs/product/`), a framework-agnostic `platform:entitlement` seam wired into
> `control-tower` (`GET /tower/entitlement`; soft/permissive now), and per-customer k8s overlays
> (`ops/k8s/overlays/{dhl,fedex}`) that set namespace + image tag + branding + license.

## 1. Goal

Prove the platform can be **built once, published, and run on a cluster the way it runs locally** —
with a reproducible toolchain and provenance. 0.2.0 is the transition from a *source + verification*
release to an *artifact distribution* release, and the on-ramp to Phase 2 (orchestration).

## 2. Scope

**In**
- Committed **Gradle wrapper** (`gradlew`/`gradlew.bat` + wrapper jar) and a repo-level
  `gradle.properties`; CI keeps a pinned `gradle-version` as a fallback but uses the wrapper.
- **Container images** for every deployable (`mdm`, `read-model-builder`, `control-tower`, `bff-ops`,
  `ingest-gateway`, `acl-tms`, `acl-wms`, `legacy-simulator`) built via jib (or Dockerfile) and
  published to `ghcr.io/logistics-os/*:0.2.0`; multi-arch where cheap; **SBOM + provenance
  attestation**; tags bumped from `0.1.0` → `0.2.0` in `ops/k8s/apps/*`.
- **k8s smoke** on a `kind` cluster: `kubectl apply -k ops/k8s`, topic bootstrap, ingest→read-model→
  tower E2E in-cluster, and **otel-collector trace validation** (jaeger/`debug` exporter assertions).
- **OpenTelemetry java agent** baked into the images (`OTEL_EXPORTER_OTLP_ENDPOINT` from the shared
  `logistics-os-env` ConfigMap; resource attrs = service + version + tenant where applicable).
- **MSK hardening**: per-cluster Streams `replication-factor`, optional Confluent Schema Registry
  wiring (retire the local `schema.resource=true` path in cloud), MSK IAM/SASL client auth.
- **Phase-2 surface (minimal, honest):** WMS payload completeness (promote `wms.*` registry entries
  from `scaffold` to `canonical` where backed by schemas + mapper tests) and a real
  `core:phase2-stubs:orchestrator` module that boots, emits `orch.command.v1`, and is covered by
  tests — replacing the `settings.gradle.kts` include that currently points at a missing directory.
- **Terraform hygiene**: module pin `~> 4.0` preserved; AWS provider `~> 5.0`; committed
  `terraform.lock.hcl`; `destroy`/state-hygiene notes; input validation via required `variables.tf`.
- Release notes for 0.2.0 + bumped `version` (`0.1.0` → `0.2.0`), tagged `v0.2.0`.

**Out (deferred to 0.3.0)**
- Full L5 orchestration (workflow engine, sagas, exception cascade, decision router) — Phase-2 core.
- Intelligence (predictive ETA, carrier selection) and analytics tiers T0–T3.
- OMS + carrier/EDI integrations; ATP/promise engine.
- Multi-tenant isolation hardening / 4PL onboarding.

## 3. Definition of Done (exit criteria)

Checklist to be ticked in the 0.2.0 release notes:

- [ ] **Wrapper committed**; `./gradlew build` reproduces CI green on a clean checkout (no system
      Gradle); `.gitignore` still excludes stray wrapper jars but the *committed* wrapper is tracked.
- [ ] CI green on `main`: `contracts` then `build`, **now driving the wrapper**; contract gates still
      drift-free and baselines reconciled (count bumped deliberately if WMS schemas are promoted).
- [ ] **Images published**: `ghcr.io/logistics-os/<svc>:0.2.0` for all eight services, each with a
      **SBOM and provenance attestation**; images boot locally (`docker run`) and answer health probes.
- [ ] **`kind` smoke passes**: `kubectl apply -k ops/k8s` converges; `topic-bootstrap` runs;
      E2E ingest→`shipment_view`→`/tower/alerts` asserted; **traces visible in the otel collector**.
- [ ] **MSK config verified**: Streams `replication-factor` >= cluster min; Schema Registry path works
      with auth (no `schema.resource=true`); MSK client auth documented and smoke-tested.
- [ ] **Phase-2 surface compiles and tests**: WMS payloads promoted where backed; `orchestrator` stub
      boots, emits a valid `orch.command.v1`, and has at least one IT; `settings.gradle.kts` ↔
      `engineering-build.md` no longer reference a missing module.
- [ ] **Terraform plan is clean** against `example.tfvars`; MSK module `~> 4.0` + AWS `~> 5.0` pinned,
      lockfile committed.
- [ ] Release version `0.2.0` (no `-SNAPSHOT`), annotated tag `v0.2.0` pushed; release notes published
      (image digests, run instructions, known limitations, 0.3.0 backlog pointer).

## 4. Milestones & gates

| # | Milestone | Work | Gate to pass | Est. |
|---|-----------|------|--------------|------|
| M0 | Toolchain reproducibility | Generate + commit Gradle wrapper; add `gradle.properties`; switch CI to `./gradlew`; document wrapper regeneration. | Clean checkout builds with `./gradlew build`; CI green using wrapper. | 0.5–1 d |
| M1 | Phase-2 surface | Create `core/phase2-stubs/orchestrator` (boot + `orch.command.v1` emit + IT); promote WMS registry entries with schema + mapper tests; reconcile baselines `--write` with review. | `gradle build` green; registry/doc drift removed; baseline bump reviewed. | 2–3 d |
| M2 | Image build + publish | jib/Dockerfile per service; GH Actions workflow to build, SBOM (`syft`/`docker buildx`), attest (`cosign`/provenance), push to `ghcr.io`; bump `ops/k8s` tags to `0.2.0`; otel java agent in images. | All eight `:0.2.0` images present + attested; `docker run` health OK. | 2–4 d |
| M3 | In-cluster smoke | `kind` cluster; `kubectl apply -k ops/k8s`; topic bootstrap; E2E + trace assertions; fix manifest drift found. | Documented smoke is repeatable; traces land in collector. | 2–3 d |
| M4 | MSK + Terraform hardening | Streams RF per cluster; Schema Registry client config; MSK auth; Terraform lockfile + destroy/state-hygiene notes (module `~> 4.0`, AWS `~> 5.0`). | MSK config verified; `terraform plan` clean. | 1–2 d |
| M5 | Release cut | Freeze `main`; set version `0.2.0`; publish images/tag; write + publish release notes; sign-off. | DoD checklist fully ticked. | 0.5–1 d |

**Sequence:** M0 → M1 → M2 → (M3 ∥ M4) → M5. M0 gates everything (all later gates assume the wrapper).

## 5. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Container registry push auth / `ghcr.io` perms | Med | Use `GITHUB_TOKEN` + `packages: write`; scope workflow permissions explicitly; dry-run locally with `docker push` to a scratch tag. |
| `kind` smoke flakiness (startup ordering, topics not ready) | Med | Init-containers / `topic-bootstrap` job with readiness gates; retry/backoff on consumer lag; keep the manifest the single source of truth. |
| Image bloat / JVM OOM in cluster | Med | JRE base + `-XX:MaxRAMPercentage`; per-service `resources.requests/limits`; verify with `kubectl top`. |
| SBOM/attestation tooling churn (cosign/syft versions) | Med | Pin action/tool versions; treat provenance as a gate, not best-effort. |
| WMS schema promotion bumps the baseline unexpectedly | Low | `snapshot-baselines.sh --write` only after review; record the exact delta in the PR + release notes. |
| Orchestrator stub drifts into "real orchestration" scope | Med | Hard "stub only" boundary: boots + emits one command + one IT; full engine is explicitly 0.3.0. |
| MSK auth blocks local parity | Low | Keep local-compose unchanged; MSK config gated behind profile; document the delta. |
| Scope creep (pulling Phase-2 engine/ML into 0.2.0) | Med | Recorded `Out` list; release notes restate deferrals; stub PR has a size cap. |

Rollback: images are immutable per digest; revert manifests to `:0.1.0`; source release revert = revert
tag/commit. No data migrations in this release.

## 6. Release process checklist (M5)

1. `main` frozen; CI green twice consecutively (`contracts` + `build`, wrapper-driven).
2. Contract baselines confirmed drift-free (or bumped deliberately + reviewed `--write`).
3. Root/version set to `0.2.0` (no `-SNAPSHOT`); `ops/k8s` image tags set to `0.2.0`.
4. Image workflow runs on the release commit; capture digests + attestation URLs.
5. Commit `release: 0.2.0`; annotated tag `v0.2.0`; push tag; publish GitHub release with digests.
6. Release notes: image list + digests, `docs/engineering-build.md` §6/§7, `ops/k8s/README.md` and
   `ops/local-compose/README.md` links, `kind` smoke transcript, known limitations, 0.3.0 backlog.
   (draft: [docs/releases/0.2.0.md](releases/0.2.0.md), with a cut-checklist status table)
7. Sign-off by docs owner + CI owner + platform owner.

## 7. Backlog hand-off → 0.3.0 (candidate)

- **L5 orchestration core**: workflow engine (Temporal/Zeebe), order-to-cash + returns sagas,
  compensations ([ADR-0004](../docs/adr/0004-saga-over-distributed-tx.md),
  [ADR-0011](../docs/adr/0011-orchestration-saga-statechart-engine.md)).
- **Trigger + exception system**: trigger catalog, coalescing, escalation ladder
  ([orchestration/trigger-catalog.md](orchestration/trigger-catalog.md),
  [escalation.md](orchestration/escalation.md)).
- **Decision context + policy routing**: OPA/Drools, human-in-the-loop tiers
  ([ADR-0012](../docs/adr/0012-policy-based-human-in-the-loop.md)).
- **ATP / promise engine** (`orch.promise.v1`).
- **Intelligence v0**: predictive ETA v1 serving path + feature store
  ([intelligence/predictive-eta.md](intelligence/predictive-eta.md)).
- **Analytics tiers T0–T1**: hot store + minimal semantic layer
  ([ADR-0017](../docs/adr/0017-analytics-tiering-streaming-first.md)).
- **Multi-region / DR drill**: first active-passive test against RTO/RPO targets
  ([nfr.md](nfr.md)).
