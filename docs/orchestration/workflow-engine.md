# Workflow Engine Design

> Status: Accepted · Related: [README.md](README.md) · [trigger-catalog.md](trigger-catalog.md) · [exception-flow.md](exception-flow.md) · [../adr/0011-orchestration-saga-statechart-engine.md](../adr/0011-orchestration-saga-statechart-engine.md) · [../adr/0004-saga-over-distributed-tx.md](../adr/0004-saga-over-distributed-tx.md)

## 1. Model: durable saga + hierarchical statechart

Orchestration runs on **Temporal** as the canonical durable-execution engine (Zeebe/Camunda documented as alternatives in [ADR-0011](../adr/0011-orchestration-saga-statechart-engine.md)). The design is a **hybrid**:

- **Saga / process manager** — long-running, durable, retrying, compensating. One workflow instance per business object (exception case, order, re-opt scope).
- **Hierarchical statechart** — each cascade is a state machine compiled into workflow code: states, transitions, guards, entry/exit actions, parallel regions, and timed transitions.

The engine gives durability, exactly-once workflow steps (idempotent activities), timers, signals, and deterministic replay. The statechart gives a legible, tenant-reviewable specification of how a cascade behaves.

### 1.1 Workflow types

| Workflow | Instance key | Hosts |
|---|---|---|
| `ExceptionCaseWorkflow` | `caseId` | one exception cascade (the primary flow below) |
| `OrderToCashWorkflow` | `orderId` | order lifecycle saga ([events.md §8](../events.md)) |
| `ReoptimizationWorkflow` | `reoptimizationId` | solver request → evaluate → apply |
| `PromiseWorkflow` | `orderId` | ATP → promise → confirmation |

All workflows share a common runtime contract (§5).

## 2. Exception-case statechart

State diagram source: [`diagrams/exception-state-machine.mmd`](diagrams/exception-state-machine.mmd).

### 2.1 States

| State | Purpose | Typical duration | SLA timer |
|---|---|---|---|
| `DETECTED` | trigger matched; case opened | ms | — |
| `TRIAGING` | dedupe/merge, classify, assemble context | < 3 s | 30 s |
| `DECIDING` | policy evaluation, rank options | < 1 s | 30 s |
| `AUTO_EXECUTING` | automated branch dispatching commands | seconds | 2 min |
| `AWAITING_HUMAN` | human task assigned | min–hours | per policy (e.g. 15 min) |
| `AWAITING_PARTNER` | external ack required | min–hours | per contract |
| `EXECUTING` | commands in flight to L3 | seconds–min | 5 min |
| `VERIFYING` | observe outcome vs expectation | min | 10 min |
| `RECONCILING` | replan / compensate on miss | min | per policy |
| `ESCALATED` | SLA breach; handed up the ladder | until acked | next tier |
| `RESOLVED` → `CLOSED` | outcome met; audit + notify | — | — |
| `FAILED` / `CANCELLED` | terminal with manual follow-up | — | — |

### 2.2 Transitions & guards

| From → To | Trigger | Guard |
|---|---|---|
| DETECTED → TRIAGING | automatic | always |
| TRIAGING → DECIDING | context assembled | `DecisionContext.options.length ≥ 1` |
| DECIDING → AUTO_EXECUTING | policy | `autonomyTier ∈ {L0,L1} ∧ confidence ≥ threshold` |
| DECIDING → AWAITING_HUMAN | policy | `autonomyTier ∈ {L2,L3} ∨ confidence < threshold ∨ cost > budget ∨ regulated` |
| DECIDING → AWAITING_PARTNER | policy | `option.requiresPartnerAck = true` |
| AWAITING_HUMAN → EXECUTING | `decision.approved` | chosen option policyAllowed |
| AWAITING_HUMAN → CANCELLED | `decision.rejected` | — |
| AWAITING_PARTNER → EXECUTING | `partner.acked` | within SLA |
| AWAITING_PARTNER → RECONCILING | `partner.declined` ∨ timer | — |
| AUTO_EXECUTING/EXECUTING → VERIFYING | commands acked | all steps terminal |
| VERIFYING → RESOLVED | outcome observed | `meetsExpectation` |
| VERIFYING → RECONCILING | outcome miss | `attempts < maxAttempts` |
| RECONCILING → EXECUTING | new plan | alternatives remain |
| RECONCILING → ESCALATED | options exhausted | automatic |
| ESCALATED → DECIDING | human reassign | — |
| * → ESCALATED | SLA timer fires | `state.slaBreached` |

### 2.3 Parallel regions

A case runs concurrent regions that start together and are joined before `CLOSED`:

- **Remediation region** — the main state sequence above.
- **Notification region** — proactive customer/partner messages keyed to milestones (`orch.command.v1:NOTIFY_CUSTOMER`).
- **Verification region** — subscribes to confirming events (e.g., carrier accepted, labor assigned).
- **Audit region** — appends every transition and decision to the Decision/Audit Store.

## 3. Trigger → cascade flow

1. **Trigger Evaluator** matches a rule and emits `orch.trigger.v1` with a `dedupeKey`.
2. **Cascade Orchestrator** computes a deterministic `caseId = hash(template, dedupeKey)`. If an open case exists for the key, the trigger **merges** into it (`DUPLICATE_MERGED`); otherwise it starts `ExceptionCaseWorkflow`.
3. `TRIAGING` assembles the [DecisionContext](../schemas/orchestration-events.md#5-decision-context--the-core-object) from read models + L4 signals.
4. `DECIDING` evaluates policy → **potentially multiple decisions** (one per sub-action). Each decision routes independently (see [decision-context.md](decision-context.md)): e.g., labor re-allocation may be AUTO while carrier re-booking is AWAITING_HUMAN.
5. Authorized decisions dispatch commands via **Action Executor**; others create human/partner tasks with SLA timers.
6. `VERIFYING` confirms outcomes; `RECONCILING` compensates; `ESCALATED` hands up the ladder; `CLOSED` records the audit.

See [`diagrams/trigger-pipeline.mmd`](diagrams/trigger-pipeline.mmd).

## 4. One case, many sub-decisions

A cascade is a **fan-out of independent decisions**, each with its own routing and SLA. This is the key design point enabling "labor re-allocation + carrier re-booking + customer notification" to run in parallel with different autonomy.

```
ExceptionCase (late inbound)
├── sub-decision: LABOR_REALLOCATION   → AUTO (L1, notify)
├── sub-decision: CARRIER_REBOOK       → AWAITING_HUMAN (L2, SLA 15m)
├── sub-decision: CUSTOMER_NOTIFY      → AUTO (L0)
└── sub-decision: REOPTIMIZE (load)    → AUTO (solver) → recommendations → policy
```

The case closes only when all sub-decisions reach a terminal outcome and the verification region is satisfied.

## 5. Runtime contract (all workflows)

| Property | Rule |
|---|---|
| **Determinism** | workflow code is deterministic; side effects only in activities |
| **Idempotency** | every activity (command emit, notify, solver call) keyed by `commandId`/`reoptimizationId`; retries safe |
| **Durability** | state persisted by the engine; restarts resume; timers survive |
| **Versioning** | long-running workflows use versioned transitions (`patched`/version gates); never break in-flight instances |
| **Signals** | external inputs (human decision, partner ack, new event) delivered as signals; validated against state guards |
| **Compensation** | each reversible command has a registered compensation (`RELEASE_RESERVATION`, `COMPENSATE`, cancel tender) |
| **Timeout policy** | every `AWAITING_*` state has an SLA timer; on fire → `ESCALATED` |
| **Observability** | `correlationId` carried end-to-end; workflow state exported to `exception_view` for the control tower |
| **Multi-tenancy** | policy + thresholds resolved per `tenantId` at `DECIDING` |

## 6. Compensations for cascades

| Command | Compensation | Notes |
|---|---|---|
| `REALLOCATE_LABOR` | `REALLOCATE_LABOR` (restore prior plan) | reversible if shift not started |
| `REBOOK_CARRIER` | cancel new tender, reinstate prior | may incur fee (recorded as exception cost) |
| `RESERVE_INVENTORY` | `RELEASE_RESERVATION` | |
| `HOLD_ORDER` | `RELEASE` hold | |
| `REROUTE` / `REOPTIMIZE` | revert to prior `RoutingPlan` | if not yet dispatched |
| `NOTIFY_CUSTOMER` | compensating notification | not fully reversible; send correction |

## 7. Failure & edge cases

- **Cascade storm:** many triggers, same subject → coalesced by `dedupeKey`; only one case, triggers accumulated in `ReoptimizationRequested.triggerIds`.
- **Conflicting sub-decisions:** resolved by policy priority (cost vs. service objective) recorded in the `DecisionContext`.
- **Human never acks:** SLA timer → `ESCALATED` → next tier; L5 executive default action if all tiers time out (configurable: proceed with recommended vs. hold).
- **Partner unavailable:** treat as `RECONCILING` with alternative carriers from `DecisionContext.options`.
- **Replay:** workflow histories + idempotent activities + deterministic case ids make replay safe.
