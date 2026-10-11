#!/usr/bin/env bash
# End-to-end smoke test on a throwaway kind cluster (docs/release-plan-0.2.0.md M3).
#
#   ops/kind/smoke.sh                        # base manifests, published image tag
#   OVERLAY=dhl ops/kind/smoke.sh            # customer overlay (namespace logistics-os-dhl)
#   KIND_LOAD_LOCAL=true OVERLAY=dhl ops/kind/smoke.sh   # build + kind-load images locally first
#   TEARDOWN=true ops/kind/smoke.sh          # also delete the cluster at the end
#
# Env:
#   OVERLAY          customer overlay under ops/k8s/overlays/ (default: none -> base manifests)
#   IMAGE_TAG        image tag to deploy (default 0.2.0). With OVERLAY=dhl the effective tag is
#                    dhl-0.2.0, matching the overlay's images: override.
#   KIND_LOAD_LOCAL  true -> ./gradlew bootJar + docker build + `kind load docker-image` per service
#   TEARDOWN         true -> delete the cluster at the end
#
# Requires: kind, kubectl, curl (and docker + a JDK when KIND_LOAD_LOCAL=true).
# Published images come from .github/workflows/images.yml; see docs/engineering-build.md section 6.
set -euo pipefail

CLUSTER="${CLUSTER:-logistics-os}"
OVERLAY="${OVERLAY:-}"
IMAGE_TAG="${IMAGE_TAG:-0.2.0}"
DEPLOY_TAG="${OVERLAY:+$OVERLAY-}$IMAGE_TAG"
NS="logistics-os${OVERLAY:+-$OVERLAY}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFESTS="$ROOT/ops/k8s${OVERLAY:+/overlays/$OVERLAY}"
TEARDOWN="${TEARDOWN:-false}"
KIND_LOAD_LOCAL="${KIND_LOAD_LOCAL:-false}"

BASE_SERVICES=(mdm ingest-gateway acl-tms acl-wms read-model-builder control-tower bff-ops legacy-simulator)
BUILD_SERVICES=("${BASE_SERVICES[@]}")
if grep -q 'orchestrator.yaml' "$MANIFESTS/kustomization.yaml" 2>/dev/null; then
  BUILD_SERVICES=("${BASE_SERVICES[@]}" orchestrator)
fi

command -v kind >/dev/null || { echo "kind not found on PATH" >&2; exit 1; }
command -v kubectl >/dev/null || { echo "kubectl not found on PATH" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl not found on PATH" >&2; exit 1; }
if [ "$KIND_LOAD_LOCAL" = "true" ]; then
  command -v docker >/dev/null || { echo "docker not found on PATH (KIND_LOAD_LOCAL=true)" >&2; exit 1; }
fi

echo "== 1/8 kind cluster =="
if kind get clusters | grep -qx "$CLUSTER"; then
  echo "cluster '$CLUSTER' already exists"
else
  kind create cluster --name "$CLUSTER" --config "$ROOT/ops/kind/kind-config.yaml"
fi

if [ "$KIND_LOAD_LOCAL" = "true" ]; then
  echo "== 2/8 build + load images locally (tag $DEPLOY_TAG) =="
  AGENT_VERSION="${OTEL_AGENT_VERSION:-1.33.6}"
  BUILD_DIR="$ROOT/build/kind-images"
  mkdir -p "$BUILD_DIR/otel"
  AGENT="$BUILD_DIR/otel/opentelemetry-javaagent.jar"
  if [ ! -f "$AGENT" ]; then
    echo "downloading otel javaagent v$AGENT_VERSION"
    curl -fsSL -o "$AGENT" \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${AGENT_VERSION}/opentelemetry-javaagent.jar"
  fi

  declare -A MODULE=(
    [mdm]=":core:mdm" [ingest-gateway]=":core:ingest-gateway" [acl-tms]=":core:acl-tms"
    [acl-wms]=":core:acl-wms" [read-model-builder]=":core:read-model-builder"
    [control-tower]=":core:control-tower" [bff-ops]=":core:bff-ops"
    [legacy-simulator]=":tools:legacy-simulator" [orchestrator]=":core:phase2-stubs:orchestrator"
  )
  declare -A DIR=(
    [mdm]="core/mdm" [ingest-gateway]="core/ingest-gateway" [acl-tms]="core/acl-tms"
    [acl-wms]="core/acl-wms" [read-model-builder]="core/read-model-builder"
    [control-tower]="core/control-tower" [bff-ops]="core/bff-ops"
    [legacy-simulator]="tools/legacy-simulator" [orchestrator]="core/phase2-stubs/orchestrator"
  )
  for svc in "${BUILD_SERVICES[@]}"; do
    echo "-- $svc"
    ( cd "$ROOT" && ./gradlew -q "${MODULE[$svc]}:bootJar" )
    jar="$(find "$ROOT/${DIR[$svc]}/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n1)"
    [ -n "$jar" ] || { echo "no boot jar produced for $svc" >&2; exit 1; }
    cp "$jar" "$BUILD_DIR/service.jar"
    docker build -q -f "$ROOT/ops/containers/service.Dockerfile" \
      --build-arg JAR=service.jar -t "ghcr.io/logistics-os/${svc}:${DEPLOY_TAG}" "$BUILD_DIR"
    kind load docker-image "ghcr.io/logistics-os/${svc}:${DEPLOY_TAG}" --name "$CLUSTER"
  done
fi

echo "== 3/8 kafka + topics catalog =="
kubectl apply -f "$ROOT/ops/kind/kafka.yaml"
kubectl apply -f "$ROOT/ops/kind/topics.configmap.yaml"
kubectl -n logistics-os rollout status deployment/logistics-os-kafka --timeout=240s

echo "== 4/8 bootstrap topics =="
kubectl -n logistics-os delete job topic-bootstrap --ignore-not-found >/dev/null
kubectl apply -f "$ROOT/ops/kind/jobs.yaml"
kubectl -n logistics-os wait --for=condition=complete job/topic-bootstrap --timeout=240s

echo "== 5/8 deploy platform ($([ -n "$OVERLAY" ] && echo "overlay $OVERLAY" || echo base)) =="
kubectl apply -k "$MANIFESTS"
for d in "${BASE_SERVICES[@]}"; do
  kubectl -n "$NS" rollout status "deployment/$d" --timeout=240s
done
if kubectl -n "$NS" get deployment/orchestrator >/dev/null 2>&1; then
  kubectl -n "$NS" rollout status deployment/orchestrator --timeout=240s
fi

echo "== 6/8 E2E: legacy-simulator -> acl-tms -> canonical topic =="
kubectl -n logistics-os delete job pipeline-probe --ignore-not-found >/dev/null
kubectl apply -f "$ROOT/ops/kind/jobs.yaml"
if kubectl -n logistics-os wait --for=condition=complete job/pipeline-probe --timeout=180s; then
  echo "canonical event observed on tms.shipment.events.v1"
else
  echo "pipeline-probe did not observe a canonical event; logs:" >&2
  kubectl -n logistics-os logs job/pipeline-probe || true
  exit 1
fi

echo "== 7/8 read-model + control-tower HTTP =="
kubectl -n "$NS" port-forward svc/control-tower 18087:8087 >/tmp/ct-port-forward.log 2>&1 &
PF_CT=$!
kubectl -n "$NS" port-forward svc/read-model-builder 18085:8085 >/tmp/rmb-port-forward.log 2>&1 &
PF_RMB=$!
trap 'kill $PF_CT $PF_RMB 2>/dev/null || true' EXIT
sleep 4
curl -fsS http://localhost:18087/tower/health; echo " <- control-tower health OK"
curl -fsS http://localhost:18085/views/health; echo " <- read-model health OK"
echo "entitlement: $(curl -fsS http://localhost:18087/tower/entitlement)"
echo "tower shipments: $(curl -fsS http://localhost:18087/tower/shipments)"
echo "view shipments:  $(curl -fsS http://localhost:18085/views/shipments)"

echo "== 8/8 otel collector traces =="
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