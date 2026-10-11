# ADR-0015: Digital-twin network simulation as offline decision support

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../intelligence/network-simulation.md](../intelligence/network-simulation.md) · [../intelligence/README.md](../intelligence/README.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md)

## Context

Network-level decisions (open/close a node, shift modal mix, change carrier mix, set SLA targets, absorb a cost shock) are high-impact and hard to reverse. Testing them on the live network is unacceptable, yet spreadsheet models ignore congestion, capacity contention, and service interactions. We need a way to explore "what-if" scenarios with realistic flows and constraints, reproducibly, without affecting production.

## Decision

Build a **digital twin**: a discrete-event + Monte Carlo network simulator that reuses the **same constraint set and solvers** as live optimization ([ADR-0014](0014-optimization-and-inference-architecture.md)), driven by a point-in-time **baseline snapshot** of the network and scenario **levers**.

1. **Offline and advisory.** Simulation is decision support; it **never** drives live control. Results feed Analytics (L7) and offline policy tuning only.
2. **Scenario as data.** A scenario is a declarative set of `ScenarioLever`s over a horizon with an objective and Monte Carlo runs (`SimulationRequested`/`SimulationCompleted` on `intel.simulation.v1`).
3. **Realizable results.** The embedded optimizer uses the production constraint model, so simulated plans are actually feasible; only the environment is synthetic.
4. **Calibrated to reality.** The twin is recalibrated against realized transit, dwell, demand, cost, and service; fidelity (e.g., simulated vs actual OTD error) is reported so consumers can weigh confidence.
5. **Reproducible.** Every run records its random seed, baseline snapshot id, and scenario JSON in `artifactRef`.

## Consequences

**Positive**
- High-impact network changes can be evaluated cheaply and safely before commitment.
- Cost–service frontiers and bottleneck/risk bands (P10/P90) inform planning and negotiation.
- Reusing production constraints avoids "optimistic spreadsheet" results.

**Negative**
- Building and calibrating a faithful twin is significant effort and ongoing maintenance.
- Simulation compute (CPU/GPU + solver licenses) is costly at scale.
- A poorly calibrated twin can mislead; results are only as good as the model.

**Mitigations**
- Start with tactical scenarios (capacity, carrier mix) before strategic network design.
- Quota/async execution for long runs; cache baseline snapshots.
- Mandatory fidelity reporting; human review for strategic decisions.

## Alternatives considered

- **Spreadsheet/linear models only** — rejected: ignore congestion, contention, and service interactions.
- **Test changes live (pilot on production)** — rejected: risk to customers/SLA; not reproducible.
- **Reuse live control loop for simulation** — rejected: violates the advisory boundary and would leak synthetic state into production.
- **Third-party simulation SaaS only** — deferred: may complement, but cannot reuse our canonical constraints/solvers, risking infeasible plans.
