# Control Tower (L6)

> Status: Accepted · Related: [README.md](README.md) · [../orchestration/README.md](../orchestration/README.md) · [../orchestration/decision-context.md](../orchestration/decision-context.md) · [../orchestration/escalation.md](../orchestration/escalation.md) · [../schemas/analytics-events.md](../schemas/analytics-events.md) · [../adr/0003-cqrs-read-models.md](../adr/0003-cqrs-read-models.md)

The operator cockpit: a live, role-filtered view of the network plus an alert queue and **one-click actions**. It is a **read-mostly projection**; actions are idempotent commands routed through policy — the tower never writes domain state directly ([ADR-0003](../adr/0003-cqrs-read-models.md), [services.md](../services.md)).

## 1. Surfaces & personas

| Surface | Persona | Primary question |
|---|---|---|
| Network wallboard | exec / shift lead | "Is the network healthy right now?" |
| Dispatch / transport board | dispatcher | "What is unassigned, late, or at risk?" |
| Warehouse board | DC supervisor | "What is blocked, behind, or at SLA risk?" |
| Yard board | yard coordinator | "What is at the gate/dock? Who is waiting?" |
| Customer-success board | CS agent | "Which customers/VIPs are impacted?" |
| Platform health | SRE | "Which integrations/pipelines are degraded?" |
| Simulation workbench | planner | "What if we change the network?" ([network-simulation.md](network-simulation.md)) |

All surfaces are BFF-composed over shared read models; **no domain logic in the UI**.

## 2. Metrics

Freshness tier in brackets: **[T0]** p99 < 2 s, **[T1]** < 30 s, **[T3]** batch.

### 2.1 Network / service
- On-time performance (pickup & delivery) — rolling 15 m / 24 h **[T1]**
- At-risk shipments (`p50 ETA > deadline − buffer`, from `intel.prediction.v1`) **[T0]**
- Open exceptions by severity & state; SLA-breaching count **[T0]**
- Promise adherence (delivered vs `orch.promise.v1`) **[T1]**
- Backlog / open orders by age; fill rate **[T1]**

### 2.2 Transport
- Tenders pending / declined / expired; unassigned loads **[T0]**
- Linehaul & vehicle utilization; empty miles **[T1]**
- Dwell at facilities; detention exposure **[T1]**
- Cost per shipment vs plan; accessorial incidence **[T2]**
- Mode split; spot vs contract share **[T3]**

### 2.3 Warehouse
- WIP, pick/pack rate (units & lines/hr), wave progress **[T0/T1]**
- Dock/door utilization; inbound/outbound backlog **[T1]**
- Pick task age vs SLA; short picks; blocked tasks **[T0]**
- Automation health: conveyor/AGV status, queue depth, fault count **[T0]**

### 2.4 Yard
- Trailers on site; gate-in/out rate; door occupancy **[T0]**
- Appointment adherence; trailer dwell; moves pending **[T1]**

### 2.5 Cost & margin
- Running cost-to-serve; margin-at-risk; cost overrun forecast **[T2]**
- Accrued vs billed; unbilled aging **[T3]**

### 2.6 Customer & platform
- VIP/tier-1 shipments at risk **[T0]**
- Customer escalations open **[T0]**
- Integration health: per-peer error rate, latency, `429`s **[T0]**
- Consumer lag per topic/group; DLQ depth; saga failure rate; model drift **[T0]**

Each metric is defined **once** in the semantic layer and reused by BI ([README.md §2.3](README.md)).

## 3. Alert triggers

Alerts are raised by the control-tower rule engine over T0/T1 state, emitted on `analytics.alert.v1`. They are the **UI-facing** view of the same conditions orchestration evaluates as triggers/exceptions ([../orchestration/trigger-catalog.md](../orchestration/trigger-catalog.md)); they are correlated, not duplicated, via `triggerRef`/`exceptionRef`.

| Alert | Condition | Severity | Default routing | Suggested one-click |
|---|---|---|---|---|
| `SLA_AT_RISK` | `p50 ETA` > deadline − buffer | HIGH | notify dispatcher | expedite, notify, re-opt |
| `SLA_BREACHED` | delivered/pickup after deadline | CRITICAL | escalate | escalation, credit, notify |
| `EXCEPTION_OPENED` | `orch.exception.v1` opened | HIGH | to exception queue | assign, apply option |
| `ESCALATION_BREACH` | escalation deadline within X | CRITICAL | page on-call/exec | escalate, reassign |
| `TENDER_DECLINED` | all tenders declined/timeout | HIGH | dispatcher | re-tender, rebroadcast |
| `UNASSIGNED_LOAD` | load past cutoff, no carrier | CRITICAL | dispatcher | re-tender, rebroadcast |
| `DWELL_EXCEEDED` | facility dwell > threshold | MEDIUM | DC supervisor | reschedule, notify |
| `DOCK_OVERBOOKED` | door demand > capacity window | MEDIUM | yard coordinator | reschedule, divert |
| `AUTOMATION_BLOCKED` | conveyor/AGV fault or queue stall | CRITICAL | DC supervisor + SRE | pause line, failover |
| `INVENTORY_SHORT` | negative ATP or short-pick | HIGH | DC supervisor | substitute, split, backorder |
| `COST_OVERRUN` | forecast cost-to-serve > target | MEDIUM | shift lead | review, re-opt, notify |
| `VIP_AT_RISK` | at-risk shipment tier-1 customer | CRITICAL | CS + dispatcher | expedite, exec notify |
| `POD_MISSING` | delivered w/o POD past window | MEDIUM | CS agent | request POD, follow up |
| `ATP_EXCEPTION` | promise cannot be met | HIGH | planner | re-promise, split |
| `INTEGRATION_LAG` | peer latency/error SLO breach | HIGH | SRE | failover, circuit, retry |
| `DLQ_BACKLOG` | DLQ depth > threshold | HIGH | SRE | inspect, replay |
| `MODEL_DRIFT` | `intel.model.events.v1` drift | MEDIUM | data science | retrain, gate |

**Alert hygiene:** debounce + dedupe on `(type, subject, window)`; auto-resolve on condition clear; suppression when a higher-severity alert subsumes a lower one on the same subject; **severity→routing** follows the [escalation ladder](../orchestration/escalation.md) and tenant policy.

## 4. One-click actions

Every action is an **idempotent command** on `orch.command.v1` (carries `commandId`, `correlationId`), authorised by policy, and recorded as a `DecisionRecord`. The UI shows the [DecisionContext](../orchestration/decision-context.md) (facts, options, expected impact, confidence) before the operator commits.

| Action | Emits (command) | Autonomy default | Notes |
|---|---|---|---|
| Accept recommendation | (applies chosen option) | AUTO/HUMAN per [ADR-0012](../adr/0012-policy-based-human-in-the-loop.md) | records `overrodeRecommendation` |
| Re-tender / rebroadcast | `TenderShipment` | HUMAN | respects tender policy & fallbacks |
| Rebook carrier | `RebookCarrier` | HUMAN (L2) | on decline/no-show |
| Re-sequence route / re-plan | `Reoptimize` | HUMAN for committed loads | warm-start, stability gate |
| Split / merge load | `RebuildLoad` | HUMAN | consolidation |
| Expedite / prioritize | `Prioritize` | AUTO (low) | affects sequencing, not promises |
| Reschedule appointment | `RescheduleAppointment` | AUTO if within rules | yard/dock |
| Hold / release | `HoldShipment` | HUMAN | blocks further automation |
| Pause / resume automation | `ControlEquipment` | HUMAN (two-person) | safety interlock respected |
| Manual ETA/status override | `OverrideStatus` | HUMAN | **append-only correction** event |
| Notify customer | `NotifyParty` | AUTO | templated, tenant-branded |
| Escalate | `Escalate` | AUTO by SLA | raises the ladder |
| Cancel / return | `CancelShipment` / `InitiateReturn` | HUMAN (L4) | compensation flow |
| Assign / reassign exception | `AssignCase` | AUTO/HUMAN | ownership only |
| Approve high-value action | `ApproveDecision` | HUMAN (four-eyes) | required over policy threshold |

Cross-cutting rules: high-impact actions require **two-person approval** above a value threshold; all actions are **audited** (`audit.*`); destructive/physical actions (`ControlEquipment`, `CancelShipment`) are the most restricted; the tower can **always** fall back to "open in orchestration" for a manual workflow.

## 5. Layout (composed view)

```
┌ Network wallboard ───────────────────────────────────────────────────────┐
│ OTD% ▂▃▅  | At-risk 37 | Exceptions 12 (3 breaching) | Promises 98.4%     │
├ Live map ────────────────────────────────────────────────────────────────┤
│ shipments (color=risk) · corridors (color=utilization) · nodes (dock %)   │
├ Alert queue (severity-sorted) ────────────────────────────────────────────┤
│ [CRIT] UNASSIGNED_LOAD  SHP-9812  cutoff 00:40  → re-tender | rebroadcast │
│ [HIGH] SLA_AT_RISK      SHP-9771  +75m          → expedite | notify       │
├ Drill-down: subject timeline ─────────────────────────────────────────────┤
│ milestones · ETA P10/P50/P90 · decisions · costs · documents              │
└───────────────────────────────────────────────────────────────────────────┘
```

Live updates stream over WebSocket/SSE from the bus ([integrations.md §7](../integrations.md)); a degraded pipeline shows an explicit **staleness badge** rather than silently stale numbers.
