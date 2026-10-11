# ADR-0013: Exception cascade model with trigger coalescing

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../orchestration/trigger-catalog.md](../orchestration/trigger-catalog.md) · [../orchestration/exception-flow.md](../orchestration/exception-flow.md) · [../orchestration/workflow-engine.md](../orchestration/workflow-engine.md)

## Context

Operational reality is noisy: one weather delay produces a burst of related events (ETA change, milestone updates, dwell increase, cutoff risk, VIP flag). Naively turning each into its own exception workflow creates **cascade storms** — duplicate cases, contradictory actions (re-book while a re-route is pending), and alert fatigue. Conversely, real-time re-optimization would thrash if every ETA jitter triggered a fresh solver run. We need a model that detects exceptions from streams but coalesces related causes into a single, coherent response.

## Decision

Adopt a **trigger → coalesce → cascade** model. A **Trigger Evaluator** evaluates versioned rules over the event stream + timers and emits `TriggerEvaluated` events carrying a **`dedupeKey`** (`type:subject:window`). The **Cascade Orchestrator** computes a **deterministic case id** from `(cascadeTemplate, dedupeKey)`, so related triggers **merge** into one `ExceptionCase` (`DUPLICATE_MERGED`) instead of spawning duplicates. Three controls prevent storms: **debounce** (per-key sliding window), **coalescing** (many triggers → one case; causes recorded), and **dependency suppression** (higher-severity triggers subsume lower ones for the same subject). **Re-optimization** triggers coalesce separately per `(scope, subject)` into a single `ReoptimizationRequested` within a short window, and their results are **proposals** routed through the same policy path — never blindly auto-applied to committed/in-transit/posted state.

## Consequences

**Positive**
- One coherent case per real incident; no duplicate or contradictory remediation.
- Solver and human load bounded by coalescing windows.
- Real-time responsiveness preserved (triggers fire in < 2 s) without thrash.
- Coalesced causes are retained for root-cause analysis.

**Negative**
- Tuning windows/thresholds is required; too wide suppresses genuine separate incidents, too narrow lets storms through.
- Slightly more state (accumulated causes, suppression links) to manage.

**Mitigations**
- Windows/thresholds are tenant policy, observable via dashboards (case-vs-trigger ratio, merged rate); simulation on historical streams before rollout.

## Alternatives considered

- **One workflow per trigger** — rejected: cascade storms, contradictions, alert fatigue.
- **Single monolithic "big" exception engine** — rejected: loses per-cascade statecharts and routing granularity.
- **Batch-only exception detection** — rejected: misses real-time re-opt and SLA windows.
- **No re-opt coalescing** — rejected: solver thrash on ETA jitter.
