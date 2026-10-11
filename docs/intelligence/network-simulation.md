# Network Simulation (Digital Twin)

> Status: Accepted · Related: [README.md](README.md) · [route-optimization.md](route-optimization.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md) · [../analytics/network-simulation.md](../analytics/network-simulation.md) · [../adr/0015-digital-twin-network-simulation.md](../adr/0015-digital-twin-network-simulation.md)

> The **scenario interface, input parameters, and impact outputs** as surfaced to planners are specified in [analytics/network-simulation.md](../analytics/network-simulation.md). This document specifies the **engine** behind them.

A **digital twin** of the logistics network for offline "what-if" analysis: simulate demand, capacity, cost, and service under alternative scenarios before committing to changes. It is **decision support**, never a live control loop.

## 1. Purpose

Answer strategic and tactical questions without touching production:

- What if we close/open a node, add a lane, or shift modal mix?
- How does a carrier-mix change or cost shock affect cost/service?
- Can we meet peak demand with current capacity? Where are the bottlenecks?
- What SLA target is achievable at what cost? (cost–service frontier)
- Impact of a lane closure / labor shortage / policy change (e.g., emissions).

## 2. Architecture

Diagram: [`diagrams/simulation-loop.mmd`](diagrams/simulation-loop.mmd).

| Component | Role |
|---|---|
| **Baseline snapshot** | point-in-time network state: nodes, lanes, capacity, rates, service levels, historical demand |
| **Scenario definition** | levers + horizon + objective + Monte Carlo runs |
| **Discrete-event simulation** | flows, queuing at nodes/docks, transit, resource contention |
| **Embedded optimizer** | re-runs routing/allocation inside each simulated period (uses L4 solvers) |
| **Monte Carlo** | samples demand, transit, capacity, no-show uncertainty → distributions |
| **Results store** | KPIs + sensitivity + artifacts; feeds Analytics (L7) and policy tuning |

The simulator reuses the **same constraint set and solvers** as live optimization, so simulated plans are realizable; only the environment is synthetic.

## 3. Scenario levers

| Lever | Meaning |
|---|---|
| `CAPACITY` | add/remove vehicle or dock capacity at a node/lane |
| `DEMAND_SHIFT` | volume change or lane re-balance |
| `LANE_CLOSURE` | disable a lane / border / mode |
| `CARRIER_MIX` | contract vs spot, carrier set changes, rate shifts |
| `COST` | fuel, labor, accessorial shocks |
| `SLA_TARGET` | tighten/relax promised windows |
| `NODE_OPEN` / `NODE_CLOSE` | add/remove DC, cross-dock, or yard |
| `MODAL_SHIFT` | truck ↔ rail ↔ air ↔ ocean |
| `LABOR` | workforce availability at nodes |

## 4. Inputs & outputs

**Inputs:** `SimulationRequested` (`intel.simulation.v1`), MDM network model ([../data-model.md](../data-model.md)), historical demand/service from the lakehouse, L4 solver configs, tenant policy.

**Outputs:** `SimulationCompleted` (`intel.simulation.v1`) with `costDeltaPct`, `otdDeltaPct`, `utilizationPct`, `emissionsDeltaPct`, `p10SlaPct`/`p90SlaPct`, `solverId`, `solveTimeMs`, `artifactRef` (detailed report). A `TOOL/SimulationRun` execution record is written to the audit log; the model is **not** stored in the event store.

## 5. Calibration (digital-twin fidelity)

The twin is calibrated against reality:

| Driven by | Recalibrates |
|---|---|
| Realized transit/dwell vs simulated | transit & dwell distributions |
| Realized demand vs forecast | demand model |
| Realized cost/rates | cost parameters |
| Realized service (OTD/SLA) | capacity & reliability params |

Recalibrate monthly or after major network change; calibration job emits a `ModelDeployed` for the simulation model when params change materially. Fidelity is reported (e.g., simulated vs actual OTD error) so consumers know confidence.

## 6. Execution & cost control

- Runs are **offline** and quota-controlled (CPU/GPU + solver licenses); long scenarios run async, results published on completion.
- Reproducible: fixed random seed recorded; scenario JSON + baseline snapshot id captured in `artifactRef`.
- Typical scenario: **< 30 min**; large Monte Carlo: hours (async).

## 7. Use in the broader system

| Consumer | Use |
|---|---|
| Analytics (L7) | decision support, planning, cost-to-serve |
| Orchestration (L5) / policy owners | tune thresholds & routing (offline; not per-decision) |
| Network planning | node/lane investment, capacity planning |
| Sales/Procurement | contract and rate negotiations |

## 8. SLA

| Metric | Target |
|---|---|
| Typical scenario completion | < 30 min |
| Large Monte Carlo | async, < 12 h |
| Baseline snapshot freshness | daily |
| Calibration cadence | monthly / post-major-change |
| Reproducibility | seed + snapshot recorded on every run |
| Fidelity (OTD error) | reported; target < 5 pts on mature networks |
