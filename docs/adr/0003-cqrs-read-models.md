# ADR-0003: CQRS read models for query-heavy surfaces

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../architecture.md](../architecture.md) · [../events.md](../events.md)

## Context

Operational UIs (control tower, tracking, ops console) need fast, denormalized, cross-domain views (e.g., an order with its inventory, shipment, milestones, and exceptions). Querying execution services directly would couple UIs to write schemas, cause heavy cross-service joins, and violate bounded-context ownership. Write and read workloads have very different shapes.

## Decision

Apply **CQRS**. Execution/orchestration services own writes and publish domain events. A dedicated `read-model-builder` (Kafka Streams/Flink) projects events into materialized read models (`order_view`, `shipment_view`, `inventory_view`, `yard_view`, `exception_view`) and compacted `*.state.v1` topics. Experience (BFF) and Intelligence read from these projections, never from execution DBs.

## Consequences

**Positive**
- Fast, purpose-built reads; write models stay normalized.
- Projections are rebuildable from event retention.
- Clear separation: L6 reads projections, L5 emits commands.

**Negative**
- Eventual consistency; read lag must be monitored and sometimes shown in UI.
- Projection code must be idempotent and handle out-of-order/replay.

**Mitigations**
- Lag SLOs + alerting; versioned projection state; rebuild tooling.

## Alternatives considered

- **Read directly from execution services** — rejected: coupling, cross-service joins, no cross-domain view.
- **Single shared read database** — rejected: violates ownership, becomes a distributed monolith.
- **Materialized views inside each service** — insufficient for cross-domain aggregates.
