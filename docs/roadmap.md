# Implementation Roadmap (3 phases · 0–36 months)

> Status: Accepted · Related: [architecture.md](architecture.md) · [build-vs-buy.md](build-vs-buy.md) · [build-vs-buy-catalog.md](build-vs-buy-catalog.md) · [events.md](events.md) · [orchestration/README.md](orchestration/README.md) · [analytics/README.md](analytics/README.md) · [ADR-0001](adr/0001-event-driven-kafka-backbone.md) · [ADR-0005](adr/0005-buy-execution-build-orchestration.md) · [ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)

Roadmap for a mid-to-large **3PL/4PL ($500M–$5B spend)**.

## 0. Starting state & assumptions

- **Exists but disconnected:** a TMS and a WMS (bought COTS) run in production; **no event-driven backbone**; integrations are point-to-point files/SFTP/manual; UIs are per-app.
- **No canonical model** across systems; no cross-domain orchestration; reporting is batch extracts.
- **Assume:** cloud (Kubernetes), a mandate to become event-driven, and willingness to buy commodity and build the moat ([ADR-0005](adr/0005-buy-execution-build-orchestration.md)).

**Principles that shape sequencing:** backbone first ([ADR-0001](adr/0001-event-driven-kafka-backbone.md)) → wrap existing systems with ACLs before replacing anything ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)) → orchestration and canonical data are the moat ([ADR-0009](adr/0009-canonical-model-as-moat.md)) → intelligence and analytics only after trustworthy events exist.

## Phase overview

| | Phase 1 — **Foundation** | Phase 2 — **Orchestration & Intelligence** | Phase 3 — **Moat & Scale** |
|---|---|---|---|
| Window | 0–6 months | 6–18 months | 18–36 months |
| Theme | Connect, canonicalize, observe | Decide, promise, predict | Optimize, simulate, multi-tenant |
| Headcount | ~12 | ~30 | ~48 |
| Diagram | [`diagrams/roadmap.mmd`](diagrams/roadmap.mmd) | | |

---

## Phase 1 — Foundation (0–6 months)

**Objective:** stand up the event backbone and connect TMS/WMS via anti-corruption adapters; publish the canonical model and CQRS read models; give operators a first real-time view; establish SLOs and governance.

### Scope

**In:** Kafka backbone + Schema Registry; identity/access; TMS & WMS ACLs (CDC/API) → canonical events; canonical `Shipment`/`Order`/`Inventory`/`Master` model; CQRS read models (`shipment_view`, `inventory_view`); MDM seed; observability; control-tower MVP (live map + alert list); outbox + idempotency plumbing; tenancy foundation.

**Out:** orchestration/sagas; ATP/promise; OMS integration; intelligence models; analytics lakehouse; multi-tenant 4PL onboarding.

### Key deliverables

| # | Deliverable | Detail |
|---|---|---|
| 1 | Managed Kafka + Schema Registry | Topics per [events.md §3](events.md); tenant-partitioned; DSL for DLQ/replay ([ADR-0001](adr/0001-event-driven-kafka-backbone.md), [ADR-0008](adr/0008-schema-registry-and-versioning.md)) |
| 2 | TMS & WMS anti-corruption adapters | CDC (Debezium) + API/webhook where available; publish canonical `tms.*`/`wms.*`/`inv.*` ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)) |
| 3 | Canonical schemas v1 | `shipment-events.md`, `reference-data.md`; modenvelope + exactly-once/idempotency ([schemas/](schemas/README.md)) |
| 4 | Read-model builder | Kafka Streams/Flink → `*_view`, `*.state.v1`; rebuildable ([ADR-0003](adr/0003-cqrs-read-models.md)) |
| 5 | MDM golden records (seed) | Customer/Carrier/Vehicle/Location; `mdm.*.events.v1` |
| 6 | IAM + API gateway | OIDC/OAuth2, tenant scoping, gateway rate limits ([cross-cutting.md §4](cross-cutting.md)) |
| 7 | Control-tower MVP | Live map, shipment drill-down, alert list (SLA-at-risk); read-only ([analytics/control-tower.md](analytics/control-tower.md)) |
| 8 | Observability | OTel tracing in event headers, RED metrics, consumer-lag SLOs, DLQ dashboards ([cross-cutting.md §5](cross-cutting.md)) |
| 9 | Outbox + idempotent consumers | Shared library; dedupe store ([ADR-0007](adr/0007-outbox-and-idempotency.md)) |
| 10 | SLO framework + ADR governance | Architecture guild, CI schema compatibility gate |

### Team composition (~12)

| Role | FTE |
|---|---|
| Principal/Staff Architect (TL) | 1 |
| Platform/Infra Engineer (Kafka/K8s) | 2 |
| Backend Engineer (domain services, ACLs) | 3 |
| Data Engineer (streaming/read models) | 2 |
| Integration Engineer (CDC/EDI/API) | 1 |
| SRE/DevOps | 1 |
| Frontend Engineer (BFF/UI) | 1 |
| QA/SDET | 0.5 |
| Security Engineer | 0.5 |
| Product Manager | 1 |
| Delivery/Program Manager | 1 |

### Exit criteria (Phase 1)

| KPI | Target |
|---|---|
| Backbone availability | ≥ 99.9% |
| Core domains emitting canonical events | TMS, WMS, MDM (100% of targeted event types) |
| `shipment_view` freshness (event→view) | p99 < 5 s |
| Read-model coverage of active shipments | ≥ 95% |
| Dual-write / lost-event incidents | 0 (outbox mandatory) |
| DLQ rate | < 0.1% of messages; 100% triaged < 24 h |
| Consumer-lag SLO attainment | ≥ 99% of consumer groups |
| Control-tower MVP | live map + SLA-at-risk alerts for 100% of active shipments |
| Schema compatibility failures in CI | 0 |
| Onboarding a new event type | < 5 business days (proven) |

### Top 3 risks

| Risk | Impact | Mitigation |
|---|---|---|
| **Boiling the ocean / scope creep** | Phase slips; no value | Fix the topic/model scope to TMS+WMS+Master; defer orchestration and ML; ruthless "out" list; ship the MVP view early for visible value |
| **Legacy/TMS/WMS integration fragility** (poor APIs, CDC gaps) | Bad/stale events poison everything downstream | ACL with schema validation + DLQ; CDC + batch reconciliation; reconcile counts vs source; treat source as provisional until parity proven ([integration-architecture.md §3](integration-architecture.md)) |
| **Data quality & org change resistance** (dispatchers distrust new views) | Low adoption; shadow systems persist | Data-quality scorecards from day 1; co-design the tower with operators; keep source UIs as fallback; publish freshness/staleness badges ([analytics/control-tower.md](analytics/control-tower.md)) |

---

## Phase 2 — Orchestration & Intelligence (6–18 months)

**Objective:** move from *observing* to *deciding and predicting*: durable orchestration for order-to-cash and exceptions, ATP/promise, OMS + carrier integrations, first intelligence models, and the analytics tiers.

### Scope

**In:** Temporal/Zebe workflow engine + flows ([ADR-0011](adr/0011-orchestration-saga-statechart-engine.md)); commands + saga orchestration ([ADR-0004](adr/0004-saga-over-distributed-tx.md)); ATP/promise engine; OMS + carrier API/EDI integration; exception cascade + escalation + decision router ([ADR-0012](adr/0012-policy-based-human-in-the-loop.md), [ADR-0013](adr/0013-exception-cascade-and-reopt-coalescing.md)); predictive ETA v1; analytics tiers T0–T3 + semantic layer; cost attribution v1; carrier scorecards v1; one-click actions in the tower.

**Out:** full route/load optimization; network simulation; multi-tenant 4PL brokerage; advanced ML lifecycle automation.

### Key deliverables

| # | Deliverable | Detail |
|---|---|---|
| 1 | Workflow engine live | Order-to-cash + returns + exception flows; durable, retryable ([orchestration/workflow-engine.md](orchestration/workflow-engine.md)) |
| 2 | Command + saga layer | `orch.command.v1` idempotent commands; compensations ([ADR-0004](adr/0004-saga-over-distributed-tx.md)) |
| 3 | ATP / promise engine | aggregate supply → promise; `orch.promise.v1` |
| 4 | OMS + carrier integrations | order capture; tender/label/status via REST/EDI ([integration-architecture.md §4.2–4.3](integration-architecture.md)) |
| 5 | Trigger + exception system | trigger catalog, coalescing, escalation ladder ([orchestration/trigger-catalog.md](orchestration/trigger-catalog.md), [escalation.md](orchestration/escalation.md)) |
| 6 | Decision context + policy routing | OPA/Drools; human-in-the-loop tiers ([ADR-0012](adr/0012-policy-based-human-in-the-loop.md)) |
| 7 | Predictive ETA v1 | quantile model + feature store + serving ([predictive-eta.md](intelligence/predictive-eta.md)) |
| 8 | Analytics tiers T0–T3 | Flink/hot store + OLAP + lakehouse + semantic layer ([ADR-0017](adr/0017-analytics-tiering-streaming-first.md)) |
| 9 | Cost attribution + scorecards v1 | `analytics.cost.attribution.v1`, `analytics.scorecard.v1` |
| 10 | Control tower v2 | alert queue with **one-click actions** (commands) + DecisionContext panel |
| 11 | MDM full + policy model | tenant policy, onboarding-as-configuration ([services.md §4](services.md)) |

### Team composition (~30)

| Role | FTE |
|---|---|
| Principal Architect + Eng Manager | 2 |
| Backend Engineer (orchestration/domain) | 8 |
| Backend/Integration Engineer (OMS/carriers/EDI) | 3 |
| Data Engineer (streaming + lakehouse) | 4 |
| ML/Data Scientist (ETA, feedback loops) | 3 |
| Frontend Engineer (tower/BFFs) | 4 |
| Platform/Infra Engineer | 2 |
| SRE/DevOps | 2 |
| Security Engineer | 1 |
| QA/SDET | 2 |
| Product Manager | 2 |
| Architect/Data Steward (canonical model) | 1 |
| Delivery/Program Manager | 1 |

### Exit criteria (Phase 2)

| KPI | Target |
|---|---|
| Order-to-cash automated (no manual handoff) | ≥ 70% of standard orders |
| Promise adherence (delivered within promise) | ≥ 95% |
| Predictive ETA accuracy (P50 MAE) | ≤ 30 min on mature lanes; P10–P90 coverage 78–82% |
| Exception auto-resolution (no human) | ≥ 40% of exceptions |
| Exception mean-time-to-resolve (MTTR) | < 2 h (from hours/days) |
| Tender acceptance rate | ≥ 85% |
| Cost attribution coverage | ≥ 99% of shipments; reconciliation variance < 0.5% |
| Carrier scorecards | 100% of active carriers, daily refresh |
| Control-tower action adoption | ≥ 60% of eligible decisions via one-click/auto |
| Re-opt loop latency (trigger→recommendation) | p99 < 2 min |

### Top 3 risks

| Risk | Impact | Mitigation |
|---|---|---|
| **Distributed-state correctness** (sagas, idempotency, duplicate money/inventory effects) | Financial/inventory corruption; trust loss | Exactly-once effects for money/inventory; idempotent commands; compensation tests; shadow-run flows before cutover ([ADR-0004](adr/0004-saga-over-distributed-tx.md), [ADR-0007](adr/0007-outbox-and-idempotency.md)) |
| **ML data readiness** (label latency, training/serving skew, cold-start lanes) | ETA/acceptance models underperform; decisions gated on low confidence | Own the feature store with online/offline parity; quantile + confidence gates; blend vendor ETA for cold start; drift monitoring ([ADR-0014](adr/0014-optimization-and-inference-architecture.md)) |
| **Adoption & change management** (operators override/hold to old tools) | Benefits unrealized; re-opt thrash | Policy-driven autonomy tiers; human-in-the-loop by default on high impact; train dispatchers; measure overrides as signal ([ADR-0012](adr/0012-policy-based-human-in-the-loop.md)) |

---

## Phase 3 — Moat & Scale (18–36 months)

**Objective:** realize the differentiator at scale: route/load optimization, network simulation, advanced intelligence, and multi-tenant 4PL brokerage — with FinOps and security hardened.

### Scope

**In:** dynamic route/load/consolidation optimization ([route-optimization.md](intelligence/route-optimization.md)); carrier selection/allocation at scale ([carrier-selection.md](intelligence/carrier-selection.md)); network simulation/digital twin ([network-simulation.md](analytics/network-simulation.md)); full ML lifecycle (registry, drift, retraining); multi-tenant/4PL brokerage + partner portal; advanced analytics (margin, carbon/ESG, cost-to-serve); FinOps; HA/DR multi-region.

**Out:** standing up new execution products (keep buying execution per [ADR-0005](adr/0005-buy-execution-build-orchestration.md)).

### Key deliverables

| # | Deliverable | Detail |
|---|---|---|
| 1 | Optimization services | pluggable solvers + constraint model + objective policy ([ADR-0014](adr/0014-optimization-and-inference-architecture.md)) |
| 2 | Carrier selection/allocation at scale | acceptance/cost/risk scoring + min-cost flow |
| 3 | Digital-twin simulation | discrete-event + Monte Carlo; calibrated ([ADR-0015](adr/0015-digital-twin-network-simulation.md)) |
| 4 | ML platform | model registry, gates, drift→retrain automation ([intelligence/README.md §7](intelligence/README.md)) |
| 5 | Multi-tenant 4PL | brokerage, `CapacityCommitment`, partner/API portal, onboarding-as-config |
| 6 | Advanced analytics | margin, cost-to-serve, carbon/ESG, semantic metrics at scale |
| 7 | FinOps + HA/DR | cost guardrails, multi-region active-passive, RTO/RPO per tier |
| 8 | Network design | node/lane/modal decisions informed by simulation |

### Team composition (~48)

| Role | FTE |
|---|---|
| Principal Architect(s) + Eng Managers | 3 |
| Backend Engineer (domain/platform) | 12 |
| Optimization/Operations-Research Engineer | 3 |
| ML Engineer / Data Scientist | 5 |
| Data Engineer (lakehouse/feature store) | 5 |
| Frontend Engineer | 5 |
| Platform/Infra Engineer | 3 |
| SRE + FinOps | 3 |
| Security Engineer | 2 |
| QA/SDET | 2 |
| Product Managers | 3 |
| Partner/Solutions Engineer | 2 |

### Exit criteria (Phase 3)

| KPI | Target |
|---|---|
| Loads optimized (routing/consolidation) | ≥ 80% of eligible loads; ≥ 60% auto-applied |
| Cost per shipment reduction | ≥ 5–10% vs Phase-2 baseline |
| Consolidation rate improvement | +10 pts |
| Optimization acceptance (recommendations not overridden) | ≥ 70% |
| Simulation-informed decisions | ≥ 80% of network changes evaluated first |
| Model deployment automation | 100% gated; drift→retrain < 48 h |
| Multi-tenant isolation | 0 cross-tenant incidents; RLS verified |
| New 4PL tenant onboarding | < 10 business days (config only) |
| Gross margin improvement | ≥ 3 pts attributable to the platform |
| Platform cost / shipment | within FinOps budget; unit cost trending down |
| Availability / DR | 99.95% (L5), RTO/RPO met in a live DR test |

### Top 3 risks

| Risk | Impact | Mitigation |
|---|---|---|
| **Multi-tenant isolation & security** (4PL data, partner access) | Breach blocks the 4PL business | RLS everywhere, per-region residency, HMAC webhooks, audit, tenant-scoped keys; external pen-test before launch ([ADR-0006](adr/0006-multi-tenant-isolation-model.md)) |
| **Optimization ROI not realized** (recommendations overridden, churn hurts) | Moat spend without benefit | Stability/churn gates; measure override reasons; tie objective weights to policy; phase rollout lane-by-lane; pair with simulation ([route-optimization.md §5](intelligence/route-optimization.md)) |
| **Scaling cost & complexity** (streaming + lakehouse + ML at scale) | Margin erosion; operational fatigue | Tiered storage, pre-aggregation, query quotas, scale-to-zero batch; FinOps guardrails; autoscaling on lag ([cross-cutting.md §8](cross-cutting.md), [ADR-0017](adr/0017-analytics-tiering-streaming-first.md)) |

---

## Cross-phase dependencies & sequencing

- **Backbone before everything** — orchestration/ML/analytics consume events that don't exist yet ([ADR-0001](adr/0001-event-driven-kafka-backbone.md)).
- **ACL before replacement** — TMS/WMS are wrapped (Phase 1), not replaced; replacement is optional later via strangler-fig ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)).
- **Canonical model before intelligence** — models need clean, canonical events ([ADR-0009](adr/0009-canonical-model-as-moat.md)).
- **Orchestration before optimization** — recommendations need a decision path to apply them ([orchestration/](orchestration/README.md)).
- Phases overlap at the edges; each phase is **capability-complete**, not calendar-fixed.

## Funding & governance

- **Phase 1** is infrastructure investment (backbone, ACLs) — value is the *visible* control-tower MVP shipped within quarter 1.
- **Phase 2** is where ROI starts (promise adherence, exception MTTR, ETA) — fund as a product with KPIs, not a project.
- **Phase 3** is where the moat compounds (margin, cost/shipment, simulation) — protect it; it is the reason to build vs buy ([ADR-0005](adr/0005-buy-execution-build-orchestration.md)).
- **Governance:** architecture guild owns canonical model + ADRs; every bought component requires an ACL, an ownership statement, and a sync/async posture before purchase ([integration-architecture.md §9](integration-architecture.md)).
