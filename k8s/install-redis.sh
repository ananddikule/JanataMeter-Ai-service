#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${K8S_NAMESPACE:-default}"

: "${REDIS_PASSWORD:?Set REDIS_PASSWORD before running this script}"

set +x

kubectl -n "$NAMESPACE" create secret generic janmeter-redis-secret \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" apply -f k8s/redis.yaml
kubectl -n "$NAMESPACE" rollout status deployment/redis --timeout=180s

kubectl -n "$NAMESPACE" get deployment redis
kubectl -n "$NAMESPACE" get service redis

echo "Redis is available inside the cluster at redis:6379."
