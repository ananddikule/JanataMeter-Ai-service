#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${K8S_NAMESPACE:-default}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -n "${KUBECTL_COMMAND:-}" ]]; then
  read -r -a KUBECTL_CMD <<< "$KUBECTL_COMMAND"
elif command -v k3s >/dev/null 2>&1 && [[ -f /etc/rancher/k3s/k3s.yaml ]]; then
  if [[ -r /etc/rancher/k3s/k3s.yaml ]]; then
    KUBECTL_CMD=(k3s kubectl)
  else
    KUBECTL_CMD=(sudo k3s kubectl)
  fi
else
  KUBECTL_CMD=(kubectl)
fi

set +x

echo "Using Kubernetes client: ${KUBECTL_CMD[*]}"

if "${KUBECTL_CMD[@]}" -n "$NAMESPACE" get secret janmeter-redis-secret \
  >/dev/null 2>&1; then
  echo "Keeping the existing janmeter-redis-secret."
else
  if [[ -z "${REDIS_PASSWORD:-}" ]]; then
    command -v openssl >/dev/null 2>&1 || {
      echo "openssl is required to generate the Redis password." >&2
      exit 1
    }
    REDIS_PASSWORD="$(openssl rand -base64 32)"
  fi

  "${KUBECTL_CMD[@]}" -n "$NAMESPACE" create secret generic janmeter-redis-secret \
    --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD"
  unset REDIS_PASSWORD
  echo "Created janmeter-redis-secret."
fi

"${KUBECTL_CMD[@]}" -n "$NAMESPACE" apply -f "$SCRIPT_DIR/redis.yaml"
"${KUBECTL_CMD[@]}" -n "$NAMESPACE" rollout status deployment/redis --timeout=180s

"${KUBECTL_CMD[@]}" -n "$NAMESPACE" get deployment redis
"${KUBECTL_CMD[@]}" -n "$NAMESPACE" get service redis

echo "Redis is available inside the cluster at redis:6379."
