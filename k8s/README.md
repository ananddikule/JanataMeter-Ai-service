# JanMeter AI Service deployment

## Home-feed worker ownership

JanMeter AI Service is the sole asynchronous worker for the JanMeter home feed. With `JANMETER_FEED_WORKER_ENABLED=true`, it creates the feed Kafka topics, publishes pending rows from `janmeter_feed_outbox`, consumes engagement and vote signals, updates Redis ranking data, invalidates stale feed caches, decays ranking signals, and removes old published outbox rows.

JanMeter Service remains the authenticated public API and read path. Do not enable its feed workers when this deployment is active.

This service uses the same Kubernetes cluster and namespace as JanMeter Service,
but has separate Deployment, Service, ConfigMap, image, and OpenAI Secret.

Create these Jenkins credentials before running the pipeline:

- `dockerhub-credentials`: Docker Hub username and access token.
- `janmeter-kubeconfig`: kubeconfig file for the shared cluster.
- `openai-api-key`: OpenAI project service-account API key (Secret text).

Apply the Jenkins namespace permissions once from an administrator shell before
the first pipeline deployment:

```sh
kubectl apply -f k8s/jenkins-rbac.yaml
```

The RoleBinding grants the existing `default/janmeter-jenkins` service account
namespace-only deployment access. Jenkins cannot apply this permission to
itself.

The Jenkins agent must have Java 21 available because the Gradle toolchain and
the production container both use Java 21.

The existing `janmeter-secrets` Kubernetes Secret supplies the shared PostgreSQL
URL, username, and password. The pipeline creates/updates `janmeter-ai-secrets`
with `OPENAI_API_KEY` without printing it.

The pipeline also creates/updates `dockerhub-registry` from
`dockerhub-credentials`, allowing Kubernetes to pull the private
`ananddikule/janmeter-ai-service` image.

The Jenkins job exposes a `DEPLOY_ENVIRONMENT` choice. Select `development`
to apply `application-dev.properties` with `k8s/configmap.yaml`. Select
`production` to apply the stricter `application-prod.properties` with
`k8s/configmap-production.yaml`. The safe default is `production`.

Install the shared Redis service from the JanMeter Service repository once
before the first AI deployment:

```sh
cd ~/Documents/projects/JanataMeter-service
bash k8s/install-redis.sh
```

The JanMeter Service repository is the single source of truth for shared Redis,
Kafka, and Kafka UI installation. The AI service continues to consume the
cluster-internal `redis:6379` Service and `janmeter-redis-secret`.

JanMeter Service reaches this service inside the cluster at:

`http://janmeter-ai-service:8089/janmeter-ai-service`

Update `SPRING_DATA_REDIS_HOST` in `k8s/configmap.yaml` if the Redis Service in
the shared cluster has a different name. Update `OPENAI_MODEL` there to change
the model without rebuilding the image.
