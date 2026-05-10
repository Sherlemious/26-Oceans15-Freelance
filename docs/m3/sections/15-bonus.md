# Section 15 — Bonus

> Split from `../m3.txt`. Original file is untouched.

| Bonus | Description |
| --- | --- |
| Full Testing Suite | (1) Unit tests for service business logic with @MockBean on all Feign clients. (2) RabbitMQ consumer integration tests with Testcontainers — publish an event, assert the consumer processes it and mutates the local DB. (3) Saga E2E test: trigger S3-F4, assert contract.created and payment.initiated are received; then inject payment failure, assert compensation runs. |
| CI/CD Pipeline | GitHub Actions: on push to feat/* → Maven build + JUnit + Docker build. On push to main → push images to a container registry. Submit as .github/workflows/ci.yml. |
| Circuit Breaker | Add spring-cloud-starter-circuitbreaker-resilience4j to services with Feign calls. Configure fallback responses. Demonstrate: circuit opens on repeated failures, fallback activates, circuit recovers. |
| Ingress | Replace NodePort on api-gateway with an Ingress resource. minikube addons enable ingress, configure Ingress with path-based routing to the gateway. |
| Horizontal Pod Autoscaler | HPA on proposal-service (highest traffic — saga state machine). CPU threshold ≥ 50%. Requires metrics-server in MiniKube. Demonstrate scale-out under simulated load. |
