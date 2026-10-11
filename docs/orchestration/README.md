# Orchestration Layer

> Status: Accepted · Related: [workflow-engine.md](workflow-engine.md) · [trigger-catalog.md](trigger-catalog.md) · [decision-context.md](decision-context.md) · [autonomous-agents.md](autonomous-agents.md) · [escalation.md](escalation.md) · [exception-flow.md](exception-flow.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md) · [../services.md](../services.md) · [../adr/0011-orchestration-saga-statechart-engine.md](../adr/0011-orchestration-saga-statechart-engine.md)

The orchestration layer (L5) sits between execution systems (L3: TMS/WMS/YMS/OMS) and the intelligence layer (L4). It is the **control tower's brain**: it watches the event stream, detects conditions that threaten goals, assembles the context needed to decide, routes each decision to an automated or human agent per policy, executes via commands, and escalates when the clock runs out.

It owns **cross-domain promises and decisions**. Execution systems never promise across boundaries ([services.md §3](../services.md)); intelligence advises but never writes domain state ([integrations.md §5](../integrations.md)). Orchestration **decides**, execution **acts**.

## 1. What it must handle

| # | Requirement | Where designed |
|---|---|---|
| (a) | **Cross-domain exception cascades** — a late inbound triggers labor re-allocation + carrier re-booking + customer notification | [trigger-catalog.md](trigger-catalog.md), [exception-flow.md](exception-flow.md) |
| (b) | **Real-time re-optimization triggers** — react to ETA deltas, new orders, resource failures, cost thresholds | [trigger-catalog.md §3](trigger-catalog.md) |
| (c) | **Policy-based routing of decisions to human vs. automated agents** | [decision-context.md](decision-context.md), [escalation.md](escalation.md) |

## 2. Internal component model

```
                       ┌───────────────────────────────────────────────┐
   events (L1–L4) ───▶ │  Trigger Evaluator                           │
   timers/cutoffs ───▶ │  (stream rules + windows + coalescing)        │
                       └───────────────┬───────────────────────────────┘
                                       │ orch.trigger.v1
                                       ▼
                       ┌───────────────────────────────────────────────┐
                       │  Cascade Orchestrator (Temporal workflows)    │
                       │  statechart per cascade; durable & idempotent │
                       └───┬───────────────┬───────────────┬───────────┘
                           │               │               │
             assemble      │        evaluate│policy     execute│
                           ▼               ▼               ▼
              ┌─────────────────┐ ┌──────────────┐ ┌──────────────────┐
              │ Context          │ │ Policy Engine│ │ Action Executor  │
              │ Assembler        │ │ (OPA)        │ │ (commands)       │
              │ read models+AI   │ └──────┬───────┘ └──────────────────┘
              └─────────────────┘        │ DecisionContext
                                         ▼
                         ┌──────────────────────────────┐
                         │ Decision Router              │
                         │ auto | human | partner       │
                         └───────┬──────────────┬───────┘
                                 │ defer        │ human/partner
                                 ▼              ▼
                    ┌──────────────────┐  ┌──────────────────┐
                    │ Re-optimization  │  │ Escalation Mgr   │
                    │ Coordinator      │  │ (SLA timers,     │
                    │ (coalesce)       │  │  on-call)        │
                    └──────────────────┘  └──────────────────┘
                                 │
                                 ▼
                       ┌──────────────────────────────┐
                       │ Decision/Audit Store         │
                       │ (explainability + metrics)   │
                       └──────────────────────────────┘
```

Diagram source: [`diagrams/trigger-pipeline.mmd`](diagrams/trigger-pipeline.mmd).

| Component | Responsibility |
|---|---|
| **Trigger Evaluator** | Subscribe to domain events + timers; evaluate rules/predicates; debounce/coalesce; emit `orch.trigger.v1`. |
| **Cascade Orchestrator** | Durable Temporal workflows — one per exception case / order / re-opt. Runs the statechart, coordinates parallel branches, applies compensations. |
| **Context Assembler** | Build the [DecisionContext](../schemas/orchestration-events.md#5-decision-context--the-core-object) from CQRS read models + L4 signals (facts, constraints, ranked options). |
| **Policy Engine (OPA)** | Evaluate tenant policy → autonomy tier, allowed options, budget/confidence thresholds. |
| **Decision Router** | Route to AUTO, HUMAN, or PARTNER per policy; emit `orch.decision.v1`. |
| **Agent Runtime** | Stateful reasoners (policy/solver + optional pinned LLM) per `(domain, site/tenant)`; propose plans from the DecisionContext; sim-validated; gated by policy; see [autonomous-agents.md](autonomous-agents.md). |
| **Re-optimization Coordinator** | Coalesce re-opt triggers, call L4 solver, return recommendations. |
| **Action Executor** | Emit idempotent `orch.command.v1` to L3; track acknowledgements. |
| **Escalation Manager** | SLA timers, tier ladder, on-call routing, de-escalation. |
| **Decision/Audit Store** | Persist `DecisionRecord`; feed explainability, KPIs, and policy learning. |

## 3. Position in the layer stack

- **Reads (in):** all domain events (`ord.*`, `tms.*`, `wms.*`, `yms.*`, `vis.*`, `inv.*`), L4 `intel.prediction.v1` / `intel.recommendation.v1`, and timers/cutoffs. Synchronous reads of L2 read models during context assembly ([integrations.md §6](../integrations.md)).
- **Writes (out):** commands on `orch.command.v1`; promises on `orch.promise.v1`; exceptions on `orch.exception.v1`; plus `orch.trigger.v1`, `orch.decision.v1`, `orch.escalation.v1`, `orch.reopt.v1`.
- **Never:** touches an L3 database, or writes domain state directly ([services.md §3](../services.md)).

## 4. Reading order

1. [workflow-engine.md](workflow-engine.md) — how a cascade is modeled and executed.
2. [trigger-catalog.md](trigger-catalog.md) — what wakes the orchestrator.
3. [decision-context.md](decision-context.md) — the object every decision is made from.
4. [escalation.md](escalation.md) — when automation yields to humans.
5. [exception-flow.md](exception-flow.md) — the whole thing end-to-end, timed.
6. [autonomous-agents.md](autonomous-agents.md) — how agents (rule- or LLM-based) decide, what they may automate, their guardrails/rollback, and how they're measured.

## 5. Non-functional requirements

| NFR | Target |
|---|---|
| Trigger detection latency (p99) | < 2 s from source event |
| Context assembly (p99) | < 3 s |
| Automated decision-to-command (p99) | < 1 s after context |
| Workflow durability | survive restarts; deterministic replay; no lost timers |
| Exactly-once effects | commands idempotent by `commandId`; decisions anchored by `DecisionRecord` |
| Explainability | every decision reproducible from `DecisionContext` + `policyRef` + signals |
| Multi-tenancy | policy + thresholds per tenant ([cross-cutting.md §1](../cross-cutting.md)) |
| Cascade storm control | debounce/coalesce per `dedupeKey` ([trigger-catalog.md §4](trigger-catalog.md)) |
