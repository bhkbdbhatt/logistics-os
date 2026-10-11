# Cross-Cutting Concerns

> Status: Accepted · Related: [architecture.md](architecture.md) · [events.md](events.md) · [ADR-0006](adr/0006-multi-tenant-isolation-model.md)

## 1. Multi-tenancy

- **Default:** logical isolation — `tenantId` is mandatory in every event envelope, every API token, and every row (row-level security). Shared topics, shared clusters.
- **Large tenants:** dedicated Kafka namespaces/topics, dedicated DB schemas or clusters; optional dedicated compute.
- **Data residency:** tenants pinned to a region; EU data stays in EU; per-tenant routing at the gateway.
- **Noise isolation:** per-tenant quotas at gateway and per-consumer-group quotas on the bus.
- See [ADR-0006](adr/0006-multi-tenant-isolation-model.md).

## 2. Consistency & transactions

- **No distributed (XA) transactions.** Cross-domain consistency via **sagas** with compensating actions ([ADR-0004](adr/0004-saga-over-distributed-tx.md)).
- **Outbox pattern** for atomic DB-write + event-publish ([ADR-0007](adr/0007-outbox-and-idempotency.md)).
- **Idempotent consumers** everywhere; `idempotencyKey` = `eventType + aggregateId + aggregateVersion`.
- **Exactly-once effect** for money/inventory via transactional producer + dedupe store.
- **Read-your-write** via CQRS read models; UIs may show eventual consistency with explicit states.

## 3. Schema governance

- All schemas in Schema Registry; **backward compatibility enforced in CI**.
- Additive-only within a topic; breaking change → new `.vN` topic + migration ADR + dual-write/dual-read ([ADR-0008](adr/0008-schema-registry-and-versioning.md)).
- Consumer-driven contract tests for critical producer/consumer pairs.

## 4. Security & identity

- **IAM** via a bought provider (Okta/Entra); OIDC/OAuth2; short-lived tokens.
- **API gateway** terminates external traffic, enforces auth, rate limits, tenant routing.
- **Service-to-service** mTLS in the mesh; least-privilege service identities.
- **Secrets** in a managed vault (AWS Secrets Manager/Vault); never in code or events.
- **Audit:** tamper-evident `audit.*` stream for all state changes and access; 7-year retention.
- **PII:** tagged fields, encrypted at rest, access-controlled; residency rules enforced.

## 5. Observability

- **Distributed tracing** with W3C `traceparent` propagated **in event headers** across async boundaries ([ADR-0001](adr/0001-event-driven-kafka-backbone.md)).
- **Metrics:** RED (rate/errors/duration) for services; **consumer lag** SLOs per topic/group.
- **Logs:** structured, correlated by `correlationId`/`traceId`.
- **Alerting:** DLQ depth, lag, saga failure rate, prediction drift, ATP exceptions.
- **SLOs:** p99 latency, availability, freshness of read models.
- Concrete targets, priorities, and alert thresholds are in [nfr.md](nfr.md).

## 6. Resilience & deployment

- **Kubernetes** + service mesh; multi-region **active-passive** (RTO/RPO per tier).
- **Backpressure:** Kafka naturally buffers; consumers scale on lag.
- **Bulkheads & circuit breakers** on sync calls (solver, external APIs).
- **Blue/green or canary** deploys; **consumer-offset-aware** releases (new schema must be backward compatible).
- **Disaster recovery:** projection rebuild from event retention; cross-region replication for Kafka and state stores.

## 7. Data lifecycle

- **Hot/warm/cold** tiering for telemetry and scans.
- **Retention** per object in [data-model.md §2](data-model.md) (7y financial, 1y operational, compact for master).
- **Right-to-erasure:** tombstones on compacted topics + purge jobs in lakehouse; events may be crypto-shredded per tenant key.
- **Archival** to object storage; replayable within retention windows.

## 8. Cost & FinOps

- Kafka topic-level cost visibility; tiered storage for cold partitions.
- Lakehouse storage tiering; query cost guardrails on gold-only BI.
- Autoscaling on lag/CPU; scale-to-zero for batch analytics.

## 9. Ownership & on-call

- **Platform SRE** owns backbone, schema registry, DLQ tooling, gateway, IAM.
- **Domain teams** own their service SLOs, topics produced, and DLQ triage for their consumers.
- **Architecture guild** owns canonical model, ADRs, and cross-domain contracts.
