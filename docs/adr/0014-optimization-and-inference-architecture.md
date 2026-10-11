# ADR-0014: Optimization and inference architecture (feature store, online/offline parity)

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../intelligence/README.md](../intelligence/README.md) · [../intelligence/predictive-eta.md](../intelligence/predictive-eta.md) · [../intelligence/route-optimization.md](../intelligence/route-optimization.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md)

## Context

The intelligence layer (L4) needs both **predictive models** (ETA, tender acceptance, carrier performance) and **combinatorial solvers** (routing, load building, allocation). These have very different runtime characteristics: models need low-latency online serving; solvers need variable, sometimes long, CPU-bound runs. A naive setup risks **training/serving skew** (features computed differently online vs offline), **non-reproducible** outputs, and **coupling** that makes an optimizer swap ripple through the platform. We also must keep L4 strictly advisory so it never writes domain state.

## Decision

Adopt a **feature-store-centered, solver-agnostic** architecture:

1. **Feature store with online/offline parity.** Features are defined once and materialized to both an online (low-latency) store and an offline (training) store. Computation is **point-in-time correct** to prevent leakage. Every model output carries `featuresHash` binding the exact feature vector for reproducibility.
2. **Separate model serving from solver services.** Predictive models run as stateless online endpoints with tight latency budgets; solvers (OR-tools/Gurobi/LNS) run as pluggable services via a sync API and event-driven batch. The **solver is an implementation detail**; outputs are solver-agnostic `Recommendation`s.
3. **Advisory outputs only, via events.** L4 publishes `intel.prediction.v1` and `intel.recommendation.v1`; orchestration (L5) maps them into the DecisionContext and applies policy. L4 never issues commands or writes domain state.
4. **Every output is versioned and reproducible.** `modelId`, `modelVersion`, `confidence`, `featuresHash`, `validUntil`, `solverId` are mandatory. Given input events + model version, any output is recomputable.
5. **Registry-gated lifecycle with drift monitoring.** Promotion requires offline metrics + shadow + canary (`ModelDeployed`); drift (`ModelDriftDetected`) triggers retraining. Feedback labels (`intel.feedback.v1`) close the learning loop.

## Consequences

**Positive**
- No training/serving skew; offline metrics are trustworthy predictors of online behavior.
- Solvers and models evolve independently; swapping a solver doesn't change the contract.
- Explainable, auditable decisions (`DecisionRecord` links to `featuresHash`/`modelId`).
- Confidence gates allow safe human-in-the-loop routing for low-confidence advice.

**Negative**
- A feature store is real infrastructure (compute, storage, operational burden).
- Point-in-time correctness and online/offline parity require discipline and testing.
- Model + solver fleet increases serving surface and cost.

**Mitigations**
- Start with a managed/simple feature store; enforce parity with automated online-vs-offline distribution tests.
- Cost control: batch/quota for solvers; cache predictions keyed by `(subject, featuresHash)`; autoscale online endpoints.
- Reproducibility tests in CI replay historical events and compare outputs.

## Alternatives considered

- **Compute features ad hoc inside each service** — rejected: training/serving skew, duplication, no reproducibility.
- **Serve predictions from the same monolith as solvers** — rejected: couples latency profiles; optimizer swap becomes a big-bang change.
- **Store model outputs as domain state** — rejected: violates the advisory principle; models must not own domain truth.
- **Let L4 apply its own recommendations** — rejected: bypasses policy/human routing; no audit trail ([ADR-0012](0012-policy-based-human-in-the-loop.md)).
