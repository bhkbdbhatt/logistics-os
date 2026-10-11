# End-to-End Exception Flow (Timed)

> Status: Accepted · Related: [workflow-engine.md](workflow-engine.md) · [trigger-catalog.md](trigger-catalog.md) · [decision-context.md](decision-context.md) · [escalation.md](escalation.md) · [diagrams/cascade-sequence.mmd](diagrams/cascade-sequence.mmd)

A worked, timed example of one exception cascade: **a weather-delayed inbound truck at a distribution center**. The delay breaks the receiving shift plan **and** the outbound cutoff for a 2-day promise. The cascade must: re-allocate labor, re-optimize the load, re-book a carrier, and notify the customer — each routed to the right agent.

Time base `T+mm:ss` from the initial weather signal. Automated work completes in ~2 minutes; the human-approved branch completes in ~18 minutes; if unacknowledged it escalates over hours (mixed scale).

## 1. Scenario

- **Subject:** inbound shipment `SHP-IN-8842` → DC `DC-AMS-01`; load `LOAD-5521`; affected outbound order `ORD-90211` (tier-1 customer).
- **Facts at assembly:** receiving shift ends 16:00; outbound cutoff 15:30; replenishment feeds order `ORD-90211`; dock `D-07` booked.
- **Signal:** severe weather on the inbound lane; predicted arrival slips from 14:40 to **16:15** (+95 min), confidence 0.86.
- **Threats:** receiving crew leaves before arrival (labor), outbound misses cutoff (service), possibly customer promise breach.

## 2. Participants

| Layer | System | Role |
|---|---|---|
| L1 | Weather feed → `ingest-gateway` | source signal |
| L3 | Visibility | marks inbound at risk; milestones |
| L4 | Intelligence | recomputes ETA; re-opt solver |
| L5 | Trigger Evaluator / Cascade Orchestrator / Context Assembler / Policy Engine / Action Executor / Escalation Mgr | the orchestrator |
| L3 | WMS (labor), TMS (carrier), Notification service | command targets |

## 3. Timeline

Diagram: [`diagrams/cascade-sequence.mmd`](diagrams/cascade-sequence.mmd).

| T+ | Actor | Action | State / emitted |
|---|---|---|---|
| 00:00 | Weather feed (L1) | severe-weather alert on inbound lane | `obs.*` weather |
| 00:03 | Visibility (L3) | inbound delayed; milestone emitted | `vis.milestone.events.v1` |
| 00:05 | Intelligence (L4) | recompute ETA → 16:15 (+95m), conf 0.86 | `intel.prediction.v1` |
| 00:05 | Trigger Evaluator | rule `LATE_INBOUND` matches (arrival > shift end ∧ > cutoff) | `orch.trigger.v1` (HIGH, `dedupeKey=LATE_INBOUND:SHP-IN-8842`) |
| 00:05 | Cascade Orchestrator | deterministic `caseId`; opens case | `orch.exception.v1` → `DETECTED` |
| 00:06 | Context Assembler | snapshot facts + signals + constraints; build options | `TRIAGING` (latency ≈ 1.5 s) |
| 00:08 | Policy Engine | evaluate 4 sub-decisions (see §4) | `DECIDING` |
| 00:09 | Decision Router | emit 4 `DecisionRequested` routed independently | `orch.decision.v1` ×4 |
| **— automated branch (seconds) —** | | | |
| 00:10 | Action Executor | `NOTIFY_CUSTOMER` proactive ETA (L0, AUTO) | `orch.command.v1` → customer notified |
| 00:10 | Action Executor | `REALLOCATE_LABOR` (L1, AUTO+notify) | `orch.command.v1` → WMS |
| 00:10 | Re-opt Coordinator | `ReoptimizationRequested` (load consolidation, MIN_COST) | `orch.reopt.v1` → L4 solver |
| 00:12 | WMS | labor re-allocation acked | `wms.task.events.v1` |
| 00:14 | Intelligence | solver returns ranked recommendations (+6% consolidation) | `orch.reopt.v1` `ReoptimizationCompleted` |
| 00:15 | Action Executor / Policy | load re-plan allowed (L1); apply recommendations | `orch.command.v1` (re-plan) |
| 02:00 | Cascade Orchestrator | automated sub-decisions verified | `AUTO_EXECUTING` → `VERIFYING` |
| **— human branch (minutes) —** | | | |
| 00:15 | Escalation Mgr | create human task `CARRIER_REBOOK` (L2), assigned shift supervisor, SLA 15 min | `AWAITING_HUMAN`; `slaDeadline = T+15:15` |
| 03:00 | Supervisor | opens task in control tower; reviews context + 3 options | (human review) |
| 04:30 | Supervisor | approves option A (re-tender alternate carrier) | `DecisionMade` (HUMAN) |
| 04:31 | Action Executor | `REBOOK_CARRIER` | `orch.command.v1` → TMS re-tender |
| ~08:00 | TMS/Carrier | re-tender accepted | `tms.tender.events.v1` |
| 09:00 | Cascade Orchestrator | all sub-decisions terminal; outcome meets expectation | `VERIFYING` → `RESOLVED` |
| 09:02 | Notification | final customer confirmation | `orch.command.v1` |
| 09:10 | Cascade Orchestrator | audit + close | `orch.exception.v1` `ExceptionCaseClosed` (`RESOLVED_HUMAN`) |

> Wall-clock: automated remediation landed in ~2 min; the full case closed in ~9–18 min depending on human responsiveness. Without a prompt ack, the escalation path in §5 stretches resolution to **hours**.

## 4. Sub-decisions (one case, four decisions)

| Sub-decision | Confidence | Cost impact | Autonomy | Route | Constraint checked |
|---|---|---|---|---|---|
| `CUSTOMER_NOTIFY` | — | $0 | `L0_FULL_AUTO` | AUTO | — |
| `LABOR_REALLOCATION` | 0.91 | low | `L1_AUTO_NOTIFY` | AUTO + notify | shift rules, labor budget |
| `REOPTIMIZE` (load) | — | low | `L1_AUTO_NOTIFY` | AUTO → L4 solver | no committed-state mutation |
| `CARRIER_REBOOK` | 0.74 | high | `L2_HUMAN_APPROVE` | HUMAN (SLA 15m) | customer rate contract, cost threshold |

The high cost/commitment of re-booking escalates it to a human; the low-risk, reversible actions stay automated. This is the core of policy-based routing ([decision-context.md](decision-context.md)).

## 5. Escalation path (if the human doesn't ack)

Dashed branch — same case, timers per [escalation.md](escalation.md):

| T+ (from task) | Event | Tier |
|---|---|---|
| 15:00 | `EscalationRaised` `SLA_BREACH` (L2 → L3), control tower paged | L3 |
| 45:00 | `EscalationRaised` (L3 → L4), account/partner manager | L4 |
| 1h45:00 | `EscalationRaised` (L4 → L5), duty manager | L5 |
| 3h45:00 | Top-tier default: **proceed with recommended option** (bounded, reversible) or **hold + executive incident** | — |

Meanwhile the automated branches (notify, labor, re-opt) already ran, so the customer saw a proactive ETA and labor was protected even while the carrier decision waited. Every escalation emits `orch.escalation.v1` with `MTTA`/`MTTR` metrics.

## 6. State transition trace

```
DETECTED ─▶ TRIAGING ─▶ DECIDING ─┬─▶ AUTO_EXECUTING ─┐
                                  └─▶ AWAITING_HUMAN ─┤
                                                       ▼
                                                   EXECUTING ─▶ VERIFYING ─▶ RESOLVED ─▶ CLOSED
(no ack) AWAITING_HUMAN ──SLA──▶ ESCALATED ─(human reassign)─▶ DECIDING
```

## 7. Emitted events & commands (with topics)

| Topic | Events in this flow |
|---|---|
| `obs.*` | weather observation |
| `vis.milestone.events.v1` | inbound delayed milestone |
| `intel.prediction.v1` | ETA prediction (+95m, conf 0.86) |
| `orch.trigger.v1` | `TriggerEvaluated` (`LATE_INBOUND`) |
| `orch.exception.v1` | `ExceptionCaseOpened` → `ExceptionCaseStateChanged` ×N → `ExceptionCaseClosed` |
| `orch.decision.v1` | 4× `DecisionRequested`, `DecisionMade` (notify, labor, reopt, carrier) |
| `orch.reopt.v1` | `ReoptimizationRequested`, `ReoptimizationCompleted` |
| `orch.command.v1` | `NOTIFY_CUSTOMER`, `REALLOCATE_LABOR`, `REBOOK_CARRIER`, re-plan, confirm |
| `wms.task.events.v1`, `tms.tender.events.v1` | execution confirmations |
| `orch.escalation.v1` | (only on the unacked path) |

## 8. Why this design holds up

- **Parallelism with per-decision autonomy:** safe actions proceed instantly; risky ones wait for a human — no serialization of the whole cascade behind the slowest decision.
- **Idempotency:** deterministic `caseId`; triggers coalesce by `dedupeKey`; commands dedupe by `commandId`; decisions anchored by `DecisionRecord` (see [schemas/orchestration-events.md §10](../schemas/orchestration-events.md)).
- **No cascade storm:** the ~dozen weather/ETA/dwell events for this shipment collapse into one case.
- **Explainability:** the case closes with the full `DecisionContext` + `policyRef` for every sub-decision, so ops and audit can see exactly why labor moved, the load changed, and a carrier was re-booked.
- **Measured:** the flow yields KPIs — detection latency (≈5 s), auto-remediation time (≈2 min), human MTTA, case MTTR, auto-resolution rate.
