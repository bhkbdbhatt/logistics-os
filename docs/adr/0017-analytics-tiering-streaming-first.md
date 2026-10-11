# ADR-0017: Analytics tiering — streaming-first with batch reconciliation

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../analytics/README.md](../analytics/README.md) · [../analytics/control-tower.md](../analytics/control-tower.md) · [../cross-cutting.md](../cross-cutting.md) · [ADR-0001](0001-event-driven-kafka-backbone.md) · [ADR-0003](0003-cqrs-read-models.md)

## Context

The control tower needs **sub-2-second** operational views, while finance, scorecards, and simulation need **accurate, reconciled** numbers and historical depth. A single pipeline cannot serve both low-latency freshness and exactness: pure streaming is fast but approximate under late/out-of-order data; pure batch is exact but hours stale. Meanwhile costs (storage, compute) and tenancy/residency constraints require deliberate tiering of hot, warm, and cold data.

## Decision

Adopt a **tiered, streaming-first pipeline with batch as the reconciliation authority**:

1. **Four serving tiers** with explicit latency budgets ([analytics/README.md §2.1](../analytics/README.md)): **T0** real-time (Flink state → hot store → WS/SSE, p99 < 2 s), **T1** near-real-time (windowed aggregation → OLAP, p99 < 30 s), **T2** micro-batch (1–15 min marts), **T3** batch (lakehouse bronze→silver→gold via dbt).
2. **Streaming is the source for live views; batch is truth for historical/financial figures.** Divergence is monitored and alerted; the stream is corrected by reconciliation and window restatement for late data (watermarks + allowed lateness + side outputs).
3. **One metric definition.** Metrics live in a **semantic layer** (dbt + metrics store) shared by control tower and BI; no metric is defined twice.
4. **Exactly-once effects for money KPIs** (transactional sink); idempotent upserts elsewhere so replay/backfill is safe.
5. **Lakehouse retains raw events (bronze)** within retention, so any mart or projection is rebuildable.
6. **Tiering is also a cost/residency control**: hot→warm→cold, partition by day/tenant, query quotas on ad-hoc, scale-to-zero batch, region-pinned data.

## Consequences

**Positive**
- Sub-2 s operational freshness **and** exact, reconciled financial history from one event source.
- Replay/backfill and projection rebuild are first-class; no manual data fixes of record.
- Consistent metrics across the tower and BI; auditable definitions.

**Negative**
- Two computational paths (stream + batch) to build, test, and operate; risk of silent divergence.
- Watermarks/restatement and batch reconciliation add complexity.
- More storage tiers and lifecycle policies to manage.

**Mitigations**
- Parity tests (stream vs batch) on key KPIs; divergence alerts.
- Reuse one semantic layer; generated pipelines where possible.
- Tiering/lifecycle automated (retention from [data-model.md](../data-model.md)).

## Alternatives considered

- **Streaming-only (Kappa)** — rejected: hard to get exact historical/financial figures and cheap deep history; late-data restatement in-stream is costly.
- **Batch-only** — rejected: cannot meet sub-2 s control-tower needs.
- **Separate ad-hoc pipelines per team** — rejected: metric drift, cost explosion, no single definition.
- **Serve operational transactions from the lake** — rejected: wrong latency/shape; violates [ADR-0003](0003-cqrs-read-models.md).
