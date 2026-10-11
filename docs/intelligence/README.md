# Optimization & Predictive Layer (L4)

> Status: Accepted · Related: [route-optimization.md](route-optimization.md) · [predictive-eta.md](predictive-eta.md) · [carrier-selection.md](carrier-selection.md) · [network-simulation.md](network-simulation.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md) · [../orchestration/decision-context.md](../orchestration/decision-context.md) · [../adr/0014-optimization-and-inference-architecture.md](../adr/0014-optimization-and-inference-architecture.md)

The intelligence layer (L4) turns current state + history into **predictions** and **ranked recommendations**. It is **advisory**: orchestration (L5) decides, policy routes, execution (L3) acts. L4 never writes domain state ([integrations.md §5](../integrations.md)).

## 1. Capabilities

| # | Capability | Doc | Primary output |
|---|---|---|---|
| (a) | Dynamic route optimization | [route-optimization.md](route-optimization.md) | `Recommendation` (`ROUTE_PLAN`, `LOAD_BUILD`) |
| (b) | Predictive ETA | [predictive-eta.md](predictive-eta.md) | `EtaPrediction` (`intel.prediction.v1`) |
| (c) | Carrier selection / allocation | [carrier-selection.md](carrier-selection.md) | `Recommendation` (`CARRIER_ASSIGNMENT`) |
| (d) | Network simulation (what-if) | [network-simulation.md](network-simulation.md) | `SimulationCompleted` (`intel.simulation.v1`) |

## 2. Runtime architecture

```
 events (L1–L3) ─▶ feature pipeline (streaming + batch) ─▶ feature store (online|offline)
                                                                   │
                          ┌────────────────────────────────────────┤
                          ▼                    ▼                    ▼
                    online inference     solver services       simulator
                    (ETA, acceptance,    (routing, load,       (discrete-event
                     performance)         allocation)           + Monte Carlo)
                          │                    │                    │
                          ▼                    ▼                    ▼
                  intel.prediction.v1   intel.recommendation.v1   intel.simulation.v1
                          └──────────────┬─────────────┘            │
                                         ▼                         ▼
                              Orchestration (L5, policy)     Analytics (L7)
                                         │
                                         ▼
                              commands to execution (L3)
                                         │
                              outcomes ─▶ intel.feedback.v1 ─▶ retrain / calibrate
```

Diagram: [`diagrams/intelligence-pipeline.mmd`](diagrams/intelligence-pipeline.mmd).

| Component | Responsibility |
|---|---|
| **Feature pipeline** | Streaming (Flink/Spark Streaming) + batch (dbt/Spark) feature computation; **point-in-time correct** to avoid leakage. |
| **Feature store** | Online (low-latency serving) + offline (training) parity; every output carries `featuresHash`. |
| **Online inference** | Model endpoints (gRPC/REST) for ETA, tender-acceptance, carrier performance; strict latency budgets. |
| **Solver services** | Routing/load/allocation optimization; sync API + event-driven batch. |
| **Simulator** | Discrete-event + Monte Carlo what-if; offline, reproducible. |
| **Model registry & monitoring** | Versioning, deployment gates, drift detection, feedback accumulation. |

## 3. Serving modes

| Mode | Trigger | Latency target | Consumers |
|---|---|---|---|
| **Online (sync)** | solver/decision request, control-tower query | p99 < 50–300 ms | Orchestration (context assembly), L6 control tower |
| **Event-triggered** | domain event / milestone / re-opt trigger | p99 < 2 s end-to-end | Orchestration |
| **Batch** | schedule (e.g., every 15 min, nightly) | minutes–hours | Orchestration (wave planning), Analytics |
| **On-demand simulation** | scenario request | minutes–hours | Analytics, policy owners |

## 4. Contract with orchestration

L4 emits **advisory events**; orchestration maps them into the [DecisionContext](../orchestration/decision-context.md):

| L4 event | → DecisionContext field | Orchestration use |
|---|---|---|
| `EtaPrediction` | `signals[]` | fire `TIME_SLA_*`/`LATE_INBOUND` triggers; confidence gate for auto |
| `Recommendation.options[]` | `options[]` | score/rank via policy; route auto/human/partner |
| `Recommendation.validUntil` | decision freshness | stale recommendations are re-requested (`DecisionDeferred` → re-opt) |
| `SimulationCompleted` | policy tuning input | adjust thresholds/network policy (offline, not per-decision) |

**Rules**
1. L4 never triggers an action directly; it publishes to `intel.prediction.v1` / `intel.recommendation.v1` ([ADR-0012](../adr/0012-policy-based-human-in-the-loop.md)).
2. Every output carries `modelId`, `modelVersion`, `confidence`, `featuresHash` — so the eventual `DecisionRecord` is explainable and reproducible.
3. Confidence below the tenant floor → orchestration routes to a human rather than auto-applying.
4. Recommendations expire (`validUntil`); orchestration re-requests rather than acting on stale advice.
5. Objective/weights come from tenant policy (`MIN_COST`, `MIN_TIME`, `MAX_SERVICE`, `BALANCE_COST_SERVICE`, `MIN_EMISSIONS`).

## 5. Feedback loops (learning)

| Capability | Ground truth source | Feedback event | Retrain cadence |
|---|---|---|---|
| ETA | POD/milestones, telemetry | `intel.feedback.v1` (`kind=ETA`) | daily/weekly + drift-triggered |
| Carrier acceptance | tender accept/decline | `intel.feedback.v1` | weekly |
| Carrier performance | OTP/OTD/damage/claims | scorecard refresh | daily |
| Route/load | planned vs actual legs, overrides | `intel.feedback.v1` | weekly + acceptance analysis |
| Simulation | realized network KPIs | calibration job | monthly / after major change |

Human overrides of recommendations are captured in `DecisionRecord.overrodeRecommendation` and become high-value training signal.

## 6. Consolidated SLAs

| Capability | Online inference | Event-to-output | Batch | Freshness |
|---|---|---|---|---|
| Predictive ETA | p99 < 50 ms | < 2 s from milestone | hourly re-score of open shipments | features < 5 min |
| Route optimization | p99 < 2 s (≤ 200 stops); < 10 s (≤ 2k) | re-opt coalesced within 2 min | 15-min rolling horizon | live traffic < 5 min |
| Carrier selection | p99 < 300 ms (rank); < 5 s (multi-shipment) | < 1 s from tender need | nightly re-score | scorecards daily |
| Network simulation | n/a (on-demand) | scenarios < 30 min (typical) | nightly baseline | baseline daily |

## 7. Governance

- **Model registry gates:** offline metrics + shadow + canary before promotion; `ModelDeployed` event on promotion.
- **Drift:** data drift (PSI/KS), concept drift (rolling error), calibration; `ModelDriftDetected` drives retraining ([ADR-0014](../adr/0014-optimization-and-inference-architecture.md)).
- **Explainability:** feature snapshots + hash per prediction; solver outputs include binding constraints and objective value.
- **Fairness/safety:** hard constraints (HOS, hazmat, weight) are never violated by an optimizer; infeasibility is reported, not silently relaxed.
- **Reproducibility:** given input events + `modelVersion`, every output is recomputable.

## 8. Reading order

1. [route-optimization.md](route-optimization.md)
2. [predictive-eta.md](predictive-eta.md)
3. [carrier-selection.md](carrier-selection.md)
4. [network-simulation.md](network-simulation.md)
