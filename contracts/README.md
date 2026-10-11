# Contracts (`contracts/`)

The Avro schema files in `contracts/avro/**/*.avsc` are the **single source of truth** for event payloads.
The markdown tables in `docs/schemas/*.md` are human-readable views **derived from** the Avro; do not change a table
without changing the `.avsc` (and vice versa). CI reconciles both directions.

## Layout

```
contracts/
  avro/
    io/logisticsos/
      events/                EventEnvelope (all events)
      types/                 Shared value types (Money, Address, Quantity, ...)
      shipment/ShipmentStatus.avsc
      shipment/v1/           Shipment event payloads
      mdm/v1/                Golden-reference payloads (Customer, Carrier, Vehicle, Location, ReferenceUpserted)
      wms/v1/                Inventory + task payloads (WmsTaskEvent, InventoryMove) - canonical
      orchestration/v1/      Exception-case, decision, escalation, reopt, command payloads
      obs/v1/                Observation payloads (Telemetry, Scan, CarrierStatus) - scaffold
  payload-registry.json      eventType -> payloadSchema mapping for envelope.payload
  baselines/                 Frozen schema snapshots for compatibility drift detection
  validation/build.gradle.kts  Gradle project: parse-all + registry + md reconcile gate
```

## Ordering rules

1. **Types first** - schemas may only reference types that resolve in namespace-graph order
   `types` -> `shipment`/`mdm` -> `orchestration`/`obs`. A record may never depend on a record defined later in the
   dependency graph.
2. Fullname = `namespace.name` (e.g. `io.logisticsos.shipment.v1.ShipmentCreated`). Nested records are flattened to
   their fullname and must not collide with an explicitly declared record.
3. Envelope payload is `bytes`; the concrete schema is resolved at runtime via `payload-registry.json`
   (`eventType` -> `payloadSchema`, subject `<payloadSchema>-value` with record-name strategy).
4. Event names are past-tense verbs; commands are imperative nouns (prefix `OrchestrationCommand` pattern).
5. `null` fields MUST be the first branch of unions and MUST carry a default.

## Forced evolution rules (enforced by CI on `avro`)

| Change | Allowed backwards | Breaking |
| --- | --- | --- |
| Add nullable field with default | yes | |
| Add non-null field with default | yes | |
| Remove field | | yes |
| Change field type | | yes |
| Remove enum symbol | | yes |
| Rename record/enum | | yes (new fullname = new contract; register new eventType, deprecate old in registry) |
| Reorder fields | yes (avro) | no for binary producers | 

When you must break compatibility: bump `eventVersion` in the envelope, register the new `payloadSchema` under a new
fullname, keep the old subject on the registry with `status: "sunset"`, and schedule a dual-read window.

## Adding a new event type

1. Write the payload schema under the correct namespace in `contracts/avro/io/logisticsos/`.
2. Add one entry to `contracts/payload-registry.json` (`eventType`, `payloadSchema`, `kind`, `status`, `sourceManager`).
3. Update the corresponding table in `docs/schemas/*.md`.
4. Run the reconcile gate: `./contracts/scripts/reconcile.sh` (CI runs the equivalent Gradle task
   `:contracts:validation:validateSchemas`).
5. Register the subject with `-value` suffix in the schema registry at deploy time (record-name-strategy).

## Validation gates (CI)

`gradle :contracts:validation:validateSchemas` does all three in one parse pass and fails the build on any drift:

1. **Parse-all**: every `*.avsc` parses into a single `Schema.Parser` (catches duplicate fullnames and
   unresolved cross-file references - the shared-`PolicyRef` anti-pattern is exactly what this catches).
2. **Registry reconcile**: every `payload-registry.json` entry's `payloadSchema` must exist and resolve; every
   top-level record in `avro/io/logisticsos/{shipment/v1,mdm/v1,orchestration/v1,obs/v1}` must have a registry entry
   (unless `@registry.exclude` is set in that file).
3. **Docs reconcile**: event names in `docs/schemas/*.md` tables exist as `.avsc` records (and vice versa).

Local runs use `contracts/baselines/` snapshots for drift detection: `-PbumpBaseline` regenerates the snapshot.

## Status ladder

- `canonical` - frozen; changes only via the forced-evolution path above.
- `draft` - under change control but not yet locked; consumers must treat as unstable.
- `scaffold` - placeholder shape to unblock builds; will be canonicalized before GA (the obs section is the example).