# Medical Emergency Coordination System Tech Stack

This setup follows the blueprint's platform stack without adding domain logic yet.

| Layer | Configured technology |
| --- | --- |
| Backend | Spring Boot 4, Java 21, Maven multi-module build |
| Messaging | Kafka with idempotent producers and per-service consumer groups |
| RPC | gRPC Java dependencies for Location and Matching services |
| Data | PostgreSQL per stateful service, PostGIS for Location, Redis for GEO/matching coordination |
| Reliability | Resilience4j starter, idempotency/outbox config placeholders, Case Service saga config placeholders |
| Auth | Spring Security OAuth2 Resource Server dependencies with JWT config placeholders |
| Observability | Actuator, Prometheus metrics, OpenTelemetry tracing, Jaeger OTLP endpoint |
| Containers | Docker Compose local infrastructure for Kafka, Redis, PostgreSQL/PostGIS, Jaeger, Prometheus, and Grafana |
| Orchestration | Kubernetes namespace/config and Matching Service HPA from 1 to 5 replicas |
