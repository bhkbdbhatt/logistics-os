# ADR-0018: Autonomous agent architecture in the orchestration layer

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../orchestration/autonomous-agents.md](../orchestration/autonomous-agents.md) · [../orchestration/decision-context.md](../orchestration/decision-context.md) · [0011-orchestration-saga-statechart-engine.md](0011-orchestration-saga-statechart-engine.md) · [0012-policy-based-human-in-the-loop.md](0012-policy-based-human-in-the-loop.md) · [0013-exception-cascade-and-reopt-coalescing.md](0013-exception-cascade-and-reopt-coalescing.md) · [0014-optimization-and-inference-architecture.md](0014-optimization-and-inference-architecture.md) · [0015-digital-twin-network-simulation.md](0015-digital-twin-network-simulation.md)

## Context

Decision routing already exists (ADR-0012): a `DecisionContext` is assembled, OPA policy assigns an autonomy tier, and an automated agent, human, or partner decides. As we scale exception cascades (labor re-allocation, wave re-planning, carrier notifications, ETA updates), we want larger fractions of low-risk decisions handled **autonomously** — including reasoning over novel combinations — without giving a free-roaming model control over state, and without sacrificing explainability, safety, or audit.

The open question was **what an "agent" is**: a free-roaming LLM with tools (env-paradigm), or a constrained, state-owning reasoner inside L5 that proposes options and is gated at every step.

## Decision

Define an **agent** as a deterministic-durable worker in L5, woken by the Cascade Orchestrator, that: perceives only a pre-assembled, bounded `DecisionContext` (+ curated event/memory window), proposes an **ordered plan of typed options**, is validated by the **digital twin** where available, and executes **only** via idempotent `orch.command.v1` commands after an **execution-time OPA re-check**. It never holds nor writes domain state directly.

- Reasoning is **hybrid**: deterministic policy/solver by default; an **optional, pinned LLM reasoner** (temperature 0, structured-output schema, versioned prompt "constitution") for triage, novel-option generation, summarization, and natural-language rationale. LLM presence is a per-tenant/per-archetype toggle; every auto path has a deterministic fallback.
- Autonomy stays archetype-based (ADR-0012 tiers): internal reversible actions ≤ `L1`, anything touching customer promises/contracts/partner commitments ≥ `L2`, regulated/safety and over-budget ≥ `L3`. The router still outranks the agent.
- **Guardrail stack:** pinned constitution + model, typed toolset, context-time OPA, sim-first, execution-time OPA, budget/impact caps, shadow→GATE ramp, and per-`(decisionType, tenant)` kill switch.
- **Rollback:** plans apply as ordered idempotent commands; partial failures use saga compensation (ADR-0011); risky archetypes carry revert commands; before/after snapshots are persisted; a per-case reversion budget forces HITL when exceeded.
- **Measure against baseline:** replay + digital-twin optimality offline; a persistent 5–20 % human **holdout baseline** online. Champion/challenger registry; demotion if an archetype loses on primary outcomes for 2 weeks.

## Consequences

**Positive**
- Safe autonomy at scale: low-risk decisions auto-resolve in seconds while judgment cases still hit humans.
- Every autonomous step is a first-class, reproducible `DecisionRecord` with full context, plan, sim margin, and guardrail checkmarks — satisfying the explainability NFR.
- No unbounded blast radius: agents can only command, under policy, with idempotency and compensation.
- Model choice is decoupled: OPA remains the authority; LLMs can be swapped, region-pinned, or disabled per tenant without changing execution semantics.

**Negative**
- Two extra OPA evaluations and a sim call add latency to autonomous decisions (budgeted: assembly < 3 s, agent step ≤ 5 s p99).
- LLM reasoning is inherently non-deterministic; we pin model+prompt and temperature-0, but reproducibility is bounded by the recorded trace rather than pure determinism.
- Sim-first depends on the digital twin; where the twin is unavailable, autonomy drops to confidence-threshold-only.

**Mitigations**
- Deterministic fallback for every auto path; degrade to policy+solver on LLM outage.
- Kill switch + shadow/GATE ramp limit blast radius while confidence builds.
- Guardrail rejections are recorded as negative decisions and used to tune policy, not hidden.

## Alternatives considered

- **Free-roaming multi-tool LLM agents (env paradigm)** — rejected: unbounded context/actions, un-auditable, hard to roll back; supply-chain decisions need identity of subject, budget, and reversion semantics.
- **One LLM per node/shipment** — rejected: unscaleable cost, no single-subject coherence, storm risk.
- **Rule-only agents** — rejected: brittle against novel combinations; LLM adds summarization/generation value within constraints.
- **Agents writing domain state directly** — rejected: violates the L3-owns-state rule and the orchestration DDD boundary (ADR-0002/ADR-0005).