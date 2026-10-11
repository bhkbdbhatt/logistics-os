# acl-tms (core, L1)

Anti-corruption layer / adapter for a legacy TMS vendor feed (ADR-0010, ADR-0016). Consumes plain
JSON strings on `legacy.tms.events.v1`, translates them onto the canonical shipment payload schemas,
and republishes envelopes to `tms.shipment.events.v1`.

## Mapping (`LegacyTmsMapper`)

| legacy type          | canonical payload                    |
|----------------------|--------------------------------------|
| `ShipmentCreated`    | `io.logisticsos.shipment.v1.ShipmentCreated` |
| `ShipmentPlanned`    | `io.logisticsos.shipment.v1.ShipmentPlanned` |
| `ShipmentDispatched` | `io.logisticsos.shipment.v1.ShipmentDispatched` |

Mapping is a pure function: canonical JSON is produced and validated by construction via
`PayloadAvro.jsonToGeneric`; unknown legacy types are rejected (logged, no dead-letter yet).

## Notes

- Scaffold-level translation: no transactional outbox yet. The producer uses `KafkaTemplate`
  directly; the build plan replaces it with an outbox binding and idempotent consumers
  (ADR-0007) in the production roadmap.
- Partition key = shipmentId ⇒ strictly ordered shipment events per partition for consumers.
- Consumer group: `acl-tms`, `auto-offset-reset: earliest`.

## Tests

- `LegacyTmsMapperTest` — pure mapper unit tests for all three types.
- `TmsPipelineIT` — embedded-Kafka roundtrip: legacy JSON in, canonical envelope out.