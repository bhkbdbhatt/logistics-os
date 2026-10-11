# read-model-builder (core, L2)

Kafka Streams projection of domain events into CQRS read models (ADR-0003): `shipment_view` and
`inventory_view`, served as compacted state topics plus an in-process snapshot layer for the BFFs.

## Topology (`ReadModelTopology`)

| input topic               | store             | output topic            | key          |
|---------------------------|-------------------|-------------------------|--------------|
| `tms.shipment.events.v1`  | `shipment-view`   | `shipment.state.v1`     | shipmentId   |
| `wms.inventory.events.v1` | `inventory-view`  | `inventory.state.v1`    | inventoryId  |

Projection processors are **version-guarded** (replay and out-of-order safe, ADR-0007):

- Shipment: applied only when `occurredAt` is not older than the stored projection's `updatedAt`.
- Inventory: applied only when `moveSequence` is strictly greater than the stored sequence.

Projection values are JSON (scaffold; a dedicated `.state.v1` Avro contract is backlog). The source
of truth stays the event topics — the views are rebuildable from retention at any time.

## Serving layer

`StateSnapshotPoller` consumes the compacted state topics into an in-process `ReadModelCache`
(single-instance scaffold; partition-keyed shards in production). `ReadModelsController` exposes:

- `GET /views/shipments/{id}` and `GET /views/shipments?status=IN_TRANSIT`
- `GET /views/inventory/{id}` and `GET /views/inventory?sku=...`
- `GET /views/health` (counts + last snapshot time)

No L3 databases are touched; this layer is read-only by construction (ADR-0003).

## Tests

- `ShipmentProjectionMapperTest` / `InventoryProjectionMapperTest` — pure projection mapping.
- `ReadModelTopologyIT` — embedded-Kafka end-to-end: lifecycle projection through CREATED →
  PLANNED → DISPATCHED, stale replay rejected; inventory duplicate `moveSequence` rejected.