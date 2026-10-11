# Canonical Data Model

> Status: Accepted · Related: [architecture.md](architecture.md) · [events.md](events.md) · [services.md](services.md)

The canonical model is the platform's shared vocabulary. It is **owned by the Data layer (L2)** and enforced by MDM + Schema Registry. Execution systems may have richer internal models, but only canonical events/objects cross service and layer boundaries.

## 1. Modeling rules

1. **One writer per object.** Write authority is assigned to exactly one service ([architecture.md §6](architecture.md#6-sources-of-truth-ownership)).
2. **Events first.** Every state change is an immutable event. Current state is a projection (`*.state.v1` compacted topics + CQRS views).
3. **Stable identifiers.** All ids are ULID/UUIDv7, tenant-scoped, never reused. Business keys (`orderNumber`, `trackingNumber`, `sscc`, `lot`) are attributes, not PKs.
4. **Money is exact.** Monetary values use minor units (integer cents) + ISO-4217 currency; never floats.
5. **Units are explicit.** Quantity carries `uom` (EA, CS, PLT) and optionally `uomConversion`.
6. **Time is UTC** with `eventTime` (when it happened) and `recordedTime` (when persisted). Never trust device clocks without sync provenance.
7. **Tenant isolation** — every object carries `tenantId`; large tenants may get dedicated stores.

## 2. Object catalog by domain

Legend: **Owner** = write authority; **Events** = primary published events; **Retention** = typical policy.

### 2.1 Party / Master data (MDM-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Party` | MDM | partyId, type (customer/carrier/vendor/partner), roles, taxId, addresses | `mdm.party.events.v1` | indefinite |
| `Location` | MDM | locationId, type (warehouse/dock/store/dc), address, geo, hours | `mdm.location.events.v1` | indefinite |
| `Node` | MDM | nodeId, tenant, capabilities (modes, services, equipment) | `mdm.node.events.v1` | indefinite |
| `Sku/Item` | MDM | sku, dims, weight, hazmat, tempClass, uom, serial/lot controlled | `mdm.item.events.v1` | indefinite |
| `Lane` | MDM | origin, dest, mode, transitMatrix, allowedCarriers | `mdm.lane.events.v1` | indefinite |
| `ServiceLevel` | MDM | slaId, cutoffs, promise windows, penalties | `mdm.sla.events.v1` | indefinite |
| `Equipment` | MDM | equipmentId, type (trailer/container), tempControl, ownership | `mdm.equipment.events.v1` | indefinite |

### 2.2 Order domain (OMS-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Order` | OMS | orderId, tenant, channel, customer, status, requestedSL, currency | `ord.order.created/confirmed/cancelled.v1` | 7y (contract) |
| `OrderLine` | OMS | lineId, sku, qty, uom, price, requestedDate | (embedded in order events) | 7y |
| `Promise` | Orchestration | promiseId, orderId, promisedDate, node, confidence | `orch.promise.v1` | 3y |
| `Allocation` | Orchestration | allocationId, orderLineId, node, sku, qty, status | `orch.allocation.v1` | 3y |

### 2.3 Inventory domain (WMS physical · Orchestration available)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `InventoryItem` | WMS | inventoryId, node, location/bin, sku, lot/serial, qty, uom, status | `inv.inventory.received/moved/adjusted.v1` | 3y |
| `StockPosition` | WMS | onHand, allocated, inTransit, quarantine | (derived) | n/a |
| `Reservation` | Orchestration | reservationId, orderLine, inventoryRef, qty, ttl | `orch.reservation.v1` | 1y |
| `InventoryAvailableView` | Orchestration | node, sku, atp, committed, safetyStock | (projection) | n/a |

### 2.4 Fulfillment / Warehouse domain (WMS-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Receipt` | WMS | receiptId, asnRef, node, status | `wms.receipt.events.v1` | 3y |
| `Wave` | WMS | waveId, node, releaseTime, strategy | `wms.wave.events.v1` | 1y |
| `PickTask` | WMS | taskId, wave, from, to, sku, qty, assignee, status | `wms.task.events.v1` | 1y |
| `PackUnit` | WMS | packUnitId, orderLine, dims, weight, sscc | `wms.pack.events.v1` | 3y |

### 2.5 Transport domain (TMS-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Shipment` | TMS | shipmentId, mode, origin, dest, stops[], status, sl | `tms.shipment.events.v1` | 7y |
| `RouteStop` | TMS | stopId, sequence, location, window, status | (in shipment events) | 7y |
| `Load/Manifest` | TMS | loadId, equipment, stops[], capacity, seal | `tms.load.events.v1` | 7y |
| `TenderOffer` | TMS | tenderId, carrier, rate, expiresAt, status | `tms.tender.events.v1` | 3y |
| `Rate` | TMS/MDM | rateId, lane, carrier, mode, charges[] | `tms.rate.events.v1` | 5y |

### 2.6 Yard domain (YMS-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Appointment` | YMS | appointmentId, dock, carrier, window, status | `yms.appointment.events.v1` | 2y |
| `YardInventory` | YMS | trailer/container, spot, dwellStart, checkIn | `yms.yard.inventory.v1` | 2y |
| `YardMove` | YMS | moveId, from, to, mover, timestamp | `yms.yard.events.v1` | 1y |

### 2.7 Visibility domain (Visibility-owned · ETA from Intelligence)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Milestone` | Visibility | milestoneId, shipmentId, code, eventTime, location, source | `vis.milestone.events.v1` | 7y |
| `EtaPrediction` | Intelligence | predictionId, shipmentId, window, confidence, modelId | `intel.prediction.v1` | 1y |
| `Exception` | Orchestration | exceptionId, ref, type, severity, resolution | `orch.exception.v1` | 2y |

### 2.8 Financial domain (Billing-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `Charge` | Billing | chargeId, ref, type, amount, currency, tax | `fin.charge.events.v1` | 7y |
| `FreightBill` | Billing | billId, carrier, invoiceRef, auditStatus | `fin.bill.events.v1` | 7y |
| `Invoice` | Billing | invoiceId, customer, period, total, status | `fin.invoice.events.v1` | 10y |
| `SettlementLine` | Billing | lineId, party, pay/charge, amount, glAccount | `fin.settlement.events.v1` | 10y |

### 2.9 Orchestration domain (Orchestration-owned)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `WorkflowInstance` | Orchestration | wfId, type, state, currentStep, correlation | `orch.workflow.events.v1` | 1y |
| `RoutingPlan` | Orchestration | planId, shipments[], loads[], objective, score | `orch.routing.v1` | 1y |
| `Policy` | Orchestration | policyId, tenant, scope, ruleset, version | `orch.policy.v1` | indefinite |
| `CapacityCommitment` | Orchestration | commitmentId, partner, lane/mode, volume, window | `orch.capacity.v1` | 3y |
| `Trigger` | Orchestration | triggerId, type, severity, ruleId, subject, dedupeKey | `orch.trigger.v1` | 1y |
| `ExceptionCase` | Orchestration | caseId, cascadeTemplate, severity, subject, state | `orch.exception.v1` | 2y |
| `DecisionRecord` | Orchestration | decisionId, caseId, decisionType, autonomyTier, chosenOptionId, policyRef | `orch.decision.v1` | 2y |
| `Escalation` | Orchestration | escalationId, caseId, from/toLevel, reason, assignee, deadline | `orch.escalation.v1` | 2y |
| `ReoptimizationRequest` | Orchestration | reoptimizationId, scope, objective, triggerIds, solverId | `orch.reopt.v1` | 1y |

### 2.10 Intelligence domain (Intelligence-owned, advisory)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `FeatureVector` | Intelligence | featuresHash, entityRef, values, asOf | (feature store) | 1y |
| `Recommendation` | Intelligence | recommendationId, type, subject, objective, options[], validUntil | `intel.recommendation.v1` | 1y |
| `Model` | Intelligence | modelId, kind, version, metrics, status, deployedAt | `intel.model.events.v1` | indefinite |
| `SimulationScenario` | Intelligence | scenarioId, baselineRef, levers[], horizon, objective, status | `intel.simulation.v1` | 1y |
| `CarrierScore` | Intelligence | carrierId, lane, window, otd, costIndex, riskScore, updatedAt | `intel.feedback.v1` | 1y |
| `FeedbackLabel` | Intelligence | entityRef, kind, predicted, actual, modelId, observedAt | `intel.feedback.v1` | 2y |

> L4 objects are **advisory**: they never represent domain of record. Predictions/recommendations feed the orchestration [DecisionContext](orchestration/decision-context.md); only L5 decisions and L3 commands change domain state.

### 2.11 Observation raw (Observation-owned, lake bronze)

| Object | Owner | Notes | Retention |
|---|---|---|---|
| `TelemetryPing` | Observation | lat/long, speed, heading, temp, deviceId, seq | 90d hot / 2y lake |
| `ScanEvent` | Observation | barcode/RFID, location, actor, ts | 1y |
| `CarrierStatusMessage` | Observation | raw EDI/API status before normalization | 2y |

### 2.12 Analytics derived (L6/L7 — projections, not sources of truth)

| Object | Owner | Key attributes | Events | Retention |
|---|---|---|---|---|
| `OperationalAlert` | Analytics/L6 | alertId, type, severity, subject, slaDeadline, state | `analytics.alert.v1` | 90d |
| `ShipmentCostAttribution` | Analytics | attributionId, shipmentId, components[], totals, margin, costStatus | `analytics.cost.attribution.v1` | 7y |
| `CarrierScorecard` | Analytics | scorecardId, carrierId, scope, dimensions[], composite, grade | `analytics.scorecard.v1` | 7y |
| `Metric` / `Fact*` / `Dimension*` | Analytics | semantic metric + gold facts/dims | (semantic layer) | per policy |

> L6/L7 objects are **derived and replayable** from canonical events; losing them loses no domain truth. Billing remains SoR for charges; ERP for ledger ([integration-architecture.md](integration-architecture.md)). Design: [analytics/](analytics/README.md).

## 3. Relationship map (high level)

```
Party(customer) ──< Order ──< OrderLine ──< Allocation >── InventoryItem
                         └── Promise                         │
                                                             ▼
Order ──> Shipment ──< RouteStop        InventoryItem ──> Warehouse/Bin
   │         │  └──< TenderOffer            
   │         │         └── Carrier
   │         └──< Milestone ──< EtaPrediction
   └──< Charge ──> Invoice ──> SettlementLine

Appointment ──> YardInventory ──> YardMove ──(feeds)── Shipment(stop)
```

## 4. Lifecycle examples

**Order:** `created → confirmed (promised) → allocated → fulfilled → closed` (+ `cancelled`, `on_hold`).
**Shipment:** `planned → tendered → accepted → in_transit → delivered` (+ `exception`, `cancelled`, `returned`).
**InventoryItem:** `received → available → allocated → picked → shipped` (+ `quarantined`, `adjusted`, `damaged`).
**Invoice:** `draft → issued → partially_paid → paid` (+ `disputed`, `written_off`).

## 5. Governance

- All schemas registered in Schema Registry; breaking changes require a new topic major (`.v2`) and a migration ADR.
- MDM publishes golden records; domains may not redefine master entities locally.
- PII fields tagged; residency rules enforced per tenant region ([cross-cutting.md](cross-cutting.md)).
