# JanMeter AI Service deployment

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

Install the cluster-internal Redis service once before the first AI deployment:

```sh
bash k8s/install-redis.sh
```

This creates the `redis` Service and the separate `janmeter-redis-secret` used
by both Redis and JanMeter AI Service. Redis is internal-only and is not exposed
through a NodePort or LoadBalancer. Its current data volume is ephemeral, so a
Redis pod replacement clears cached data.

The installer automatically uses local K3s when available and generates a
secure password when the Secret does not exist. Rerunning it preserves the
existing password. For a different cluster client, set `KUBECTL_COMMAND`, for
example `KUBECTL_COMMAND="kubectl --context production"`.

JanMeter Service reaches this service inside the cluster at:

`http://janmeter-ai-service:8089/janmeter-ai-service`

Update `SPRING_DATA_REDIS_HOST` in `k8s/configmap.yaml` if the Redis Service in
the shared cluster has a different name. Update `OPENAI_MODEL` there to change
the model without rebuilding the image.
