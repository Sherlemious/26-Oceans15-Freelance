# Section 13 — Work Distribution

> Split from `../m3.txt`. Original file is untouched.

## 13.1 Branch Format
feat/M3/<scope>/<ID>/<studentID>
Commit format: feat(<scope>): <description> (studentID)

## 13.2 The 15 Deliverables
**Rule:** Each deliverable is a vertical slice that touches all parts of M3 — Java code, Kubernetes manifests, and observability artifacts. No deliverable is purely Java, K8s, or YAML. The 15 slices are designed so every team member works in parallel without blocking anyone else (see "Parallelism Strategy" below the table).

## The 15 deliverables are organized as 5 services × 3 vertical slices per service

Slice A — Read & DB: DB isolation, outbound Feign clients, exposed read endpoints, Postgres K8s, ≥3 LogQL panels, Logback config.
Slice B — Events & Saga: RabbitMQ topology, publishers/consumers, saga participation, Spring Boot K8s, ≥3 PromQL panels, actuator config.
Slice C — Cross-Cutting Infra: that service's gateway route entry + scrape job entry + dashboard JSON aggregation, plus one assigned shared-infra item.
| # | Branch ID | Service | Work (Java + K8s + Observability) |
| --- | --- | --- | --- |
| 1 | S1-READ-DB | user | DB isolation (datasource → freelancedb-users); ContractServiceClient + WalletServiceClient Feign interfaces with try-catch error handling + correlation interceptor; user-postgres K8s (StatefulSet + PVC + Secret + headless Service); logback-spring.xml; ≥3 LogQL panels for user-service dashboard. |
| 2 | S1-EVENTS | user | user.events TopicExchange + publishers (user.registered, user.deactivated); consumer queue user.proposal.saga-listener + DLQ; consumers for proposal.completed/proposal.cancelled updating freelancer stats; user-service K8s Deployment + ClusterIP Service + ConfigMap; actuator config; ≥3 PromQL panels for user-service dashboard; S1-F3, S1-F4, S1-F6, S1-F9 Java refactor (Feign + event publish). |
| 3 | S1-INFRA | user | user-service gateway route entry; user-service scrape job entry in prometheus.yml; final user-service dashboard JSON file. Shared infra owned by this slice: api-gateway Maven module (incl. JwtGatewayFilter) + gateway K8s Deployment + NodePort Service (30080) + /api/auth/** bypass + Mongo K8s StatefulSet + Service. |
| 4 | S2-READ-DB | job | DB isolation (datasource → freelancedb-jobs); ContractServiceClient (active-count) + ProposalServiceClient (job summary) Feign interfaces with error handling; job-postgres K8s (StatefulSet + PVC + Secret + Service); logback-spring.xml; ≥3 LogQL panels for job-service dashboard. |
| 5 | S2-EVENTS | job | job.events TopicExchange + publishers (job.status-changed, job.rated, job.closed); consumer queue job.proposal.saga-listener + DLQ; consumers for proposal.accepted/proposal.completed/proposal.cancelled/proposal.withdrawn; job-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S2-F3, S2-F4, S2-F7, S2-F12 Java refactor. |
| 6 | S2-INFRA | job | job-service gateway route + scrape job entry + final dashboard JSON. Shared infra owned by this slice: monitoring namespace YAML + Loki K8s (StatefulSet + ConfigMap + PVC + Service named loki) + Redis K8s StatefulSet + Service. |
| 7 | S3-READ-DB | proposal | DB isolation (datasource → freelancedb-proposals); add saga statuses to Proposal enum; expose GET /api/proposals/job/{jobId}/summary; UserServiceClient + JobServiceClient + ContractServiceClient Feign interfaces with error handling; proposal-postgres K8s (StatefulSet + PVC + Secret + Service); logback-spring.xml; ≥3 LogQL panels for proposal-service dashboard. |
| 8 | S3-EVENTS | proposal | proposal.events TopicExchange + publishers (proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn); consumer queue proposal.saga-feedback + DLQ; consumers for contract.created/contract.status-changed/payment.initiated/payment.completed/payment.failed (compensation trigger)/payment.refunded; proposal-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S3-F2, S3-F4 (saga trigger), S3-F7 (withdraw), S3-F11, S3-F12 Java refactor. |
| 9 | S3-INFRA | proposal | proposal-service gateway route + scrape job entry + final dashboard JSON. Shared infra owned by this slice: Prometheus K8s (Deployment + ConfigMap holding the full 5-job prometheus.yml + PVC + Service named prometheus) + Neo4j K8s StatefulSet + Service. |
| 10 | S4-READ-DB | contract | DB isolation (datasource → freelancedb-contracts); expose GET /api/contracts/user/{id}/{summary,active-count,completed-count}, GET /api/contracts/job/{jobId}/active-count, GET /api/contracts/proposal/{proposalId}/active (saga pre-check endpoint); UserServiceClient + JobServiceClient Feign interfaces with error handling; contract-postgres K8s (StatefulSet + PVC + Secret + Service); logback-spring.xml; ≥3 LogQL panels for contract-service dashboard. |
| 11 | S4-EVENTS | contract | contract.events TopicExchange + publishers (contract.created, contract.status-changed, contract.cancelled); consumer queue contract.saga-listener + DLQ; consumers for proposal.accepted (create Contract → publish contract.created)/proposal.completed (mark Contract COMPLETED)/proposal.cancelled (revert + publish)/user.deactivated; contract-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S4-F1, S4-F3, S4-F8, S4-F9 Java refactor. |
| 12 | S4-INFRA | contract | contract-service gateway route + scrape job entry + final dashboard JSON. Shared infra owned by this slice: Grafana K8s (Deployment + datasources ConfigMap pointing at Loki & Prometheus + dashboards ConfigMap embedding all 5 service dashboards + PVC + NodePort Service on 30030) + Cassandra K8s StatefulSet + Service. |
| 13 | S5-READ-DB | wallet | DB isolation (datasource → freelancedb-wallet); expose GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate; UserServiceClient + ContractServiceClient + JobServiceClient Feign interfaces with error handling; wallet-postgres K8s (StatefulSet + PVC + Secret + Service); logback-spring.xml; ≥3 LogQL panels for wallet-service dashboard. |
| 14 | S5-EVENTS | wallet | payment.events TopicExchange + publishers (payment.initiated, payment.completed, payment.failed, payment.refunded); consumer queue payment.saga-listener + DLQ; consumers for proposal.completed (Feign → user → create PENDING payout → publish payment.initiated) and proposal.cancelled (refund → publish payment.refunded); wallet-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S5-F3, S5-F4, S5-F10 Java refactor. |
| 15 | S5-INFRA | wallet | wallet-service gateway route + scrape job entry + final dashboard JSON. Shared infra owned by this slice: RabbitMQ K8s (StatefulSet + Service exposing 5672 + 15672) + Elasticsearch K8s StatefulSet + Service + saga end-to-end test scenarios A/B/C from §8.6 implemented as JUnit integration tests. |
## Parallelism Strategy — How All 15 Members Work Without Blocking Each Other
The 15 slices are designed so nobody waits for anyone else. The key is contract-first development: every cross-service interface is agreed in a kickoff meeting on Day 1, written down, and committed before any feature work starts. From that moment, each member writes against the contract — not against another member's implementation — so they can compile, test (with mocks), and deploy their slice independently.

## Day-0 kickoff contracts (committed by the team lead, ~2 hours)

Feign client interfaces — every @FeignClient interface signature (e.g., ContractServiceClient.getUserContractSummary) and the DTOs they return (UserContractSummaryDTO, JobProposalSummaryDTO, etc.). Committed once to a contracts/ Maven module that all services depend on.
Event payload records — every record class (ProposalCompletedEvent, PaymentFailedEvent, …) is added to that same contracts/ module. Routing keys + exchange names are fixed in §2.9 (no team debate).
New endpoint paths + DTO shapes — exact path, query params, response JSON. Already documented in each service's "New Endpoints" table (§3–§7).
K8s Service names — loki, prometheus, rabbitmq, <svc>-postgres — fixed up-front so DNS resolves correctly across slices.
Shared YAML stub files — api-gateway/application.yml (with route placeholders), prometheus-configmap.yaml (with scrape-job placeholders), grafana-dashboards.yaml ConfigMap (referencing 5 dashboard JSON paths). Each "INFRA" slice owns the creation of one stub; each service slice fills in its own block.
## Compile-time independence — once the contracts/ module is in place, slice 1's ContractServiceClient.getUserContractSummary(...) call compiles even if slice 10 hasn't implemented GET /api/contracts/user/{userId}/summary yet. The interface is the only thing slice 1 needs to compile and unit-test.

## Runtime independence (mocking) — for local dev each slice uses @MockBean on Feign clients and Testcontainers RabbitMQ. A slice can run, deploy, and verify in isolation without the other 14 slices being merged.

## Disjoint file ownership — each slice writes to its own packages and YAML blocks. The only shared YAML files are api-gateway/application.yml, prometheus.yml, and grafana-dashboards.yaml — these have a stable structure agreed at kickoff so each slice edits only its assigned block. Merge conflicts are minimized to non-existent.

## Deploy-time independence — when a slice's branch is ready, it merges into main whenever; the merge order is not prescribed because no slice depends on another slice being merged first. Integration verification (saga end-to-end, gateway routing) happens after all 15 are merged, owned by S5-INFRA.

## 13.3 Team Size Mapping
| Team size | Mapping |
| --- | --- |
| 15 members | 1 deliverable per member, exactly. Default mapping. |
| 14 members | One member takes 2 deliverables. Recommended pairing: S<i>-READ-DB + S<i>-INFRA for any service whose INFRA slice is light (e.g., S2-INFRA if Loki is the team's most-familiar tool). |
| 13 members | Two members each take 2 deliverables. Recommended: pair S<i>-READ-DB + S<i>-INFRA for two services whose INFRA assignments are smaller (e.g., S2 + S4). |
## 13.4 Merge Order
Because the contract-first design eliminates compile-time dependencies, merge order is unconstrained — branches can be merged in any order, as long as the contracts/ module exists in main first.

Day 0: Team lead merges the contracts/ Maven module + the 3 stub YAML files (api-gateway/application.yml, prometheus-configmap.yaml, grafana-dashboards.yaml) into main.
Day 1 onwards: All 15 slices proceed in parallel; each merges to main when ready. No slice blocks another.
Final integration: Once all 15 slices are merged, S5-INFRA owner runs the saga end-to-end test scenarios A/B/C (§8.6) and signs off.
