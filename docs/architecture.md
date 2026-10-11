# Logistics OS — Layered Architecture

> Status: Accepted · Owner: Platform Architecture · Related: [events.md](events.md) · [services.md](services.md) · [integrations.md](integrations.md) · [build-vs-buy.md](build-vs-buy.md) · [cross-cutting.md](cross-cutting.md)

## 1. Purpose

A cloud-native, event-driven Logistics Operating System for a mid-to-large **3PL/4PL** serving e-commerce and B2B. It unifies **TMS, WMS, YMS, OMS, carrier visibility, and billing/settlement** into one platform on a Kafka event backbone with microservices, and layers orchestration, intelligence, experience, and analytics on top.

## 2. Design stance (the "why")

A 3PL/4PL's moat is **orchestration and data**, not the individual execution apps. Therefore:

- **Execution apps are bounded, replaceable domains.** TMS/WMS/YMS/OMS/Visibility/Billing each own their state and publish domain events. Any of them can be a COTS product wrapped by an anti-corruption layer (see [ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)).
- **The moat lives above execution.** ATP/promise, load-building, consolidation, dynamic sourcing, control tower, tenant policy, and the canonical data model are **built and owned** (see [ADR-0009](adr/0009-canonical-model-as-moat.md)).
- **Everything is an event.** Canonical order/shipment/inventory state is a *projection*, not a master row. Cross-domain flows are sagas, never distributed transactions (see [ADR-0004](adr/0004-saga-over-distributed-tx.md)).
- **No system is the source of truth for what it does not physically control.** Ownership is explicit (see §6).

### Backbone assumptions

| Concern | Choice |
|---|---|
| Event log | Kafka (MSK/Confluent/Redpanda), one logical cluster per region |
| Serialization | Avro or Protobuf + Schema Registry, backward-compatible evolution |
| Delivery | At-least-once by default; **exactly-once** for money and physical inventory |
| Write atomicity | Transactional **outbox** + idempotent consumers (see [ADR-0007](adr/0007-outbox-and-idempotency.md)) |
| Query model | **CQRS** read models for query-heavy UIs (see [ADR-0003](adr/0003-cqrs-read-models.md)) |
| Runtime | Kubernetes + service mesh (mTLS), multi-region active-passive |
| Streaming compute | Kafka Streams for light transforms; Flink for stateful/large-window |

## 3. Layer diagram

![Layered architecture](diagrams/layers.mmd)

> Source: [`diagrams/layers.mmd`](diagrams/layers.mmd). Renders on GitHub/GitLab or via `mmdc`.
>
> ```mermaid
> %% Logistics OS - Layered Architecture (inline, same source as diagrams/layers.mmd)
> flowchart TB
>   subgraph L7["7. Analytics / AI"]
>     A7["Lakehouse - Semantic layer - ML - Digital twin"]
>   end
>   subgraph L6["6. Experience"]
>     A6["Customer portal - Ops console - Driver/Associate app - Partner/API portal"]
>   end
>   subgraph L5["5. Orchestration"]
>     A5["Saga engine - Control tower - ATP/Promise - Routing - Exceptions - Policy"]
>   end
>   subgraph L4["4. Intelligence"]
>     A4["Forecasting - Predictive ETA - Slotting - Placement - Optimization - Anomaly"]
>   end
>   subgraph L3["3. Execution"]
>     A3["OMS | TMS | WMS | YMS | Visibility | Billing"]
>   end
>   subgraph L2["2. Data"]
>     A2["Kafka backbone - Event store - Canonical models - CQRS read models - MDM - CDC"]
>   end
>   subgraph L1["1. Observation"]
>     A1["IoT/telematics - GPS - Scanners - Cameras - EDI/API feeds - Weather/Traffic - Edge gateways"]
>   end
>   L1 --> L2 --> L4
>   L3 --> L2
>   L2 --> L5
>   L2 --> L6
>   L2 --> L7
>   L5 --> L3
>   L4 --> L5
>   L7 --> L5
> ```

**Flow rule:** layers only communicate through the data layer (L2). There are **no direct synchronous chains** from L6 experience into L3 execution databases, and **no L5 orchestration calling L3 databases** — commands and events flow over the bus.

## 4. Layer-by-layer

Each layer below states its **role**, **responsibilities**, **integration pattern**, and **key objects**. Canonical definitions live in [data-model.md](data-model.md); topic details in [events.md](events.md).

### 4.1 Observation (L1)

- **Role:** Normalize the physical and external world into trusted signals — the only place "dumb" inputs enter.
- **Responsibilities:** device/telematics ingest (ELD, GPS, cold-chain, RFID/BLE, dimensioners, dock cameras); edge gateways that buffer/filter/dedupe/time-sync (NTP/PTP); external feeds (carrier EDI 214/210/990/997, 204/214 LTL, carrier APIs, project44/FourKites, port/terminal, weather, traffic, fuel, customs).
- **Integration pattern:** *Ingress adapters → topic per signal type.* Each source gets an anti-corruption adapter mapping raw payloads to a canonical `ObservationEvent`. Raw payloads land in the lake (bronze) for replay. At-least-once is acceptable; dedupe by `(source, deviceId, seq, eventTime)`.
- **Key objects:** `TelemetryPing`, `SensorReading`, `ScanEvent`, `CarrierStatusMessage`, `EDI204/214/990`, `GeofenceEvent`, `WeatherAlert`, `TrafficIncident`.

### 4.2 Data (L2)

- **Role:** The canonical, event-sourced spine — source of truth for **state you derive**, not state a system already owns.
- **Responsibilities:** Kafka topics as the log of record (compacted for current state, retained for history); stream processing that builds **CQRS read models**; canonical domain models + **MDM** (locations, parties, SKUs, equipment, lanes, service levels); CDC connectors (Debezium) from legacy DBs-of-record; lakehouse ingest (bronze→silver→gold); Schema Registry + contract governance; outbox tables.
- **Integration pattern:** *Event-carried state transfer + choreography.* Execution services publish domain events; L2 projects materialized views (`order_view`, `shipment_view`, `inventory_available_view`) consumed by L4–L7. No synchronous cross-domain chains. Write authority stays with the owning service.
- **Key objects:** see §6 and [data-model.md](data-model.md).

### 4.3 Execution (L3)

- **Role:** Transactional systems of record for physical and logical execution — each a bounded context with its own store.
- **Responsibilities / ownership:**

| Domain | System of record for | Key external integrations |
|---|---|---|
| **OMS** | Order capture, sourcing/split rules, promises | Commerce/B2B channels, payment, tax |
| **TMS** | Route plan, tendering, carrier assignment, tracking | Carriers (EDI/API), rating, dock appointment |
| **WMS** | Receiving, putaway, picking, packing, cycle count | Automation (ASRS/AMR), scanners, print/label |
| **YMS** | Yard inventory, gate, spot assignment, moves | Gate OCR, LPR cameras, dock scheduling |
| **Visibility** | Milestones, ETAs, exception flags | Carrier networks, project44/FourKites |
| **Billing/Settlement** | Freight audit, pay/charge, invoicing | Accounting, factor, customer billing |

- **Integration pattern:** each service is sovereign (own DB, own API, publishes via outbox). Cross-domain flows use **sagas/process managers** with compensating actions. Synchronous calls only for read-your-write UI. COTS products wrapped with **strangler-fig** adapters.
- **Key objects:** `PickTask`, `Wave`, `RouteStop`, `TenderOffer`, `YardMove`, `Appointment`, `Milestone`, `FreightBill`.

### 4.4 Intelligence (L4)

- **Role:** Turn state + history into **advisory** predictions and recommendations. Stateless-ish compute over L2 read models, events, and a feature store. Never writes domain state. Full design: [intelligence/](intelligence/README.md).
- **Responsibilities:** ETA/predictive visibility ([predictive-eta.md](intelligence/predictive-eta.md)); demand & volume forecasting; labor/inventory planning; optimization — VRP/load building ([route-optimization.md](intelligence/route-optimization.md)), slotting, placement, wave/dispatch; carrier selection/allocation ([carrier-selection.md](intelligence/carrier-selection.md)); anomaly/detention prediction; network simulation ([network-simulation.md](intelligence/network-simulation.md)); feature store serving batch + online inference with online/offline parity ([ADR-0014](adr/0014-optimization-and-inference-architecture.md)).
- **Integration pattern:** *Event-triggered inference + request/response for optimization.* Subscribe to domain events, emit `intel.prediction.v1`/`intel.recommendation.v1` back to the bus; expose sync gRPC/REST for sub-second solvers. Outputs are advisory and are mapped into the orchestration [DecisionContext](orchestration/decision-context.md). Every output carries `modelId`, `modelVersion`, `confidence`, `featuresHash` for auditability; feedback labels (`intel.feedback.v1`) close the learning loop.
- **Key objects:** `FeatureVector`, `EtaPrediction`, `Recommendation`, `RecommendationOption`, `Model`, `SimulationScenario`, `ForecastSeries`, `AnomalySignal`, `Score`, `FeedbackLabel`.

### 4.5 Orchestration (L5)

- **Role:** The control tower and brain — coordinates execution, applies policy, makes cross-domain promises. **Primary differentiator.** Full design: [orchestration/](orchestration/README.md).
- **Responsibilities:** ATP/promise engine (aggregate supply → promise); sourcing/split/orchestration across nodes and partners; load planning, carrier selection, multimodal routing, consolidation; workflow/saga engine for order-to-cash and returns; **cross-domain exception cascades** ([trigger-catalog.md](orchestration/trigger-catalog.md), [exception-flow.md](orchestration/exception-flow.md)); **real-time re-optimization triggers**; policy/rules engine routing decisions to **human vs. automated agents** ([decision-context.md](orchestration/decision-context.md), [escalation.md](orchestration/escalation.md)); cross-tenant/4PL brokerage.
- **Integration pattern:** *Process managers + rules.* Long-running stateful workflows ([workflow-engine.md](orchestration/workflow-engine.md)) emit commands (`TenderShipment`, `ReserveInventory`, `ReallocateLabor`, `RebookCarrier`) and consume domain events. Commands are idempotent and versioned. 4PL/tenant logic is configuration (rules), never forked code.
- **Key objects:** `Promise`, `AllocationPlan`, `RoutingPlan`, `TenderOffer`, `WorkflowInstance`, `Trigger`, `ExceptionCase`, `DecisionRecord`, `Escalation`, `Policy`, `CapacityCommitment`.

### 4.6 Experience (L6)

- **Role:** Role-specific, composable front ends over shared BFFs. **No domain logic.** The **control tower** is the operator cockpit — full design: [analytics/control-tower.md](analytics/control-tower.md).
- **Surfaces:** customer portal (track/quote/book/returns/invoices); ops console/control tower (live map, alerts, exception queue, one-click actions); warehouse associate & driver mobile apps (offline-capable); partner/API portal (carrier & 3PL onboarding, self-service); white-label storefront.
- **Integration pattern:** *BFF per surface* calling CQRS read models and orchestration commands; WebSocket/SSE/GraphQL subscriptions for live updates fed from the bus. One-click actions are idempotent `orch.command.v1` commands routed through policy; BFFs never touch L3 databases.
- **Key objects:** `ShipmentView`, `InventoryView`, `ExceptionView`, `Alert`, `Notification`, `UserSession`.

### 4.7 Analytics / AI (L7)

- **Role:** Batch + streaming analytics, finance/margin, scorecards, and the digital twin. Full design: [analytics/](analytics/README.md).
- **Responsibilities:** tiered pipeline (T0 real-time → T3 batch, [analytics/README.md §2](analytics/README.md)); lakehouse (Iceberg/Delta) bronze/silver/gold; streaming analytics (Flink) for live KPIs; **shipment-level cost attribution** ([cost-attribution.md](analytics/cost-attribution.md)); **carrier scorecards** ([carrier-scorecards.md](analytics/carrier-scorecards.md)); network what-if ([network-simulation.md](analytics/network-simulation.md), engine in [L4](intelligence/network-simulation.md)); cost-to-serve, margin, SLA batch, capacity analytics, carbon/ESG; semantic layer (dbt + metrics store).
- **Integration pattern:** *Kafka → streaming (Flink) + sink connectors → lakehouse; dbt transforms; semantic metrics.* Governed metrics; self-serve BI over gold only ([ADR-0017](adr/0017-analytics-tiering-streaming-first.md)).
- **Key objects:** `FactShipment`, `FactInventoryMove`, `FactCost`, `ShipmentCostAttribution`, `CarrierScorecard`, `Dimension(Carrier, Lane, Customer, Node)`, `Metric`, `OperationalAlert`, `SimulationScenario`.

## 5. Cross-cutting strip

Applies to all layers: **Identity/IAM**, **API Gateway**, **Observability** (OTel traces carried in event headers), **Secrets**, **Multi-tenancy**, **Schema Registry**, **Feature Store**, **Policy engine**. Details in [cross-cutting.md](cross-cutting.md).

## 6. Sources of truth (ownership)

| Object | Owner (write authority) | Authoritative store | Everyone else reads via |
|---|---|---|---|
| Order | OMS | OMS DB | `order_view`, `ord.*` events |
| Shipment / Route | TMS | TMS DB | `shipment_view`, `tms.*` events |
| Inventory physical | WMS | WMS DB | `inventory_view`, `inv.*` events |
| Inventory available (ATP) | Orchestration | ATP read model | `Promise`, `orch.promise.*` |
| Yard state | YMS | YMS DB | `yard_view`, `yms.*` events |
| Milestones / ETA | Visibility (+ Intelligence for ETA) | Vis store / prediction store | `vis.*`, `intel.prediction.*` |
| Charge / Invoice | Billing | Billing DB (strict) | `fin.*` events, `Invoice` |
| Canonical master data | MDM | MDM registry | `*.state.v1` compacted topics |

See [data-model.md](data-model.md) for the full lifecycle tables.

## 7. Event backbone (topic overview)

```
obs.telemetry.v1        obs.scan.v1          obs.carrier-status.v1
ord.order.events.v1     inv.inventory.events.v1   wms.task.events.v1
tms.shipment.events.v1  tms.tender.events.v1  yms.yard.events.v1
vis.milestone.events.v1 fin.settlement.events.v1
intel.prediction.v1     intel.recommendation.v1   intel.model.events.v1
intel.simulation.v1     intel.feedback.v1
orch.command.v1         orch.promise.v1       orch.exception.v1
orch.trigger.v1         orch.decision.v1      orch.escalation.v1
orch.reopt.v1
dlq.*                   audit.*   (compacted current state: *.state.v1)
```

Ordering key = aggregate id (e.g., `shipmentId`). Money/inventory topics use transactional producers + idempotent consumers. Full catalog in [events.md](events.md).
