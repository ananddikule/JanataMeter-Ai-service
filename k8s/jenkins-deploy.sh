#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${K8S_NAMESPACE:-default}"
DEPLOYMENT="${K8S_DEPLOYMENT:-janmeter-ai-service}"
CONTAINER="${K8S_CONTAINER:-janmeter-ai-service}"

: "${IMAGE:?IMAGE environment variable is required}"
: "${KUBECONFIG:?KUBECONFIG environment variable is required}"

if [[ "$IMAGE" == *"|"* || "$IMAGE" == *$'\n'* ]]; then
  echo "IMAGE contains an invalid character." >&2
  exit 1
fi

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

sed "s|image: janmeter-ai-service:local|image: $IMAGE|" \
  k8s/deployment.yaml > "$TEMP_DIR/deployment.yaml"

kubectl -n "$NAMESPACE" apply \
  -f k8s/configmap.yaml \
  -f "$TEMP_DIR/deployment.yaml" \
  -f k8s/service.yaml

kubectl -n "$NAMESPACE" rollout status \
  "deployment/$DEPLOYMENT" \
  --timeout=300s

ACTUAL_IMAGE="$(
  kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT" \
    -o jsonpath="{.spec.template.spec.containers[?(@.name=='$CONTAINER')].image}"
)"

echo "Expected image: $IMAGE"
echo "Actual image:   $ACTUAL_IMAGE"
test "$ACTUAL_IMAGE" = "$IMAGE"

kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT"
kubectl -n "$NAMESPACE" get service janmeter-ai-service
