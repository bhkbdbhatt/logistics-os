# kind smoke test (M3)

Throwaway single-node `kind` cluster used to prove the 0.2.0 images boot, wire up to Kafka/OTel,
and move an event end-to-end. This is the automated form of the M3 acceptance check in
[docs/release-plan-0.2.0.md](../../docs/release-plan-0.2.0.md).

## Run

```bash
# images must exist in a registry the cluster can pull (GHCR public) — built by .github/workflows/images.yml
ops/kind/smoke.sh                 # deploy + verify, keep the cluster for inspection
TEARDOWN=true ops/kind/smoke.sh   # also delete the cluster afterwards
```

The script:

1. creates the `logistics-os` kind cluster (idempotent),
2. deploys a single-broker KRaft Kafka + the shared topic catalog,
3. runs the `topic-bootstrap` Job (creates topics from `ops/local-compose/topics.lst`),
4. `kubectl apply -k ops/k8s` and waits for every Deployment to roll out,
5. runs the `pipeline-probe` Job, which consumes one `tms.shipment.events.v1` record — proving
   `legacy-simulator -> acl-tms -> canonical` works in-cluster,
6. port-forwards `control-tower`/`read-model-builder` and checks the health + list endpoints,
7. greps the OTel Collector (debug exporter) logs for span data.

## Files

- `kind-config.yaml` — cluster definition (single control-plane node).
- `kafka.yaml` — single-broker Kafka (plaintext, RF=1, **smoke only**).
- `topics.configmap.yaml` — the topic catalog mounted into the bootstrap Job.
- `jobs.yaml` — `topic-bootstrap` + `pipeline-probe` Jobs.
- `smoke.sh` — orchestrates the whole flow.

## Notes / limits

- Kafka here is deliberately plaintext + RF=1; production uses MSK (see `ops/terraform`) with
  replication factor >= 3. The read-model-builder `STREAMS_REPLICATION_FACTOR` is `1` in the k8s
  manifest to match this smoke broker — raise it for real clusters.
- The OTel Collector ships with the `debug` exporter for Phase 1; a real backend is future work.
- Not wired into CI yet: it needs Docker + a kind-capable runner. Tracked as a follow-up in the
  release plan.
