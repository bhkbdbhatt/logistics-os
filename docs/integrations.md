# Integration Patterns (per layer)

> Status: Accepted · Related: [architecture.md](architecture.md) · [events.md](events.md) · [services.md](services.md) · [ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)

This document defines **how each layer integrates**, both internally and with external parties, and the mandatory **anti-corruption boundaries**.

> Per-**system** integration design (ERP, OMS, carrier APIs, WCS/automation, mobile, customer tracking) — with sync/async pattern, contract style, rate limiting, retry/idempotency, and system of record — lives in [integration-architecture.md](integration-architecture.md). Governing decision: [ADR-0016](adr/0016-external-integration-contract.md).

## 1. Pattern summary

| Layer | Primary pattern | Secondary | Forbidden |
|---|---|---|---|
| L1 Observation | Ingress adapter → topic (at-least-once + dedupe) | batch file/SFTP fallback | direct domain DB writes |
| L2 Data | Event-carried state transfer + CQRS projections | CDC, Kafka Connect sinks | synchronous cross-domain chains |
| L3 Execution | Sovereign service + outbox; saga steps | sync API for read-your-write UI | shared DB, distributed transactions |
| L4 Intelligence | Event-triggered inference + sync solver API | feature store lookups | writing domain state |
| L5 Orchestration | Process managers + commands + rules | sync query to read models | direct L3 DB access |
| L6 Experience | BFF + CQRS reads + WS/SSE subscriptions | GraphQL | domain logic, direct DB access |
| L7 Analytics | Stream+batch into lakehouse; reverse-ETL | dbt semantic layer | serving operational transactions |

Guiding rule: **layers communicate only through L2 (the bus and its projections).** Any arrow that would bypass L2 is a defect.

## 2. L1 — Observation integration

**Inbound sources**
- **Devices:** MQTT/HTTPS/gRPC from edge gateways. Edge does buffering, dedupe, NTP/PTP time sync so upstream sees clean signals.
- **Telematics:** ELD/GPS vendor APIs (Samsara, Geotab) polled or webhooked → `obs.telemetry.v1`.
- **Carrier EDI:** AS2/SFTP/API via `edi-adapter`; X12 214 (status), 210 (invoice), 204/214 (LTL), 990 (response), 997 (ack).
- **Carrier APIs & visibility networks:** project44/FourKites and direct carrier webhooks → `obs.carrier-status.v1`.
- **Third-party feeds:** weather, traffic, port/terminal, customs.

**Contract:** every source has an **anti-corruption adapter** producing canonical `ObservationEvent`; raw payload retained in lake bronze for replay. Dedupe key `(source, deviceId, seq, eventTime)`.

**Failure handling:** adapters retry with backoff; unparseable payloads to `dlq.obs.*`. Missing `eventTime` → adapter timestamp with `timeProvenance` flag.

## 3. L2 — Data integration

- **Publish:** transactional **outbox** per service → Kafka (see [ADR-0007](adr/0007-outbox-and-idempotency.md)).
- **Project:** Kafka Streams/Flink build CQRS read models (`*_view`) and compacted `*.state.v1` topics.
- **CDC:** Debezium from COTS/legacy DBs where no API/events exist; mapped to canonical events by an ACL.
- **Lakehouse:** Kafka Connect sinks → bronze; dbt for silver/gold.
- **Contracts:** Schema Registry; backward-compatible evolution enforced in CI ([ADR-0008](adr/0008-schema-registry-and-versioning.md)).

**Read-model rebuild:** all projections must be reconstructable from retained `*.events.v1` (or compacted state). No projection is a source of truth.

## 4. L3 — Execution integration

- Each execution service is **sovereign**: own DB, own API, publishes via outbox.
- **Cross-domain flows are sagas**: process manager issues commands; services reply with events; compensations on failure ([ADR-0004](adr/0004-saga-over-distributed-tx.md)).
- **Sync calls only for read-your-write UI**; all else event-driven.
- **COTS wrapping:** adopt WMS/TMS/OMS/YMS as products, front them with an **ACL** translating their model to canonical events/commands; apply the **strangler-fig** pattern if replacing a legacy system.

**Example saga — order-to-cash:** reserve inventory → allocate → pick/pack → tender → ship → deliver → accrue → invoice, with compensations (release reservation, cancel tender, reverse accrual) wired by the orchestrator.

## 5. L4 — Intelligence integration

- **Event-triggered inference:** subscribe to domain events → emit `intel.prediction.v1` / `intel.recommendation.v1` ([intelligence/README.md](intelligence/README.md)).
- **Sync solver API:** gRPC/REST for sub-second optimization (routing, ATP).
- **Feature store:** online/offline parity; predictions carry `modelId`, `modelVersion`, `confidence`, `featuresHash` ([ADR-0014](adr/0014-optimization-and-inference-architecture.md)).
- **Lifecycle & simulation:** `intel.model.events.v1` (deploy/drift), `intel.simulation.v1` (what-if), `intel.feedback.v1` (realized labels) close the loop.
- **No writes to domain state** — Intelligence advises, Orchestration decides, Execution acts. Recommendations are advisory options in the [DecisionContext](orchestration/decision-context.md).

## 6. L5 — Orchestration integration

- **Process managers (saga runtime):** Temporal/Camunda/Zeebe; long-running, stateful, retryable.
- **Commands:** emitted on `orch.command.v1`, idempotent, versioned, carry `commandId` + `correlationId`.
- **Rules/policy:** tenant-scoped rules evaluated via policy engine (OPA/Drools); 4PL customer logic is configuration, never forked code.
- **Reads:** query L2 read models synchronously when needed for decisions; never call L3 DBs.

**Tenant onboarding pattern:** new 3PL/4PL customer = new `Policy` set + SLA + routing profile + integration endpoints — no code fork.

## 7. L6 — Experience integration

- **BFF per surface** aggregating CQRS reads + orchestrator commands.
- **Live updates:** WebSocket/SSE/GraphQL subscriptions fed from the bus (milestones, exceptions, ETAs).
- **Offline-capable mobile:** local queue + sync protocol; conflict resolution server-side.
- **Contract:** BFFs contain no business rules; they transform and compose only. The **control tower** reads the hot store + OLAP and turns one-click actions into idempotent `orch.command.v1` commands ([analytics/control-tower.md](analytics/control-tower.md)).

## 8. L7 — Analytics integration

- **Ingest:** tiered — Flink streaming (T0/T1), micro-batch (T2), Kafka Connect/CDC → lakehouse bronze (T3) ([analytics/README.md](analytics/README.md)).
- **Transform:** dbt silver/gold; semantic/metrics layer for shared definitions.
- **Serve:** BI over gold; ML training from feature store; control tower over hot/OLAP; cost attribution + carrier scorecards ([analytics/](analytics/README.md)).
- **Digital twin:** what-if via the L4 simulator ([analytics/network-simulation.md](analytics/network-simulation.md)).
- **Reverse-ETL:** operational analytics pushed back to ops tools (not into transactional state).
- **Tiering rationale:** [ADR-0017](adr/0017-analytics-tiering-streaming-first.md).

## 9. External partner / 4PL integration

- **Carrier onboarding:** profile (modes, lanes, equipment, cutoffs) + EDI/API credentials via `edi-adapter`; tendered over `orch.command.v1`, statuses normalized to `vis.milestone.events.v1`.
- **3PL partner capacity:** `CapacityCommitment` in orchestrator; brokerage matches demand → partner; settlement via `fin.*`.
- **Customer/tenant integration:** API keys/OAuth via API gateway; webhooks for events; EDI/SFTP for B2B; self-service in partner portal.
- **Isolation:** partners touch only their tenant scope; all access audited on `audit.*`.

## 10. Anti-corruption boundary rules (mandatory)

1. No external/COTS data structure crosses into the canonical model unmapped.
2. Every inbound external feed has an adapter with schema validation + DLQ.
3. Every COTS product is wrapped; its DB is never queried by other services.
4. Canonical events are the only cross-domain contract.
5. Deprecations follow the versioning policy ([ADR-0008](adr/0008-schema-registry-and-versioning.md)).
