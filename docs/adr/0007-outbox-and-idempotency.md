# ADR-0007: Transactional outbox + idempotent consumers

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../events.md](../events.md) · [../cross-cutting.md](../cross-cutting.md)

## Context

Services must update their database and publish an event atomically. Writing to the DB and then to Kafka in the same code path ("dual write") can lose events on crash or publish events for rolled-back transactions, corrupting downstream projections and payments. Meanwhile, at-least-once delivery means consumers will see duplicates.

## Decision

Every service uses the **transactional outbox pattern**: state change and the outbound event are written to the same DB transaction in an `outbox` table; a relay (Debezium/CDC or poller) publishes to Kafka in order. All consumers are **idempotent**, deduplicating by `idempotencyKey = eventType + aggregateId + aggregateVersion` (or producer-supplied key) using a dedupe store. Money and inventory consumers additionally use exactly-once *effects*.

## Consequences

**Positive**
- Atomic state+publish; no lost/phantom events.
- Safe replays and reprocessing (idempotency).
- Ordering preserved per aggregate via the outbox relay.

**Negative**
- Extra table and relay infrastructure; latency of poller if not CDC.
- Every consumer must implement idempotency (non-optional discipline).

**Mitigations**
- CDC relay for low latency; shared idempotency library; dedupe-store TTL aligned to topic retention.

## Alternatives considered

- **Direct dual write** — rejected: data loss / inconsistency.
- **Kafka transactions only** — insufficient: doesn't cover the DB write atomically.
- **Listen-to-yourself** — rejected as primary: ordering and replay gaps vs. outbox.
