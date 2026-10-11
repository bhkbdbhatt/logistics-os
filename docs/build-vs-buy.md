# Build vs. Buy Decision Matrix

> Status: Accepted · Related: [architecture.md](architecture.md) · [services.md](services.md) · [ADR-0005](adr/0005-buy-execution-build-orchestration.md) · [build-vs-buy-catalog.md](build-vs-buy-catalog.md)

**Rule of thumb:** *Buy* commodity, regulated, and fast-moving-standards components. *Build* the domains that encode your operating model, your network, or your data moat.

> Component-level recommendations — with rationale, top-3 vendor options, and the key integration risk for TMS, WMS, YMS, OMS, route optimization, predictive ETA, carrier marketplace, tracking portal, control tower, event streaming, lakehouse, IAM, and workflow engine — are in [build-vs-buy-catalog.md](build-vs-buy-catalog.md).

## 1. Decision matrix

Legend: ● primary option · ○ secondary/viable alternative.

| Component | Build | Buy | Decide by | Recommended default |
|---|:--:|:--:|---|---|
| **Event backbone (Kafka)** | ○ | ● | Ops maturity vs. internals control | **Buy** managed Kafka (MSK/Confluent/Redpanda) |
| **EDI / carrier API translation** | ○ | ● | Rarely a differentiator | **Buy** (Cleo/Orderful/SPS) |
| **WMS core** | ● | ● | Highly unstandardized ops vs. speed | **Buy** (Manhattan/Blue Yonder/Logiwa) unless novel automation demands build |
| **TMS core (planning/execution)** | ● | ● | Network complexity is the moat | **Buy** baseline; **build** load-building/consolidation on top |
| **YMS** | ● | ● | Small, configurable domain | **Buy** |
| **OMS** | ● | ● | Channel breadth + promise logic | **Buy** capture; **build** sourcing/ATP |
| **Carrier visibility (milestones)** | ○ | ● | Network effect needs many carriers | **Buy** project44/FourKites |
| **Predictive ETA** | ● | ○ | Your data is the moat | **Build** (optionally blend vendor) |
| **Route / inventory optimization** | ● | ● | Solver math commodity; inputs/policy are not | **Buy** solver (Gurobi/OR-tools), **build** model + orchestration |
| **ATP / promise engine** | ● | ○ | Core differentiator | **Build** |
| **Orchestration / workflow engine** | ● | ● | Engine commodity, flows proprietary | **Buy** engine (Temporal), **build** flows |
| **Control tower / exception mgmt** | ● | ● | 4PL value-add | **Build** |
| **Billing / freight audit** | ○ | ● | Tied to accounting of record | **Buy** (or build if unique customer contracts) |
| **Data platform / lakehouse** | ○ | ● | Undifferentiated ops burden | **Buy** (Snowflake/Databricks/Fabric) |
| **MDM** | ● | ● | Few vendors, high fit cost | **Buy** or light **build** on a golden-record service |
| **Identity / IAM** | ○ | ● | Security is not a differentiator | **Buy** (Okta/Entra) |
| **Observability** | ○ | ● | — | **Buy** OTel + Datadog/Grafana |
| **Multi-tenant isolation** | ● | ○ | Architectural + pervasive | **Build** |
| **Feature store** | ○ | ● | — | **Buy** (Feast/Tecton) |
| **Rules / decisioning** | ● | ● | Tenant rules = configuration | **Buy** engine (OPA/Drools), **build** authoring UX |

## 2. Governed defaults

- **Always buy:** event backbone, EDI translation, IAM, observability, lakehouse, visibility network, commodity solvers, feature store.
- **Always build:** orchestration flows, ATP/promise, control tower, multi-tenant policy/isolation, tenant onboarding.
- **Buy-then-extend:** WMS/TMS/OMS/YMS — adopt COTS execution, wrap with anti-corruption adapters, and **never** let vendor models leak into L2/L5/L7.

## 3. How to decide (scoring rubric)

Score each component 1–5 on:

| Criterion | Buy-leaning | Build-leaning |
|---|---|---|
| **Differentiation** | Commodity / table stakes | Encodes your operating model / network / data |
| **Rate of change** | Fast standards (EDI, carriers) | Stable domain you can evolve deliberately |
| **Regulatory exposure** | High (tax, audit, IAM) | Low |
| **Integration fit** | Good APIs/standards | Poor fit requiring deep customization |
| **Talent & ops** | Scarce specialty | In-house strength |
| **Time-to-value** | Needed now | Long-term strategic |

**Decision rule:** if a component is high-differentiation **and** either low-regulatory or strategically central, **build**. If commodity/regulated/fast-moving, **buy**. If mixed, **buy the core and extend at the edges**.

## 4. Anti-patterns to avoid

- **Build the commodity** (custom Kafka, custom IAM) — burns runway for no moat.
- **Buy the moat** (turnkey ATP/routing for a 4PL) — you inherit a generic algorithm and lose differentiation.
- **Fork on tenant** — 4PL customer-specific logic must be **configuration** (policy/rules), not code branches.
- **Direct COTS DB access** — always wrap with an ACL ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)).
- **Distributed transactions across bought apps** — use sagas ([ADR-0004](adr/0004-saga-over-distributed-tx.md)).
