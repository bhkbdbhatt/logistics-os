# Non-Functional Requirements (NFRs)

> Status: Accepted · Related: [architecture.md](architecture.md) · [cross-cutting.md](cross-cutting.md) · [events.md](events.md) · [data-model.md](data-model.md) · [integration-architecture.md](integration-architecture.md) · [analytics/README.md](analytics/README.md) · [roadmap.md](roadmap.md)

NFRs for a mid-to-large **3PL/4PL ($500M–$5B spend)**. Targets are **SLOs** (not best-effort); each carries an owner and an error budget. Numeric targets are **baseline commitments** to tune per tenant/contract; priority is P0/P1/P2.

**Priority:** **P0** — breach is a customer-impacting or financial/safety incident (stop-the-line); **P1** — material degradation, tolerated briefly; **P2** — quality/optimization target.

**Enforcement:** SLOs are defined in code (config), asserted in load/chaos tests and CI, and monitored with error budgets ([cross-cutting.md §5](cross-cutting.md)). Any relaxation requires an ADR.

---

## 1. Availability (per layer)

| Layer / service | Target | Window | Degradation mode on breach | P |
|---|---|---|---|---|
| **L5 Orchestration** (workflow engine, ATP/promise, decision router) | **99.95%** | monthly | read-only mode: serve state, queue commands, no new promises | **P0** |
| **L2 Event backbone** (Kafka) | **99.9%** (stretch 99.95%) | monthly | producers buffer via outbox; consumers lag | **P0** |
| **L2 Read models / CQRS projections** | 99.9% | monthly | serve last-known with staleness badge | **P1** |
| **L3 Execution** — TMS/WMS/OMS/YMS (bought) | 99.9% | monthly | manual fallback; last-known state | **P0** |
| **L3 Billing / settlement** | 99.9% | monthly | accruals queue durably; post when recovered | **P0** |
| **L1 Observation / ingest** | 99.5% | monthly | edge buffers; batch backfill | **P1** |
| **L4 Intelligence** (ETA, solvers, recommendations) | 99.5% | monthly | **degrade to "no signal"**; decisions fall back to rules/human | **P1** |
| **L6 Control tower** | 99.9% | business hours | read stale + alert; commands via API | **P1** |
| **L6 Customer tracking portal** | 99.9% | monthly | cached last-known ETA | **P1** |
| **L7 Analytics — real-time KPIs (T0/T1)** | 99.5% | monthly | freeze last aggregate | **P2** |
| **L7 Analytics — batch (T3)** | 99.0% | per run | rerun window next cycle | **P2** |
| **MDM** | 99.95% | monthly | cached golden records | **P0** |

Orchestration and financial paths are held to **99.95%/P0**; analytics is explicitly **99.5%/P2** — analytics downtime must never block execution.

## 2. Latency budgets (event → action)

| Critical path | Stages (p99 budget) | End-to-end p99 | P |
|---|---|---|---|
| **SLA-at-risk → action** | event <200 ms → ETA recompute <2 s → trigger <500 ms → alert <500 ms → one-click/auto command <1 s | **< 5 s** | **P0** |
| **Exception cascade → decision → command** | detect <1 s → coalesce window (config 0–120 s) → context assemble <3 s → policy route <500 ms → command <1 s | **< 8 s** (excl. coalesce) | **P0** |
| **Order → promise (ATP)** | order event <200 ms → ATP query p99 <1 s → promise write <500 ms | **< 2 s** | **P0** |
| **Tender → carrier accept (sync)** | TMS → carrier API p99 <1 s → ack | **< 2 s** | **P1** |
| **Pick → pack → ship (site)** | WMS→WCS handshake <100 ms → task flow → scan confirm <500 ms | **< 2 s/step** | **P0** |
| **POD capture → customer notification** | mobile sync <2 s (online) → milestone <500 ms → webhook/SSE <1 s | **< 5 s** | **P1** |
| **Re-opt trigger → recommendation → apply** | trigger <1 s → coalesce (2 min) → solver p99 <2 s → policy apply <1 s | **< 2 min** (incl. coalesce) | **P1** |
| **Cost attribution (tentative → view)** | dispatch event → attribution <2 s → dashboard <500 ms | **< 3 s** | **P1** |
| **Sync rating / label (carrier)** | API round trip | **< 1 s p99** | **P1** |
| **Control-tower UI read (hot)** | point lookup | **< 10 ms p99** | **P1** |
| **OLAP ad-hoc query** | pre-aggregated / bounded ad-hoc | **< 1 s / < 5 s p99** | **P2** |
| **Notification fan-out (webhook)** | eligible → delivered | **< 30 s p99** | **P2** |

Sync path rule: any synchronous call in a critical path has a **timeout + fallback** so a slow peer degrades gracefully ([integration-architecture.md §3](integration-architecture.md)). Money/inventory effects are **exactly-once**; everything else idempotent ([ADR-0007](adr/0007-outbox-and-idempotency.md)).

## 3. Scalability targets

| Dimension | Baseline | Peak (headroom) | Notes | P |
|---|---|---|---|---|
| **Backbone throughput** | 50k msg/s sustained | **200k msg/s burst** | telemetry-dominated; partition by aggregate | **P0** |
| **Domain events/sec** | 5k/s | 20k/s | order/shipment/inventory | **P0** |
| **Telemetry ingest** | 50k/s | 150k/s | edge-filtered; 5–15 TB/day lake | **P1** |
| **Concurrent active shipments** | 200k | **500k** | hot store + views | **P0** |
| **Shipments/day** | 1M | 2.5M | peak season | **P1** |
| **Warehouse throughput (per site)** | 10k lines/hr, 50k units/hr | 25k lines/hr | wave + WCS handshake | **P1** |
| **Network warehouse lines/hr** | 200k | 500k | across sites | **P1** |
| **Concurrent control-tower users** | 200 | 500 | per tenant | **P1** |
| **Concurrent tracking users** | 10k | 50k | cache + CDN | **P1** |
| **Carrier API calls/min** | per-contract | token-bucket + webhooks | [integration-architecture.md §4.3](integration-architecture.md) | **P0** |
| **Tenants (4PL customers)** | 10 | 100 | RLS, per-region | **P1** |
| **Update rate (hot store)** | 20k writes/s | 60k/s | latest-state semantics | **P2** |

Scale rule: every component **autoscales on lag/CPU**; no physical line is throttled by the bus (back-pressure/edge buffering) ([cross-cutting.md §6](cross-cutting.md)). Load tests must prove **2× peak** without SLO breach.

## 4. Data retention

| Data class | Hot | Warm/Cold (lake) | Total / policy | P |
|---|---|---|---|---|
| Money: invoices/settlements | 1y | 9y archive | **10y**; legal hold; immutable | **P0** |
| Money: charges / freight bills | 1y | 6y archive | **7y** | **P0** |
| Audit log | 1y | 6y WORM | **7y**, tamper-evident | **P0** |
| Shipment / milestone lifecycle | 6 mo | archive | **7y** | **P1** |
| Orders | 1y | archive | **7y** | **P1** |
| Inventory events | 1y | 2y | **3y** | **P1** |
| Orchestration (promise 3y; decision 2y; exception/escalation 2y; trigger/reopt 1y) | live | archive | per object ([data-model.md §2.9](data-model.md)) | **P1** |
| Intelligence (prediction/recommendation/simulation 1y; model/feedback 2y) | live | archive | per object ([data-model.md §2.10](data-model.md)) | **P2** |
| Analytics (alerts 90d; cost attribution 7y; scorecards 7y) | live | archive | per object ([data-model.md §2.12](data-model.md)) | **P2** |
| Master data (MDM) | compacted (current) | — | current + history | **P0** |
| Telemetry / scans | 90d (scans 30d) | 2y | tiered cold | **P2** |
| DLQ | 30d | — | replayable within window | **P1** |

Retention is **per tenant and per region**; erasure via tombstones on compacted topics + lake purge + **crypto-shredding** per tenant key ([cross-cutting.md §7](cross-cutting.md)).

## 5. Security & sovereignty

| Requirement | Target | P |
|---|---|---|
| **Tenant isolation** | logical default (`tenantId` everywhere + RLS); dedicated namespaces/clusters for large tenants | **P0** |
| **Encryption** | TLS 1.2+ in transit; AES-256 at rest; mTLS service-to-service | **P0** |
| **Identity** | OIDC/OAuth2, MFA, short-lived tokens; least privilege | **P0** |
| **Secrets** | managed vault only; never in code/events | **P0** |
| **Audit** | tamper-evident `audit.*`; all state changes + access; 7y | **P0** |
| **PII** | tagged fields, minimized, access-controlled, encrypted; PII absent from Kafka keys | **P0** |
| **GDPR / DSR** | lawful basis; right-to-erasure + data portability; consent tracking; DPO process | **P0** |
| **Data residency (sovereignty)** | tenant pinned to region; **EU data stays in EU**; gateway/region routing; no cross-border without safeguards (SCCs) | **P0** |
| **Webhook security** | HMAC-signed + timestamp replay window | **P1** |
| **Vulnerability mgmt** | SAST/DAST/SCA in CI; external pen-test annually + pre-4PL-launch | **P1** |
| **Compliance posture** | SOC 2 Type II + ISO 27001 target | **P1** |
| **PCI** | not in scope (use tokenized payment provider) | **P2** |
| **RBAC/ABAC** | role + tenant + attribute policies (OPA) | **P1** |

## 6. Disaster recovery (RPO/RTO)

| Tier | Data / capability | Failure scope | RPO | RTO | Strategy | P |
|---|---|---|---|---|---|---|
| **0** | Money/inventory, orchestration state | AZ outage | **≈0** (<1 min) | **< 15 min** | synchronous replication / multi-AZ | **P0** |
| **0** | Money/inventory, orchestration state | **Region outage** | **< 5 min** | **< 4 h** | active-passive region; Kafka mirroring; state replication | **P0** |
| **1** | Backbone (Kafka) | AZ | < 1 min | < 30 min | replication factor ≥3, min.insync=2 | **P0** |
| **1** | Read models / hot store | AZ | < 1 min | < 30 min | rebuild from events / replicas | **P1** |
| **1** | Execution (TMS/WMS/OMS/YMS) | Region | < 15 min | < 8 h | vendor DR terms + ACL replay | **P1** |
| **2** | Analytics (lakehouse/OLAP) | Region | < 1 h | < 24 h | object-store replication + rebuild | **P2** |
| **2** | Intelligence (models) | Region | < 1 h | < 8 h | registry replication; re-serve last model | **P2** |
| **3** | Telemetry/raw feeds | Region | best-effort | < 48 h | backfill from source/lake | **P2** |

DR is **tested** at least twice yearly (live region-failover game day); projections and read models are rebuildable from retained events ([cross-cutting.md §6](cross-cutting.md)).

## 7. Observability

### 7.1 What to trace

| Signal | What | Why | P |
|---|---|---|---|
| **Distributed tracing** | W3C `traceparent` propagated **in event headers** across async boundaries (producer→consumer→saga step) | one trace across the whole order/shipment lifecycle | **P0** |
| **Correlation** | `correlationId` + `causationId` on every event/command | reconstruct causes | **P0** |
| **Domain flow** | order→promise→allocate→tender→dispatch→deliver→settle spans | end-to-end path SLOs | **P0** |
| **Orchestration** | workflow instance state, saga step, compensation, timer, escalation | debug stuck/failed sagas | **P0** |
| **Integration** | per-peer latency, error class, retries, `429`s, circuit state, idempotent replays | isolate a bad carrier/ERP | **P0** |
| **Consumption** | consumer lag per topic/group, processing rate, DLQ | pipeline health | **P0** |
| **Outbox** | relay lag, unpublished rows | detect publish stalls | **P0** |
| **Decisioning** | DecisionContext assembly, policy outcome, overrides | explain auto/human routing | **P1** |
| **ML** | prediction latency, confidence distribution, drift, feature skew | trust/degrade models | **P1** |
| **Data freshness** | stream-vs-batch parity, T0/T1 store age | catch silent staleness | **P1** |
| **Cost** | Kafka topic cost, query cost, unit cost/shipment | FinOps | **P2** |

Logs: **structured, correlated, PII-scrubbed**. No PII in trace/event keys.

### 7.2 Alert thresholds & SLOs

| Alert | Condition / SLI | Severity | P |
|---|---|---|---|
| Backbone availability | < 99.9% (rolling) | SEV1 | **P0** |
| Orchestration availability | < 99.95% (rolling) | SEV1 | **P0** |
| Consumer lag | > 10k msgs or age > 60 s (P0 topics) | SEV2 | **P0** |
| DLQ depth | > 100 msgs or growth > 10/min | SEV2 | **P0** |
| Outbox relay lag | > 30 s | SEV2 | **P0** |
| Saga failure rate | > 0.5% of instances / 5 min | SEV1 | **P0** |
| ATP promise latency | p99 > 2 s | SEV2 | **P0** |
| SLA-at-risk → alert latency | p99 > 5 s | SEV2 | **P0** |
| WCS handshake latency | p99 > 100 ms | SEV2 | **P0** |
| Integration error rate (per peer) | > 2% or `429` sustained | SEV2 | **P0** |
| Money/inventory duplicate-effect attempt | > 0 | SEV1 | **P0** |
| Read-model freshness | `shipment_view` p99 > 5 s | SEV3 | **P1** |
| Control-tower staleness | hot age > 10 s | SEV3 | **P1** |
| ETA calibration coverage | outside 78–82% (rolling 24 h) | SEV3 | **P1** |
| Model data drift | PSI/KS beyond threshold | SEV3 | **P1** |
| Stream-vs-batch divergence | > 0.5% on money KPIs | SEV2 | **P1** |
| Batch job SLA miss | night batch not done by 06:00 | SEV3 | **P2** |
| Analytics availability | < 99.5% | SEV4 | **P2** |
| Query cost | > budget/tenant/day | SEV4 | **P2** |

**Alert hygiene:** dedupe/debounce by `(type, subject, window)`; auto-resolve on recovery; route by severity to the [escalation ladder](orchestration/escalation.md); every SEV1/2 has a runbook. Error budgets: when a P0 SLO budget is exhausted, feature work pauses until reliability is restored.

## 8. Capacity assumptions & headroom

- Targets sized for **2× peak** with autoscaling; peak season (Q4) is the design point.
- Telemetry dominates volume; keep it edge-filtered and sampled where contractually allowed.
- Every tier keeps **≥ 30% headroom** at peak before scaling events fire.
- NFR changes are versioned; a breach of a P0 target triggers an ADR + architecture-guild review.
