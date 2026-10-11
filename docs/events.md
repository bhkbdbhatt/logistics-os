# Event Catalog & Contracts

> Status: Accepted · Related: [architecture.md](architecture.md) · [data-model.md](data-model.md) · [services.md](services.md) · [schemas/](schemas/README.md) · [ADR-0008](adr/0008-schema-registry-and-versioning.md)

Kafka is the log of record. This document defines the **standard event envelope**, **topic catalog**, **partitioning**, **delivery semantics**, and **replay/DLQ** policy. Schema source of truth is the Schema Registry; this doc is the human-readable contract index.

> **Field-level schemas:** the concrete Avro/Protobuf definitions for the envelope, shared types, and the full **Shipment/Trip lifecycle events** (order placement → pickup → line-haul → last-mile → POD → settlement) live in [`schemas/`](schemas/README.md) — see [schemas/shipment-events.md](schemas/shipment-events.md), [schemas/common-types.md](schemas/common-types.md), and [schemas/reference-data.md](schemas/reference-data.md).

## 1. Standard event envelope

Every event on every topic uses the same envelope (Protobuf/Avro). The `payload` varies by event type.

```yaml
# Canonical envelope - logical shape (serialized as Avro/Protobuf)
envelope:
  eventId: string            # ULID, unique
  eventType: string          # e.g. "tms.shipment.delivered"
  eventVersion: int          # schema version
  tenantId: string           # mandatory - multi-tenancy
  aggregateType: string      # e.g. "shipment"
  aggregateId: string        # partition key
  occurredAt: string         # ISO-8601 UTC, when it happened
  recordedAt: string         # ISO-8601 UTC, when produced
  producer: string           # service name + version
  correlationId: string      # end-to-end business flow id
  causationId: string        # eventId of the immediate cause
  traceparent: string        # W3C trace context (observability)
  idempotencyKey: string     # dedupe key for consumers
  payload: object            # event-specific body
  metadata:                  # optional
    sourceSystem: string
    schemaSubject: string
```

**Rules**
- Consumers must ignore unknown fields (forward compatible).
- `correlationId` stitches a business flow (e.g., order-to-cash) across topics.
- `traceparent` carries distributed tracing across async boundaries ([ADR-0001](adr/0001-event-driven-kafka-backbone.md)).
- `idempotencyKey` = `eventType + aggregateId + aggregateVersion` (or producer-supplied) for exactly-once effects.

## 2. Delivery semantics

| Class | Topics | Guarantee | Mechanism |
|---|---|---|---|
| Signals / telemetry | `obs.*` | at-least-once | dedupe by `(source, deviceId, seq, eventTime)` |
| Domain events (general) | `ord.*`, `tms.*`, `wms.*`, `yms.*`, `vis.*` | at-least-once, ordered per aggregate | partition by aggregateId; outbox on publish |
| Money & physical inventory | `inv.*`, `fin.*` | **exactly-once effect** | transactional producer + idempotent consumer + idempotencyKey |
| Commands | `orch.command.*` | at-least-once, idempotent | command id + dedupe store |
| Projections | `*.state.v1` | compacted, latest-wins | key = aggregateId, tombstone on delete |

## 3. Topic catalog

| Topic | Producer | Primary consumers | Key | Retention / Cleanup | Notes |
|---|---|---|---|---|---|
| `obs.telemetry.v1` | Observation | Intelligence, Analytics | deviceId | 90d delete | high volume; edge-filtered |
| `obs.scan.v1` | Observation | WMS-integrators, Analytics | nodeId | 30d delete | scan/barcode |
| `obs.carrier-status.v1` | Observation | Visibility, TMS | shipmentRef | 90d delete | raw EDI/API normalized |
| `ord.order.events.v1` | OMS | Orchestration, Analytics, L6 | orderId | 7y delete | order lifecycle |
| `inv.inventory.events.v1` | WMS | Orchestration, Intelligence, Analytics | inventoryId | 3y delete | **exactly-once effects** |
| `wms.task.events.v1` | WMS | Orchestration, L6, Analytics | nodeId | 1y delete | pick/pack/receive/wave |
| `tms.shipment.events.v1` | TMS | Visibility, Billing, L6, Analytics | shipmentId | 7y delete | shipment lifecycle |
| `tms.tender.events.v1` | TMS | Orchestration, Billing | shipmentId | 3y delete | tender/accept/reject |
| `tms.load.events.v1` | TMS | YMS, Analytics | loadId | 3y delete | load/manifest |
| `yms.yard.events.v1` | YMS | TMS, Orchestration, L6 | nodeId | 2y delete | gate/move/dock |
| `yms.appointment.events.v1` | YMS | TMS, L6 | appointmentId | 2y delete | dock scheduling |
| `vis.milestone.events.v1` | Visibility | Orchestration, Billing, L6, Analytics | shipmentId | 7y delete | tracking milestones |
| `intel.prediction.v1` | Intelligence | Orchestration, Visibility, L6 | shipmentId | 1y delete | ETA/anomaly predictions |
| `intel.recommendation.v1` | Intelligence | Orchestration, L6 | subjectId | 1y delete | route/slot/reassign recs |
| `intel.model.events.v1` | Intelligence | Analytics, Orchestration | modelId | 2y delete | model deploy/drift/retire |
| `intel.simulation.v1` | Intelligence | Analytics, Orchestration | scenarioId | 1y delete | network what-if runs |
| `intel.feedback.v1` | Intelligence | Analytics | entityId | 2y delete | realized-outcome labels |
| `analytics.alert.v1` | Analytics/L6 | L6, Orchestration | subjectId | 90d delete | control-tower operational alerts |
| `analytics.cost.attribution.v1` | Analytics | L6, Billing, Analytics | shipmentId | 7y delete | per-shipment cost attribution |
| `analytics.scorecard.v1` | Analytics | L4, L6, Procurement | carrierId | 7y delete | carrier scorecards |
| `orch.command.v1` | Orchestration | TMS, WMS, YMS, OMS | commandTargetId | 30d delete | idempotent commands |
| `orch.promise.v1` | Orchestration | OMS, L6, Analytics | orderId | 3y delete | ATP promises |
| `orch.exception.v1` | Orchestration | L6, Analytics, L4 | caseId | 2y delete | exception-case lifecycle |
| `orch.trigger.v1` | Orchestration | Analytics, L6 | triggerId | 1y delete | evaluated trigger conditions |
| `orch.decision.v1` | Orchestration | L6, Analytics | decisionId | 2y delete | decision requests/outcomes (Decision Context) |
| `orch.escalation.v1` | Orchestration | L6, Analytics | caseId | 2y delete | escalation ladder events |
| `orch.reopt.v1` | Orchestration | L4, L6, Analytics | reoptId | 1y delete | re-optimization requests/results |
| `fin.charge.events.v1` | Billing | Analytics | refId | 7y delete | accruals/charges |
| `fin.settlement.events.v1` | Billing | Analytics | partyId | 10y delete | pay/charge settlement |
| `mdm.party.events.v1` | MDM | all domains | partyId | compact | golden records |
| `mdm.location.events.v1` | MDM | all domains | locationId | compact | golden records |
| `mdm.item.events.v1` | MDM | all domains | sku | compact | golden records |
| `*.state.v1` (per aggregate) | derived | L4–L7 read models | aggregateId | compact, delete | current-state projections |
| `audit.*` | all services | Compliance, Security | actorId | 7y delete | tamper-evident audit log |
| `dlq.*` | all consumers | Ops tooling | original key | 30d delete | poisoned messages; replayable |

> Naming: `<layer>.<domain>.<event-type>.vN`. The `.vN` suffix is the **major** version; minor/backward-compatible changes stay within the same topic via Schema Registry.

## 4. Partitioning & ordering

- **Partition key = `aggregateId`** for domain events → per-aggregate ordering.
- For `obs.telemetry.v1`, key = `deviceId`; for `obs.scan.v1`, key = `nodeId`.
- **Never** partition money/inventory by customer — partition by the financial aggregate (`invoiceId`) or `inventoryId` to keep per-entity ordering.
- Consumer groups named `<service>.<purpose>` (e.g., `billing.accrual`).

## 5. Commands vs events

- **Events** are past-tense facts (`shipment.delivered`), published by the owning service, many consumers.
- **Commands** are imperative intents (`TenderShipment`), addressed to a target domain, always idempotent, carry a `commandId`.
- Orchestration emits commands on `orch.command.v1`; execution services reply with domain events.

## 6. Schema governance

- All schemas in Schema Registry; **backward compatibility enforced** in CI.
- Breaking change → new topic major `.v2` + migration ADR + dual-write/dual-read window.
- Consumer-driven contract tests for critical producers.
- Fields are additive; removing/renaming is a major change.

## 7. Error handling, DLQ, replay

- Poison messages → `dlq.<original-topic>` with headers: `errorReason`, `originalTopic`, `originalOffset`, `failedAt`.
- Failed messages are **replayable**: re-drive from DLQ or from the topic offset after a fix.
- Dead-letter triage is an operational runbook owned by platform SRE.
- **Replay safety:** consumers must be idempotent (see [ADR-0007](adr/0007-outbox-and-idempotency.md)); projections can be rebuilt from `*.events.v1` retention windows.

## 8. Worked flow (order-to-cash)

See [`diagrams/event-flow.mmd`](diagrams/event-flow.mmd) for the sequence. For the full order → POD → settlement view including external touchpoints (ERP, carrier APIs, WCS, mobile, customer tracking), see [`diagrams/order-pod-settlement.mmd`](diagrams/order-pod-settlement.mmd) and [integration-architecture.md](integration-architecture.md). Summary:

1. `ord.order.created.v1` → Orchestration starts order workflow.
2. Orchestration calls ATP → `orch.promise.v1` → OMS confirms → `ord.order.confirmed.v1`.
3. Orchestration dispatches `ReserveInventory` + `Allocate` to WMS/YMS via `orch.command.v1`.
4. WMS emits `wms.task.events.v1`, `inv.inventory.events.v1`.
5. TMS tenders & ships → `tms.tender.events.v1`, `tms.shipment.events.v1`.
6. Visibility emits `vis.milestone.events.v1`; Intelligence emits `intel.prediction.v1`.
7. Delivery → Billing accrues → `fin.charge.events.v1` … `fin.settlement.events.v1`.
8. Any deviation → `orch.exception.v1` (consumed by L6 control tower and L4 analytics).
