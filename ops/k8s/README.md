# Kubernetes deployment (scaffold)

Kustomize layout for the Phase-1 services in a `logistics-os` namespace. Every workload reads the
shared `logistics-os-env` ConfigMap (`KAFKA_BOOTSTRAP_SERVERS`, `DEFAULT_TENANT`,
`OTEL_EXPORTER_OTLP_ENDPOINT`); bff-ops additionally resolves its upstreams via service DNS.

## Apply

```
kubectl apply -k ops/k8s
```

## Layout

- `namespace.yaml` — `logistics-os` namespace.
- `config.yaml` — shared non-secret configuration (Kafka bootstrap, tenant, OTLP endpoint).
- `apps/*.yaml` — one Deployment+Service per service (mirrors `docs/services.md` + module ports) and
  an **OpenTelemetry Collector** (contrib, OTLP gRPC/HTTP in, debug exporter out for Phase 1).
- `kustomization.yaml` — resource list; `commonLabels` applied to all objects.

## Notes / gaps (deferred, as documented in the WPs)

- **Images** are built + published to `ghcr.io/logistics-os/<svc>:0.2.0` by
  `.github/workflows/images.yml` (see [docs/engineering-build.md](../../docs/engineering-build.md)
  §6). For a full in-cluster rehearsal, use the `kind` smoke test in [ops/kind](../kind/).
- **Kafka** is assumed reachable at `logistics-os-kafka:9092` (e.g. Strimzi/Amazon MSK KafkaOperator
  in-cluster, or an external broker via external-name service). Topics must be bootstrapped from the
  shared catalog (`ops/local-compose/topics.lst`) which is the single source of truth.
- `read-model-builder` is a stateful Streams app: it uses an `emptyDir` for stream state (fine for a
  single replica; a PVC/StatefulSet and `RMB_SNAPSHOT_CONSUME=false` on replicas are production
  hardening). Changelog topic replication-factor must match the cluster minimum (set
  `spring.kafka.streams.replication-factor` accordingly; default 1 targets the local broker).
- Readiness uses a TCP probe; services expose `/views/health` (read-model), `/tower/health`
  (control-tower), `/ops/health` (bff-ops) — switch probes to HTTP when Actuator is added.
- No Ingress/HPA/PDB/PodDisruptionBudget yet; single-replica apps are non-HA by design until the
  repository + state layers land (roadmap).