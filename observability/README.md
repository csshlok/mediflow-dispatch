# MediFlow Phase 6 Observability

This folder contains the simplified Phase 6 observability setup:

- Prometheus scrapes all MediFlow service `/actuator/prometheus` endpoints.
- Grafana auto-provisions two dashboards.
- k6 scripts exercise high traffic, autoscaling, Kafka failure, and hospital failure.

## Dashboards

- Business Metrics
  - P95 Dispatch Latency
  - Throughput
  - Saga Recovery Rate
  - Event Loss Rate
- Infrastructure
  - Matching Service Replicas
  - Pod Recovery Time evidence

## Kubernetes Setup

Apply the stack:

```powershell
kubectl apply -k observability/k8s
```

Grafana is exposed on `http://localhost:30300`.

Prometheus is exposed on `http://localhost:30090`.

Grafana login is `admin` with the password from the `grafana-secret` secret. Create it before applying:

```powershell
kubectl -n mediflow create secret generic grafana-secret --from-literal=GF_SECURITY_ADMIN_PASSWORD=<password>
```

## k6 Scenarios

Run with Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace grafana/k6 run observability/k6/scenarios/high-traffic.js
```

For Kubernetes via the gateway:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace -e BASE_URL=http://host.docker.internal:30080 grafana/k6 run observability/k6/scenarios/high-traffic.js
```

The scripts create their own admin and dispatcher users, seed test resources, and send requests through the API gateway.

## Failure Scenarios

Kafka failure:

1. Start `observability/k6/scenarios/kafka-failure.js`.
2. Stop Kafka during the run.
3. Restart Kafka.
4. Confirm Event Loss Rate returns to `0%`.

Hospital failure:

1. Enable forced reservation failure:

```powershell
kubectl set env deployment/hospital-service FORCE_RESERVATION_FAILURE=true -n mediflow
kubectl rollout status deployment/hospital-service -n mediflow
```

2. Run `observability/k6/scenarios/hospital-failure.js`.
3. Confirm Saga Recovery Rate increases. If other failure/load tests are in the same dashboard time range, the rate may be below `100%` because it includes non-recoverable failures too.
4. Disable the flag:

```powershell
kubectl set env deployment/hospital-service FORCE_RESERVATION_FAILURE- -n mediflow
kubectl rollout status deployment/hospital-service -n mediflow
```

## Pod Recovery Time

Measure with:

```powershell
kubectl delete pod -n mediflow -l app=emergency-request-service
kubectl get pods -n mediflow -w
```

Record the time from deletion to the replacement pod reaching `1/1 Running`.
