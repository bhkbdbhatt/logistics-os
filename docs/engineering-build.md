# Engineering build — Phase-1 scaffold

> Status: Active · Related: [ADR-0019](adr/0019-repository-and-build-runtime.md) · [../ops/README.md](../ops/README.md) · [../contracts/] contract governance scripts · [../README.md](../README.md)

How the Phase-1 monorepo is built, tested, run, containerized and deployed. Everything in this
document is enforced by `.github/workflows/ci.yml`; the local host has **no Java/Gradle/Docker**
requirement for committing — CI is the sole compiler and test runner.

## 1. Modules

| Module | Layer | Role | Port | Key tests |
|--------|-------|------|------|-----------|
| `contracts` | — | Avro schemas, registry, validation | — | `validateSchemas` + bash gates |
| `platform:event-envelope` | L2 | `EventEnvelope`, builder, ULID, codec | — | builder/codec unit |
| `platform:kafka-commons` | L2 | serde, `SchemaResolvers`, `PayloadAvro`, `EnvelopeRouting` | — | `EventEnvelopeSerdeTest`, resolver tests |
| `platform:outbox` / `idempotency` / `tenant` / `observability` | L2 | cross-cutting libs | — | platform unit tests |
| `core:ingest-gateway` | L1 | REST ingest → obs.* canonical | 8081 | `IngestServiceTest`, `KafkaIngestPublisherIT` |
| `core:acl-tms` | L1 | legacy TMS JSON → `tms.shipment.events.v1` | 8083 | `LegacyTmsMapperTest`, `TmsPipelineIT` |
| `core:acl-wms` | L1 | legacy WMS JSON → obs/wms canonical | 8084 | `LegacyWmsMapperTest` |
| `core:mdm` | L2 | golden records → `mdm.*.events.v1` (compacted) | 8086 | `ReferenceCodecTest`, `MdmServiceTest`, `MdmPublishIT` |
| `core:read-model-builder` | L2 | CQRS projections → `shipment/inventory.state.v1` | 8085 | `*ProjectionMapperTest`, `ReadModelTopologyIT` |
| `core:control-tower` | L5/L6 | live ops + alerts + one-click `orch.command.v1` | 8087 | `AlertRuleEngineTest`, `CommandBuilderTest` |
| `core:bff-ops` | L6 | compose BFF (no domain logic) | 8088 | `OpsViewMapperTest`, `BffOpsControllerTest` |
| `core:phase2-stubs:orchestrator` | L5 | Phase-2 command stub (emits `orch.command.v1`, `POST /orch/commands`, scheduled heartbeat) | 8090 | `StubCommandBuilderTest`, `CommandEmitIT` |
| `tools:legacy-simulator` | — | scheduled legacy feed emitter | 8089 | — |

## 2. Toolchain

- **JDK 21** (Temurin), **Gradle 8.12.1** via the **committed wrapper** (`gradlew` / `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` with a pinned
  `distributionSha256Sum`). CI validates the wrapper jar and warms the distribution with
  `gradle/actions/setup-gradle` (`gradle-version: 8.12.1`); the wrapper is the sole build entrypoint,
  so a system Gradle install is never assumed.
- Repo-level `gradle.properties` mirrors CI memory settings (`-Xmx2g`) and enables parallel + caching;
  `GRADLE_OPTS` in CI remains as a fallback for the action bootstrap.
- Versions live in `gradle/libs.versions.toml` (Spring Boot 3.4.1, Kafka 3.7.1, Avro 1.11.3,
  Confluent 7.6.3). Repos: Maven Central + `packages.confluent.io/maven`.
- Module plugins use catalog aliases only (`alias(libs.plugins.spring.boot)`,
  `alias(libs.plugins.dependency.management)`); bare IDs without a version do not inherit root
  versions.

## 3. Build & test

```bash
gradle build                                   # compile + test + assemble everything
gradle :contracts:validation:validateSchemas   # schema/registry contract gate
gradle :core:mdm:test                          # single module
gradle :core:read-model-builder:bootJar        # build a deployable jar
gradle --no-daemon build                       # CI form
```

### Test matrix

- **Unit tests** (pure JVM, no Kafka): mappers, codecs, serde, alert rules, service fakes.
- **Embedded-Kafka ITs** (`*IT`, `spring-kafka-test`): `KafkaIngestPublisherIT`, `TmsPipelineIT`,
  `ReadModelTopologyIT`, `MdmPublishIT`. They boot a real broker in-process and verify
  round-trip + guard semantics (replay/out-of-order safety).
- **Contract gates** (bash, require no JDK): `contracts/scripts/reconcile.sh` and
  `contracts/scripts/snapshot-baselines.sh --write` (baseline currently **68 schemas**).

## 4. Local run (compose + bootRun)

See [ops/local-compose/README.md](../ops/local-compose/README.md). Short form:

```bash
docker compose -f ops/local-compose/docker-compose.yml up -d
docker compose -f ops/local-compose/docker-compose.yml run --rm topic-bootstrap
gradle :core:mdm:bootRun &            # seeds golden records on start
gradle :core:read-model-builder:bootRun &
gradle :core:control-tower:bootRun &
gradle :core:bff-ops:bootRun &
```

Smoke:

```bash
curl http://localhost:8086/mdm/location
curl http://localhost:8085/views/shipments
curl http://localhost:8087/tower/alerts
curl http://localhost:8088/ops/overview
curl -X POST http://localhost:8088/ops/actions/notify-customer \
     -H 'Content-Type: application/json' -d '{"shipmentId":"<id>"}'
```

Feed data end-to-end: run `:tools:legacy-simulator` (legacy JSON → ACLs → canonical → read
models → tower). The `topics.lst` catalog is the single source of truth for topic creation on every
environment, local or cloud (`ops/local-compose/scripts/bootstrap-topics.sh`, `KAFKA_BOOTSTRAP`).

## 5. CI

`.github/workflows/ci.yml` (two jobs):

1. **Contract gates** — `validateSchemas`, `reconcile.sh`, `snapshot-baselines.sh` (drift check).
2. **Build & tests** — `gradle build` (needs `contracts` green first), plus a jar-integrity step.

## 6. Container image recipe

Images are built by `.github/workflows/images.yml` (M2 in [release-plan-0.2.0.md](release-plan-0.2.0.md)):
boot jars via the Gradle wrapper, baked via `ops/containers/service.Dockerfile` (eclipse-temurin:21-jre)
with the OpenTelemetry java agent embedded, and pushed to `ghcr.io/logistics-os/<svc>:0.2.0` with OCI
provenance + SBOM attestations. Local equivalent (single service):

```bash
./gradlew :core:mdm:bootJar
mkdir -p otel && curl -fsSL https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.33.6/opentelemetry-javaagent.jar -o otel/opentelemetry-javaagent.jar
docker build --build-arg JAR="$(ls core/mdm/build/libs/mdm-*.jar | grep -v plain)" \
             -t ghcr.io/logistics-os/mdm:0.2.0 -f ops/containers/service.Dockerfile .
```

Deploy: `kubectl apply -k ops/k8s`. See [ops/k8s/README.md](../ops/k8s/README.md) for the OpenTelemetry
Collector wiring (`OTEL_EXPORTER_OTLP_ENDPOINT` from the shared `logistics-os-env` ConfigMap, which
overrides the image default).

## 7. Deferred / known gaps (tracked)

- No container registry pipeline images built yet; no wrapper → resolved in 0.2.0 (M0).
- Embedded-Kafka ITs need `spring-kafka-test` to pull `kafka_2.13` (JVM memory on CI runners).
- `spring.kafka.streams.replication-factor: 1` must be raised per cluster (MSK requires matching
  replication minimum). State-store topology is single-replica.
- Cloud MSK has no client auth/Schema Registry yet (Phase-1 runs `schema.resource=true`).
- WMS payloads (`wms.v1.WmsTaskEvent`, `wms.v1.InventoryMove`) are promoted to `canonical` in the
  registry; orchestration stubs remain `draft` and obs payloads remain `scaffold` status.

## 8. Contract governance quick reference

```bash
bash contracts/scripts/reconcile.sh            # WARN-only for docs-heading backlog
bash contracts/scripts/snapshot-baselines.sh --write   # after intentional schema changes
```

Registry rules: every envelope payload is registered (`kind: event|command|observation`); embedded
sub-records/wrapped records (e.g. `io.logisticsos.mdm.record.v1.*`) live in
`contracts/validation/registry-exclusions.txt`.