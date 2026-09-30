# Phase 6 Metrics Evidence

Collected on 2026-06-16 against the Docker Desktop Kubernetes `mediflow` namespace.

## Prometheus Scraping

All configured targets were `up`:

- 9 MediFlow services on `/actuator/prometheus`
- Prometheus
- kube-state-metrics

## High Traffic

- k6 scenario: `observability/k6/scenarios/high-traffic.js`
- Emergency iterations: 60
- HTTP failure rate: 0%
- Prometheus throughput after the run: about 24 requests/min
- P95 dispatch latency: about 30,000 ms

## Kafka Failure

- Kafka was stopped before sending emergency requests.
- Emergency requests accepted during outage: 52
- Temporary event-loss gauge during outage: 17.16%
- After Kafka restart and outbox drain: 456 stored / 456 published
- Settled event-loss rate: 0%

## Hospital Failure

- `FORCE_RESERVATION_FAILURE=true` was enabled only for this test, then removed.
- k6 scenario: `observability/k6/scenarios/hospital-failure.js`
- Emergency iterations: 20
- HTTP failure rate: 0%
- Saga compensation observed: 19 compensated sagas
- Saga recovery rate immediately after scenario: 82.61%

## Autoscaling

- k6 scenario: `observability/k6/scenarios/autoscaling.js`
- Completed iterations: 3,077
- HTTP failure rate during peak: 1.43%
- Matching service scaled from 1 replica to 5 replicas.
- Final observed matching-service state after scale-down started: 2 replicas.

## Final Prometheus Snapshot

- Matching replicas max over 30 minutes: 5
- Throughput over 30 minutes: about 102 requests/min
- Event loss over 60 minutes, range-based dashboard query: 0%
- Saga recovery over 60 minutes, range-based query: about 26.03%

The 60-minute saga recovery rate includes hospital-failure compensation plus non-recoverable overload/no-resource failures from the autoscaling scenario.
