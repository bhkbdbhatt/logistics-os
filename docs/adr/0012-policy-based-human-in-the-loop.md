# ADR-0012: Policy-based human-in-the-loop decision routing

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../orchestration/decision-context.md](../orchestration/decision-context.md) · [../orchestration/escalation.md](../orchestration/escalation.md) · [0005-buy-execution-build-orchestration.md](0005-buy-execution-build-orchestration.md) · [0006-multi-tenant-isolation-model.md](0006-multi-tenant-isolation-model.md)

## Context

Not every operational decision should be automated: re-booking a carrier on a committed load, overriding a customer contract, or handling hazmat must involve a human, while re-allocating labor or sending a proactive ETA should not. The dividing line varies by tenant, customer, severity, cost, and regulatory context. Hard-coding this logic per flow would fork behavior per tenant and make the system ungovernable. We also need every decision to be auditable and explainable.

## Decision

Make the **decision a first-class, auditable object** and route it via **policy**. At each sub-decision, orchestration assembles a `DecisionContext` (facts, signals, constraints, ranked options, `policyRef`) and an **OPA policy engine** returns an **autonomy tier** (`L0_FULL_AUTO` … `L3_HUMAN_ONLY`), allowed options, and an automation envelope (confidence threshold, cost budget). The **Decision Router** then sends the decision to an automated agent, a human, or a partner. Outcomes are persisted as `DecisionRecord`s carrying the pinned `policyRef` so any decision is reproducible. Human tasks always carry an SLA timer (see [ADR/ escalation](../orchestration/escalation.md)).

## Consequences

**Positive**
- Autonomy is configurable per tenant/severity/decision type — no per-tenant code forks.
- Every decision is explainable (facts + signals + options + policy version) and auditable.
- Safe actions run instantly; risky ones wait for humans; the case is not serialized behind either.
- Human overrides become training/tuning data for policy and ML.

**Negative**
- A policy layer and authoring UX to build/operate; policy bugs can be high-impact.
- Context assembly adds latency (bounded, < 3 s p99).

**Mitigations**
- Policy-as-code with tests and staged rollout; hard constraints cannot be overridden; kill-switch to force human routing globally; decision audit + simulation (digital twin) before policy changes.

## Alternatives considered

- **Per-flow hard-coded thresholds** — rejected: forks per tenant, ungovernable.
- **Fully automated** — rejected: unacceptable for regulated/high-cost/contractual decisions.
- **Fully manual** — rejected: unscalable; defeats real-time response.
- **ML-only autonomy classifier** — rejected as sole mechanism: opaque; kept as an input signal, not the authority.
