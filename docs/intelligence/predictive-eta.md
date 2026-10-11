# Predictive ETA

> Status: Accepted · Related: [README.md](README.md) · [route-optimization.md](route-optimization.md) · [../orchestration/decision-context.md](../orchestration/decision-context.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md) · [../adr/0014-optimization-and-inference-architecture.md](../adr/0014-optimization-and-inference-architecture.md)

Predicts the arrival time of a shipment/leg as a **distribution** (P10/P50/P90), refreshed on every milestone and telemetry update. Powers SLA-at-risk triggers, proactive customer notifications, and re-optimization ([../orchestration/trigger-catalog.md](../orchestration/trigger-catalog.md)).

## 1. Model

- **Core:** gradient-boosted trees (LightGBM/XGBoost) with **quantile regression** (P10/P50/P90) — robust to mixed features, fast to serve, well-calibrated tails.
- **Long-haul / sparse:** optional sequence model (temporal CNN/Transformer) over telemetry + stop sequence for multi-day trips, ensembled with the GBT.
- **Segmentation:** global model with lane/customer/season features; specialized per-mode/per-region models where data supports it.
- **Output:** quantile timestamps + `predictedDelayMin` + `confidence` + `modelId`/`modelVersion`/`featuresHash`.

## 2. Features

| Group | Examples |
|---|---|
| **Position/progress** | remaining distance, remaining stop count, current leg index, fraction complete |
| **Live conditions** | current speed, recent speed trend, stopped time, traffic index, weather severity, road closures |
| **Historical lane** | lane-specific transit distribution, day-of-week/hour effects, seasonality, holiday flags |
| **Facility/dwell** | expected dock wait, facility dwell history, appointment window, congestion at node |
| **Carrier/driver** | carrier OTP/OTD history, driver HOS remaining, driver/lane experience, prior delays on this trip |
| **Shipment** | mode, service level, weight/volume, hazmat/reefer, priority, tender method |
| **External** | port/terminal status, customs hold, border delay |
| **Interaction** | time-of-day × lane, weather × mode, dwell × facility |

Feature computation is **point-in-time correct** (no leakage) in the feature store; `featuresHash` binds the exact vector.

## 3. Training data

| Source | Role |
|---|---|
| `vis.milestone.events.v1` | milestone timestamps → segment actuals |
| `obs.telemetry.v1` | speed/position history; stop detection |
| `tms.shipment.events.v1` | dispatched/picked-up/delivered ground truth |
| POD timestamps | final arrival label |
| Historical traffic/weather archives | features at prediction time (must replay as-of) |
| `yms.appointment.events.v1` / `yms.yard.events.v1` | dwell actuals |
| Carrier scorecards | reliability covariates |

**Label:** actual arrival − baseline (contracted/route ETD/ETA) per leg; trained as a distribution (quantile) to produce P10/P50/P90. Time-based splits (never random) to avoid leakage; backtesting on rolling windows.

## 4. Drift monitoring

| Type | Metric | Detection | Action |
|---|---|---|---|
| Data drift | PSI / KS on top features | threshold breach on rolling window | alert; investigate feature pipeline |
| Concept drift | rolling MAPE/MAE vs baseline, calibration (P10–P90 coverage) | breach of coverage/error SLO | retrain |
| Feature skew | online vs offline feature distributions | divergence | fix pipeline parity |
| Label delay | lag in ground truth | freshness monitor | adjust training cutoff |

Emits `ModelDriftDetected` (`intel.model.events.v1`) with severity; feeds the model registry and retraining pipeline ([ADR-0014](../adr/0014-optimization-and-inference-architecture.md)).

## 5. Serving & latency budget

Diagram: [`diagrams/eta-serving.mmd`](diagrams/eta-serving.mmd).

| Stage | Budget (p99) |
|---|---|
| Feature fetch (online store) | < 20 ms |
| Model scoring (quantiles) | < 30 ms |
| Persist prediction + snapshot | < 10 ms |
| **Total per prediction** | **< 50 ms** |
| Trigger-to-`intel.prediction.v1` (event path) | < 2 s |
| Hourly re-score of open shipments | batch, < 5 min |

Serving is stateless and horizontally scaled; predictions cached by `(shipmentId, leg, featuresHash)` and invalidated on new milestones/telemetry. If the online store degrades, serve from the last snapshot with a `staleness` flag rather than fail.

## 6. Output & orchestration use

`EtaPrediction` on `intel.prediction.v1`:

- `p10EtaAt`, `p50EtaAt`, `p90EtaAt`, `predictedDelayMin`, `confidence`, `validUntil`.
- Consumed by orchestration as `DecisionContext.signals`:
  - `TIME_SLA_DELIVERY_AT_RISK` when `p50 > promised deadline − buffer`;
  - `LATE_INBOUND` for inbound legs;
  - confidence gate: if `confidence < autoThreshold`, SLA-at-risk decisions route to humans.
- Also consumed by Visibility (customer-facing ETA) and L6 control tower.

## 7. Feedback loop

| Signal | Source | Use |
|---|---|---|
| Actual arrival | POD, `tms.shipment.delivered` | label; error/coverage metrics |
| Milestone actuals | `vis.milestone.events.v1` | per-segment error attribution |
| Human overrides of ETA-driven decisions | `orch.decision.v1` | detect systematic bias |
| Drift alarms | monitor | trigger retrain |

Emitted as `intel.feedback.v1` (`kind=ETA`); retrain cadence: daily/weekly + drift-triggered.

## 8. SLA

| Metric | Target |
|---|---|
| Online inference | p99 < 50 ms |
| Event-to-prediction | p99 < 2 s |
| Median absolute error (P50) | ≤ 15 min on mature lanes |
| P10–P90 coverage | 78–82% (calibrated) |
| Feature freshness | < 5 min |
| Open-shipment re-score | hourly |
| Drift detection latency | < 24 h |
