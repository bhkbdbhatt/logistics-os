# ADR-0001: Event-driven Kafka backbone

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../architecture.md](../architecture.md) · [../events.md](../events.md)

## Context

A 3PL/4PL must unify TMS, WMS, YMS, OMS, visibility, and billing, plus external feeds (EDI, carrier APIs, telematics). Integration by synchronous point-to-point calls creates a fragile spider web, couples release cycles, and makes replay/audit impossible. We need a durable, replayable, ordered log of record that decouples producers from consumers.

## Decision

Adopt **Apache Kafka** (managed: MSK/Confluent/Redpanda) as the platform event backbone across all layers. All cross-domain integration is via events/commands on topics using a **standard envelope** with `tenantId`, `correlationId`, `causationId`, `traceparent`, and `idempotencyKey`. Current state is exposed via compacted `*.state.v1` topics. Delivery is at-least-once by default with exactly-once *effects* for money/inventory.

## Consequences

**Positive**
- Loose coupling; services evolve and scale independently.
- Replay enables read-model rebuild, audit, and backfill.
- Natural buffering/backpressure; consumer lag as a scaling signal.

**Negative**
- Operational complexity (brokers, Schema Registry, rebalancing).
- Eventual consistency must be surfaced in UIs.
- Requires disciplined schema governance and idempotent consumers.

**Mitigations**
- Managed Kafka; IaC-owned topics; OTel tracing propagated in headers.

## Alternatives considered

- **Synchronous REST/gRPC integration** — rejected: tight coupling, no replay, cascade failures.
- **RabbitMQ/AMQP** — rejected: weaker ordering and log-replay semantics.
- **Pulsar** — viable; kept as an option if multi-tenancy/geo-replication needs dominate.
- **Database-as-queue** — rejected: poor throughput and fan-out.
