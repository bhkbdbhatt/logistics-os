# Carrier Scorecards

> Status: Accepted · Related: [README.md](README.md) · [../intelligence/carrier-selection.md](../intelligence/carrier-selection.md) · [../schemas/analytics-events.md](../schemas/analytics-events.md) · [../data-model.md](../data-model.md)

Objective, multi-dimensional scoring of carriers to drive **procurement, allocation, and selection**. Output is `analytics.scorecard.v1` ([schema](../schemas/analytics-events.md)); the composite score is a **feature** in L4 carrier selection ([carrier-selection.md](../intelligence/carrier-selection.md)). Scorecards are **scoped** (per lane/mode/service), because a carrier good on one lane may be poor on another.

## 1. Scoring dimensions

| Dimension | What it measures | Direction |
|---|---|---|
| **Cost** | rate competitiveness vs lane benchmark, accessorial incidence, invoice accuracy | lower cost → higher score |
| **OTD** | on-time delivery vs promised | higher → higher |
| **OTP** | on-time pickup at origin | higher → higher |
| **Transit variance** | spread of actual vs planned transit | lower → higher |
| **Damage & claims** | damage rate, claims ratio, claim resolution time | lower → higher |
| **Tender acceptance** | accept rate, response latency | higher → higher |
| **Capacity coverage** | ability to cover committed/demand surcharge windows, no-shows/falloff | higher → higher |
| **Compliance & safety** | insurance/certs validity, safety scores, hazmat, regulatory | higher → higher |
| **Sustainability** | emissions per ton-mile, fleet mix | lower → higher |
| **Integration quality** | API/EDI availability, event timeliness/completeness, label quality | higher → higher |
| **Dispute rate** | billing disputes, chargebacks | lower → higher |

## 2. Data sources

| Source | Feeds |
|---|---|
| `tms.tender.events.v1` | tender acceptance, response latency |
| `tms.shipment.events.v1` | dispatch, pickup, delivery, exceptions |
| `vis.milestone.events.v1` | OTP/OTD, transit variance, milestone timeliness |
| `fin.bill.events.v1`, `fin.charge.events.v1` | cost vs benchmark, accessorials, invoice accuracy, disputes |
| Claims system | damage/loss, claims ratio, resolution time |
| `obs.telemetry.v1` (carrier feeds) | safety, speed, harsh events |
| `mdm.carrier.events.v1` | compliance docs, certifications, insurance expiry |
| Integration logs / `integration-architecture.md` | API/EDI availability, error rates, freshness |
| `intel.feedback.v1` | model-vs-actual acceptance for calibration |

Data quality is tracked per source: stale or sparse inputs **lower the confidence** of that dimension (§4).

## 3. Scoring math

1. **Raw metric** per dimension over the period (e.g., OTD = delivered-on-time / delivered).
2. **Normalize to 0–100** within a **comparison cohort** (same lane/mode/service or the tenant's carrier set) using min–max, percentile, or z-score (policy per dimension). Normalization-method choice is fixed per dimension for stability.
3. **Shrinkage for small samples:** empirical-Bayes / Bayesian average toward the cohort mean, weighted by sample size, so a carrier with 3 deliveries isn't ranked above one with 3,000.
   ```
   score = (n / (n + k)) * observed + (k / (n + k)) * cohortMean
   ```
4. **Weight & composite:**
   ```
   composite = Σ (weight_d × score_d) / Σ weight_d
   ```
   with each dimension's weight from a **named weights profile** (§4).
5. **Grade & rank:** map composite to `A/B/C/D` thresholds; rank within cohort.

## 4. Re-weighting logic

Weights are **policy-driven and dynamic**, computed per scorecard, never hard-coded:

| Lever | Effect |
|---|---|
| **Tenant / objective profile** | base weights by business model — cost-led vs service-led (e.g., `BALANCE_COST_SERVICE`, `MAX_SERVICE`, `MIN_COST`). |
| **Business context** | **peak season** ↑ service/reliability/capacity; cost pressure ↑ cost; sustainability mandate ↑ emissions. Context signals from calendar/policy. |
| **Lane/segment context** | hazmat/reefer lanes ↑ compliance & safety; express ↑ OTD & transit variance. |
| **Data quality** | dimensions with stale/sparse/low-confidence data are **down-weighted**; if a dimension is below a minimum sample, it's excluded and the remaining weights renormalize. |
| **Recency** | exponential time decay so recent performance dominates; window (30/90/365 d) selectable per profile. |
| **Confidence scaling** | dimension contribution scaled by its confidence (sample size × data freshness). |
| **Manual override (governed)** | procurement can pin/raise a weight for a strategic carrier or trial, recorded with actor + reason + expiry. |

**Renormalization rule:** weights always sum to 1 after exclusions/overrides, so composites remain comparable.

Diagram: [`diagrams/carrier-scorecard.mmd`](diagrams/carrier-scorecard.mmd).

## 5. Cadence

| Output | Tier | Freshness |
|---|---|---|
| Intraday indicator (rolling) | T1 | < 30 s |
| Daily official scorecard | T3 | by 06:00 local |
| Weekly/monthly trends & grades | T3 | weekly / monthly |
| Materials-change alert (`MODEL_DRIFT`-style) | T1 | on grade change / threshold breach |

## 6. Uses

- **L4 carrier selection** consumes the composite + dimensions as features and as a soft constraint ([carrier-selection.md](../intelligence/carrier-selection.md)).
- **Procurement** negotiation and lane awards; **allocation** tilt toward high performers.
- **Control tower** surfaces at-risk carriers (falling grade) as a risk signal.
- **Compliance** flags expired insurance/certs automatically.

## 7. Governance & anti-gaming

- **Cohort normalization** prevents a good market from inflating all scores.
- **Multi-dimensional** composite resists optimizing a single metric.
- **Caps** limit any one dimension's share; **floors** prevent zeroing a carrier on one bad week.
- **Transparency:** scorecard exposes per-dimension raw value, weight, confidence, and sample size (auditable).
- **Dispute path:** carriers can challenge data via the partner portal; corrections create new scorecards (append-only).
- **Bias checks:** monitor for lane/size/region bias; refresh profiles on a schedule.

## 8. SLAs

| Metric | Target |
|---|---|
| Daily scorecard completion | by 06:00 local |
| Intraday indicator freshness | < 30 s |
| Cohort minimum sample before scoring | ≥ 20 shipments (else excluded/shrunk) |
| Grade-change alert latency | < 15 min |
| Coverage: shipments scored | ≥ 99% |
