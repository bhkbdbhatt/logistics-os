# Canonical Schemas

> Status: Accepted · Related: [../events.md](../events.md) · [../data-model.md](../data-model.md) · [../adr/0007-outbox-and-idempotency.md](../adr/0007-outbox-and-idempotency.md) · [../adr/0008-schema-registry-and-versioning.md](../adr/0008-schema-registry-and-versioning.md)

Avro/Protobuf-style definitions for the Logistics OS canonical events. These are the concrete contracts behind [events.md](../events.md).

| Document | Contents |
|---|---|
| [common-types.md](common-types.md) | Event envelope, shared value types (Money, Quantity, GeoPoint, Address, TimeWindow), lightweight refs, **exactly-once & idempotency contract** |
| [shipment-events.md](shipment-events.md) | Shipment/Trip lifecycle (17 events) with required/optional fields, emitters, consumers |
| [reference-data.md](reference-data.md) | Customer, Carrier, Vehicle, Location golden-record schemas + reference events |
| [orchestration-events.md](orchestration-events.md) | Orchestration (L5): triggers, exception cases, **Decision Context**, decisions, escalations, re-optimization, commands |
| [intelligence-events.md](intelligence-events.md) | Intelligence (L4): ETA predictions, ranked recommendations, model lifecycle/drift, network simulation, feedback labels |
| [analytics-events.md](analytics-events.md) | L6/L7: operational alerts, per-shipment cost attribution, carrier scorecards |

## Quick reference

**Core aggregate:** `Shipment` (a.k.a. Trip/Consignment) — topic `tms.shipment.events.v1`, partition key `shipmentId`.

**Lifecycle:** `CREATED → PLANNED → TENDERED → DISPATCHED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED → SETTLED`, with cross-cutting `EXCEPTION`, `CANCELLED`, `RETURNED`.

**Every event carries** the [EventEnvelope](common-types.md#2-event-envelope): `eventId`, `tenantId`, `aggregateId`, `aggregateVersion`, `occurredAt`, `correlationId`, `traceparent`, `idempotencyKey`.

**Exactly-once + idempotency** (summary):
- Producer: transactional outbox; `aggregateVersion` monotonic per aggregate; partition by aggregate id.
- Consumer: dedupe on `idempotencyKey`, version-guard on `aggregateVersion`, idempotent upserts, ledger insert-or-ignore for money/inventory.
- Streams: `exactly_once_v2` for Kafka-to-Kafka; outbox + idempotent sink for external writes.

See [common-types.md §4](common-types.md#4-exactly-once--idempotency-contract) for the full contract and failure matrix.
