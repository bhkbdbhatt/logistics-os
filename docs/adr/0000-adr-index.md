# ADR Index

Architecture Decision Records for the Logistics OS. Format: [Michael Nygard](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions). ADRs are **immutable** once `Accepted`; superseding requires a new ADR.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-event-driven-kafka-backbone.md) | Event-driven Kafka backbone | Accepted |
| [0002](0002-microservices-bounded-contexts.md) | Microservices decomposed by bounded context | Accepted |
| [0003](0003-cqrs-read-models.md) | CQRS read models for query-heavy surfaces | Accepted |
| [0004](0004-saga-over-distributed-tx.md) | Sagas over distributed transactions | Accepted |
| [0005](0005-buy-execution-build-orchestration.md) | Buy execution, build orchestration | Accepted |
| [0006](0006-multi-tenant-isolation-model.md) | Multi-tenant isolation model | Accepted |
| [0007](0007-outbox-and-idempotency.md) | Transactional outbox + idempotent consumers | Accepted |
| [0008](0008-schema-registry-and-versioning.md) | Schema Registry and event versioning | Accepted |
| [0009](0009-canonical-model-as-moat.md) | Canonical model as the data moat | Accepted |
| [0010](0010-cots-strangler-and-anti-corruption.md) | COTS strangler-fig + anti-corruption layer | Accepted |
| [0011](0011-orchestration-saga-statechart-engine.md) | Durable saga + hierarchical statechart orchestration | Accepted |
| [0012](0012-policy-based-human-in-the-loop.md) | Policy-based human-in-the-loop decision routing | Accepted |
| [0013](0013-exception-cascade-and-reopt-coalescing.md) | Exception cascade model with trigger coalescing | Accepted |
| [0014](0014-optimization-and-inference-architecture.md) | Optimization and inference architecture (feature store, online/offline parity) | Accepted |
| [0015](0015-digital-twin-network-simulation.md) | Digital-twin network simulation as offline decision support | Accepted |
| [0016](0016-external-integration-contract.md) | External integration contract: async-by-default, keyed idempotency, explicit ownership | Accepted |
| [0017](0017-analytics-tiering-streaming-first.md) | Analytics tiering: streaming-first with batch reconciliation | Accepted |
| [0018](0018-autonomous-agent-architecture.md) | Autonomous agent architecture in the orchestration layer | Accepted |
| [0019](0019-repository-and-build-runtime.md) | Repository & build runtimes (Gradle/JVM/Kotlin-L5, Avro-as-source, local-first) | Accepted |

## Status values

- **Proposed** — under discussion.
- **Accepted** — agreed and in effect.
- **Superseded by ADR-XXXX** — replaced.
- **Deprecated** — no longer relevant.
