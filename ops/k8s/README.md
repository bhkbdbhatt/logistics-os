# Kubernetes deployment (scaffold)

Kustomize layout for the services in a `logistics-os` namespace. Every workload reads the
shared `logistics-os-env` ConfigMap (`KAFKA_BOOTSTRAP_SERVERS`, `DEFAULT_TENANT`,
`OTEL_EXPORTER_OTLP_ENDPOINT`, plus the 0.3.0 identity/persistence settings); the secured services
(`mdm`, `ingest-gateway`, `control-tower`, `bff-ops`) also read `logistics-os-secrets`; bff-ops
additionally resolves its upstreams via service DNS.

## Apply

```
kubectl apply -k ops/k8s
# per-customer deployment (namespace + image tag + branding + license)
kubectl apply -k ops/k8s/overlays/<customer>
```

## Layout

- `namespace.yaml` — `logistics-os` namespace.
- `config.yaml` — shared non-secret configuration (Kafka bootstrap, tenant, OTLP endpoint,
  `logistics.security.*`, MDM DB URL).
- `secrets.yaml` — **dev-default** Secret (local JWT secret, Postgres/Keycloak credentials). Replace
  with a real secret manager before deploying.
- `apps/*.yaml` — one workload per component. Phase-1 services (mirrors `docs/services.md` + module
  ports) plus 0.3.0 additions: **`postgres`** (system-of-record, ADR-0021), **`keycloak`** (reference
  IAM, ADR-0020), and **`web`** (ops console SPA, ADR-0022). An **OpenTelemetry Collector** (contrib,
  OTLP gRPC/HTTP in, debug exporter out for Phase 1) is also included. `apps/orchestrator.yaml` is
  **not** in the base — edition overlays that include L5 add it.
- `kustomization.yaml` — resource list; `commonLabels` applied to all objects; the single `images:`
  override point for tags.
- `overlays/<customer>/` — per-customer units (namespace, tag, branding, soft license); see
  [overlays/README.md](overlays/README.md).

## Notes / gaps (deferred, as documented in the WPs)

- **Images** are built + published to `ghcr.io/logistics-os/<svc>` by
  `.github/workflows/images.yml` (services **and** the `web` console) and, per edition, by
  `.github/workflows/release.yml` (see [docs/engineering-build.md](../../docs/engineering-build.md) §6).
  The tags in `kustomization.yaml`/overlays are placeholders refreshed at the release cut (`web`
  tracks 0.3.0, the services still 0.2.0 until the 0.3.0 cut retags them). For a full in-cluster
  rehearsal, use the `kind` smoke test in [ops/kind](../kind/).
- **Identity**: with `LOGISTICS_SECURITY_MODE=oidc` the four secured services validate Keycloak-issued
  JWTs and bind `TenantContext` from the `tenant_id` claim. Set `LOGISTICS_SECURITY_MODE=local` to fall
  back to HS256 dev tokens (`/auth/login`). Browsers must reach the OIDC issuer directly — front
  Keycloak with an Ingress and set `LOGISTICS_WEB_OIDC_ISSUER` to its public URL.
- **Kafka** is assumed reachable at
  `logistics-os-kafka.logistics-os.svc.cluster.local:9092` (FQDN so customer namespaces can reach it;
  e.g. Strimzi/Amazon MSK KafkaOperator in-cluster, or an external broker via external-name service).
  Topics must be bootstrapped from the shared catalog (`ops/local-compose/topics.lst`) which is the
  single source of truth.
- `read-model-builder` is a stateful Streams app: it uses an `emptyDir` for stream state (fine for a
  single replica; a PVC/StatefulSet and `RMB_SNAPSHOT_CONSUME=false` on replicas are production
  hardening). Changelog topic replication-factor must match the cluster minimum (set
  `spring.kafka.streams.replication-factor` accordingly; default 1 targets the local broker).
- Readiness uses a TCP probe; services expose `/views/health` (read-model), `/tower/health`
  (control-tower), `/ops/health` (bff-ops) — switch probes to HTTP when Actuator is added.
- No Ingress/HPA/PDB/PodDisruptionBudget yet; single-replica apps are non-HA by design until the
  repository + state layers land (roadmap).