# Build-vs-Buy Catalog (component-level)

> Status: Accepted · Related: [build-vs-buy.md](build-vs-buy.md) · [ADR-0005](adr/0005-buy-execution-build-orchestration.md) · [ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md) · [integration-architecture.md](integration-architecture.md) · [services.md](services.md)

Component-level recommendations for a **3PL/4PL with $500M–$5B annual logistics spend**. At this scale you have the budget and volume to buy best-of-breed execution and to justify building the differentiating layer — but not so much that re-implementing commodity infrastructure pays off (see [build-vs-buy.md §3](build-vs-buy.md)).

Legend: **Build** · **Buy** · **Buy + extend** (buy the core, own the differentiated edges). Vendor lists are **illustrative and time-sensitive** (current at time of writing) — re-run the [rubric](build-vs-buy.md#3-how-to-decide-scoring-rubric) each procurement cycle; names are not endorsements.

## Summary

| # | Component | Recommendation | One-line why |
|---|---|---|---|
| 1 | TMS | **Buy + extend** | Buy planning/execution core; build load-building/consolidation + orchestration |
| 2 | WMS | **Buy + extend** | Undifferentiated unless your automation is novel; wrap with ACL |
| 3 | YMS | **Buy** | Small, configurable domain; no moat |
| 4 | OMS | **Buy + extend** | Buy capture/order mgmt; build sourcing/ATP/promise |
| 5 | Route optimization engine | **Buy solver, build model** | Solver math is commodity; constraints/policy/inputs are the moat |
| 6 | Predictive ETA | **Build** (optionally blend) | Your movement data is the moat |
| 7 | Carrier marketplace | **Buy / blend** | Liquidity + network effect can't be built |
| 8 | Customer tracking portal | **Buy + extend** | Branded window on your canonical visibility |
| 9 | Control tower | **Build** | 4PL value-add and the primary differentiator |
| 10 | Event streaming platform | **Buy** | Ops burden, no differentiation |
| 11 | Data warehouse/lake | **Buy** | Undifferentiated ops; managed beats self-run at this scale |
| 12 | Identity & access | **Buy** | Security/regulated; never a differentiator |
| 13 | Workflow engine | **Buy engine, build flows** | Engine is commodity; process is proprietary |

Cross-cutting: every bought component is wrapped by an **anti-corruption layer** and reached only through the bus ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)); no shared databases, no distributed transactions ([ADR-0004](adr/0004-saga-over-distributed-tx.md)).

---

## 1. TMS

**Recommendation: Buy + extend.** Buy the planning/execution core (rating, tendering, dispatch, tracking intake) from a mature vendor; **build** load-building/consolidation, cross-dock/sourcing decisions, and the orchestration that ties them to ATP and the control tower — that logic encodes your network and is the [moat](adr/0009-canonical-model-as-moat.md).

**Rationale.** TMS commodity features (rate shopping, tender, track) are decades-mature; building them is wasted runway. But consolidation and multimodal sourcing rules are firm-specific and directly margin-driving, so they belong to you ([services.md §L3](services.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Oracle Transportation Management (OTM) | Deep, global, multimodal; strong optimization | Heavy, costly, complex to customize |
| SAP TM | Tight ERP integration if you run SAP | Best value only inside SAP stack |
| Blue Yonder TMS | Strong planning/execution + network design | Consolidation needs config; license cost |

**Key integration risk.** The vendor's shipment/route model leaks into orchestration and analytics, and vendor "recommendations" compete with your L4 decisions. **Mitigation:** strict ACL to canonical `Shipment`/`RouteStop`; the TMS executes your commands and emits canonical events — it does not own cross-domain promises ([integration-architecture.md](integration-architecture.md)).

## 2. WMS

**Recommendation: Buy + extend.** Buy unless warehouse automation is genuinely differentiating. Standard receiving/putaway/pick/pack/cycle-count is commoditized; your novelty (if any) is in automation orchestration, not the WMS core.

**Rationale.** WMS is operationally critical but standardized; vendors absorb regulatory and integration churn (scanners, printers, ASRS/AMR, WCS). At $500M–$5B a proven WMS de-risks peak season. Extend at the edges (slotting/placement is L4, automation orchestration is WCS-facing).

| Vendor | Strength | Watch-out |
|---|---|---|
| Manhattan Associates WMS | Enterprise-scale, automation-friendly | Cost, integration effort |
| Blue Yonder WMS | Strong across network sizes | Customization can be pricey |
| SAP EWM | Best with SAP ECC/S4 | Requires SAP-centric shop |

**Key integration risk.** Tight WCS/automation coupling and vendor-inventory-model divergence from canonical inventory; also real-time event quality. **Mitigation:** WMS owns physical inventory, wrapped by ACL; WCS↔WMS via the low-latency handshake in [integration-architecture.md §4.4](integration-architecture.md); scans are ground truth ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)).

## 3. YMS

**Recommendation: Buy.** Small, well-bounded, configurable domain with no differentiated math.

**Rationale.** Gate/dock/spot/move management is a feature of the TMS/WMS or a niche product; building it yourself is pure cost. Integrate with dock scheduling and yard automation.

| Vendor | Strength | Watch-out |
|---|---|---|
| C3 Solutions YMS | Focused, configurable yard/dock | Narrower ecosystem |
| Manhattan YMS | Bundled with WMS/TMS | Value depends on existing footprint |
| Kaleris (Navis) YMS | Strong for ports/terminals/asset yards | Terminal-centric heritage |

**Key integration risk.** Appointment/dock data fragmentation and inconsistent trailer identity across YMS, TMS, WMS. **Mitigation:** canonical `Appointment`/`YardMove` + MDM `Location`; normalize via ACL; publish `yms.*` events.

## 4. OMS

**Recommendation: Buy + extend.** Buy order capture and order management for channel breadth (Web/e-com/marketplace/B2B/EDI); **build** sourcing/ATP/promise — the promise is a cross-domain differentiator and the L5 moat.

**Rationale.** Order capture, returns, and channel orchestration are broad and fast-moving; vendors keep pace with channels you can't. But availability-to-promise across nodes/inventory/partners is your operating model ([ADR-0005](adr/0005-buy-execution-build-orchestration.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| IBM Sterling Order Management | Enterprise, omnichannel, deep rules | Weight and license cost |
| Manhattan OMS | Strong with Manhattan suite | Best value if already invested |
| Fluent Commerce | Cloud-native, flexible fulfillment orchestration | Smaller enterprise footprint |

**Key integration risk.** Two sources of truth for order/fulfillment state (vendor OMS vs your `ord.*`/orchestration) and double-promising. **Mitigation:** explicit [ownership](integration-architecture.md#6-data-ownership-system-of-record); vendor OMS owns raw capture, you own canonical order lifecycle once accepted; all promises go through ATP.

## 5. Route optimization engine

**Recommendation: Buy the solver, build the model.** Buy a commercial/open solver (MIP/VRP metaheuristic); **build** the constraint model, objective weighting, feature inputs, and orchestration around it.

**Rationale.** The optimization *math* is a commodity; the **value is in the constraints and policy** (HOS, hazmat, time windows, customer rules, cost/emission weights) and the embeddings (predicted travel times, carrier behavior). This is exactly the L4 design ([route-optimization.md](intelligence/route-optimization.md)). Buying a "solved" turnkey product bakes in a generic algorithm and gives away the edge ([ADR-0005](adr/0005-buy-execution-build-orchestration.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Gurobi Optimizer | Best-in-class MIP performance/bounds | License cost; you own the model |
| IBM CPLEX | Mature, enterprise support | Similar cost profile |
| Google OR-tools | Free, strong VRP/CP-SAT, fast to start | You own tuning/scale; support is community |

Routing SaaS (e.g., Descartes, Routific, Optrak) is a viable *baseline* for simple last-mile, but check it doesn't own your policy layer.

**Key integration risk.** Solver service becomes a synchronous bottleneck / vendor lock-in of the model, or latency breaks the decision loop. **Mitigation:** solver is pluggable behind a service; outputs are solver-agnostic `Recommendation`s; sync API + async batch with latency SLOs ([intelligence/README.md §3](intelligence/README.md)).

## 6. Predictive ETA

**Recommendation: Build** (optionally **blend** a vendor feed for cold-start lanes).

**Rationale.** ETA accuracy is directly your data and your moat: your lanes, drivers, carriers, facilities, customers. Vendor ETA (visibility networks) is great for *their* aggregated network and carrier scans but is generic for your operations; you can blend it as a feature. Proprietary ETA powers SLA triggers, re-opt, and customer experience ([predictive-eta.md](intelligence/predictive-eta.md)).

| Vendor (blend/benchmark) | Strength | Watch-out |
|---|---|---|
| project44 | Broad carrier network, movement data | Generic to your network; cost |
| FourKites | Strong carrier integrations + predictions | Coverage/fit varies by lane |
| Shippeo | Strong in EU/road | Region-weighted coverage |

**Key integration risk.** Feature dependency on a vendor's aggregated data creates training/serving skew and lock-in; radar/telemetry gaps degrade tail accuracy. **Mitigation:** own the feature store and model; treat vendor ETA as an input feature with `featuresHash`; monitor drift ([ADR-0014](adr/0014-optimization-and-inference-architecture.md)).

## 7. Carrier marketplace

**Recommendation: Buy / blend.** Participate in external digital freight marketplaces for spot capacity; **build** only a *private* capacity/procurement layer for your contracted network.

**Rationale.** Marketplace **liquidity is a network effect** you cannot build — the value is the pool of carriers/loads. At this spend tier, marketplaces give surge and lane coverage; your private layer encodes negotiated capacity and rules. Don't try to out-marketplace an incumbent.

| Vendor | Strength | Watch-out |
|---|---|---|
| Uber Freight | Large network, brokerage + TMS platform | Rate volatility; margin share |
| Loadsmart | Digital brokerage, automation | Lane coverage varies |
| Echo Global Logistics | Broad 3PL/brokerage network | Broker-style service variability |

(Analogs: C.H. Robinson/Navisphere, Coyote Logistics, Flexport for managed capacity.)

**Key integration risk.** Tender/broker workflows bypass your orchestration; opaque pricing and carrier substitution break scorecards and SLA visibility; and a marketplace can become a competitor. **Mitigation:** all tenders issued as your commands; brokers/carriers report into canonical milestones; scorecard data captured regardless of source ([integration-architecture.md §4.3](integration-architecture.md)).

## 8. Customer tracking portal

**Recommendation: Buy + extend.** Buy or license a branded tracking/notification experience, fed by **your** canonical visibility; extend with your branding, SLAs, and APIs — don't build the front end from scratch unless it's a core product surface.

**Rationale.** The *experience* is commoditized (tracking page, notifications, EDD) and vendor-managed (hosting, deliverability, channels). The *truth* (canonical milestones/ETA) is yours ([integration-architecture.md §4.6](integration-architecture.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| project44 (visibility + portal) | Carrier network + buy-side UX | Tie-in to their data model |
| Narvar | Post-purchase/returns CX, brand fit | Retail-centric |
| AfterShip | Tracking API/notifications, fast to launch | Less 3PL enterprise depth |

**Key integration risk.** The portal becomes a second, inconsistent source of tracking truth (vendor vs canonical), causing customer-facing disputes and privacy/PII exposure. **Mitigation:** you remain SoR for milestones/ETA; the portal is a **read/push consumer** (REST + HMAC webhooks + WS/SSE); change-tracker keys expire at POD.

## 9. Control tower

**Recommendation: Build.** Keep it — it's the operator cockpit, the 4PL value-add, and where decisions, exceptions, and policy converge ([analytics/control-tower.md](analytics/control-tower.md)).

**Rationale.** A bought control tower imposes a generic workflow and metric model; the differentiator is *your* exception cascades, autonomy policy, and one-click actions on *your* canonical state ([orchestration/](orchestration/README.md), [ADR-0005](adr/0005-buy-execution-build-orchestration.md)). Consume vendor visibility as **input data**, not as the tower.

| Vendor (data source, not tower) | Strength | Watch-out |
|---|---|---|
| project44 | Network-wide tracking signals | Can't model your policies/actions |
| FourKites | Live tracking + predictive | Same; integration vs build |
| Blue Yonder Control Tower | Supply-chain control tower suite | Generic workflows; heavy fit |

**Key integration risk.** Building on a vendor's tower (or letting it own exceptions) surrenders the decision/policy layer and creates metric drift between tower and canonical state. **Mitigation:** tower is a read-mostly projection over your bus; alerts correlate (not duplicate) orchestration triggers; one metric definition in the semantic layer ([ADR-0017](adr/0017-analytics-tiering-streaming-first.md)).

## 10. Event streaming platform

**Recommendation: Buy.** Managed Kafka (or a managed Kafka-compatible). The backbone is undifferentiated infrastructure with real operational pain.

**Rationale.** Self-running Kafka at multi-region, multi-tenant scale is a specialist burden with no moat; managed services absorb upgrades, rebalancing, tiered storage, and SLAs ([architecture.md §7](architecture.md), [ADR-0001](adr/0001-event-driven-kafka-backbone.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Confluent Cloud | Full Kafka ecosystem, connectors, governance | Cost at high volume |
| Amazon MSK (+ MSK Connect) | Native AWS integration, tiered storage | More self-management than Confluent |
| Redpanda Cloud | Kafka API, low latency, simpler ops | Smaller ecosystem; validate tooling fit |

**Key integration risk.** Avoiding vendor-specific extensions that break portability, and **schema/tenancy discipline** across topics. **Mitigation:** Schema Registry + backward-compat CI; partition key = aggregate id; tenant isolation ([ADR-0008](adr/0008-schema-registry-and-versioning.md), [ADR-0006](adr/0006-multi-tenant-isolation-model.md)).

## 11. Data warehouse / lake

**Recommendation: Buy.** Managed lakehouse; self-hosting storage/compute is not a differentiator.

**Rationale.** Your data *model* is the moat, not the storage engine. Managed platforms give elasticity, governance, and cost controls; analytics pipelines build on top ([analytics/README.md](analytics/README.md), [ADR-0017](adr/0017-analytics-tiering-streaming-first.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Snowflake | Separation of storage/compute, governance, ecosystem | Cost governance needed |
| Databricks (Lakehouse) | Spark + Delta + ML in one place | Complexity; cost |
| Google BigQuery | Serverless, strong analytics + BQML | GCP affinity; some governance gaps |

**Key integration risk.** Reverse-ETL/streaming back into operational systems (violating SoR boundaries) and metric divergence across engines. **Mitigation:** gold-layer BI only; one semantic layer; reverse-ETL to ops tools, never into transactional state ([integrations.md §8](integrations.md)).

## 12. Identity & access

**Recommendation: Buy.** IAM is regulated, security-critical, and standardized — never a differentiator.

**Rationale.** OIDC/OAuth2, MFA, SSO, lifecycle, and federation are solved by specialists; building it invites security incidents and audit findings ([cross-cutting.md §4](cross-cutting.md), [ADR-0006](adr/0006-multi-tenant-isolation-model.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Okta | Broad enterprise SSO/MFA/Customer IAM | Cost; auth outages are high-impact |
| Microsoft Entra ID | Best with Microsoft estate | Entra-centric if Microsoft-heavy |
| Auth0 (Okta CUS) | Fast for customer/partner identity | Now Okta-owned; pricing |

**Key integration risk.** Entangling **tenant identity** with **authorization** so tenant-scoping leaks, and coupling the gateway so an auth outage halts operations. **Mitigation:** IAM only authenticates; authorization/tenant scoping enforced in the gateway and per-row policy; short-lived tokens; degrade gracefully ([ADR-0006](adr/0006-multi-tenant-isolation-model.md)).

## 13. Workflow engine

**Recommendation: Buy the engine, build the flows.** Adopt Temporal/Camunda/Step Functions as the durable runtime; own the saga definitions, compensating actions, and policy — the flows *are* the moat ([orchestration/workflow-engine.md](orchestration/workflow-engine.md)).

**Rationale.** Durable execution, retries, timers, and state persistence are hard to get right and undifferentiated; your order-to-cash/returns/exception flows are proprietary ([ADR-0004](adr/0004-saga-over-distributed-tx.md), [ADR-0011](adr/0011-orchestration-saga-statechart-engine.md)).

| Vendor | Strength | Watch-out |
|---|---|---|
| Temporal | Code-first durable execution, strong ecosystem | Operate workers; self-host or cloud |
| Camunda (Zeebe) | Visual BPMN, business-friendly modeling | BPMN modeling overhead; clustering |
| AWS Step Functions | Serverless, low ops on AWS | AWS-coupled; complex state can get unwieldy |

**Key integration risk.** The engine's workflow state becomes a shadow source of truth, and long-running workflows couple to vendor semantics; or flows fork per tenant. **Mitigation:** engine holds orchestration state only; domain truth stays in owning services; tenant logic is policy/configuration, never forked code ([build-vs-buy.md §4](build-vs-buy.md)).

---

## Cross-cutting integration risks (apply to every bought component)

| Risk | Why it bites at $500M–$5B | Mitigation |
|---|---|---|
| **Vendor model leakage** | Vendor schemas seep into L2/L5/L7, blocking replacement | ACL + canonical events ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)) |
| **Shadow sources of truth** | Bought apps and you both "own" order/shipment/inventory | Explicit ownership + no direct DB access ([integration-architecture.md §6](integration-architecture.md)) |
| **Distributed transactions** | Multi-vendor flows need atomicity that doesn't exist | Sagas + idempotent commands ([ADR-0004](adr/0004-saga-over-distributed-tx.md)) |
| **Vendor rate limits / outages** | A slow carrier/ERP mars a lane or close | Bulkheads, circuit breakers, backoff ([integration-architecture.md §3](integration-architecture.md)) |
| **Lock-in via proprietary extensions** | Migration cost balloons; strategy captured | Prefer open standards (Avro, Kafka API, OIDC, OTel) |
| **Per-tenant forks** | Customer-specific code branches ossify the platform | Configuration/policy, never forks ([build-vs-buy.md §4](build-vs-buy.md)) |

## How to run a component decision

1. Score the component on the [six criteria](build-vs-buy.md#3-how-to-decide-scoring-rubric).
2. Confirm the [governed default](build-vs-buy.md#2-governed-defaults); deviate only with an ADR.
3. Require an **anti-corruption adapter**, an **ownership statement**, and a **synchronous/async posture** before purchase ([integration-architecture.md §9](integration-architecture.md)).
4. Prefer **two viable vendors** in the shortlist to preserve negotiation leverage and exit options.
