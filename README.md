# Logistics OS

Architecture documentation for a cloud-native, event-driven **Logistics Operating System** for mid-to-large **3PL/4PL** operators serving e-commerce and B2B. It unifies **TMS, WMS, YMS, OMS, carrier visibility, and billing** into one platform on a **Kafka** event backbone with microservices, and layers orchestration, intelligence, experience, and analytics on top.

> Docs-first repository: the canonical architecture and contracts live in `docs/`; a **Phase-1
> buildable scaffold** (Gradle/Java 21 + Spring Boot, event backbone, ACLs, read models, MDM,
> control-tower, BFFs) is implemented under `platform/`·`core/`·`tools/` with ops wiring under
> `ops/`. See [ops/README.md](ops/README.md) to run it.

## Start here

1. **[docs/architecture.md](docs/architecture.md)** — the 7-layer architecture (observation → data → execution → intelligence → orchestration → experience → analytics), layer responsibilities, and sources of truth.
2. **[docs/data-model.md](docs/data-model.md)** — canonical data objects and ownership.
3. **[docs/events.md](docs/events.md)** — event envelope, topic catalog, delivery semantics, DLQ/replay.
4. **[docs/schemas/](docs/schemas/README.md)** — canonical Avro/Protobuf event schemas (Shipment lifecycle, Vehicle/Location/Carrier/Customer) + exactly-once & idempotency contract.
5. **[docs/services.md](docs/services.md)** — bounded-context service catalog.
6. **[docs/integrations.md](docs/integrations.md)** — integration pattern per layer + anti-corruption rules.
7. **[docs/integration-architecture.md](docs/integration-architecture.md)** — per-system integration design (ERP, OMS, carrier APIs, WCS/automation, mobile, customer tracking): sync/async, contract style, rate limiting, retry/idempotency, system of record + order→POD→settlement sequence.
8. **[docs/build-vs-buy.md](docs/build-vs-buy.md)** — decision matrix and governed defaults; **[docs/build-vs-buy-catalog.md](docs/build-vs-buy-catalog.md)** — component-level recommendations (vendors + integration risk).
9. **[docs/cross-cutting.md](docs/cross-cutting.md)** — tenancy, consistency, security, observability, resilience.
10. **[docs/orchestration/](docs/orchestration/README.md)** — control-tower orchestration: workflow engine, trigger catalog, decision context, escalation, timed exception flow.
11. **[docs/intelligence/](docs/intelligence/README.md)** — L4 optimization & prediction: route optimization, predictive ETA, carrier selection, network simulation + the advisory contract with orchestration.
12. **[docs/analytics/](docs/analytics/README.md)** — L6/L7 control tower & analytics: real-time dashboard, alerts, one-click actions, shipment cost attribution, carrier scorecards, network what-if + the event→store pipeline tiers.
13. **[docs/roadmap.md](docs/roadmap.md)** — 3-phase implementation roadmap (scope, deliverables, team, exit KPIs, risks) from a disconnected TMS/WMS baseline.
14. **[docs/nfr.md](docs/nfr.md)** — non-functional requirements: availability, latency budgets, retention, scalability, security/sovereignty, DR (RPO/RTO), observability (P0/P1/P2).
15. **[docs/orchestration/autonomous-agents.md](docs/orchestration/autonomous-agents.md)** — how rule/LLM-based agents decide: automated vs. human-in-the-loop archetypes, context window, guardrails & rollback, performance-vs-baseline measurement, worked warehouse-agent trace.

## Phase-1 buildable scaffold

Docs-first aside, the repo now contains a buildable Phase-1 scaffold (Java 21 + Gradle + Spring
Boot) implementing the event backbone slice of the architecture:

```
platform/  event-envelope · kafka-commons · outbox · idempotency · tenant · observability
core/      ingest-gateway · acl-tms · acl-wms · mdm · read-model-builder · control-tower · bff-ops · phase2-stubs/orchestrator
tools/     legacy-simulator          ops/    local-compose · terraform · k8s           .github/  CI
contracts/ avro schemas · registry · validation · baselines
```

**Run it** (see [ops/local-compose/README.md](ops/local-compose/README.md) and
[docs/engineering-build.md](docs/engineering-build.md)):

```bash
docker compose -f ops/local-compose/docker-compose.yml up -d
docker compose -f ops/local-compose/docker-compose.yml run --rm topic-bootstrap
gradle :core:mdm:bootRun        # + read-model-builder, control-tower, bff-ops, legacy-simulator
```

CI gates (`.github/workflows/ci.yml`): `validateSchemas` → `reconcile.sh` → baseline drift check →
full `gradle build` (unit + embedded-Kafka ITs).

## Documentation map

```
README.md
docs/
├─ engineering-build.md        # build/test/run/container/deploy runbook (Phase-1 scaffold)
├─ release-plan.md             # 0.1.0 scope, DoD, milestones, risks, 0.2.0 backlog
├─ release-plan-0.2.0.md       # 0.2.0 scope (images + k8s smoke + MSK + wrapper), DoD, milestones, risks
├─ architecture.md            # layered architecture + sources of truth
├─ data-model.md              # canonical objects, ownership, lifecycles
├─ events.md                  # envelope, topics, delivery, replay
├─ services.md                # bounded-context catalog
├─ integrations.md            # per-layer integration + ACL rules
├─ integration-architecture.md # per-system integration design + ownership
├─ build-vs-buy.md            # decision matrix + rubric
├─ build-vs-buy-catalog.md    # per-component build/buy + vendors + integration risk
├─ roadmap.md                 # 3-phase implementation roadmap (scope/team/KPIs/risks)
├─ nfr.md                     # availability, latency, retention, scale, security, DR, observability
├─ cross-cutting.md           # tenancy, consistency, security, ops
├─ schemas/                   # canonical Avro/Protobuf contracts
│  ├─ README.md               # schema index
│  ├─ common-types.md         # envelope, shared types, exactly-once/idempotency
│  ├─ shipment-events.md      # shipment lifecycle events
│  ├─ reference-data.md       # Customer/Carrier/Vehicle/Location
│  ├─ orchestration-events.md # triggers, decision context, escalations, commands
│  ├─ intelligence-events.md  # predictions, recommendations, model, simulation, feedback
│  └─ analytics-events.md     # operational alerts, cost attribution, carrier scorecards
├─ orchestration/             # L5 orchestration design
│  ├─ README.md               # component model + responsibilities
│  ├─ workflow-engine.md      # durable saga + statechart engine
│  ├─ trigger-catalog.md      # trigger conditions + re-opt triggers + coalescing
│  ├─ decision-context.md     # decision context object + policy routing
│  ├─ autonomous-agents.md    # agents: what may automate, context window, guardrails
│  ├─ escalation.md           # autonomy tiers, escalation ladder, SLA timers
│  ├─ exception-flow.md       # end-to-end timed late-inbound cascade
│  └─ diagrams/               # state machine, pipeline, cascade sequence, routing
├─ intelligence/              # L4 optimization & prediction design
│  ├─ README.md               # serving modes, orchestration contract, SLAs
│  ├─ route-optimization.md   # VRP/load building, constraints, re-opt cadence
│  ├─ predictive-eta.md       # quantile ETA model, features, drift, serving
│  ├─ carrier-selection.md    # acceptance/cost/risk scoring + allocation
│  ├─ network-simulation.md   # digital-twin what-if + calibration
│  └─ diagrams/               # pipeline, ETA serving, carrier scoring, sim loop
├─ analytics/                 # L6/L7 control tower & analytics design
│  ├─ README.md               # pipeline tiers + latency budgets
│  ├─ control-tower.md        # dashboard, alerts, one-click actions
│  ├─ cost-attribution.md     # fixed + variable cost per shipment, real-time
│  ├─ carrier-scorecards.md   # dimensions, sources, re-weighting
│  ├─ network-simulation.md   # what-if inputs/outputs (analytics view)
│  └─ diagrams/               # analytics pipeline, cost attribution, scorecard
├─ diagrams/
│  ├─ layers.mmd              # layered architecture (Mermaid)
│  ├─ event-flow.mmd          # order-to-cash sequence (Mermaid)
│  ├─ order-pod-settlement.mmd # order → POD → settlement, all touchpoints (Mermaid)
│  ├─ roadmap.mmd             # 3-phase implementation Gantt (Mermaid)
│  └─ context.mmd             # system context (Mermaid)
└─ adr/
   ├─ 0000-adr-index.md
   ├─ 0001-event-driven-kafka-backbone.md
   ├─ 0002-microservices-bounded-contexts.md
   ├─ 0003-cqrs-read-models.md
   ├─ 0004-saga-over-distributed-tx.md
   ├─ 0005-buy-execution-build-orchestration.md
   ├─ 0006-multi-tenant-isolation-model.md
   ├─ 0007-outbox-and-idempotency.md
   ├─ 0008-schema-registry-and-versioning.md
   ├─ 0009-canonical-model-as-moat.md
   ├─ 0010-cots-strangler-and-anti-corruption.md
   ├─ 0011-orchestration-saga-statechart-engine.md
   ├─ 0012-policy-based-human-in-the-loop.md
   ├─ 0013-exception-cascade-and-reopt-coalescing.md
   ├─ 0014-optimization-and-inference-architecture.md
   ├─ 0015-digital-twin-network-simulation.md
   ├─ 0016-external-integration-contract.md
   ├─ 0017-analytics-tiering-streaming-first.md
   └─ 0018-autonomous-agent-architecture.md
```

## Architecture at a glance

```
7. Analytics/AI   lakehouse · semantic layer · ML · digital twin
6. Experience     customer portal · ops console · mobile · partner/API portal
5. Orchestration  saga engine · control tower · ATP/promise · routing · exceptions · policy
4. Intelligence   forecasting · predictive ETA · slotting · placement · optimization
3. Execution      OMS | TMS | WMS | YMS | Visibility | Billing
2. Data           Kafka backbone · event store · canonical models · CQRS views · MDM
1. Observation    IoT/telematics · GPS · scanners · cameras · EDI/API feeds · edge
```

Diagram source: [`docs/diagrams/layers.mmd`](docs/diagrams/layers.mmd). Flow: [`docs/diagrams/event-flow.mmd`](docs/diagrams/event-flow.mmd).

## Core principles

- **Orchestration and data are the moat**; execution apps are replaceable bounded contexts.
- **Everything is an event** on a Kafka backbone; canonical state is a projection.
- **Buy execution, build orchestration** ([ADR-0005](docs/adr/0005-buy-execution-build-orchestration.md)).
- **Sagas, not distributed transactions** ([ADR-0004](docs/adr/0004-saga-over-distributed-tx.md)).
- **Outbox + idempotency** for every write ([ADR-0007](docs/adr/0007-outbox-and-idempotency.md)).
- **Canonical model, anti-corruption layers** for every COTS/external system ([ADR-0009](docs/adr/0009-canonical-model-as-moat.md), [ADR-0010](docs/adr/0010-cots-strangler-and-anti-corruption.md)).

## Rendering the diagrams

The `.mmd` files are [Mermaid](https://mermaid.js.org/) sources and render on GitHub/GitLab. To export images:

```bash
npm i -g @mermaid-js/mermaid-cli
mmdc -i docs/diagrams/layers.mmd     -o docs/diagrams/layers.svg
mmdc -i docs/diagrams/event-flow.mmd -o docs/diagrams/event-flow.svg
mmdc -i docs/diagrams/order-pod-settlement.mmd -o docs/diagrams/order-pod-settlement.svg
mmdc -i docs/diagrams/roadmap.mmd    -o docs/diagrams/roadmap.svg
mmdc -i docs/diagrams/context.mmd    -o docs/diagrams/context.svg
```

Layer-specific diagram folders (`docs/orchestration/diagrams/`, `docs/intelligence/diagrams/`, `docs/analytics/diagrams/`) follow the same `mmdc -i … -o …` usage.

## Conventions

- Markdown + Mermaid; ADRs follow Michael Nygard's format and are immutable once `Accepted`.
- Topic naming: `<layer>.<domain>.<event-type>.vN`; partition key = aggregate id.
