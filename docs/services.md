# Service Catalog (Bounded Contexts)

> Status: Accepted · Related: [architecture.md](architecture.md) · [data-model.md](data-model.md) · [events.md](events.md) · [build-vs-buy.md](build-vs-buy.md)

Each service is a bounded context with **one logical database**, **its own API**, and an **outbox** for event publication. Services are grouped by architecture layer. "Type" is `Build`, `Buy` (COTS wrapped by an anti-corruption layer), or `Buy+Extend`.

## 1. Service map

| Service | Layer | Type | Owns (data) | Publishes | Consumes | Sync API |
|---|---|---|---|---|---|---|
| `ingest-gateway` | L1 | Build | none (edge buffer) | `obs.telemetry/scan/carrier-status.v1` | — | ingest REST/MQTT |
| `edi-adapter` | L1 | Buy | translation maps | `obs.carrier-status.v1` | `orch.command.v1` | EDI translator |
| `mdm` | L2 | Buy+Extend | golden records | `mdm.*.events.v1` | domain events | MDM REST |
| `event-backbone` | L2 | Buy | — (Kafka) | — | — | admin only |
| `read-model-builder` | L2 | Build | all `*_view` projections | `*.state.v1` | all domain events | read APIs |
| `lakehouse-ingest` | L2/L7 | Buy | bronze/silver | — | all topics | — |
| `oms` | L3 | Buy+Extend | orders | `ord.order.events.v1` | `orch.command.v1`, `orch.promise.v1` | order REST/GraphQL |
| `tms` | L3 | Buy+Extend | shipments, loads, tenders | `tms.*.events.v1` | `orch.command.v1` | shipping REST |
| `wms` | L3 | Buy+Extend | inventory, tasks, waves | `wms.*`, `inv.inventory.events.v1` | `orch.command.v1` | warehouse REST |
| `yms` | L3 | Buy | yard inventory, appointments | `yms.*.events.v1` | `orch.command.v1` | yard REST |
| `visibility` | L3 | Buy | milestones | `vis.milestone.events.v1` | `intel.prediction.v1` | tracking REST |
| `billing` | L3 | Buy+Extend | charges, bills, invoices | `fin.*.events.v1` | `tms.*`, `vis.*` | billing REST |
| `intelligence` | L4 | Build | features, predictions | `intel.prediction/recommendation.v1` | domain events, `*_view` | inference gRPC |
| `orchestrator` | L5 | Build | workflows, policies, exception cases, decisions | `orch.command/promise/exception/trigger/decision/escalation/reopt.v1` | all domain events | command REST |
| `atp-engine` | L5 | Build | available-to-promise | `orch.promise.v1` | `inv.*`, `wms.*`, `tms.*` | promise REST |
| `control-tower` | L5/L6 | Build | live ops state | — | all events | ops REST/WS |
| `bff-*` (customer, ops, partner, mobile) | L6 | Build | session cache | — | `*_view`, `orch.command.v1` | per-surface |
| `analytics` (dbt + semantic) | L7 | Buy | gold marts | reverse-ETL events | lakehouse | BI/metrics API |

## 2. Service responsibilities (detail)

### L1 — Observation
- **`ingest-gateway`**: terminates device/MQTT/HTTP streams at the edge; buffers, dedupes, time-syncs, and normalizes to `ObservationEvent`; forwards to Kafka. Stateless, horizontally scaled.
- **`edi-adapter`**: protocol/protocol translation (X12/EDIFACT ↔ canonical). A COTS EDI platform (Cleo/Orderful/SPS) fronted by an ACL. Never leaks EDI structure into domain services.

### L2 — Data
- **`mdm`**: golden-record registry for parties, locations, nodes, items, lanes, SLAs, equipment. Enforces uniqueness and survivorship rules. Publishes on compacted `mdm.*` topics.
- **`event-backbone`**: managed Kafka; owns topic creation (IaC), ACLs, quotas, Schema Registry.
- **`read-model-builder`**: Kafka Streams/Flink jobs projecting domain events into `order_view`, `shipment_view`, `inventory_view`, `yard_view`, `exception_view`. Rebuildable from event retention.
- **`lakehouse-ingest`**: Kafka Connect sinks + CDC into bronze; scheduling for silver/gold.

### L3 — Execution (all Buy+Extend except YMS/Visibility = Buy)
- **`oms`**: order capture from channels/EDI/API; order validation; sourcing rules bound to orchestrator; publishes order lifecycle. Extension owns split/merge policy hooks.
- **`tms`**: planning handoff, tendering, carrier assignment, tracking ingestion. Extension owns load-building *inputs* (the solver lives in L4).
- **`wms`**: receiving, putaway, picking, packing, cycle counting, physical inventory; integrates automation (ASRS/AMR) and scanners. Extension = automation drivers.
- **`yms`**: gate check-in/out (OCR/LPR), yard inventory, spot assignment, dock scheduling, moves.
- **`visibility`**: aggregates milestones from carrier networks (project44/FourKites) and internal scans; emits normalized milestone events.
- **`billing`**: freight audit/pay, customer charging, invoicing, settlement, GL export. High governance (exactness).

### L4 — Intelligence
- **`intelligence`**: model endpoints — ETA, forecast, anomaly, recommendation — plus pluggable solver services (routing/load/allocation) and the network simulator. Feature store for online+offline parity. Emits predictions (`intel.prediction.v1`), recommendations (`intel.recommendation.v1`), model lifecycle (`intel.model.events.v1`), simulations (`intel.simulation.v1`), and feedback labels (`intel.feedback.v1`); **never writes domain state** ([intelligence/](intelligence/README.md)).

### L5 — Orchestration
- **`orchestrator`**: saga/process-manager runtime (Temporal/Camunda/Zeebe) hosting order-to-cash, returns, and exception workflows. Internally composed of Trigger Evaluator, Cascade Orchestrator, Context Assembler, Policy Engine, Decision Router, Re-optimization Coordinator, Action Executor, and Escalation Manager. Emits commands; applies tenant `Policy`. Routes decisions to human vs. automated agents. **Core moat.** Full design: [orchestration/](orchestration/README.md).
- **`atp-engine`**: aggregates available inventory + capacity into promises; domain differentiator.
- **`control-tower`**: real-time ops state, alert queue, live map, one-click actions; read-mostly projection over events + hot store. Actions are idempotent `orch.command.v1` commands. Design: [analytics/control-tower.md](analytics/control-tower.md).

### L6 — Experience
- **`bff-customer` / `bff-ops` / `bff-partner` / `bff-mobile`**: backend-for-frontend per surface. Compose `*_view` reads + orchestrator commands; push live updates via WS/SSE. **No domain logic.** Design: [analytics/control-tower.md](analytics/control-tower.md).

### L7 — Analytics
- **`analytics`**: tiered pipeline (T0 streaming → T3 lakehouse) + dbt + semantic layer; produces cost attribution ([cost-attribution.md](analytics/cost-attribution.md)), carrier scorecards ([carrier-scorecards.md](analytics/carrier-scorecards.md)), KPIs, and drives network what-if (engine in L4/[network-simulation.md](analytics/network-simulation.md)). Full design: [analytics/](analytics/README.md).

## 3. Interaction rules (enforced)

1. **No shared databases.** Integration is events or explicit sync APIs only.
2. **No BFF → L3 DB.** BFFs read CQRS views and call orchestrator commands.
3. **No L5 → L3 DB.** Orchestration emits commands; execution replies with events.
4. **Cross-domain writes are sagas**, each step idempotent, with compensations ([ADR-0004](adr/0004-saga-over-distributed-tx.md)).
5. **Every write publishes via outbox**; no dual-write to DB + broker ([ADR-0007](adr/0007-outbox-and-idempotency.md)).
6. **Only orchestrator/L5 owns cross-domain promises.** Execution domains never promise across boundaries.

## 4. Build vs buy per service

See the full [build-vs-buy.md](build-vs-buy.md) matrix. Summary of this catalog:

- **Build:** `ingest-gateway`, `read-model-builder`, `intelligence`, `orchestrator`, `atp-engine`, `control-tower`, all BFFs.
- **Buy (ACL-wrapped):** `edi-adapter`, `event-backbone`, `lakehouse-ingest`, `yms`, `visibility`, `analytics`.
- **Buy+Extend:** `mdm`, `oms`, `tms`, `wms`, `billing`.
