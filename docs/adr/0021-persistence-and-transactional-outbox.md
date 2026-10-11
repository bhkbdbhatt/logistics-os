# ADR-0021: Persistence & transactional outbox

- **Status:** Accepted
- **Date:** 2026-10-11
- **Related:** [0007-outbox-and-idempotency.md](0007-outbox-and-idempotency.md) · [0003-cqrs-read-models.md](0003-cqrs-read-models.md) · [0002-microservices-bounded-contexts.md](0002-microservices-bounded-contexts.md) · [../services.md](../services.md) §3 · [../data-model.md](../data-model.md)

## Context

The scaffold has **no database**: `MdmRegistry` is a `ConcurrentHashMap`, the outbox and idempotency platform modules are in-memory, and every service publishes directly via `KafkaTemplate`. This contradicts the accepted rules: each service owns **one logical database** ([services.md §3](../services.md)), no shared databases, and **every write publishes via the outbox** so the state change and the event are atomic ([ADR-0007](0007-outbox-and-idempotency.md), cross-cutting §2). The version catalog already declares PostgreSQL + Flyway (unused).

We need a first real persistence slice that (a) proves the outbox/idempotency pattern end-to-end and (b) does not turn the CQRS read models into a second source of truth.

## Decision

1. **PostgreSQL per service** (own database/schema); **Flyway** migrations under each service's `src/main/resources/db/migration`. No cross-service table access — integration stays events/sync APIs.
2. **`platform:persistence`** supplies framework-agnostic (`java.sql` only, no Spring) building blocks:
   - `JdbcOutboxRepository implements OutboxRepository` — Postgres/H2, `UNIQUE(dedupe_key)` enforced; `save` returns `false` on conflict (duplicate = no-op, per ADR-0007).
   - `JdbcDedupeStore implements DedupeStore` — TTL via `expires_at_ms`.
   - **Shared migrations** shipped as classpath resources (`io/logisticsos/persistence/migration/*.sql`) that services include in their Flyway locations, so the outbox/idempotency tables are defined once.
3. **First system-of-record = MDM golden records.** Replace the in-process map with a `MdmStore` interface + `JdbcMdmStore` (table `mdm_golden_record`: `entity, id, tenant_id, version, active, canonical_json`, `UNIQUE(entity,id)`, `tenant_id` indexed). Versioning semantics preserved (`next` = +1 unless canonical+active unchanged).
4. **Transactional outbox for MDM writes.** `MdmService.upsert/deactivate` writes the golden record **and** an outbox row in one transaction; a scheduled `OutboxPublisher` (Spring) dispatches pending rows to Kafka via an `OutboxDispatcher` (implemented with `KafkaTemplate`). Direct `KafkaTemplate.send` from the service is removed. This realizes ADR-0007 for MDM.
5. **Idempotency:** `core:ingest-gateway`'s `DedupeStore` becomes Postgres-backed (`JdbcDedupeStore`) when a datasource is present.
6. **Read models stay Kafka-sourced.** `read-model-builder` (Kafka Streams `*_view`) is **not** persisted to Postgres — projections must remain rebuildable from event retention (ADR-0003). Control-tower remains a projection. No "database per query view".
7. **Dev/test:** local compose runs **PostgreSQL 16**; unit tests use **H2 in PostgreSQL compatibility mode** (SQL kept portable; duplicate detection via SQLState `23505`); optional Testcontainers-Postgres ITs for dialect fidelity in CI.

## Consequences

**Positive**
- First durable SoR + the ADR-0007 outbox loop proven on real infrastructure; MDM survives restarts and can be read by other services via its API/events.
- Reusable JDBC stores + shared migrations mean the next service (OMS/TMS/WMS, billing) adopts persistence without new plumbing.
- CQRS purity preserved: read models are a cache over the log, not a database.

**Negative**
- Services now need a database at runtime; local/CI must provide one (H2 for tests, Postgres for dev). Mitigated by keeping in-memory implementations for tests and a `datasource-absent` fallback for pure-unit runs.
- Two write paths exist briefly during migration (direct publish vs outbox); mitigated by moving MDM fully to the outbox and documenting the pattern.
- Operational surface grows (backups, migrations, connection pools); mitigated by one managed Postgres per environment in cloud (RDS/Cloud SQL).

## Alternatives considered

- **Keep everything in-memory** — rejected: not durable, no outbox, can't satisfy ADR-0007.
- **Persist read models too** — rejected: creates a second source of truth and breaks rebuild-from-log (ADR-0003).
- **Event-sourcing all services (no DB, log-as-truth)** — rejected for now: higher complexity; outbox+DB is the pragmatic, documented path.
- **ORM (JPA/Hibernate) first** — deferred: plain JDBC keeps platform modules framework-agnostic and the SQL explicit; services may adopt Spring Data JPA later behind their own module boundary.
- **Redis for idempotency/dedupe** — kept as an option for high-throughput ingest; the `DedupeStore` interface allows it (catalog already lists Redis).
