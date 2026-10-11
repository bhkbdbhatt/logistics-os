# Trigger Catalog

> Status: Accepted · Related: [README.md](README.md) · [workflow-engine.md](workflow-engine.md) · [decision-context.md](decision-context.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md) · [../adr/0013-exception-cascade-and-reopt-coalescing.md](../adr/0013-exception-cascade-and-reopt-coalescing.md)

A **trigger** is a rule evaluated by the Trigger Evaluator over the event stream and timers. When a rule matches, it emits a `TriggerEvaluated` on `orch.trigger.v1` carrying a `dedupeKey` and, usually, a **cascade template** that the Cascade Orchestrator instantiates.

Every trigger has: **source event**, **predicate/guard**, **threshold**, **debounce/coalesce window**, **severity**, **cascade template**, and an **idempotency key**.

## 1. Rule shape

```yaml
trigger:
  id: TIME_SLA_DELIVERY_AT_RISK
  type: TIME_SLA
  severity: HIGH
  sourceEvents: [intel.prediction.v1, vis.milestone.events.v1]
  predicate: >
    prediction.entity == shipment
    AND prediction.kind == ETA
    AND prediction.etaAt > promise.deliveryDeadline - bufferMin
  params:
    bufferMin: 30          # tenant-overridable
    minConfidence: 0.7
  window:                  # debounce / coalesce
    mode: sliding
    seconds: 300
  dedupeKey: "TIME_SLA:{shipmentId}:eta"
  cascadeTemplate: SHIPMENT_AT_RISK
```

Predicates are evaluated with tenant `Policy` overlays; thresholds are configuration, not code ([build-vs-buy.md](../build-vs-buy.md)).

## 2. Trigger classes

### 2.1 Time / SLA

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `SLIP_PICKUP_WINDOW` | `intel.prediction.v1`, `tms.shipment.events.v1` | predicted pickup > window.end | HIGH | `SHIPMENT_AT_RISK` |
| `TIME_SLA_DELIVERY_AT_RISK` | `intel.prediction.v1` | predicted delivery > promised deadline − buffer | HIGH/CRITICAL | `SHIPMENT_AT_RISK` |
| `CUTOFF_APPROACHING` | timer | now > cutoff − leadTime ∧ not dispatched | MEDIUM | `DISPATCH_URGENT` |
| `DWELL_EXCEEDED` | `yms.yard.events.v1` | yard dwell > threshold (detention risk) | MEDIUM | `DETENTION_MITIGATION` |
| `APPOINTMENT_MISS` | `yms.appointment.events.v1` | no check-in by window.end | MEDIUM | `RESLOT_APPOINTMENT` |
| `SLA_BREACH_STATE_TIMER` | workflow timer | case in `AWAITING_*` > SLA | HIGH | → `ESCALATED` (not a template) |

### 2.2 Quality

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `TEMPERATURE_BREACH` | `obs.telemetry.v1` | reading outside `tempRange` for > N min | CRITICAL | `COLD_CHAIN_BREACH` |
| `DAMAGE_DETECTED` | `obs.scan.v1`, `wms.task.events.v1` | damage flag on scan/receipt | HIGH | `DAMAGE_HANDLING` |
| `INVENTORY_MISCOUNT` | `inv.inventory.events.v1` | cycle count variance > threshold | MEDIUM | `RECOUNT_AND_ADJUST` |
| `PACK_QUALITY_FAIL` | `wms.task.events.v1` | QC inspection fail | MEDIUM | `REWORK_PACK` |

### 2.3 Capacity / resource

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `LATE_INBOUND` | `intel.prediction.v1`, `vis.milestone.events.v1` | predicted inbound arrival > planned by > threshold | HIGH | `LATE_INBOUND_CASCADE` |
| `DOCK_CONGESTION` | `yms.yard.events.v1` | docks busy > X ∧ queue > Y | HIGH | `DOCK_REBALANCE` |
| `LABOR_SHORTAGE` | `wms.task.events.v1`, `*_view` | on-shift capacity < required demand | HIGH | `LABOR_REALLOCATE` |
| `EQUIPMENT_UNAVAILABLE` | `tms.shipment.events.v1` | assigned vehicle fails/offline | HIGH | `EQUIPMENT_REASSIGN` |
| `CARRIER_DECLINE` | `tms.tender.events.v1` | tender declined | MEDIUM | `RETENDER` |
| `CAPACITY_SHORTAGE` | `*_view`, `intel.prediction.v1` | available capacity < committed demand on lane | HIGH | `CAPACITY_PROTECT` |

### 2.4 Financial

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `RATE_DEVIATION` | `tms.tender.events.v1`, `fin.charge.events.v1` | |agreed − expected| / expected > X | MEDIUM | `RATE_REVIEW` |
| `ACCESSORIAL_SPIKE` | `fin.charge.events.v1` | accessorials > budget | MEDIUM | `BILLING_REVIEW` |
| `COST_OVERRUN_FORECAST` | `intel.prediction.v1` | forecast cost-to-serve > target | MEDIUM | `COST_MITIGATION` |
| `DETENTION_ACCRUING` | `yms.yard.events.v1` | billable dwell > free time | MEDIUM | `DETENTION_MITIGATION` |

### 2.5 Customer / service

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `VIP_AT_RISK` | `intel.prediction.v1`, `*_view` | at-risk shipment belongs to tier-1 customer | CRITICAL | `SHIPMENT_AT_RISK` (priority) |
| `ORDER_PROMISE_AT_RISK` | `intel.prediction.v1` | fulfillment trend breaks promise | HIGH | `PROMISE_RECOVERY` |
| `RETURN_SURGE` | `ord.order.events.v1` | return volume > baseline | MEDIUM | `RETURN_SURGE_HANDLING` |

### 2.6 External

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `SEVERE_WEATHER` | `obs.*` weather feed | weather severity affecting active lane | HIGH | `LANE_MITIGATION` |
| `PORT_CUSTOMS_HOLD` | external feed | hold/clearance delay | HIGH | `CUSTOMS_ESCALATION` |
| `ROAD_CLOSURE` | traffic feed | closure on active route | MEDIUM | `REROUTE` |
| `CARRIER_OUTAGE` | `obs.carrier-status.v1` | carrier API/network outage | HIGH | `CARRIER_FAILOVER` |

### 2.7 System / data quality

| Trigger | Source | Predicate | Severity | Cascade template |
|---|---|---|---|---|
| `TELEMETRY_LOSS` | `obs.telemetry.v1` | no ping > N min on active trip | MEDIUM | `TRACE_RECOVERY` |
| `PREDICTION_DRIFT` | model monitor | drift > threshold | LOW/MEDIUM | `MODEL_REVIEW` (ops, not domain) |
| `INTEGRATION_FAILURE` | `edi-adapter` | sustained EDI/API failures | HIGH | `INTEGRATION_INCIDENT` |

## 3. Real-time re-optimization triggers

Re-optimization is a distinct, high-frequency trigger class handled by the **Re-optimization Coordinator**, not the exception-case flow directly. It coalesces many low-level triggers into a single solver request.

| Re-opt trigger | Source | Condition | Objective default | Coalesce scope |
|---|---|---|---|---|
| `ETA_DELTA` | `intel.prediction.v1` | \|newETA − currentETA\| > N min on a load | MIN_TIME / BALANCE | load |
| `NEW_ORDER_INSERTION` | `ord.order.events.v1` | new order fits an open load (consolidation opportunity) | MIN_COST | load/lane |
| `RESOURCE_FAILURE` | `tms.*`, `obs.telemetry.v1` | vehicle/driver/dock unavailable | MIN_TIME | node/load |
| `COST_THRESHOLD` | `intel.*`, `fin.*` | projected cost > threshold | MIN_COST | lane/network |
| `SERVICE_PRIORITY_CHANGE` | `ord.order.events.v1` | order upgraded/VIP | MAX_SERVICE | load |
| `BATCH_WINDOW` | timer | periodic (e.g., every 15 min) wave/load re-plan | BALANCE | wave/network |
| `CAPACITY_RELEASE` | `inv.*`, `wms.*` | new capacity/slot opened | MIN_COST | node |

**Flow:** triggers → coalesce → `ReoptimizationRequested` (`orch.reopt.v1`) → L4 solver (sync gRPC) → `ReoptimizationCompleted` → **recommendations are decisions** → policy routes apply/no-apply (auto or human) → commands.

Re-opt never auto-applies to committed, in-transit, or financially-posted state without policy allowance; it proposes.

## 4. Debounce, coalescing, and storm control

Cascades must not amplify. The evaluator applies three controls:

1. **Debounce (sliding window):** a rule won't re-fire for the same `dedupeKey` within `window.seconds`; later matches update the open case rather than open a new one.
2. **Coalescing:** many triggers with the same `dedupeKey` collapse into one `ExceptionCase`; all contributing `triggerId`s are recorded on the case and on any `ReoptimizationRequested.triggerIds`.
3. **Dependency suppression:** if a higher-severity trigger for the same subject is active, lower-severity triggers are **subsumed** (linked, not dropped) to prevent contradictory actions.

| Control | Key | Default window | Notes |
|---|---|---|---|
| Debounce | `trigger.dedupeKey` | 5 min | tenant-overridable per rule |
| Case coalesce | `hash(cascadeTemplate, dedupeKey)` | life of case | deterministic `caseId` |
| Re-opt coalesce | `(scope, subject)` | 2 min | latest wins; accumulate causes |
| Subsumption | `subject + severity` | life of case | higher severity suppresses lower |

## 5. Idempotency of triggers

- `TriggerEvaluated.dedupeKey` = `type:subject:window` → replay produces the same key, so it merges rather than duplicates.
- `caseId` is deterministic from `(template, dedupeKey)`.
- Trigger evaluation is a pure function of `(event, rule, policy version)`; results are cacheable and reproducible.
- Thresholds/predicates versioned via `ruleVersion`; a policy change mid-case does not retroactively rewrite an open case's decisions (policy version is pinned in the `DecisionContext`).
