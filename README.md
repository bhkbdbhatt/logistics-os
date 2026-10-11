# Logistics OS

Cloud-native, event-driven **Logistics Operating System** for mid-to-large **3PL/4PL** operators serving
e-commerce and B2B. It unifies **TMS, WMS, YMS, OMS, carrier visibility, and billing** on a **Kafka**
event backbone with microservices, and layers orchestration, intelligence, experience, and analytics on top.

This repository is a working **monorepo**, not just a design document: a buildable **Phase-1 scaffold**
(Java 21 + Gradle 8.12.1 + Spring Boot 3.4.1) implementing the event backbone, anti-corruption layers,
read models, MDM, control-tower and BFFs, plus a **React + TypeScript ops console**. The reference
architecture, data model, event contracts and ADRs live under [`docs/`](docs/architecture.md).

> **Status:** `0.1.0` scaffold → `0.2.0` distributable (images + kind smoke + MSK) → `0.3.0`
> "Experience, identity & persistence" (draft). See [docs/releases/0.3.0.md](docs/releases/0.3.0.md)
> and the plans in [docs/release-plan-0.3.0.md](docs/release-plan-0.3.0.md).

## Repository layout

```
platform/  event-envelope · kafka-commons · outbox · idempotency · tenant · observability · entitlement · persistence · security
core/      ingest-gateway · acl-tms · acl-wms · mdm · read-model-builder · control-tower · bff-ops · phase2-stubs/orchestrator
web/       React + TypeScript + Vite ops console (fronts the platform through bff-ops)
tools/     legacy-simulator        contracts/ avro schemas · registry · validation · baselines
editions/  sellable SKU catalog (standard · professional · enterprise) + versions
ops/       local-compose · terraform (AWS MSK) · k8s (+ overlays) · containers · kind
.github/   CI · images · smoke · release · web
```

## What's implemented

### Event backbone & platform libraries

| Module | Role |
|--------|------|
| `platform:event-envelope` | `EventEnvelope`, builder, ULID ids, codec |
| `platform:kafka-commons` | serde, `SchemaResolvers`, `PayloadAvro`, `EnvelopeRouting` |
| `platform:outbox` | outbox port + in-memory repo (transactional publish seam) |
| `platform:idempotency` | dedupe store port (claim / record / release) |
| `platform:tenant` | request-scoped `TenantContext` |
| `platform:observability` | OpenTelemetry wiring helpers |
| `platform:entitlement` | edition feature flags + licensing seam (soft enforcement) |
| `platform:persistence` | `JdbcOutboxRepository`, `JdbcDedupeStore`, shared Flyway migrations (`V900+`) |
| `platform:security` | stateless OAuth2 resource server (`oidc`/`local` modes) + tenant filter |

### Core services

| Service | Layer | Port | Role |
|---------|-------|------|------|
| `core:ingest-gateway` | L1 | 8081 | REST ingest → `obs.*` canonical (dedupe) |
| `core:acl-tms` | L1 | 8083 | legacy TMS JSON → `tms.shipment.events.v1` |
| `core:acl-wms` | L1 | 8084 | legacy WMS JSON → obs/wms canonical |
| `core:read-model-builder` | L2 | 8085 | CQRS projections → `shipment/inventory.state.v1` |
| `core:mdm` | L2 | 8086 | golden records → `mdm.*.events.v1` (compacted) |
| `core:control-tower` | L5/L6 | 8087 | live ops, alerts, one-click `orch.command.v1` |
| `core:bff-ops` | L6 | 8088 | compose-only BFF (no domain logic) |
| `core:phase2-stubs:orchestrator` | L5 | 8090 | Phase-2 command stub (`POST /orch/commands`, heartbeat) |
| `tools:legacy-simulator` | — | 8089 | scheduled legacy feed emitter |

### Ops console (`web/`)

React + TypeScript + Vite SPA served by nginx that talks **only** to `bff-ops` (ADR-0022). Surfaces:
dashboard (shipment/alert counts, source health, edition badge), shipment list + detail with the
operator-gated "Notify customer" action, and Alerts. Auth supports `local` (HS256 via `POST /auth/login`)
and `oidc` (Authorization Code + PKCE). See [web/README.md](web/README.md).

### Identity, persistence & outbox (0.3.0)

- **Security** (`platform:security`): `mode=oidc` validates Keycloak JWTs (lazy JWKS); `mode=local`
  (default) issues/validates HS256 dev tokens. `TenantContextFilter` binds the tenant from the
  `tenant_id` claim and rewrites `X-Tenant-Id`. Roles `viewer`/`operator`/`admin`. Wired into
  `mdm`, `ingest-gateway`, `control-tower` and `bff-ops`; `bff-ops` relays the caller's bearer token.
- **Persistence** (`platform:persistence`): JDBC outbox + idempotency stores. **MDM** becomes a
  PostgreSQL system-of-record under the `jdbc` profile — golden records are upserted with optimistic
  versioning and the change event is written to the outbox in the **same transaction**, then dispatched
  by a scheduled relay. Without the profile, MDM keeps the in-memory registry + direct publish.

### Contracts, editions & ops

- `contracts/` — Avro schemas, registry (`kind: event|command|observation`), validation gates and
  baselines; governed by `reconcile.sh` + `snapshot-baselines.sh`.
- `editions/` — sellable SKU catalog (`standard`/`professional`/`enterprise`) + version pins.
- `ops/` — Docker Compose local stack (Kafka + Postgres + Keycloak + web), AWS MSK Terraform, Kustomize
  k8s base + per-customer overlays, container recipes and the `kind` smoke suite. See [ops/README.md](ops/README.md).

## Build & test

The Gradle wrapper is the sole build entrypoint (no system Gradle assumed):

```bash
./gradlew build                                    # compile + unit tests + embedded-Kafka ITs + assemble
./gradlew :contracts:validation:validateSchemas    # schema/registry contract gate
./gradlew :core:mdm:test                           # single module
./gradlew :core:mdm:bootJar                        # build a deployable jar
```

Contract governance:

```bash
bash contracts/scripts/reconcile.sh                    # registry vs docs/schemas drift
bash contracts/scripts/snapshot-baselines.sh           # baseline drift check (WARN-only docs backlog)
bash contracts/scripts/snapshot-baselines.sh --write   # after intentional schema changes
```

Web console:

```bash
cd web
npm install
npm run dev        # http://localhost:5173, proxying /ops,/auth to http://localhost:8088
npm run build      # tsc --noEmit + production build to dist/
```

## Run locally

See [ops/local-compose/README.md](ops/local-compose/README.md) and
[docs/engineering-build.md](docs/engineering-build.md). Short form:

```bash
docker compose -f ops/local-compose/docker-compose.yml up -d
docker compose -f ops/local-compose/docker-compose.yml run --rm topic-bootstrap
./gradlew :core:mdm:bootRun          # + read-model-builder, control-tower, bff-ops, legacy-simulator
```

Postgres backend for MDM (transactional outbox) is enabled with the `jdbc` profile:

```bash
SPRING_PROFILES_ACTIVE=jdbc MDM_DB_URL=jdbc:postgresql://localhost:5432/mdm \
MDM_DB_USER=logistics MDM_DB_PASSWORD=logistics ./gradlew :core:mdm:bootRun
```

Servers are secured in `0.3.0`; fetch a dev token first (`admin`/`admin`, `ops`/`ops`, `viewer`/`viewer`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8088/auth/login \
  -H "Content-Type: application/json" -d '{"username":"ops","password":"ops"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/ops/overview
```

## CI

`.github/workflows/` — `ci.yml` (contract gates → `gradle build` with embedded-Kafka ITs), `web.yml`
(SPA typecheck + build), `images.yml` (service + `web` images to GHCR with OCI provenance/SBOM),
`smoke.yml` (kind E2E on manual/nightly) and `release.yml` (edition build → push → cosign sign).

## Reference architecture (docs)

The canonical design lives under [`docs/`](docs/):

- [docs/architecture.md](docs/architecture.md) — 7-layer architecture + sources of truth
- [docs/data-model.md](docs/data-model.md) · [docs/events.md](docs/events.md) · [docs/schemas/](docs/schemas/README.md) — canonical objects, event envelope/topics, Avro/Protobuf contracts
- [docs/services.md](docs/services.md) · [docs/integrations.md](docs/integrations.md) · [docs/integration-architecture.md](docs/integration-architecture.md) — bounded contexts and integration design
- [docs/cross-cutting.md](docs/cross-cutting.md) · [docs/nfr.md](docs/nfr.md) — tenancy/consistency/security/observability and non-functional requirements
- [docs/orchestration/](docs/orchestration/README.md) · [docs/intelligence/](docs/intelligence/README.md) · [docs/analytics/](docs/analytics/README.md) — L4/L5/L6 design
- [docs/build-vs-buy.md](docs/build-vs-buy.md) · [docs/roadmap.md](docs/roadmap.md) · [docs/product/](docs/product/editions.md) — decisions, roadmap, commercial model
- [docs/adr/](docs/adr/0000-adr-index.md) — architecture decision records (0001–0022)

Architecture at a glance (source: [`docs/diagrams/layers.mmd`](docs/diagrams/layers.mmd)):

```
7. Analytics/AI   lakehouse · semantic layer · ML · digital twin
6. Experience     customer portal · ops console · mobile · partner/API portal
5. Orchestration  saga engine · control tower · ATP/promise · routing · exceptions · policy
4. Intelligence   forecasting · predictive ETA · slotting · placement · optimization
3. Execution      OMS | TMS | WMS | YMS | Visibility | Billing
2. Data           Kafka backbone · event store · canonical models · CQRS views · MDM
1. Observation    IoT/telematics · GPS · scanners · cameras · EDI/API feeds · edge
```

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

## Conventions

- Markdown + Mermaid; ADRs follow Michael Nygard's format and are immutable once `Accepted`.
- Topic naming: `<layer>.<domain>.<event-type>.vN`; partition key = aggregate id.
- Code: Java 21 / Kotlin JVM 21 modules with version-catalog plugins only; the ops console is
  BFF-only ([ADR-0022](docs/adr/0022-experience-layer-and-frontend.md)).
