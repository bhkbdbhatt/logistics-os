# Common Types, Envelope & Idempotency

> Status: Accepted · Related: [../events.md](../events.md) · [shipment-events.md](shipment-events.md) · [reference-data.md](reference-data.md) · [../adr/0007-outbox-and-idempotency.md](../adr/0007-outbox-and-idempotency.md)

This document defines the shared Avro/Protobuf building blocks used by all canonical events: the **event envelope**, **common value types**, and the **exactly-once + idempotency contract**.

## 1. Schema conventions

- **Serialization:** Avro by default (shown below); Protobuf mappings noted where relevant. One Schema Registry subject per topic (`<topic>-value`).
- **Required vs optional (Avro):**
  - Required field → non-nullable type, no default, must be present.
  - Optional field → `["null", <type>]` with `"default": null`.
  - Required-with-default → non-nullable type + `"default"` (additive evolution).
- **Naming:** `UpperCamelCase` types/fields, `snake_case` namespaces, `SCREAMING_SNAKE` enum symbols.
- **Evolution:** additive-only with defaults, `BACKWARD` compatibility enforced ([ADR-0008](../adr/0008-schema-registry-and-versioning.md)). Breaking change → new `.vN` topic.
- **Money:** integer **minor units** + ISO-4217 currency. Never floats.
- **Time:** epoch millis UTC (Avro `timestamp-millis` logical type). `occurredAt` = when it happened; `recordedAt` = when persisted.
- **Ids:** ULID strings (lexicographically sortable), tenant-scoped.
- **Tenant scope:** `tenantId` is mandatory on the envelope and every reference record.

## 2. Event envelope

Every record on every topic is wrapped in this envelope. `payload` is the event-specific record.

```json
{
  "type": "record",
  "name": "EventEnvelope",
  "namespace": "io.logisticsos.events",
  "doc": "Universal envelope for all canonical events. Enables exactly-once and idempotent processing.",
  "fields": [
    { "name": "eventId",          "type": "string",  "doc": "ULID; globally unique event identity" },
    { "name": "eventType",        "type": "string",  "doc": "Fully-qualified type, e.g. tms.shipment.picked_up" },
    { "name": "eventVersion",     "type": "int",     "doc": "Payload schema version (minor/major int)" },
    { "name": "tenantId",         "type": "string",  "doc": "Owning tenant; mandatory for isolation" },
    { "name": "aggregateType",    "type": "string",  "doc": "e.g. shipment, trip, load, invoice" },
    { "name": "aggregateId",      "type": "string",  "doc": "Partition key; the entity id" },
    { "name": "aggregateVersion", "type": "long",    "doc": "Monotonic per-aggregate sequence; idempotency/ordering guard" },
    { "name": "partitionSequence","type": "long",    "doc": "Broker offset (populated by relay/pkg); optional at produce time" },
    { "name": "occurredAt",       "type": { "type": "long", "logicalType": "timestamp-millis" }, "doc": "When the domain fact happened" },
    { "name": "recordedAt",       "type": { "type": "long", "logicalType": "timestamp-millis" }, "doc": "When the producer persisted it" },
    { "name": "producer",         "type": "string",  "doc": "service@version, e.g. tms@2.4.1" },
    { "name": "correlationId",    "type": "string",  "doc": "End-to-end business flow id" },
    { "name": ["null", "string"], "name": "causationId",   "default": null, "doc": "eventId of the immediate cause" },
    { "name": ["null", "string"], "name": "traceparent",   "default": null, "doc": "W3C trace context for distributed tracing" },
    { "name": "idempotencyKey",   "type": "string",  "doc": "eventType:aggregateId:aggregateVersion (or producer key)" },
    { "name": "schemaSubject",    "type": "string",  "doc": "Schema Registry subject, e.g. tms.shipment.events.v1-value" },
    { "name": ["null", "string"], "name": "sourceSystem","default": null, "doc": "Origin system (for ACL-mapped events)" },
    { "name": "payload",          "type": "bytes",   "doc": "Serialized event-specific record (see shipment-events.md)" }
  ]
}
```

**Protobuf mapping (equivalent):**

```proto
message EventEnvelope {
  string event_id = 1;
  string event_type = 2;
  int32 event_version = 3;
  string tenant_id = 4;
  string aggregate_type = 5;
  string aggregate_id = 6;
  int64 aggregate_version = 7;
  int64 partition_sequence = 8;
  int64 occurred_at = 9;   // epoch millis
  int64 recorded_at = 10;
  string producer = 11;
  string correlation_id = 12;
  optional string causation_id = 13;
  optional string traceparent = 14;
  string idempotency_key = 15;
  string schema_subject = 16;
  optional string source_system = 17;
  bytes payload = 18;
}
```

## 3. Shared value types

```json
{
  "type": "record",
  "name": "Money",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "amountMinor", "type": "long",   "doc": "Minor units (e.g. cents)" },
    { "name": "currency",    "type": "string", "doc": "ISO-4217, e.g. USD" }
  ]
}
```

```json
{
  "type": "record",
  "name": "Quantity",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "value", "type": "double", "doc": "Numeric quantity" },
    { "name": "uom",   "type": { "type": "enum", "name": "Uom",
      "symbols": ["EA","CS","PLT","KG","LB","M3","FT3","L","GAL"] } },
    { "name": ["null","double"], "name": "uomConversion", "default": null,
      "doc": "Multiplier to base uom, if applicable" }
  ]
}
```

```json
{
  "type": "record",
  "name": "GeoPoint",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "lat", "type": "double" },
    { "name": "lon", "type": "double" },
    { "name": ["null","double"], "name": "accuracyMeters", "default": null },
    { "name": ["null","string"], "name": "source", "default": null, "doc": "gps|geocode|manual" }
  ]
}
```

```json
{
  "type": "record",
  "name": "Address",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "line1",        "type": "string" },
    { "name": ["null","string"], "name": "line2", "default": null },
    { "name": "city",         "type": "string" },
    { "name": ["null","string"], "name": "region", "default": null },
    { "name": "postalCode",   "type": "string" },
    { "name": "country",      "type": "string", "doc": "ISO-3166-1 alpha-2" },
    { "name": ["null","GeoPoint"], "name": "geo", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "TimeWindow",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "start", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "end",   "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

### 3.1 Lightweight references

Events embed **lightweight refs** (denormalized key + display fields) rather than full golden records. Consumers that need the full record fetch it from MDM (read model) by id. This keeps events small and decoupled ([ADR-0009](../adr/0009-canonical-model-as-moat.md)).

```json
{
  "type": "record",
  "name": "PartyRef",
  "namespace": "io.logisticsos.types",
  "doc": "Generic reference to a customer/carrier/vendor party",
  "fields": [
    { "name": "partyId", "type": "string" },
    { "name": "role",    "type": "string", "doc": "CUSTOMER|CONSIGNEE|CARRIER|SHIPPER|BROKER" },
    { "name": ["null","string"], "name": "name", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "LocationRef",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "locationId", "type": "string" },
    { "name": ["null","string"], "name": "type", "default": null, "doc": "WAREHOUSE|DOCK|STORE|DC|PORT|CUSTOMER_SITE" },
    { "name": ["null","string"], "name": "name", "default": null },
    { "name": ["null","GeoPoint"], "name": "geo", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "CarrierRef",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "carrierId", "type": "string" },
    { "name": ["null","string"], "name": "scac", "default": null },
    { "name": ["null","string"], "name": "name", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "VehicleRef",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "vehicleId", "type": "string" },
    { "name": ["null","string"], "name": "type", "default": null, "doc": "TRUCK|VAN|TRAILER|CONTAINER|BIKE|VESSEL|RAIL" },
    { "name": ["null","string"], "name": "licensePlate", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "DriverRef",
  "namespace": "io.logisticsos.types",
  "fields": [
    { "name": "driverId", "type": "string" },
    { "name": ["null","string"], "name": "name", "default": null },
    { "name": ["null","string"], "name": "phone", "default": null }
  ]
}
```

## 4. Exactly-once & idempotency contract

The platform guarantees **at-least-once delivery** on the wire and **effectively-once processing** at consumers. There is no global transaction; correctness comes from the combination below.

### 4.1 Producer side

| Requirement | Setting / pattern |
|---|---|
| Atomic DB write + publish | **Transactional outbox** table; relay via CDC or poller ([ADR-0007](../adr/0007-outbox-and-idempotency.md)) |
| Or, native Kafka transactions | `enable.idempotence=true`, `acks=all`, `transactional.id=<service>`, `max.in.flight.requests.per.connection<=5` |
| Deterministic dedupe key | `idempotencyKey = eventType:aggregateId:aggregateVersion` |
| Monotonic version | `aggregateVersion` incremented per aggregate under optimistic lock at write time |
| Ordering | partition key = `aggregateId` → per-entity total order |
| Tracing | `traceparent` propagated from the producing request |

### 4.2 Consumer side (mandatory)

Every consumer MUST implement all four:

1. **Deduplicate** on `idempotencyKey` (or `(aggregateId, aggregateVersion)`) using a dedupe store (RocksDB state store / Redis / DB unique index). TTL ≥ topic retention.
2. **Version-guard** writes: apply only if incoming `aggregateVersion` is **greater** than the stored version, unless the handler is explicitly commutative/upsert-by-natural-key. Stale events are ignored (logged, not errored).
3. **Idempotent effects:** upserts keyed by natural key; monetary/inventory effects use a ledger row keyed by `idempotencyKey` (insert-or-ignore) with conditional balance updates.
4. **Ordering tolerance:** buffer within a small window (e.g., 30s) to absorb reorder; otherwise rely on per-partition ordering and version guard.

### 4.3 Exactly-once for stream processors

Kafka Streams / Flink jobs that read-process-write **within Kafka** use `processing.guarantee=exactly_once_v2` (or Flink checkpointing with transactional sinks). For read-process-write to **external systems** (DB, HTTP), use the outbox pattern + idempotent sink; true XA is not used ([ADR-0004](../adr/0004-saga-over-distributed-tx.md)).

### 4.4 Failure & duplicate matrix

| Failure | Symptom | Handling |
|---|---|---|
| Producer crash after DB commit, before publish | Event missing | Outbox relay republishes from table (idempotent) |
| Producer publishes twice | Duplicate event | Consumer dedupe on `idempotencyKey` |
| Rebalance mid-batch | Reprocessing | Dedupe + version guard make replay safe |
| Out-of-order across partitions | Stale apply | Partition by `aggregateId`; version guard rejects stale |
| Poison message | Consumer stuck | Route to `dlq.<topic>`, alert, replay after fix |
| Consumer lag spike | Stale read models | Autoscale on lag; lag SLO alerts |

### 4.5 Canonical dedupe keys per entity

| Aggregate | Partition key | Version source | Effect guard |
|---|---|---|---|
| Shipment / Trip | `shipmentId` | shipment state version | unique `idempotencyKey` in effect store |
| Inventory | `inventoryId` | move sequence | ledger row insert-or-ignore |
| Invoice | `invoiceId` | invoice version | idempotent posting by `idempotencyKey` |
| Order | `orderId` | order version | upsert + version guard |
