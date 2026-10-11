# ADR-0011: Durable saga + hierarchical statechart orchestration

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../orchestration/workflow-engine.md](../orchestration/workflow-engine.md) · [0004-saga-over-distributed-tx.md](0004-saga-over-distributed-tx.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md)

## Context

The orchestration layer runs long-lived, cross-domain flows (exception cascades, order-to-cash, re-optimization) that wait on external systems, humans, and timers for minutes to hours. We already chose sagas over distributed transactions ([ADR-0004](0004-saga-over-distributed-tx.md)) but must fix **how** flows are expressed and executed: a pure code saga is hard for operations to reason about, while a pure BPMN model is hard to test and version like software. We also need durability, timers, deterministic replay, and safe long-running versioning.

## Decision

Adopt a **hybrid: a durable saga runtime with hierarchical statecharts as the workflow model**, implemented on **Temporal** (canonical). Each cascade is authored as a statechart (states, transitions, guards, entry/exit actions, parallel regions, timed transitions) and compiled into a Temporal workflow. Activities (command emit, notify, solver call, context assembly) are idempotent and side-effect-only. One workflow instance per business object, keyed deterministically (`caseId = hash(template, dedupeKey)`). Zeebe/Camunda are documented alternatives if BPMN-first authoring by business users is required.

## Consequences

**Positive**
- Durability, timers, deterministic replay, and step-level idempotency come from the engine.
- Statecharts are legible to operations and policy authors, and testable/versionable as code.
- Parallel regions model independent sub-decisions with different autonomy, giving per-decision routing and SLA.
- Compensations are first-class, satisfying saga semantics without XA.

**Negative**
- Two mental models (engine + statechart) to learn.
- Long-running workflow versioning requires discipline (version gates).
- Engine is a critical dependency (must be HA).

**Mitigations**
- A shared workflow runtime contract (idempotency, timers, versioning, observability); a small set of workflow templates; engine run in HA with multi-region failover.

## Alternatives considered

- **Pure code saga (no statechart)** — rejected: hard to reason about/operate; weaker per-state SLA modeling.
- **Pure BPMN engine (Camunda/Zeebe)** — viable alternative; deferred because workflow-as-code fits the engineering org and CI/CD better.
- **Choreography-only** — rejected: unobservable/uncompensatable for complex cascades.
- **Custom orchestrator** — rejected: rebuilding durability/timers/replay.
