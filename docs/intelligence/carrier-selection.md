# Carrier Selection & Allocation

> Status: Accepted · Related: [README.md](README.md) · [route-optimization.md](route-optimization.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md) · [../orchestration/trigger-catalog.md](../orchestration/trigger-catalog.md) · [../adr/0014-optimization-and-inference-architecture.md](../adr/0014-optimization-and-inference-architecture.md)

Chooses the best carrier / mode / service for a shipment or a set of loads, predicting acceptance likelihood and real-world performance. Output is a ranked `Recommendation`; the orchestrator tenders and handles counteroffers/fallbacks.

## 1. Decision scope

| Case | Method |
|---|---|
| Single shipment, small candidate set | learned utility ranking (top-k) |
| Batch of loads, shared capacity/contracts | min-cost flow / transportation LP + heuristics |
| Auction / spot | sealed-bid scoring + reserve price |
| Fallback / re-tender | re-rank excluding declined carriers; escalate per policy |

## 2. Candidate assembly

From MDM + contracts: carriers authorized on the lane (mode, equipment, hazmat, temperature), with active contract/rate, meeting service class, within geographic coverage, not on hold, with remaining committed capacity. Hard-infeasible carriers are removed before scoring.

## 3. Predictions (per candidate)

| Prediction | Model | Description |
|---|---|---|
| **Acceptance probability** | gradient boosting / logistic on tender history | P(carrier accepts this tender) |
| **Expected cost** | regression + rate engine | contract/spot cost, accessorials, fuel, expected re-tender cost |
| **Expected transit / ETA** | reuse ETA model | predicted delivery vs promised |
| **Reliability / risk** | carrier performance model | OTP/OTD, damage, claim, cancellation, safety |
| **Capacity availability** | capacity model | likelihood capacity is actually available at pickup time |

## 4. Utility scoring

```
U(carrier) = w_cost   * (1 - normCost)
           + w_rel    * reliabilityScore
           + w_accept * acceptanceProb
           + w_transit* (1 - normTransit)
           - w_risk   * riskScore
           - w_spot   * spotPremium
```

Weights are tenant policy (supports `MIN_COST`, `MAX_SERVICE`, `BALANCE_COST_SERVICE`, `MIN_EMISSIONS`). For batch allocation, maximize total utility subject to contract minimums/maximums, committed volume, diversity limits, and per-carrier caps (transportation problem / min-cost flow).

Diagram: [`diagrams/carrier-scoring.mmd`](diagrams/carrier-scoring.mmd).

## 5. Inputs

| Input | Source |
|---|---|
| Tender need / load | `ord.order.events.v1`, `tms.load.events.v1` |
| Carrier contracts/rates | MDM `Contract`, `RateCard`, `Lane` |
| Tender history | `tms.tender.events.v1` (offered/accepted/declined/expired) |
| Carrier performance | `vis.milestone.events.v1`, claims, `tms.shipment.events.v1` |
| Capacity signals | carrier feeds, `tms.*`, `yms.*` |
| Predicted ETA | `intel.prediction.v1` |
| Policy/weights | tenant policy |

## 6. Output

`Recommendation` on `intel.recommendation.v1` (`type=CARRIER_ASSIGNMENT`):

- Ranked `options[]` (top-k carriers), each with `expectedCost`, `expectedTimeMin`, `riskScore`, `confidence`, `score`, and `params` (carrierId, service, mode, rate ref).
- `recommendedOptionId`, `objective`, `validUntil`, `modelId`, `featuresHash`.
- Batch case: one option per load with the suggested allocation.

Orchestration routes: AUTO tender for trusted lanes under policy; HUMAN for strategic/high-value; PARTNER for collaborated carriers ([ADR-0012](../adr/0012-policy-based-human-in-the-loop.md)). Tender counteroffers update the context and may trigger re-ranking (`DecisionDeferred` → re-opt).

## 7. Feedback loop

| Signal | Source | Use |
|---|---|---|
| Accept / decline / expire | `tms.tender.events.v1` | train acceptance model |
| Actual cost vs predicted | billing/invoice | calibrate cost model |
| Actual OTP/OTD/damage | milestones, claims | refresh carrier scorecards |
| Human override of suggestion | `orch.decision.v1` | detect bias; tune weights |

Emitted as `intel.feedback.v1`; acceptance model retrains weekly, scorecards daily.

## 8. SLA

| Metric | Target |
|---|---|
| Rank single shipment | p99 < 300 ms |
| Batch allocation (≤ 1k loads) | p99 < 5 s |
| Tender-need-to-recommendation | p99 < 1 s |
| Acceptance-model AUC | ≥ 0.80 on mature lanes |
| Carrier scorecard freshness | daily |
| Cost prediction MAPE | ≤ 10% |
