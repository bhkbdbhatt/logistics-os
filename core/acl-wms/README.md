# acl-wms (core, L1)

Anti-corruption layer / adapter for a legacy WMS vendor feed (ADR-0010, ADR-0016). Consumes plain
JSON strings on `legacy.wms.events.v1` and republishes canonical envelopes to the obs and wms topics.

## Mapping (`LegacyWmsMapper`)

| legacy type       | canonical payload                        | topic                  |
|-------------------|------------------------------------------|------------------------|
| `ScanEvent`       | `io.logisticsos.obs.v1.Scan`             | `obs.scan.v1`          |
| `WmsTaskUpdate`   | `io.logisticsos.wms.v1.WmsTaskEvent`     | `wms.task.events.v1`   |
| `StockMove`       | `io.logisticsos.wms.v1.InventoryMove`    | `wms.inventory.events.v1` |

Mapping is a pure function validated by construction via `PayloadAvro.jsonToGeneric`; unknown
legacy types are rejected (logged, no dead-letter yet).

## Notes

- Scaffold-level translation: `KafkaTemplate` used directly; outbox + idempotent consumers
  (ADR-0007) replace it in the production roadmap.
- Partition keys: scan → deviceId, task → nodeId, inventory move → inventoryId.

## Tests

- `LegacyWmsMapperTest` — pure mapper unit tests for all three types.