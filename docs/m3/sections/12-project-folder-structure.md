# Section 12 — Project Folder Structure

> Split from `../m3.txt`. Original file is untouched.

This is the canonical layout your team's repo must end up in by the end of M3. Every file path referenced elsewhere in this spec maps onto this tree.

```text
PROJECT TREE · FREELANCE MARKETPLACE M3
Expand all
Collapse all
freelance-m3/
git repo root
pom.xml
parent POM — 7 modules (contracts + 5 services + api-gateway)
README.md
docker-compose.yml
local dev compose: 5 postgres + RabbitMQ + 5 NoSQL + 5 services + gateway
contracts/
Day-0 kickoff module (see §13.2 Parallelism Strategy) — depended on by all 5 services
pom.xml
src/main/java/com/<teamID>/freelance/contracts/
user-service/
S1
pom.xml
Dockerfile
src/
job-service/
S2
pom.xml
Dockerfile
src/main/java/com/<teamID>/freelance/job/
proposal-service/
S3 (saga state machine lives here)
pom.xml
Dockerfile
src/main/java/com/<teamID>/freelance/proposal/
contract-service/
S4
pom.xml
Dockerfile
src/main/java/com/<teamID>/freelance/contract/
wallet-service/
S5 (payout processing + reversal logic)
pom.xml
Dockerfile
src/main/java/com/<teamID>/freelance/wallet/
api-gateway/
6th Maven module — Spring Cloud Gateway (reactive)
pom.xml
spring-cloud-starter-gateway-server-webflux + spring-boot-starter-webflux
Dockerfile
src/main/
k8s/
all Kubernetes manifests
namespaces/
secrets/
configmaps/
pvcs/
statefulsets/
deployments/
services/
one ClusterIP + one headless per pair
api-gateway/
monitoring/
everything in `monitoring` namespace
.github/workflows/
bonus — CI/CD
ci.yml
```
## 12.1 How Services Reference Files From Other Modules
The contracts/ module is the mechanism that lets all 5 services share Feign interfaces, DTOs, and event records without duplicating any Java code. It is a plain Maven JAR (no Spring Boot parent, no executable) that the 5 services depend on.

Package-name placeholder convention: every Java package path in this section uses <teamID> as a placeholder — e.g., com.<teamID>.freelance.user. Replace <teamID> with your team's actual package prefix from M1 (the unique value derived from your team's student IDs that the M1 grader auto-detects). Do NOT use a literal com.<teamID>.freelance.* prefix — that would (1) break M1/M2 grader package detection, (2) make every team use identical package names (defeating plagiarism detection), and (3) require you to rename every M1/M2 source file. The Maven groupId follows the same rule: use your existing team groupId from M1, not com.freelance. Code samples in this section show com.<teamID>.freelance.* purely as a readable example; substitute your real <teamID> everywhere before committing.

Parent pom.xml — Module Aggregator
The root pom.xml lists every module in build order. Maven's reactor builds contracts first because the 5 services declare it as a <dependency>:

```xml
<modules>
    <module>contracts</module>
    <module>user-service</module>
    <module>job-service</module>
    <module>proposal-service</module>
    <module>contract-service</module>
    <module>wallet-service</module>
    <module>api-gateway</module>
</modules>
```
contracts/pom.xml — The Shared Types Module
```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.<teamID>.freelance</groupId>
        <artifactId>freelance-m3</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>contracts</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```
The spring-cloud-starter-openfeign dependency is required because the @FeignClient annotation lives on interfaces inside this module. Event records and DTOs are plain Java records and need no extra dependencies.

Each Service pom.xml — Depends on contracts
Add this to user-service/pom.xml, job-service/pom.xml, proposal-service/pom.xml, contract-service/pom.xml, and wallet-service/pom.xml:

```xml
<dependency>
    <groupId>com.<teamID>.freelance</groupId>
    <artifactId>contracts</artifactId>
    <version>1.0.0</version>
</dependency>
```
The api-gateway does not depend on contracts (it does not call Feign clients itself; it just forwards HTTP requests).

How Java Code Imports Across Modules
Once a service depends on contracts, every type defined there is importable like any other Java package. For example, user-service calling contract-service via Feign:

package com.<teamID>.freelance.user.service;

import com.<teamID>.freelance.contracts.feign.ContractServiceClient;       // from contracts module
import com.<teamID>.freelance.contracts.dto.UserContractSummaryDTO;        // from contracts module
import com.<teamID>.freelance.user.entity.User;                             // local to user-service
import com.<teamID>.freelance.user.repository.UserRepository;               // local to user-service

```java
@Service
public class UserContractSummaryService {
    private final UserRepository userRepository;
    private final ContractServiceClient contractClient;

    public UserContractSummaryService(UserRepository userRepository, ContractServiceClient contractClient) {
        this.userRepository = userRepository;
        this.contractClient = contractClient;
    }

    public UserContractSummaryDTO buildSummary(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        UserContractSummaryDTO summary = contractClient.getUserContractSummary(userId);
        return enrichWithUserData(user, summary);
    }
}
```
Same pattern for events — wallet-service consuming proposal.completed:

package com.<teamID>.freelance.wallet.messaging.consumers;

import com.<teamID>.freelance.contracts.events.ProposalCompletedEvent;     // from contracts
import com.<teamID>.freelance.contracts.feign.UserServiceClient;            // from contracts
import com.<teamID>.freelance.wallet.entity.Payout;                          // local

```java
@Component
public class ProposalEventConsumer {
    private final UserServiceClient userClient;
    private final PayoutService payoutService;

    @RabbitListener(queues = "payment.saga-listener")
    public void onProposalCompleted(ProposalCompletedEvent event) {
        UserDTO freelancer = userClient.getUser(event.freelancerId());
        payoutService.createPendingPayout(event.proposalId(), event.contractId(), event.agreedAmount(), freelancer);
    }
}
```
Build Order — Maven Reactor Handles It Automatically
Run mvn clean install from the repo root. Maven's reactor:

Detects that user-service (and the other 4 services) depend on contracts:1.0.0.
Builds contracts first, installs it into the local Maven repo (~/.m2/repository/com/<teamID>/freelance/contracts/1.0.0/).
Builds the 5 services in any order (no inter-service dependencies — they all only depend on contracts).
Builds api-gateway last (or in parallel with services — it depends on neither contracts nor any service).
For local Docker dev (docker-compose up), each service's Dockerfile copies its own JAR — the contracts JAR is already baked into the service JAR via Maven's shade/repackage plugin during step 3.

## Why This Eliminates Cross-Slice Compile Blockers
When student A starts work on S1-READ-DB (which calls ContractServiceClient.getUserContractSummary(...)), they need that interface to exist in contracts/ so their code compiles. Day-0 kickoff (§13.2 Parallelism Strategy) ensures:

All 5 Feign client interfaces + all DTOs + all event records are committed to contracts/ on Day 0, before any of the 15 slices begin work.
Student A's user-service compiles immediately because ContractServiceClient exists in the imported contracts JAR — even though student G (owner of S4-READ-DB) hasn't yet implemented the matching GET /api/contracts/user/{userId}/summary endpoint inside contract-service.
Runtime testing: student A uses @MockBean ContractServiceClient until student G's branch merges.
## 12.2 Module-to-Slice Map
The 15 deliverable slices (§13.2) map onto the folder tree as follows. Use this as a per-slice checklist of which files a member touches:

| Slice | Touches |
| --- | --- |
| S1-READ-DB | user-service/src/main/java/.../entity/ + controller/ + service/ + application.yml (datasource block); contracts/.../feign/ContractServiceClient.java + WalletServiceClient.java; k8s/secrets/user-postgres-secret.yaml + k8s/pvcs/user-postgres-pvc.yaml + k8s/statefulsets/user-postgres-statefulset.yaml + k8s/services/user-postgres-svc.yaml; user-service/src/main/resources/logback-spring.xml; LogQL panels in k8s/monitoring/grafana/dashboards/user-dashboard.json. |
| S1-EVENTS | user-service/src/main/java/.../config/UserEventConfig.java + messaging/publishers/UserEventPublisher.java + messaging/consumers/ProposalEventConsumer.java; contracts/.../events/UserRegisteredEvent.java + UserDeactivatedEvent.java; k8s/configmaps/user-service-configmap.yaml + k8s/deployments/user-service-deployment.yaml + k8s/services/user-service-svc.yaml; PromQL panels in user-dashboard.json. |
| S1-INFRA | user-service route block in api-gateway/src/main/resources/application.yml; user-service scrape job in k8s/monitoring/prometheus/prometheus-configmap.yaml; final assembly of user-dashboard.json. Shared infra: entire api-gateway/ Maven module (incl. JwtGatewayFilter.java) + k8s/api-gateway/gateway-deployment.yaml + gateway-service.yaml (NodePort 30080) + k8s/statefulsets/mongo-statefulset.yaml + k8s/services/mongo-svc.yaml + k8s/pvcs/mongo-pvc.yaml. |
| S2-READ-DB | job-service equivalent of S1-READ-DB; contracts/.../feign/ContractServiceClient.java (active-count) + ProposalServiceClient.java (job summary). |
| S2-EVENTS | job-service equivalent of S1-EVENTS — JobEventConfig + publishers (status-changed, rated, closed) + consumers (proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn); contracts/.../events/JobStatusChangedEvent.java + JobRatedEvent.java + JobClosedEvent.java. |
| S2-INFRA | job-service route + scrape entry + dashboard. Shared infra: k8s/namespaces/monitoring-namespace.yaml + entire k8s/monitoring/loki/ + k8s/statefulsets/redis-statefulset.yaml + redis Service + PVC. |
| S3-READ-DB | proposal-service entity (incl. saga statuses on Proposal) + new endpoint GET /api/proposals/job/{jobId}/summary; contracts/.../feign/UserServiceClient.java + JobServiceClient.java + ContractServiceClient.java; proposal-postgres K8s; logback + LogQL panels. |
| S3-EVENTS | proposal-service saga/ package (S3-F4 complete, S3-F7 withdraw, payment-event consumers, contract-event consumer) + ProposalEventConfig + publishers (proposal.accepted/completed/cancelled/withdrawn); contracts/.../events/ProposalCompletedEvent.java etc.; proposal-service Deployment + Service + ConfigMap; PromQL panels. |
| S3-INFRA | proposal-service route + scrape entry + dashboard. Shared infra: entire k8s/monitoring/prometheus/ (Deployment + ConfigMap holding the full 5-job scrape config + PVC + Service) + k8s/statefulsets/neo4j-statefulset.yaml + neo4j Service + PVC. |
| S4-READ-DB | contract-service entity + new endpoints GET /api/contracts/user/{id}/{summary,active-count,completed-count}, GET /api/contracts/job/{jobId}/active-count, GET /api/contracts/proposal/{proposalId}/active (saga pre-check); contracts/.../feign/UserServiceClient.java (read uses) + JobServiceClient.java; contract-postgres K8s; logback + LogQL panels. |
| S4-EVENTS | contract-service ContractEventConfig + publishers (contract.created/status-changed/cancelled) + consumers (proposal.accepted/completed/cancelled, user.deactivated); contracts/.../events/ContractCreatedEvent.java + ContractStatusChangedEvent.java + ContractCancelledEvent.java; contract-service Deployment + Service + ConfigMap; PromQL panels. |
| S4-INFRA | contract-service route + scrape entry + dashboard. Shared infra: entire k8s/monitoring/grafana/ (Deployment + datasources ConfigMap + dashboards ConfigMap embedding all 5 JSONs + PVC + NodePort 30030) + k8s/statefulsets/cassandra-statefulset.yaml + cassandra Service + PVC. |
| S5-READ-DB | wallet-service entity + new endpoint GET /api/payouts/freelancer/{freelancerId}/total; contracts/.../feign/UserServiceClient.java + ContractServiceClient.java + JobServiceClient.java (read uses); wallet-postgres K8s; logback + LogQL panels. |
| S5-EVENTS | wallet-service PaymentEventConfig + publishers (payment.initiated/completed/failed/refunded) + consumers (proposal.completed → create PENDING payout, proposal.cancelled → refund); contracts/.../events/Payment*.java; wallet-service Deployment + Service + ConfigMap; PromQL panels. |
| S5-INFRA | wallet-service route + scrape entry + dashboard. Shared infra: k8s/statefulsets/rabbitmq-statefulset.yaml + rabbitmq Service (5672 + 15672) + PVC + k8s/statefulsets/elasticsearch-statefulset.yaml + ES Service + PVC + saga end-to-end test scenarios A/B/C from §8.6 (JUnit integration tests). |
