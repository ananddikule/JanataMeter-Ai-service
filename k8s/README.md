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

If Redis requires authentication, add `SPRING_DATA_REDIS_PASSWORD` to the
`janmeter-ai-secrets` Secret. It is optional when Redis has no password.

JanMeter Service reaches this service inside the cluster at:

`http://janmeter-ai-service:8089/janmeter-ai-service`

Update `SPRING_DATA_REDIS_HOST` in `k8s/configmap.yaml` if the Redis Service in
the shared cluster has a different name. Update `OPENAI_MODEL` there to change
the model without rebuilding the image.
