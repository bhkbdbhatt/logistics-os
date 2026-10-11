#!/usr/bin/env bash
# End-to-end smoke test on a throwaway kind cluster (docs/release-plan-0.2.0.md M3).
#
#   ops/kind/smoke.sh                 # create/use the cluster, deploy, verify
#   TEARDOWN=true ops/kind/smoke.sh   # also delete the cluster at the end
#
# Requires: kind, kubectl, curl, docker. Images are built/published by .github/workflows/images.yml
# (or build them locally first; see docs/engineering-build.md section 6).
set -euo pipefail

CLUSTER="${CLUSTER:-logistics-os}"
NS=logistics-os
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEARDOWN="${TEARDOWN:-false}"

command -v kind >/dev/null || { echo "kind not found on PATH" >&2; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl not found on PATH" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl not found on PATH" >&2; exit 1; }

echo "== 1/7 kind cluster =="
if kind get clusters | grep -qx "$CLUSTER"; then
  echo "cluster '$CLUSTER' already exists"
else
  kind create cluster --name "$CLUSTER" --config "$ROOT/ops/kind/kind-config.yaml"
fi

echo "== 2/7 kafka + topics catalog =="
kubectl apply -f "$ROOT/ops/kind/kafka.yaml"
kubectl apply -f "$ROOT/ops/kind/topics.configmap.yaml"
kubectl -n "$NS" rollout status deployment/logistics-os-kafka --timeout=240s

echo "== 3/7 bootstrap topics =="
kubectl -n "$NS" delete job topic-bootstrap --ignore-not-found >/dev/null
kubectl apply -f "$ROOT/ops/kind/jobs.yaml"
kubectl -n "$NS" wait --for=condition=complete job/topic-bootstrap --timeout=240s

echo "== 4/7 deploy platform =="
kubectl apply -k "$ROOT/ops/k8s"
for d in mdm ingest-gateway acl-tms acl-wms read-model-builder control-tower bff-ops legacy-simulator; do
  kubectl -n "$NS" rollout status "deployment/$d" --timeout=240s
done

echo "== 5/7 E2E: legacy-simulator -> acl-tms -> canonical topic =="
kubectl -n "$NS" delete job pipeline-probe --ignore-not-found >/dev/null
kubectl apply -f "$ROOT/ops/kind/jobs.yaml"
if kubectl -n "$NS" wait --for=condition=complete job/pipeline-probe --timeout=180s; then
  echo "canonical event observed on tms.shipment.events.v1"
else
  echo "pipeline-probe did not observe a canonical event; logs:" >&2
  kubectl -n "$NS" logs job/pipeline-probe || true
  exit 1
fi

echo "== 6/7 read-model + control-tower HTTP =="
kubectl -n "$NS" port-forward svc/control-tower 18087:8087 >/tmp/ct-port-forward.log 2>&1 &
PF_CT=$!
kubectl -n "$NS" port-forward svc/read-model-builder 18085:8085 >/tmp/rmb-port-forward.log 2>&1 &
PF_RMB=$!
trap 'kill $PF_CT $PF_RMB 2>/dev/null || true' EXIT
sleep 4
curl -fsS http://localhost:18087/tower/health; echo " <- control-tower health OK"
curl -fsS http://localhost:18085/views/health; echo " <- read-model health OK"
echo "tower shipments: $(curl -fsS http://localhost:18087/tower/shipments)"
echo "view shipments:  $(curl -fsS http://localhost:18085/views/shipments)"

echo "== 7/7 otel collector traces =="
if kubectl -n "$NS" logs deployment/otel-collector --since=2m 2>/dev/null | grep -qi 'spans'; then
  echo "otel collector observed span data (debug exporter)"
else
  echo "warning: no span data seen in otel-collector logs yet (debug exporter)"
fi

if [ "$TEARDOWN" = "true" ]; then
  echo "tearing down kind cluster '$CLUSTER'"
  kind delete cluster --name "$CLUSTER"
fi

echo "SMOKE OK"