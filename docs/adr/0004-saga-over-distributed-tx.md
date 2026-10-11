# ADR-0004: Sagas over distributed transactions

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../architecture.md](../architecture.md) · [../integrations.md](../integrations.md)

## Context

Order-to-cash spans OMS, WMS, TMS, YMS, and Billing — often several COTS products and external parties. A single business action (place order, ship, invoice) mutates multiple stores. Distributed transactions (XA/2PC) across heterogeneous systems and third parties are impractical: they require global locks, coordinate poorly with APIs/EDI, and hurt availability.

## Decision

Implement long-running cross-domain flows as **sagas orchestrated by process managers** (Temporal/Camunda/Zeebe in the `orchestrator`). Each step is a local transaction in one service; the orchestrator emits **commands** and consumes resultant **events**, applying **compensating actions** on failure. Commands and steps are idempotent and versioned; correlation is via `correlationId`.

## Consequences

**Positive**
- No global locks; works across COTS, APIs, and partners.
- Explicit, observable, resumable flows; easy retries and manual intervention.
- Compensations keep the system in a consistent business state without XA.

**Negative**
- Application-level complexity; compensations must be designed per step.
- Intermediate states are visible and must be modeled in UIs.

**Mitigations**
- A workflow engine handles durability/retries; a catalog of sagas and compensations; `orch.exception.v1` drives control-tower intervention.

## Alternatives considered

- **2PC/XA** — rejected: brittle across heterogeneous/remote systems, poor availability.
- **Choreography-only (no orchestrator)** — rejected for complex flows: hard to observe/compensate; acceptable for simple 2–3 step flows.
- **Best-effort with manual reconciliation** — rejected: error-prone and unscalable.
