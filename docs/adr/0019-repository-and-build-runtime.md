# ADR-0019: Repository & build runtimes

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../engineering-build.md](../engineering-build.md) · [0001-event-driven-kafka-backbone.md](0001-event-driven-kafka-backbone.md) · [0003-cqrs-read-models.md](0003-cqrs-read-models.md) · [0005-buy-execution-build-orchestration.md](0005-buy-execution-build-orchestration.md) · [0007-outbox-and-idempotency.md](0007-outbox-and-idempotency.md)

## Context

The architecture documents prescribe the *what* (services, contracts, layers, infeed/outfeed) but — deliberately — never pinned the *how*: programming language/framework, build tooling, repo layout, local dev environment, or cloud provider. To start building Phase 1 (event backbone, ACLs, canonical model, read models, control-tower MVP) we must fix these once so they can't fork per team, while staying portable across the documented cloud options (MSK/Confluent/Redpanda; AWS/GCP/Azure).

## Decision

1. **Monorepo** with Gradle (Kotlin DSL) multi-module build, one version catalog (`gradle/libs.versions.toml`), Java 21 (LTS) toolchain for all JVM modules.
2. **Languages:** Java 21 + Spring Boot for L1–L4/L6/L7 services; **Kotlin** for L5 (orchestration) where the Temporal/OPA decision runtime lives — matching the code-first, state-machine-heavy nature of orchestration. Shared "platform" libraries are Java.
3. **Repository layout:** `contracts/` (canonical Avro `*.avsc`, source of truth), `platform/` (shared libs: event-envelope, kafka-commons, outbox, idempotency, tenant, observability), `core/` (domain services), `phase2-stubs/` (Temporal/ATP/intelligence placeholders), `ops/` (compose, Terraform modules, Kubernetes base, observability), `.github/workflows/`.
4. **Avro is the canonical contract format** (ADR-0008); the `docs/schemas/*.md` documents become *views* of the `contracts/avro/*` sources. Breaking changes require a new `.vN` topic, verified in CI against committed `contracts/baselines/`.
5. **Local-first, cloud-agnostic dev:** `docker-compose.yml` runs KRaft Kafka + Confluent Schema Registry, Postgres 16, Redis 7, Debezium Connect, Keycloak (OIDC stand-in for Okta/Entra), Temporal (Phase-2 ready), MinIO. Terraform modules are provider-abstracted (work with MSK/Confluent/Redpanda and AWS/EKS, GCP/GKE, or Azure) and never applied in this repo state.
6. **CI gates in GitHub Actions:** schema compatibility, `gradle build` + tests, end-to-end compose smoke, code lint, `terraform fmt/validate`. Local runs and CI use the same pinned version catalog.

## Consequences

**Positive**
- One build, one contract source, one local environment — onboarding a new event type is a scripted, CI-gated change (Phase-1 exit: < 5 business days).
- Java 21 + Spring Boot gives the strongest Kafka Streams / Schema Registry / outbox ecosystem; Kotlin-Temporal matches the orchestration layer's statechart modeling.
- Cloud-agnostic dev avoids premature lock-in; swapping broker vendors is a compose profile + Terraform module, not a code change.
- CI compat gate enforces ADR-0008 from day one.

**Negative**
- Two languages (Java + Kotlin) to maintain; mitigated by shared Java platform libs and Gradle's unified build.
- Local-first environment does not equal production (no vendor carrier/CDC parity); mitigated with a simulation harness for TMS/WMS and explicit "prod parity" notes in [engineering-build.md](../engineering-build.md).
- Avro-as-source requires discipline to keep schema `.md` docs truthful; a reconcile step in CI diff-checks them.

## Alternatives considered

- **Polyglot per service** — rejected: shared platform libs and outbox/idempotency would be duplicated per language.
- **Node/TypeScript core** — rejected: weaker for Kafka Streams stateful semantics and exactly-once effect patterns the docs require (ADR-0003/0007).
- **Go core** — considered; strong for ingest/ACLs, but the streaming/streams ecosystem and Temporal code-first ergonomics favor the JVM for the read-model and orchestration layers.
- **Cloud-pinned scaffolding (AWS)** — rejected: docs keep AWS as a leaning, not a decision; provider-abstracted IaC keeps GCP/Azure viable.-