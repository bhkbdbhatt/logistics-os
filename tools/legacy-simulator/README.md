# tools:legacy-simulator

Fake legacy TMS/WMS vendors for local and CI smoke runs. Emits provider JSON (no envelopes, no
Avro) onto the legacy topics that `acl-tms` / `acl-wms` consume:

- `legacy.tms.events.v1` — `ShipmentCreated` / `ShipmentPlanned` / `ShipmentDispatched` cycle
- `legacy.wms.events.v1` — `ScanEvent` / `WmsTaskUpdate` / `StockMove` cycle

Each round cycles a working set (`simulator.working-set`) so the downstream topology sees ongoing,
coherent lifecycle flows. Legacy records carry `X-Tenant-Id` and `X-Legacy-Type` headers.

## Configuration

| property                      | env                        | default |
|-------------------------------|----------------------------|---------|
| `simulator.enabled`           | `SIMULATOR_ENABLED`        | true    |
| `simulator.interval-millis`   | `SIMULATOR_INTERVAL_MS`    | 2000    |
| `simulator.tenant-id`         | `SIMULATOR_TENANT`         | acme    |
| `simulator.working-set`       | `SIMULATOR_WORKING_SET`    | 50      |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 |

Run with `java -jar tools/legacy-simulator/build/libs/*.jar` (or the boot task) after starting a
Kafka broker.