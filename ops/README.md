# Ops

Operational wiring for the Phase-1 scaffold.

| Area | Where | What |
|------|-------|------|
| Local dev | [`local-compose/`](local-compose/README.md) | Docker Compose Kafka (KRaft) + one-shot topic bootstrap + Kafka UI. Apps run on the host via `gradle bootRun`. |
| Cloud (AWS) | [`terraform/`](terraform/README.md) | Amazon MSK cluster definition (single source of truth stays the shared topic catalog). |
| Kubernetes | [`k8s/`](k8s/README.md) | Kustomize layout: namespace, shared env ConfigMap, per-service Deployment+Service, OpenTelemetry Collector. |
| CI | [`../.github/workflows/ci.yml`](../.github/workflows/ci.yml) | Contract gates (validateSchemas, reconcile, baselines) + full `gradle build` incl. embedded-Kafka ITs. |

## Topic catalog — single source of truth

`local-compose/topics.lst` is the authoritative topic declaration (name, partitions, cleanup). It is
reused verbatim by the local bootstrap, and the deploy-time bootstrap against MSK uses the same
script (`local-compose/scripts/bootstrap-topics.sh`, `KAFKA_BOOTSTRAP` injected) so every
environment converges on the same set.

## Run it

```bash
# local (host reaches the broker on localhost; the compose bootstrap uses kafka:9092 via env)
KAFKA_BOOTSTRAP=localhost:9092 bash ops/local-compose/scripts/bootstrap-topics.sh
docker compose -f ops/local-compose/docker-compose.yml up -d
# then gradle bootRun the services (see local-compose/README.md)
```

CI gates:

```bash
gradle :contracts:validation:validateSchemas
bash contracts/scripts/reconcile.sh
bash contracts/scripts/snapshot-baselines.sh
gradle build
```