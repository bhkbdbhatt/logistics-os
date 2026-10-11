# Control Tower & Analytics (L6/L7)

> Status: Accepted · Related: [control-tower.md](control-tower.md) · [cost-attribution.md](cost-attribution.md) · [carrier-scorecards.md](carrier-scorecards.md) · [network-simulation.md](network-simulation.md) · [../architecture.md](../architecture.md) · [../integrations.md](../integrations.md) · [../schemas/analytics-events.md](../schemas/analytics-events.md) · [ADR-0017](../adr/0017-analytics-tiering-streaming-first.md)

The control tower (**Experience, L6**) is the operator's real-time cockpit; **Analytics (L7)** is the batch + streaming brain that produces KPIs, cost, scorecards, and simulation. Both are **read models over the event backbone** — neither owns domain state ([architecture.md §4.6–4.7](../architecture.md), [ADR-0003](../adr/0003-cqrs-read-models.md)).

| Capability | Doc | Primary output |
|---|---|---|
| (a) Real-time operational dashboard + alerts + one-click actions | [control-tower.md](control-tower.md) | `analytics.alert.v1`, `orch.command.v1` |
| (b) Shipment-level cost attribution | [cost-attribution.md](cost-attribution.md) | `analytics.cost.attribution.v1` |
| (c) Carrier scorecards | [carrier-scorecards.md](carrier-scorecards.md) | `analytics.scorecard.v1` |
| (d) Network-level simulation | [network-simulation.md](network-simulation.md) | `intel.simulation.v1` (engine in [L4](../intelligence/network-simulation.md)) |

---

## 1. Layer split

- **L6 control tower** — *serving*: sub-second, per-shipment/exception views, live map, alert queue, one-click actions. Reads CQRS `*_view` + the hot store; writes only **commands** (`orch.command.v1`). No business logic ([ADR-0003](../adr/0003-cqrs-read-models.md)).
- **L7 analytics** — *computing*: streaming + batch aggregation into an OLAP + lakehouse; produces metrics, cost, scorecards, forecasts, and drives the digital twin. Serves BI/metrics API; never serves operational transactions.

Guiding rule: **the control tower decides with what analytics computes and orchestration executes.** Every one-click action is an idempotent command routed through policy; every metric has one definition (semantic layer).

## 2. Data pipeline: event stream → queryable store

```
                       ┌─────────────────────────────── L2 backbone (Kafka) ───────────────────────────────┐
 domain events ───────▶│ ord.* tms.* wms.* yms.* vis.* inv.* fin.* obs.* → intel.* orch.*                  │
                       └───────────────┬───────────────────────┬───────────────────────┬───────────────────┘
                                       │ Tier 0/1 streaming     │ Tier 2 micro-batch     │ Tier 3 batch
                                       ▼                        ▼                        ▼
                              Flink / Kafka Streams     Flink/Spark micro-batch   Kafka Connect/CDC sink
                                       │                        │                        ▼
                     ┌─────────────────┴──────────┐             │                 Lakehouse (Iceberg/Delta)
                     ▼                            ▼             │                 bronze → silver (canonical)
              Hot store (Redis/Aerospike)   OLAP (ClickHouse/    │                        │
              (latest state, leaderboards)  Druid/Pinot)          │                 dbt → gold marts
                     │                            │             │                        │
                     ▼                            ▼             ▼                        ▼
             control-tower WS/SSE          control-tower charts  near-RT marts    semantic layer + BI + ML
```

### 2.1 Tiers and latency budgets

| Tier | Path | Store | Freshness / latency budget | Consumers |
|---|---|---|---|---|
| **T0 Real-time** | event → Flink state → hot store → WS/SSE | Redis/Aerospike (per-entity state), in-memory rules | **p99 < 2 s** end-to-end<br>ingest < 200 ms · project < 300 ms · deliver < 500 ms | live dashboard, alert firing, live map |
| **T1 Near-real-time** | event → Flink windowed aggregation → OLAP | ClickHouse/Druid/Pinot (columnar) | **p99 < 30 s** to queryable<br>(vs T0 **p99 < 60 s** dashboard refresh) | charts, rolling KPIs, scorecards (intraday), ad-hoc ops queries |
| **T2 Micro-batch** | event → micro-batch (1–15 min) → OLAP/marts | OLAP + hot marts | **p99 < 15 min** | near-real-time cost attribution, intraday scorecards |
| **T3 Batch** | sink → lakehouse bronze → dbt silver/gold | Iceberg/Delta + semantic layer | nightly (T+1) / hourly for finance; **SLA by 06:00 local** | cost-to-serve, carrier scorecards, margin, reconciliation, BI, ML training |
| **T4 Simulation** | pull baseline from lakehouse → L4 simulator → results | scenario store + artifact | on-demand, **minutes–hours** (async) | what-if / network design ([network-simulation.md](network-simulation.md)) |

Query SLOs: hot-store point lookup **p99 < 10 ms**; OLAP interactive query **p99 < 1 s** (pre-aggregated) / < 5 s (ad-hoc bounded); BI over gold only.

### 2.2 Streaming-first with batch reconciliation (Lambda-ish)

- **Streaming (T0/T1) is the source for live views.** Low latency, approximate for late data within the watermark.
- **Batch (T3) recomputes authoritatively.** Historical and financial figures are **truth from the batch layer**; the stream is corrected on reconciliation. Divergence between stream and batch is monitored and alerted.
- **Late/out-of-order data** handled via watermarks + allowed lateness with side-outputs that re-run the affected window ("restatement").
- **Replay/backfill**: because bronze retains raw events within retention, any mart is rebuildable.

Rationale and trade-offs: [ADR-0017](../adr/0017-analytics-tiering-streaming-first.md).

### 2.3 Consistency, tenancy, and cost

- **Exactly-once for money KPIs** (`fin.*` aggregations) via Kafka transactional sink; **at-least-once + idempotent upsert** elsewhere. Aggregations are keyed and idempotent so replay is safe.
- **Metric definitions live in the semantic layer** (dbt + metrics store); no metric is defined twice. Control tower and BI share the same definitions.
- **Tenancy**: every row/aggregation carries `tenantId`; row-level security in OLAP and lakehouse; data residency per region ([cross-cutting.md §1](../cross-cutting.md)).
- **Cost guardrails**: pre-aggregation, partitioning by day/tenant, tiered hot→warm→cold, query quotas on ad-hoc, scale-to-zero batch.

## 3. Where each capability reads/writes

| Capability | Reads | Computes at | Writes |
|---|---|---|---|
| Dashboard + alerts | hot store, OLAP, `*_view` | T0/T1 | `analytics.alert.v1`; actions → `orch.command.v1` |
| Cost attribution | `fin.*`, `tms.load/vshipment`, rate cards, timesheets | T0 (estimate) → T2/T3 (true-up) | `analytics.cost.attribution.v1` |
| Carrier scorecards | `tms.tender`, `vis.milestone`, `fin.*`, claims, MDM | T1 (intraday) → T3 (official) | `analytics.scorecard.v1` |
| Network simulation | lakehouse baseline + MDM network | T4 (L4 engine) | `intel.simulation.v1` |

## 4. Reading order

1. [control-tower.md](control-tower.md)
2. [cost-attribution.md](cost-attribution.md)
3. [carrier-scorecards.md](carrier-scorecards.md)
4. [network-simulation.md](network-simulation.md)
