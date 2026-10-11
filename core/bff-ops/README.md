# bff-ops (L6)

Backend-for-frontend for the operations surface. **No domain logic** (ADR-0003): it composes
CQRS `*_view` reads (read-model-builder), control-tower alerts/actions, and MDM location-name
decoration, and forwards one-click actions verbatim. No Kafka, no L3 DB access.

## API

- `GET /ops/overview` — one call for the dashboard: shipments + inventory + open alerts +
  tower freshness. Each source is best-effort (`sourcesAvailable_*` flags).
- `GET /ops/shipments/{shipmentId}` — shipment detail with resolved MDM location names
  (`pickupLocationName` / `deliveryLocationName`).
- `POST /ops/actions/notify-customer` — `{ "shipmentId": "...", "caseId": "..." }`, forwarded to the
  control-tower which emits the idempotent `orch.command.v1`. Upstream failure → `502`.
- `GET /ops/health` — upstream reachability.

## Run

```
gradle :core:bff-ops:bootRun     # port 8088
```

Requires read-model-builder (8085), control-tower (8087) and mdm (8086) reachable
(`READ_MODEL_BASE_URL`, `TOWER_BASE_URL`, `MDM_BASE_URL`).

## Tests

- `OpsViewMapperTest` — overview composition + availability flags, MDM name resolution, health.
- `BffOpsControllerTest` — controller against a fake `OpsPort` (found/not-found, action ack / 502).