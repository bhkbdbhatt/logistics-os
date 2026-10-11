# Dynamic Route Optimization

> Status: Accepted · Related: [README.md](README.md) · [predictive-eta.md](predictive-eta.md) · [../orchestration/trigger-catalog.md](../orchestration/trigger-catalog.md) · [../adr/0014-optimization-and-inference-architecture.md](../adr/0014-optimization-and-inference-architecture.md)

Solves vehicle routing / load building / consolidation continuously as the network changes. Output is a **recommendation**, never a direct route change; orchestration decides whether to apply ([../orchestration/decision-context.md](../orchestration/decision-context.md)).

## 1. Problem classes supported

| Class | Description | Typical size |
|---|---|---|
| VRP / VRPTW | multi-vehicle routing with time windows | 50–2,000 stops |
| PDPTW | pickup-delivery with pairing/precedence | 20–500 tasks |
| Load building / consolidation | pack orders into loads respecting capacity | 100–10,000 lines |
| Multi-trip / multi-day | HOS-aware, return-to-depot, overnights | network scale |
| Superior/flexible tasks | optional stops, penalties for misses | mixed |

## 2. Constraint set

### 2.1 Hard constraints (never violated)

| Constraint | Source |
|---|---|
| Time windows (pickup/delivery/appointment) | `tms`, `yms.appointment`, MDM SLA |
| Vehicle capacity (weight, volume, pallet positions, axles) | MDM `Vehicle` |
| Vehicle capabilities (reefer, liftgate, hazmat, temperature) | MDM `Vehicle`, item master |
| Driver hours of service (HOS/ELD) | `obs.telemetry`, driver rules |
| Hazmat segregation & routing restrictions | item master, MDM |
| Customer/site restrictions (access, curfew, max vehicle size) | MDM `Location` |
| Service area / authorized lanes / terms | MDM `Lane`, contract |
| Max ride time / stop count per route | policy |
| Cold-chain temperature class continuity | item master |

### 2.2 Soft objectives (optimized, weighted by policy)

Cost, total drive time, distance, overtime, SLA slack, emissions, load balance across carriers/regions, driver preference, consolidation reward, route stability (minimize churn vs. prior plan).

### 2.3 Objective function

```
minimize   w_cost   * totalCost
         + w_time   * totalDriveMin
         + w_late   * sum(latenessMinutes * penaltyWeight)
         + w_overtime * overtimeMinutes
         + w_emit   * emissionsGrams
         + w_stab   * routeChangePenalty
         - w_consol * consolidationSaving
s.t.       all hard constraints
```

Weights are tenant policy; `routeChangePenalty`/`routeStability` prevents plan thrash across re-optimizations.

## 3. Solver choice

| Situation | Solver | Rationale |
|---|---|---|
| Small/medium (≤ ~200 stops), need optimal-ish fast | **OR-tools** (CP-SAT / routing) | open-source, fast for VRP/VRPTW |
| Large/complex, needing strong bounds | **Gurobi/CPLEX** (MIP) | best bounds; license cost |
| Very large / dynamic / sub-second | **LNS/ALNS metaheuristic** seeded by prior solution | anytime, warm-start from last plan |
| Multi-load consolidation | min-cost flow / set-partitioning + heuristics | decomposition |
| ML-guided | learned cost/repair policies (ML-assisted LNS) | accelerates convergence; optional |

**Default:** OR-tools for standard VRP/PDPTW; Gurobi for high-value MIPs; LNS for large dynamic re-opt. Solver is pluggable; output is solver-agnostic ([ADR-0014](../adr/0014-optimization-and-inference-architecture.md)).

## 4. Inputs

| Input | Events / sources |
|---|---|
| Demand | `ord.order.events.v1`, `orch.promise.v1` |
| Current shipment/stop state | `tms.shipment.events.v1`, `tms.load.events.v1` |
| Capacity/equipment | MDM `Vehicle`, `tms.*`, `yms.yard.events.v1` |
| Dock/appointments | `yms.appointment.events.v1` |
| Live conditions | `obs.telemetry.v1`, traffic/weather feeds |
| Predicted ETAs | `intel.prediction.v1` |
| Re-opt triggers | `orch.trigger.v1`, `orch.reopt.v1` |
| Constraints/network | MDM (`Lane`, `Location`, `ServiceLevel`) |

## 5. Re-optimization cadence

Two loops, coalesced by the orchestrator ([../orchestration/trigger-catalog.md §3](../orchestration/trigger-catalog.md)):

| Loop | Trigger | Latency | Scope | Stability |
|---|---|---|---|---|
| **Live / event-driven** | `ETA_DELTA`, `RESOURCE_FAILURE`, `NEW_ORDER_INSERTION`, `ROAD_CLOSURE` | < 2 s solve; coalesce window 2 min | single shipment → load → node | warm-start; only apply if improvement > churn threshold |
| **Rolling horizon (batch)** | timer (e.g., every 15 min) | < 60 s | wave/lane | full re-plan with stability penalty |
| **Nightly strategic** | timer | minutes–hours | network / next-day | consolidate loads; align to appointments |

**Apply gate:** a new plan is proposed only if `improvement − instabilityChurn > threshold`; otherwise keep the current plan. This prevents flip-flopping.

## 6. Output

`Recommendation` on `intel.recommendation.v1` (`type=ROUTE_PLAN`/`LOAD_BUILD`):

- Ranked `options[]`, each with `expectedCost`, `expectedTimeMin`, `riskScore`, `confidence`, `score`, `constraintsSatisfied`, and `params` (ordered stops, assigned vehicles, load ids).
- `objective`, `modelId`/`solverId`, `validUntil`, `featuresHash`.
- Infeasibilities reported explicitly (`constraintsSatisfied=false` + reason), never silently relaxed.

Orchestration maps options into the DecisionContext and routes: minor re-sequences may be AUTO (L1); commited-load re-routes go to HUMAN (L2) ([ADR-0012](../adr/0012-policy-based-human-in-the-loop.md)).

## 7. Feedback loop

| Signal | Source | Use |
|---|---|---|
| Planned vs actual leg times | `tms.shipment.events.v1`, telemetry | calibrate travel-time model + cost coefficients |
| Plan acceptance / override | `orch.decision.v1` (`overrodeRecommendation`) | tune weights; detect bad recommendations |
| Route stability events | dispatch logs | tune churn threshold |
| Missed windows / SLA | `vis.milestone`, `orch.exception` | adjust penalties |

## 8. SLA

| Metric | Target |
|---|---|
| Live solve (≤ 200 stops) | p99 < 2 s |
| Live solve (≤ 2,000 stops) | p99 < 10 s |
| Batch wave/lane | < 60 s |
| Nightly network | < 60 min |
| Trigger-to-recommendation (event) | p99 < 2 min (incl. coalesce) |
| Travel-time feature freshness | < 5 min |
| Infeasibility reporting | always explicit |
