# JanMeter AI Service deployment

This service uses the same Kubernetes cluster and namespace as JanMeter Service,
but has separate Deployment, Service, ConfigMap, image, and OpenAI Secret.

Create these Jenkins credentials before running the pipeline:

- `dockerhub-credentials`: Docker Hub username and access token.
- `janmeter-kubeconfig`: kubeconfig file for the shared cluster.
- `openai-api-key`: OpenAI project service-account API key (Secret text).

The existing `janmeter-secrets` Kubernetes Secret supplies the shared PostgreSQL
URL, username, and password. The pipeline creates/updates `janmeter-ai-secrets`
with `OPENAI_API_KEY` without printing it.

Install the cluster-internal Redis service once before the first AI deployment:

```sh
export REDIS_PASSWORD="$(openssl rand -base64 32)"
bash k8s/install-redis.sh
unset REDIS_PASSWORD
```

This creates the `redis` Service and the separate `janmeter-redis-secret` used
by both Redis and JanMeter AI Service. Redis is internal-only and is not exposed
through a NodePort or LoadBalancer. Its current data volume is ephemeral, so a
Redis pod replacement clears cached data.

JanMeter Service reaches this service inside the cluster at:

`http://janmeter-ai-service:8089/janmeter-ai-service`

Update `SPRING_DATA_REDIS_HOST` in `k8s/configmap.yaml` if the Redis Service in
the shared cluster has a different name. Update `OPENAI_MODEL` there to change
the model without rebuilding the image.
