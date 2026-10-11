# mdm (L2)

Golden-record registry for the Phase-1 master entities **Customer / Carrier / Vehicle / Location**
(`docs/schemas/reference-data.md`). Single writer; publishes enveloped `ReferenceUpserted` events on
the compacted topics `mdm.customer.events.v1` · `mdm.carrier.events.v1` · `mdm.vehicle.events.v1` ·
`mdm.location.events.v1` (key = entity id).

## Behaviour

- **Upsert** — `POST /mdm/{entity}` with canonical record JSON (schema
  `io.logisticsos.mdm.record.v1.{Customer,Carrier,Vehicle,Location}`). The entity id is read from the
  record's id field. An unchanged record is an **idempotent no-op** (no new event, no version bump);
  a changed record increments `aggregateVersion` and publishes.
- **Deactivate** — `POST /mdm/{entity}/{id}/deactivate` publishes the same record with `active=false`.
- **Read** — `GET /mdm/{entity}` (list) · `GET /mdm/{entity}/{id}`.
- On start a small working set is seeded (`mdm.seed-enabled`, roadmap Phase 1 "MDM seed").

Each event wraps the canonical record as `bytes` in the `ReferenceUpserted` payload
(`io.logisticsos.mdm.v1`), carries `entityType`, `active` and `sourceSystem`, and is keyed by entity
id so consumers (ACLs, read models, control tower) can maintain latest-golden-record lookups.

## Run

```
gradle :core:mdm:bootRun        # port 8086, topic mdm.*.events.v1
```

`KAFKA_BOOTSTRAP_SERVERS` points at the broker. Compacted `mdm.*` topics are created by the local
compose/terraform bootstrap in WP6.

## Tests

- `ReferenceCodecTest` — wrap/unwrap + canonical JSON round-trip.
- `MdmServiceTest` — idempotent upsert, version bump, deactivation via a fake sink.
- `MdmPublishIT` — embedded broker: publishes and decodes a keyed `ReferenceUpserted`.

> Scaffold notes: the registry is in-process (a compacted-topic-backed store + DB replace it later);
> survivorship/merge (`mergedFrom`) and policy are Phase-2+ (`roadmap.md`).
