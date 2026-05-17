# Milestone 3 — Freelance Marketplace Platform

## True Microservices: Service Isolation, Inter-Service Communication & Kubernetes

> **Weight:** 40% of final grade  
> **Theme:** Freelance Marketplace
>
> **Deadline:** Saturday 17/05/2026 at 11:59 PM

---

## Services in This Theme

| Service          | Module name        | Internal port | Database (M3)           |
| ---------------- | ------------------ | ------------- | ----------------------- |
| User Service     | `user-service`     | 8080          | `freelancedb-users`     |
| Job Service      | `job-service`      | 8080          | `freelancedb-jobs`      |
| Proposal Service | `proposal-service` | 8080          | `freelancedb-proposals` |
| Contract Service | `contract-service` | 8080          | `freelancedb-contracts` |
| Wallet Service   | `wallet-service`   | 8080          | `freelancedb-wallet`    |
| API Gateway      | `api-gateway`      | 8080          | —                       |

---

## What M3 Adds to Your Codebase

M1 built 5 services sharing one PostgreSQL database.  
M2 added 6 databases (polyglot persistence), authentication, caching, and design patterns — still one PostgreSQL, still cross-service SQL JOINs inside that PostgreSQL.  
M3 finishes the transformation:

- **Database isolation** — each service gets its own PostgreSQL instance. No service can open a JDBC connection to another service's database.
- **OpenFeign** — synchronous HTTP calls replace cross-service SQL JOINs for read dependencies.
- **RabbitMQ** — asynchronous events replace cross-service write side-effects.
- **Spring Cloud Gateway** — a 6th Maven module acts as the single entry point. JWT validation moves here.
- **Kubernetes** — all services and databases deploy to a local MiniKube cluster.

### What Does NOT Change

- All 45 M1 features — except the cross-service SQL inside ~16 of them (see sections below)
- All 7 M2 design patterns
- All 6 M2 databases (PostgreSQL + MongoDB + Redis + Elasticsearch + Neo4j + Cassandra)
- JWT authentication (shared secret, stays the same)
- Redis caching (all cached endpoints remain cached)
- MongoDB event logging (Observer pattern stays in place)

### New Proposal Status Values

M3 adds saga-related statuses to the `Proposal` entity's status enum. Existing M1 values (`SUBMITTED`, `SHORTLISTED`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`) are preserved; new values are appended for saga lifecycle tracking:

| New status        | When it is set                                                  |
| ----------------- | --------------------------------------------------------------- |
| `COMPLETING`      | S3 sets this immediately before publishing `proposal.completed` |
| `PAYMENT_PENDING` | S3 sets this when `payment.initiated` event is consumed         |
| `PAID`            | S3 sets this when `payment.completed` event is consumed         |
| `PAYMENT_FAILED`  | S3 sets this when `payment.failed` event is consumed            |
| `REFUNDED`        | S3 sets this when `payment.refunded` event is consumed          |

> **Note on saga entity:** The Proposal acts as the saga trigger entity even though M1's S3-F4 transitions Contract.status (not Proposal.status). M3 lifts the saga state to Proposal so all subscribers correlate by `proposalId`. Contract.status continues to follow its M1 lifecycle (`ACTIVE` → `COMPLETED`/`TERMINATED`/`DISPUTED`) under the saga's direction.

---

## Section 1 — Database Isolation

### 1.1 What Changes

Every service previously connected to a single shared database:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/freelancedb
```

In M3, each service connects to its own database on its own PostgreSQL instance:

```yaml
# user-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://user-postgres:5432/freelancedb-users

# job-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://job-postgres:5432/freelancedb-jobs

# proposal-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://proposal-postgres:5432/freelancedb-proposals

# contract-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://contract-postgres:5432/freelancedb-contracts

# wallet-service application.yml
spring:
  datasource:
    url: jdbc:postgresql://wallet-postgres:5432/freelancedb-wallet
```

### 1.2 Cross-Service FK Columns Become Plain Longs

Every `@ManyToOne` or `@JoinColumn` that pointed to another service's entity becomes a plain `Long` field. The column still exists in the database, but there is no JPA foreign-key relationship across databases. (Most cross-service references in Freelance M1 were already plain `Long` columns per the M1 spec; this section confirms the rule and makes any remaining JPA cross-service relationships plain.)

| Table       | Column          | Before (M1/M2)                      | After (M3)                               |
| ----------- | --------------- | ----------------------------------- | ---------------------------------------- |
| `jobs`      | `client_id`     | `Long` FK reference (already plain) | `private Long clientId;` (unchanged)     |
| `proposals` | `job_id`        | `Long` FK reference (already plain) | `private Long jobId;` (unchanged)        |
| `proposals` | `freelancer_id` | `Long` FK reference (already plain) | `private Long freelancerId;` (unchanged) |
| `contracts` | `job_id`        | `Long` FK reference (already plain) | `private Long jobId;` (unchanged)        |
| `contracts` | `freelancer_id` | `Long` FK reference (already plain) | `private Long freelancerId;` (unchanged) |
| `contracts` | `client_id`     | `Long` FK reference (already plain) | `private Long clientId;` (unchanged)     |
| `contracts` | `proposal_id`   | `Long` FK reference (already plain) | `private Long proposalId;` (unchanged)   |
| `payouts`   | `contract_id`   | `Long` FK reference (already plain) | `private Long contractId;` (unchanged)   |
| `payouts`   | `freelancer_id` | `Long` FK reference (already plain) | `private Long freelancerId;` (unchanged) |

### 1.3 NoSQL Databases — Shared Instance, Separate Ownership

MongoDB, Redis, Elasticsearch, Neo4j, and Cassandra remain as **single shared instances** (one StatefulSet each in Kubernetes). Each service already owns its own collections/indexes/keyspace and never reads another service's data — the logical isolation from M2 is sufficient. Running 5 MongoDB + 5 Redis + 5 Elasticsearch + 5 Neo4j + 5 Cassandra StatefulSets would make MiniKube unrunnable.

**The M3 rule:** No service connects to another service's PostgreSQL. Each service continues to own its MongoDB collections, Redis key prefix, Elasticsearch index, and Cassandra keyspace.

### 1.4 Deliverables for DB Isolation

- [ ] `user-service/application.yml` — datasource URL points to `user-postgres:5432/freelancedb-users`
- [ ] `job-service/application.yml` — datasource URL points to `job-postgres:5432/freelancedb-jobs`
- [ ] `proposal-service/application.yml` — datasource URL points to `proposal-postgres:5432/freelancedb-proposals`
- [ ] `contract-service/application.yml` — datasource URL points to `contract-postgres:5432/freelancedb-contracts`
- [ ] `wallet-service/application.yml` — datasource URL points to `wallet-postgres:5432/freelancedb-wallet`
- [ ] All cross-service `@ManyToOne` fields confirmed as plain `Long` (Freelance's M1 already used plain Longs — verify no regressions)
- [ ] New saga statuses added to `Proposal` status enum

---

## Section 2 — Inter-Service Communication Setup

### 2.1 OpenFeign Dependency

Add to every service that makes Feign calls:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

Add Spring Cloud BOM to `dependencyManagement`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Enable on `@SpringBootApplication`:

```java
@SpringBootApplication
@EnableFeignClients
public class UserServiceApplication { }
```

### 2.2 Feign Client Pattern (Example)

```java
@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/user/{userId}/summary")
    UserContractSummaryDTO getUserContractSummary(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/active-count")
    int getActiveContractCount(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/count")
    long getTotalContractCount(@PathVariable Long userId);
}
```

In `application.yml`, add each service to the one that requires it:

```yaml
feign:
  user-service:
    url: http://user-service:8080
  job-service:
    url: http://job-service:8080
  proposal-service:
    url: http://proposal-service:8080
  contract-service:
    url: http://contract-service:8080
  wallet-service:
    url: http://wallet-service:8080
```

> **Important:** the `url` attribute is **mandatory** in our K8s setup. When `@FeignClient` is given an explicit `url`, Spring Cloud Feign skips its load-balancer logic and calls that URL directly — Kubernetes' built-in DNS + Service load balancing then balances across pods. If you omit `url`, Feign falls back to Spring Cloud LoadBalancer, which expects a service registry (Eureka/Consul) — we do not deploy one, so the Feign call will fail at startup with `LoadBalancer for service-name not available`. Always declare both `name` and `url`.

### 2.3 Correlation ID Propagation

Every service must forward `X-Correlation-ID` on all outgoing Feign calls:

```java
@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }
}
```

### 2.4 Error Handling

Wrap every Feign call in try-catch. Never let a downstream failure crash the calling service, for example:

```java
try {
    UserContractSummaryDTO summary = contractServiceClient.getUserContractSummary(userId);
    return buildDTO(user, summary);
} catch (FeignException.NotFound e) {
    return buildDTO(user, UserContractSummaryDTO.empty());
} catch (FeignException e) {
    log.warn("contract-service unavailable for user {}: {}", userId, e.getMessage());
    throw new ServiceUnavailableException("Contract service temporarily unavailable");
}
```

---

### 2.5 RabbitMQ Dependency

Add to every service that publishes or consumes events:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2.6 RabbitMQ Connection Configuration

Add to every service's `application.yml`:

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```

### 2.7 Topology — What Each Service Must Declare

Each service declares its own RabbitMQ topology as Spring `@Bean`s in a `@Configuration` class. The responsibilities are split:

- **Producer service** declares only the `TopicExchange` it publishes to.
- **Consumer service** declares the `Queue`, the `DLQ`, another `TopicExchange` reference (same name — Spring deduplicates), and the `Binding` that connects them.

Every consumer queue must have a dead-letter queue. The DLQ is wired by declaring the consumer queue with the `x-dead-letter-exchange` and `x-dead-letter-routing-key` arguments pointing at a separate dead-letter exchange + DLQ. Combined with `default-requeue-rejected: false` (§2.6), this means: when a listener method throws and Spring's retry exhausts (`max-attempts: 3`), the message is automatically routed to the DLQ — no manual `basicAck`/`basicNack` code in the consumer.

The exchange type for all Freelance Marketplace events is **`TopicExchange`**. This allows routing key wildcards so future events can be added without declaring a new exchange.

### 2.8 Event Payload Records

Event payloads are plain Java `record` classes, serialized to JSON by Jackson. They live in the shared `contracts/` Maven module under `contracts/src/main/java/com/<teamID>/freelance/contracts/events/` (see §12 folder structure + §13.4 parallelism strategy) — **not** inside each service. Putting them in `contracts/` is structural: every service that consumes a cross-service event must import the record (e.g., user-service consumes `ProposalCompletedEvent`), and if records lived inside the publisher service the consumers would need a Maven dependency on the publisher — producing cyclic dependencies that Maven refuses to build. The `contracts/` module sits one level above all 5 services exactly to hold these shared symbols.

The comments below group the records by **which service publishes them** — they are organizational labels, not file-location headers. All record files live together in the single `contracts/.../events/` package.

```java
// published by proposal-service
public record ProposalCompletedEvent(Long proposalId, Long jobId, Long freelancerId, Long contractId, BigDecimal agreedAmount) {}
public record ProposalCancelledEvent(Long proposalId, Long jobId, Long freelancerId, String reason) {}
public record ProposalAcceptedEvent(Long proposalId, Long jobId, Long freelancerId, BigDecimal bidAmount) {}
public record ProposalWithdrawnEvent(Long proposalId, Long jobId, Long freelancerId) {}

// published by contract-service
public record ContractCreatedEvent(Long contractId, Long proposalId, Long jobId, Long freelancerId, BigDecimal agreedAmount) {}
public record ContractStatusChangedEvent(Long contractId, String oldStatus, String newStatus) {}
public record ContractCancelledEvent(Long contractId, Long proposalId) {}

// published by wallet-service
public record PaymentInitiatedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal amount) {}
public record PaymentCompletedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal amount) {}
public record PaymentFailedEvent(Long payoutId, Long proposalId, Long contractId, String reason) {}
public record PaymentRefundedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal refundAmount) {}

// published by user-service
public record UserRegisteredEvent(Long userId, String email, String role) {}
public record UserDeactivatedEvent(Long userId) {}

// published by job-service
public record JobStatusChangedEvent(Long jobId, String oldStatus, String newStatus) {}
public record JobRatedEvent(Long jobId, Long contractId, Double rating, Long ratedBy) {}
public record JobClosedEvent(Long jobId, Long clientId) {}
```

### 2.9 Full Event Map (Freelance Marketplace)

| Producer         | Exchange          | Routing key               | Payload record               | Consumers                                                   |
| ---------------- | ----------------- | ------------------------- | ---------------------------- | ----------------------------------------------------------- |
| user-service     | `user.events`     | `user.registered`         | `UserRegisteredEvent`        | proposal-service                                            |
| user-service     | `user.events`     | `user.deactivated`        | `UserDeactivatedEvent`       | proposal-service, contract-service                          |
| job-service      | `job.events`      | `job.status-changed`      | `JobStatusChangedEvent`      | proposal-service, contract-service                          |
| job-service      | `job.events`      | `job.rated`               | `JobRatedEvent`              | proposal-service                                            |
| job-service      | `job.events`      | `job.closed`              | `JobClosedEvent`             | proposal-service                                            |
| proposal-service | `proposal.events` | `proposal.accepted`       | `ProposalAcceptedEvent`      | job-service, contract-service                               |
| proposal-service | `proposal.events` | `proposal.completed`      | `ProposalCompletedEvent`     | user-service, job-service, contract-service, wallet-service |
| proposal-service | `proposal.events` | `proposal.cancelled`      | `ProposalCancelledEvent`     | user-service, job-service, contract-service, wallet-service |
| proposal-service | `proposal.events` | `proposal.withdrawn`      | `ProposalWithdrawnEvent`     | job-service                                                 |
| contract-service | `contract.events` | `contract.created`        | `ContractCreatedEvent`       | proposal-service                                            |
| contract-service | `contract.events` | `contract.status-changed` | `ContractStatusChangedEvent` | proposal-service                                            |
| contract-service | `contract.events` | `contract.cancelled`      | `ContractCancelledEvent`     | proposal-service                                            |
| wallet-service   | `payment.events`  | `payment.initiated`       | `PaymentInitiatedEvent`      | proposal-service                                            |
| wallet-service   | `payment.events`  | `payment.completed`       | `PaymentCompletedEvent`      | proposal-service                                            |
| wallet-service   | `payment.events`  | `payment.failed`          | `PaymentFailedEvent`         | proposal-service                                            |
| wallet-service   | `payment.events`  | `payment.refunded`        | `PaymentRefundedEvent`       | proposal-service                                            |

---

## Section 3 — User Service Refactoring (S1)

### New Endpoints S1 Must Expose

No new external endpoints — user-service already exposes `GET /api/users/{id}` (M1 CRUD). Downstream services call this existing endpoint.

### Features That Require Feign Calls

---

#### [S1-F3] Get User Contract Summary

**Branch:** `feat/M3/user/S1-F3/<studentID>`  
**Endpoint:** `GET /api/users/{id}/contract-summary`

**M1 implementation:** Direct native SQL JOIN on the shared `contracts` table aggregating `totalContracts`, `completedContracts`, `terminatedContracts`, `totalEarnings`, `averageContractValue` for the given user.

**M3 change:** Replace the SQL JOIN with a Feign call to contract-service, so that the interface would be:

```java
@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {
    @GetMapping("/api/contracts/user/{userId}/summary")
    UserContractSummaryDTO getUserContractSummary(@PathVariable Long userId);
}
```

`UserContractSummaryDTO` returned by contract-service: `{totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue}`

user-service calls this and merges it with the local `User` data to build the full response DTO with `userId`, `name`.

**Test scenario:**

1. (setup) In user-postgres: create User ID=1 (name="Ahmed"). In contract-postgres: create 5 contracts for freelancerId=1 — 3 COMPLETED with agreedAmounts 500, 1000, 1500; 1 TERMINATED; 1 ACTIVE.
2. (action) `GET /api/users/1/contract-summary` with valid Bearer token.
3. (expect) 200 — `userId=1, name="Ahmed", totalContracts=5, completedContracts=3, terminatedContracts=1, totalEarnings=3000.00, averageContractValue=1000.00`.
4. (verify) No direct JDBC connection from user-postgres to contract-postgres. The contract counts come from a Feign HTTP call.

---

#### [S1-F4] Deactivate User Account

**Branch:** `feat/M3/user/S1-F4/<studentID>`  
**Endpoint:** `PUT /api/users/{id}/deactivate`

**M1 implementation:** `SELECT COUNT(*) FROM contracts WHERE freelancer_id = ? AND status = 'ACTIVE'` directly on the shared database, plus `UPDATE proposals SET status='WITHDRAWN' WHERE freelancer_id = ? AND status='SUBMITTED'`.

**M3 change:** Replace the cross-service SQL with:

1. Feign call to contract-service `GET /api/contracts/user/{userId}/active-count` → returns `int`. If > 0, throw 400 ("User has active contracts").
2. Set the user's status to `DEACTIVATED` in user-postgres and save.
3. Publish `user.deactivated` event to `user.events`. proposal-service consumes this event and withdraws all SUBMITTED proposals for that freelancer in proposal-postgres.

**Test scenario:**

1. (setup) User ID=1 in user-postgres (role=FREELANCER). Contract in contract-postgres: freelancerId=1, status=ACTIVE.
2. (action) `PUT /api/users/1/deactivate` → Feign → contract-service returns active-count=1.
3. (expect) 400 — cannot deactivate user with active contracts.
4. (setup) Update the contract status to COMPLETED in contract-postgres. Add 2 SUBMITTED proposals for freelancerId=1 in proposal-postgres.
5. (action) `PUT /api/users/1/deactivate` → Feign returns active-count=0.
6. (expect) 200 — user status = DEACTIVATED in user-postgres. RabbitMQ `user.deactivated` event published. After event processing, the 2 SUBMITTED proposals in proposal-postgres are now WITHDRAWN.

---

#### [S1-F6] Top Freelancers by Earnings

**Branch:** `feat/M3/user/S1-F6/<studentID>`  
**Endpoint:** `GET /api/users/reports/top-freelancers?startDate={date}&endDate={date}&limit={n}`

**M1 implementation:** Native SQL JOIN of `users` with `payouts` (or `contracts`) GROUP BY freelancer ORDER BY SUM(amount) DESC.

**M3 change:** user-service cannot JOIN the `payouts` table (it lives in wallet-postgres). Instead:

1. Fetch all FREELANCER users from user-postgres.
2. For each freelancer, call Feign → `GET /api/payouts/freelancer/{freelancerId}/total?startDate={d}&endDate={d}` on wallet-service → returns `BigDecimal` (total COMPLETED payout amount for this freelancer in the date range).
3. Sort freelancers by total descending, take first `limit`.
4. Build and return `List<TopFreelancerDTO>` with `userId`, `name`, `totalEarnings`, `contractCount` (also fetched per freelancer via Feign → contract-service).

> **Note on date filtering:** Pass `startDate` and `endDate` as query params to the wallet-service endpoint so it filters server-side rather than fetching all payouts.

```java
@FeignClient(name = "wallet-service", url = "${feign.wallet-service.url}")
public interface WalletServiceClient {
    @GetMapping("/api/payouts/freelancer/{freelancerId}/total")
    BigDecimal getFreelancerPayoutTotal(
        @PathVariable Long freelancerId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```

**Test scenario:**

1. (setup) 3 FREELANCER users in user-postgres (Ahmed, Sara, Omar). In wallet-postgres: COMPLETED payouts within March 2026 — Ahmed = 3000 total, Sara = 8000 total, Omar = 1000 total.
2. (action) `GET /api/users/reports/top-freelancers?startDate=2026-03-01&endDate=2026-03-31&limit=2`.
3. (expect) 200 — `[{userId: Sara, totalEarnings: 8000, contractCount: ...}, {userId: Ahmed, totalEarnings: 3000, contractCount: ...}]`.
4. (verify) user-service made Feign calls to wallet-service for each freelancer's total. No direct query on `payouts` table.

---

#### [S1-F9] Find Users by Language Preference with Minimum Contracts

**Branch:** `feat/M3/user/S1-F9/<studentID>`  
**Endpoint:** `GET /api/users/preferences/language?lang={lang}&minContracts={n}`

**M1 implementation:** JSONB query on `users.preferences` for language match + subquery counting `contracts` rows by `freelancer_id` where status=COMPLETED.

**M3 change:**

1. Query user-postgres for users whose `preferences->>'language'` matches the given value.
2. For each matching user, call Feign → `GET /api/contracts/user/{userId}/completed-count` → returns `long`.
3. Keep only users whose returned count ≥ `minContracts`.

**Test scenario:**

1. (setup) 3 users in user-postgres: User A (preferences `language=ar`), User B (preferences `language=ar`), User C (preferences `language=en`). In contract-postgres: User A has 5 COMPLETED contracts, User B has 2 COMPLETED contracts, User C has 10.
2. (action) `GET /api/users/preferences/language?lang=ar&minContracts=3`.
3. (expect) 200 — only User A returned (User B has 2 < 3).
4. (verify) Feign call made to contract-service for each candidate user.

---

### RabbitMQ: S1 Publishes

| Routing key        | Exchange      | Payload                 | When                                               |
| ------------------ | ------------- | ----------------------- | -------------------------------------------------- |
| `user.registered`  | `user.events` | `{userId, email, role}` | After successful user registration (auth endpoint) |
| `user.deactivated` | `user.events` | `{userId}`              | After S1-F4 successfully sets DEACTIVATED          |

### RabbitMQ: S1 Consumes

| Routing key          | From exchange     | Action                                                                                       |
| -------------------- | ----------------- | -------------------------------------------------------------------------------------------- |
| `proposal.completed` | `proposal.events` | Update freelancer's stats (increment completed contracts, total earnings) in user-postgres   |
| `proposal.cancelled` | `proposal.events` | Reverse freelancer's stats (decrement completed contracts, subtract amount) in user-postgres |

Queue declaration: `user.proposal.saga-listener` with DLQ `user.proposal.saga-listener.dlq`.

### S1 Deliverables

- [ ] Confirm cross-service `@ManyToOne` fields are plain `Long` in User / UserSkill entities (Freelance M1 already used Longs)
- [ ] `feign.contract-service.url` and `feign.wallet-service.url` in `application.yml`
- [ ] `ContractServiceClient` Feign interface with `getUserContractSummary`, `getActiveContractCount`, `getCompletedContractCount`
- [ ] `WalletServiceClient` Feign interface with `getFreelancerPayoutTotal`
- [ ] S1-F3 refactored to use Feign → contract-service
- [ ] S1-F4 refactored to use Feign → contract-service for active-contract check; publishes `user.deactivated` after success
- [ ] S1-F6 refactored to use Feign → wallet-service per freelancer
- [ ] S1-F9 refactored to use Feign → contract-service per matching user
- [ ] RabbitMQ `user.events` TopicExchange declared
- [ ] `user.registered` published on registration
- [ ] `user.deactivated` published on S1-F4
- [ ] Consumer for `proposal.completed` and `proposal.cancelled` with auto ACK + DLQ via `x-dead-letter-exchange`
- [ ] `logback-spring.xml` with Loki4J appender (see Section 11)

---

## Section 4 — Job Service Refactoring (S2)

### New Endpoints S2 Must Expose

These endpoints are called by other services via Feign. They must exist before S1, S3, S4, S5 SYNC branches are merged.

| Endpoint             | Called by | Returns  | Description                                                                                                                     |
| -------------------- | --------- | -------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/jobs/{id}` | S3, S4    | `JobDTO` | Already exists (M1 CRUD). Verify it returns `id, clientId, title, description, category, status, budgetMin, budgetMax, rating`. |

---

#### [S2-F3] Get Job Proposal Summary

**Branch:** `feat/M3/job/S2-F3/<studentID>`  
**Endpoint:** `GET /api/jobs/{id}/proposal-summary?startDate={date}&endDate={date}`

**M1 implementation:** Native SQL aggregate on `proposals` table for this job in date range — `totalProposals`, `averageBidAmount`, `lowestBid`, `highestBid`.

**M3 change:** Replace with Feign → proposal-service.

```java
@FeignClient(name = "proposal-service", url = "${feign.proposal-service.url}")
public interface ProposalServiceClient {
    @GetMapping("/api/proposals/job/{jobId}/summary")
    JobProposalSummaryDTO getJobProposalSummary(
        @PathVariable Long jobId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```

`JobProposalSummaryDTO` from proposal-service: `{totalProposals, averageBidAmount, lowestBid, highestBid}`

job-service merges this with the local `Job.title` to build the response DTO.

**Test scenario:**

1. (setup) Job ID=1 (title="E-Commerce Backend") in job-postgres. 5 proposals in proposal-postgres for jobId=1 with bidAmounts 500, 800, 1200, 600, 900 submitted in March 2026.
2. (action) `GET /api/jobs/1/proposal-summary?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `jobId=1, title="E-Commerce Backend", totalProposals=5, averageBidAmount=800, lowestBid=500, highestBid=1200`.
4. (verify) No direct JOIN on proposal-postgres from job-service.

---

#### [S2-F4] Close Job Posting

**Branch:** `feat/M3/job/S2-F4/<studentID>`  
**Endpoint:** `PUT /api/jobs/{id}/close`

**M1 implementation:** Two cross-service operations on the shared database:

- `SELECT COUNT(*) FROM contracts WHERE job_id = ? AND status = 'ACTIVE'` to check no active contracts exist.
- `UPDATE proposals SET status='REJECTED' WHERE job_id = ? AND status='SUBMITTED'` to reject all submitted proposals.

**M3 change:**

1. Feign call to contract-service `GET /api/contracts/job/{jobId}/active-count` → returns `int`. If > 0, throw 400 ("Job has active contracts").
2. Update the job's status to `CLOSED` in job-postgres and save.
3. Publish `job.closed` event to `job.events`. proposal-service consumes this event and rejects all SUBMITTED proposals for that job in proposal-postgres.

**Test scenario:**

1. (setup) Job ID=1 (status=OPEN) in job-postgres. ACTIVE contract for jobId=1 in contract-postgres.
2. (action) `PUT /api/jobs/1/close` → Feign returns active-count=1.
3. (expect) 400 — cannot close job with active contracts.
4. (setup) Update the contract status to COMPLETED. Add 3 SUBMITTED proposals for jobId=1 in proposal-postgres.
5. (action) `PUT /api/jobs/1/close` → Feign returns active-count=0.
6. (expect) 200 — job status = CLOSED in job-postgres. RabbitMQ `job.closed` event published. After event processing, the 3 SUBMITTED proposals in proposal-postgres are now REJECTED.

---

#### [S2-F7] Rate Job Client After Contract

**Branch:** `feat/M3/job/S2-F7/<studentID>`  
**Endpoint:** `POST /api/jobs/{id}/rate`

**M1 implementation:** `SELECT * FROM contracts WHERE id = ? AND job_id = ? AND status = 'COMPLETED'` on the shared database to verify the contract belongs to this job and is COMPLETED.

**M3 change:** Replace with Feign → contract-service `GET /api/contracts/{contractId}` (reuses existing M1 CRUD endpoint). Validate the returned contract:

- Exists (Feign returns 404 → throw 404)
- `jobId` matches the job being rated (mismatch → throw 400)
- `status` = `COMPLETED` (wrong status → throw 400)
- Rating is between 1 and 5 (out of range → throw 400)

If all checks pass, recalculate Job's running average rating, update `Job.rating` and `Job.totalRatings`, and save. Publish `job.rated` RabbitMQ event.

**Test scenario:**

1. (setup) Job ID=1 (rating=0.0, totalRatings=0) in job-postgres. Contract ID=10 in contract-postgres: jobId=1, status=COMPLETED.
2. (action) `POST /api/jobs/1/rate` body `{contractId: 10, rating: 5}`.
3. (expect) 200 — Job rating=5.0, totalRatings=1. `job.rated` event published.
4. (action) Same call again with rating=3 referencing a different completed contract → Job rating=4.0, totalRatings=2.
5. (action) `POST /api/jobs/1/rate` body `{contractId: 99, rating: 4}` → Feign returns 404 → throw 404.
6. (action) `POST /api/jobs/1/rate` body `{contractId: 10, rating: 6}` → 400 (rating out of range).
7. (action) Rate with a contract belonging to a different job → 400.

---

#### [S2-F12] Get Job Market Dashboard _(M2)_

**Branch:** `feat/M3/job/S2-F12/<studentID>`  
**Endpoint:** `GET /api/jobs/{id}/dashboard`

**M2 implementation:** Aggregated `totalProposals`, `acceptedProposals`, `averageBidAmount` from `proposals WHERE job_id = ?` on the shared database. `activeAttachments` and `rating` come from S2's own tables (job_attachments + jobs — no change needed).

**M3 change:** The `proposals` aggregation becomes a Feign call to proposal-service — reuse the same `GET /api/proposals/job/{jobId}/summary` endpoint from S2-F3 (extend the DTO to include `acceptedProposals`).

**Test scenario:**

1. (setup) Job ID=5 in job-postgres with 2 active job_attachments (expiryDate >= today) and rating=4.5. In proposal-postgres: 5 proposals for jobId=5 with bidAmounts 500/800/1000/1200/900 (1 ACCEPTED, others SUBMITTED).
2. (action) `GET /api/jobs/5/dashboard` with valid Bearer token.
3. (expect) 200 — `jobId=5, title=..., totalProposals=5, acceptedProposals=1, averageBidAmount=880, activeAttachments=2, rating=4.5`.
4. (action) `GET /api/jobs/999/dashboard` → 404.

---

### RabbitMQ: S2 Publishes

| Routing key          | Exchange     | Payload                                | When                                  |
| -------------------- | ------------ | -------------------------------------- | ------------------------------------- |
| `job.status-changed` | `job.events` | `{jobId, oldStatus, newStatus}`        | When job transitions between statuses |
| `job.rated`          | `job.events` | `{jobId, contractId, rating, ratedBy}` | After S2-F7 rating submission         |
| `job.closed`         | `job.events` | `{jobId, clientId}`                    | After S2-F4 closes the job            |

### RabbitMQ: S2 Consumes

| Routing key          | From exchange     | Action                                                                                |
| -------------------- | ----------------- | ------------------------------------------------------------------------------------- |
| `proposal.accepted`  | `proposal.events` | Update job status to `IN_PROGRESS` for the referenced jobId in job-postgres           |
| `proposal.completed` | `proposal.events` | Update job status to `CLOSED` for the referenced jobId in job-postgres                |
| `proposal.cancelled` | `proposal.events` | If saga compensation, revert job status if needed (stats reverse)                     |
| `proposal.withdrawn` | `proposal.events` | If this was the only active proposal, revert job status to `OPEN` (M1 S3-F7 behavior) |

Queue declaration: `job.proposal.saga-listener` with DLQ `job.proposal.saga-listener.dlq`.

### S2 Deliverables

- [ ] DB isolation: datasource → `freelancedb-jobs`
- [ ] `feign.contract-service.url` and `feign.proposal-service.url` in `application.yml`
- [ ] `ContractServiceClient` Feign interface with `getActiveContractCountForJob`, `getContract`
- [ ] `ProposalServiceClient` Feign interface with `getJobProposalSummary`
- [ ] S2-F3 refactored to use Feign → proposal-service
- [ ] S2-F4 refactored to use Feign → contract-service for active-contract check; publishes `job.closed`
- [ ] S2-F7 refactored to use Feign → contract-service for contract validation; publishes `job.rated`
- [ ] S2-F12 refactored to use Feign → proposal-service for proposal aggregation
- [ ] RabbitMQ `job.events` TopicExchange declared
- [ ] `job.status-changed` published on status transitions
- [ ] `job.rated` published on S2-F7
- [ ] `job.closed` published on S2-F4
- [ ] Consumer for `proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn` with auto ACK + DLQ via `x-dead-letter-exchange`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 5 — Proposal Service Refactoring (S3)

### New Endpoints S3 Must Expose

These are called by S1, S2, S4, S5 via Feign. All must be implemented before the ASYNC branches merge.

| Endpoint                                 | Called by                | Returns                 | Description                                                                                           |
| ---------------------------------------- | ------------------------ | ----------------------- | ----------------------------------------------------------------------------------------------------- |
| `GET /api/proposals/job/{jobId}/summary` | S2 (S2-F3, S2-F12)       | `JobProposalSummaryDTO` | `{totalProposals, acceptedProposals, averageBidAmount, lowestBid, highestBid}` filtered by date range |
| `GET /api/proposals/{proposalId}`        | S4 (saga), S5 (saga), S2 | `ProposalDTO`           | Already exists (M1 CRUD). Verify it returns `id, jobId, freelancerId, status, bidAmount, acceptedAt`. |

---

#### [S3-F2] Accept Proposal and Create Contract

**Branch:** `feat/M3/proposal/S3-F2/<studentID>`  
**Endpoint:** `PUT /api/proposals/{proposalId}/accept`

**M1 implementation:** Multi-step transactional operation on the shared database:

- `SELECT * FROM users WHERE id = ? AND role='FREELANCER'` to verify the freelancer.
- `UPDATE jobs SET status='IN_PROGRESS' WHERE id = ?`.
- `INSERT INTO contracts (...) VALUES (...)` with status=ACTIVE, agreedAmount=bidAmount, startDate=now.

**M3 change:** Replace each cross-service step:

1. Find proposal (404 if not found). Validate proposal status is SUBMITTED or SHORTLISTED (400).
2. Feign → user-service `GET /api/users/{freelancerId}` → verify role is `FREELANCER` (404 if not found, 400 if not a freelancer).
3. Set the proposal's status to `ACCEPTED` and `acceptedAt = now()` in proposal-postgres. Save.
4. Publish `proposal.accepted` event to `proposal.events`. job-service consumes this event and updates the job status to `IN_PROGRESS`. contract-service consumes this event and creates a new ACTIVE contract record with the agreed amount.
5. Return the updated proposal.

```java
@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {
    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable Long id);
}
```

> **Note:** Contract creation moves from inline INSERT to event-driven. The proposal's `acceptedAt` is set immediately for client-side responsiveness, but the contract record exists only after `proposal.accepted` is consumed by contract-service. To avoid a race condition where S5-F4 (Process Payout) is called before the contract record exists, S5-F4 retries Feign → contract-service with a brief backoff if the contract is not yet found — see §7.

**Test scenario:**

1. (setup) Proposal ID=1 in proposal-postgres: status=SUBMITTED, jobId=10, freelancerId=5, bidAmount=2000. Job ID=10 in job-postgres: status=OPEN. User ID=5 in user-postgres: role=FREELANCER.
2. (action) `PUT /api/proposals/1/accept` → Feign returns user with role=FREELANCER.
3. (expect) 200 — proposal status=ACCEPTED, `acceptedAt` set. `proposal.accepted` event published.
4. (verify after event processing) job-postgres job ID=10 status=IN_PROGRESS; contract-postgres has a new ACTIVE contract referencing proposalId=1, jobId=10, freelancerId=5, agreedAmount=2000.
5. (action) Try accepting again → 400 (proposal not SUBMITTED/SHORTLISTED).
6. (action) Try with a user whose role is CLIENT → 400. Try with freelancerId=999 → Feign 404 → 404.

---

#### [S3-F11] Record Freelancer-Job Interaction _(M2)_

**Branch:** `feat/M3/proposal/S3-F11/<studentID>`  
**Endpoint:** `POST /api/proposals/{proposalId}/record-interaction`

**M2 implementation:** Cross-service native SQL pattern on shared database — query `users WHERE id = ?` (freelancer name) and `jobs WHERE id = ?` (job title) to fetch enrichment data for the Neo4j graph node creation.

**M3 change:** Replace both SQL queries with Feign calls.

```java
// Get user details from user-service
UserDTO freelancer = userServiceClient.getUser(proposal.getFreelancerId());

// Get job details from job-service
JobDTO job = jobServiceClient.getJob(proposal.getJobId());

// Then proceed with Neo4j graph write as in M2
// (FreelancerNode, JobNode, PROPOSED_TO relationship with idempotency marker)
```

The rest of the feature (Neo4j idempotency check via `recorded_proposal_ids` collection on the relationship, `PROPOSED_TO` relationship increment, MongoDB `INTERACTION_RECORDED` event logging) is unchanged from M2.

**Test scenario:**

1. (setup) User ID=5 in user-postgres (role=FREELANCER, name="Sara"). Job ID=10 in job-postgres (title="E-Commerce Backend"). Submitted proposal ID=20 in proposal-postgres: freelancerId=5, jobId=10.
2. (action) `POST /api/proposals/20/record-interaction`.
3. (expect) 200. Neo4j has (Freelancer:5)-[:PROPOSED_TO {proposalCount:1, recorded_proposal_ids:[20]}]->(Job:10). MongoDB has INTERACTION_RECORDED event.
4. (verify) Feign calls made to user-service and job-service. No direct SQL on user-postgres or job-postgres from proposal-service.

---

#### [S3-F12] Get Recommended Jobs for Freelancer _(M2)_

**Branch:** `feat/M3/proposal/S3-F12/<studentID>`  
**Endpoint:** `GET /api/proposals/recommendations?freelancerId={id}&limit={n}`

**M2 implementation:** After traversing the Neo4j graph to find candidate jobs, the M2 spec enriches each result with the job's `title` and `category` from the shared `jobs` table via native SQL.

**M3 change:** Replace SQL enrichment with Feign calls.

```java
// Verify the requesting freelancer exists (still needs PG existence check)
UserDTO freelancer = userServiceClient.getUser(freelancerId);

// For each candidate jobId from Neo4j graph traversal:
JobDTO job = jobServiceClient.getJob(candidateJobId);
// Build JobRecommendationDTO from job + score
```

The Neo4j graph traversal logic, score calculation, and ownership check (caller's `uid` must match `freelancerId` or be ADMIN) are unchanged from M2.

**Test scenario:**

1. (setup) 3 freelancers (A=ID 1, B=ID 2, C=ID 3) in user-postgres. 4 jobs (J1 WEB_DEV, J2 MOBILE, J3 WEB_DEV, J4 DESIGN) in job-postgres. Neo4j edges: A→J1, A→J2, B→J1, B→J3, C→J2, C→J4.
2. (action) `GET /api/proposals/recommendations?freelancerId=1&limit=5` with A's token.
3. (expect) 200 — should include J3 (B also proposed to J1 that A proposed to) and J4 (C also proposed to J2 that A proposed to). Should NOT include J1 or J2.
4. (verify) Feign calls to user-service for A's existence and to job-service for J3 and J4 enrichment. No SQL on user-postgres or job-postgres from proposal-service.

---

> **S3-F4 and S3-F7** are RabbitMQ-based saga participants, not Feign refactors. They are fully described in **Section 8 (Proposal Lifecycle Saga & Cancellation Cascade)**.

---

### RabbitMQ: S3 Publishes

| Routing key          | Exchange          | Payload                                                       | When                                                   |
| -------------------- | ----------------- | ------------------------------------------------------------- | ------------------------------------------------------ |
| `proposal.accepted`  | `proposal.events` | `{proposalId, jobId, freelancerId, bidAmount}`                | After S3-F2 accepts the proposal                       |
| `proposal.completed` | `proposal.events` | `{proposalId, jobId, freelancerId, contractId, agreedAmount}` | After S3-F4 completes the proposal (saga trigger)      |
| `proposal.cancelled` | `proposal.events` | `{proposalId, jobId, freelancerId, reason}`                   | After S3-F7 cancel/withdraw, or after compensation     |
| `proposal.withdrawn` | `proposal.events` | `{proposalId, jobId, freelancerId}`                           | After S3-F7 withdraws a SUBMITTED/SHORTLISTED proposal |

### RabbitMQ: S3 Consumes

| Routing key         | From exchange     | Action                                                                                        |
| ------------------- | ----------------- | --------------------------------------------------------------------------------------------- |
| `contract.created`  | `contract.events` | Link the contractId into the proposal record (store `contractId` on the Proposal)             |
| `payment.initiated` | `payment.events`  | Mark proposal status = `PAYMENT_PENDING`                                                      |
| `payment.completed` | `payment.events`  | Mark proposal status = `PAID`                                                                 |
| `payment.failed`    | `payment.events`  | Mark proposal status = `PAYMENT_FAILED` → publish `proposal.cancelled` (compensation trigger) |
| `payment.refunded`  | `payment.events`  | Mark proposal status = `REFUNDED`                                                             |

Queue declaration: `proposal.saga-feedback` with DLQ `proposal.saga-feedback.dlq`.

### S3 Deliverables

- [ ] DB isolation: datasource → `freelancedb-proposals`
- [ ] Add saga statuses to Proposal status enum: COMPLETING, PAYMENT_PENDING, PAID, PAYMENT_FAILED, REFUNDED
- [ ] Expose `GET /api/proposals/job/{jobId}/summary`
- [ ] `feign.user-service.url` and `feign.job-service.url` and `feign.contract-service.url` in `application.yml`
- [ ] `UserServiceClient` Feign interface with `getUser`
- [ ] `JobServiceClient` Feign interface with `getJob`
- [ ] `ContractServiceClient` Feign interface with `getContract`
- [ ] S3-F2 refactored to use Feign → user-service for freelancer validation; publishes `proposal.accepted`
- [ ] S3-F4 refactored: pre-saga Feign checks + publish `proposal.completed` (no direct payout insert) — see §8
- [ ] S3-F7 refactored: publish `proposal.withdrawn` / `proposal.cancelled` (no direct job/contract write) — see §8
- [ ] S3-F11 refactored to use Feign → user-service + job-service
- [ ] S3-F12 refactored to use Feign → user-service + job-service
- [ ] RabbitMQ `proposal.events` TopicExchange declared
- [ ] Consumers for `contract.created`, `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 6 — Contract Service Refactoring (S4)

### New Endpoints S4 Must Expose

| Endpoint                                           | Called by              | Returns                  | Description                                                                                                                            |
| -------------------------------------------------- | ---------------------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/contracts/user/{userId}/summary`         | S1 (S1-F3)             | `UserContractSummaryDTO` | `{totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue}`                                       |
| `GET /api/contracts/user/{userId}/active-count`    | S1 (S1-F4)             | `int`                    | Count of contracts with status=ACTIVE for this freelancer                                                                              |
| `GET /api/contracts/user/{userId}/completed-count` | S1 (S1-F9)             | `long`                   | Count of contracts with status=COMPLETED for this freelancer                                                                           |
| `GET /api/contracts/job/{jobId}/active-count`      | S2 (S2-F4)             | `int`                    | Count of contracts with status=ACTIVE for this job                                                                                     |
| `GET /api/contracts/proposal/{proposalId}/active`  | S3 (saga pre-check)    | `ContractDTO`            | Returns the ACTIVE contract for this proposalId. **404 if none.** This is a new endpoint not in M1/M2.                                 |
| `GET /api/contracts/{contractId}`                  | S2 (S2-F7), S5 (S5-F4) | `ContractDTO`            | Already exists (M1 CRUD). Verify it returns `id, jobId, freelancerId, clientId, proposalId, agreedAmount, status, startDate, endDate`. |

---

#### [S4-F1] Get Active Contract for User

**Branch:** `feat/M3/contract/S4-F1/<studentID>`  
**Endpoint:** `GET /api/contracts/user/{userId}/active`

**M1 implementation:** Verify user exists via `SELECT FROM users WHERE id = ?` on shared database before querying contracts.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(userId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("User not found: " + userId);
}
// Then query local contracts table for the most recent ACTIVE contract
```

**Test scenario:**

1. (setup) User ID=1 in user-postgres. 3 contracts in contract-postgres for freelancerId=1, all ACTIVE, with different `createdAt` timestamps.
2. (action) `GET /api/contracts/user/1/active`.
3. (expect) 200 — the most recent ACTIVE contract.
4. (action) `GET /api/contracts/user/999/active` → Feign → user-service throws 404 → 404.
5. (action) User ID=2 with no active contracts → 404.

---

#### [S4-F3] Find Contracts by Budget Range with Freelancer Info

**Branch:** `feat/M3/contract/S4-F3/<studentID>`  
**Endpoint:** `GET /api/contracts/search?minAmount={a}&maxAmount={a}&status={s}`

**M1 implementation:** Native SQL JOIN of `contracts` with `users` (for freelancer name) and `jobs` (for job title) — DTO contains `freelancerName` and `jobTitle` from those external services' tables.

**M3 change:** Two Feign calls replace the JOIN:

1. Query contract-postgres for contracts in the budget range and status filter (local query, no JOIN).
2. For each contract, Feign → user-service `GET /api/users/{freelancerId}` → get `name`.
3. For each contract, Feign → job-service `GET /api/jobs/{jobId}` → get `title`.
4. Compute `durationDays` from local `startDate`/`endDate`. Build `ContractSummaryDTO`.

> **Optimization:** Cache the `freelancerId → name` and `jobId → title` lookups locally (in a Map) within the request lifecycle to avoid duplicate Feign calls when contracts share users or jobs.

**Test scenario:**

1. (setup) 3 contracts in contract-postgres: agreedAmount 1000 (ACTIVE), 3000 (COMPLETED), 5000 (ACTIVE). Corresponding users in user-postgres (Ahmed, Sara, Omar) and jobs in job-postgres ("E-Commerce Backend", "Mobile App UI", "Data Pipeline").
2. (action) `GET /api/contracts/search?minAmount=2000&maxAmount=6000&status=ACTIVE`.
3. (expect) 200 — single result for the 5000 ACTIVE contract with `freelancerName` and `jobTitle` populated from Feign.
4. (verify) Feign calls to user-service and job-service. No direct SQL on user-postgres or job-postgres from contract-service.

---

#### [S4-F8] Freelancer Performance Summary

**Branch:** `feat/M3/contract/S4-F8/<studentID>`  
**Endpoint:** `GET /api/contracts/freelancer/{freelancerId}/summary?startDate={d}&endDate={d}`

**M1 implementation:** Verify the freelancer exists via `SELECT FROM users WHERE id = ?` on the shared database before aggregating.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(freelancerId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("Freelancer not found: " + freelancerId);
}
// Then aggregate locally from contract-postgres
```

The aggregation logic (totalContracts, totalEarnings, averageContractValue, completionRate, averageDurationDays) remains local — the `contracts` table is owned by S4.

**Test scenario:**

1. (setup) Freelancer ID=1 in user-postgres. 5 contracts in contract-postgres for freelancerId=1 (March 2026): 4 COMPLETED (amounts 1000/1500/2000/2500; durations 10/15/20/25 days), 1 TERMINATED.
2. (action) `GET /api/contracts/freelancer/1/summary?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `totalContracts=5, totalEarnings=7000, averageContractValue=1400, completionRate=80.0, averageDurationDays=17.5`.
4. (action) Freelancer ID=999 → Feign returns 404 → 404.

---

#### [S4-F9] Find Stalled Contracts

**Branch:** `feat/M3/contract/S4-F9/<studentID>`  
**Endpoint:** `GET /api/contracts/stalled?maxProgress={p}&stalledDays={d}`

**M1 implementation:** Native SQL JOIN of `contracts` with `users` (freelancer name) and `jobs` (job title) — DTO contains `freelancerName` and `jobTitle`.

**M3 change:** Same Feign-enrichment pattern as S4-F3:

1. Query contract-postgres for ACTIVE contracts whose JSONB `metadata.progressPercentage` ≤ `maxProgress` and whose `metadata.lastActivityDate` is more than `stalledDays` days ago.
2. For each candidate contract, Feign → user-service for `freelancerName` and Feign → job-service for `jobTitle`.
3. Compute `daysSinceLastActivity` locally. Build `StalledContractDTO`.

**Test scenario:**

1. (setup) 3 ACTIVE contracts in contract-postgres with users + jobs in their respective DBs:
   - Contract A (progressPercentage=10, lastActivityDate=30 days ago)
   - Contract B (progressPercentage=5, lastActivityDate=2 days ago)
   - Contract C (progressPercentage=80, lastActivityDate=30 days ago)
2. (action) `GET /api/contracts/stalled?maxProgress=50&stalledDays=7`.
3. (expect) 200 — only Contract A returned (progress ≤ 50 AND stalled > 7 days), with `freelancerName` and `jobTitle` populated via Feign.
4. (verify) Feign calls only for the surviving candidate (A); B and C are filtered out before enrichment.

---

### Features Verified as NOT Cross-Service (Freelance-Specific)

**S4-F4 (Batch Contract Status Update)** — operates only on the local `contracts` table; the request body carries the IDs and target statuses. Validates each contract exists in contract-postgres only. **No M3 change required.**

**S4-F6 (Get Contracts in Date Range)** — pure local query on `contracts` filtered by `createdAt` and optional `status`. Response is the contract list. **No M3 change required.**

**S4-F7 (Purge Old Contract Data)** — local DELETE on `contracts` filtered by status (COMPLETED/TERMINATED) and age. **No M3 change required.**

**S4-F2 (Update Contract Progress with Metadata)** — local JSONB update on `contracts.metadata`. **No M3 change required.**

**S4-F5 (Filter Contracts by Metadata)** — local JSONB query on `contracts.metadata`. **No M3 change required.**

**S4-F10 (Contract Analytics Dashboard) (M2)** — aggregates only over `contracts` per the M2 spec. The DTO does not require freelancer or job enrichment. **No M3 change required.**

### RabbitMQ: S4 Publishes

| Routing key               | Exchange          | Payload                                                       | When                                                                           |
| ------------------------- | ----------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `contract.created`        | `contract.events` | `{contractId, proposalId, jobId, freelancerId, agreedAmount}` | After consuming `proposal.accepted` and creating the Contract                  |
| `contract.status-changed` | `contract.events` | `{contractId, oldStatus, newStatus}`                          | When contract status is updated (e.g., COMPLETED on saga completion)           |
| `contract.cancelled`      | `contract.events` | `{contractId, proposalId}`                                    | After consuming `proposal.cancelled` (compensation) and reverting the contract |

### RabbitMQ: S4 Consumes

| Routing key          | From exchange     | Action                                                                                                                                                                                                                               |
| -------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `proposal.accepted`  | `proposal.events` | Create new Contract entity (status=ACTIVE) in contract-postgres → publish `contract.created`                                                                                                                                         |
| `proposal.completed` | `proposal.events` | Update Contract status to COMPLETED + set endDate → publish `contract.status-changed`                                                                                                                                                |
| `proposal.cancelled` | `proposal.events` | If a Contract exists for this proposal, set its status to `TERMINATED` (covers both ACTIVE → TERMINATED early cancellation and COMPLETED → TERMINATED post-completion compensation when payout fails) → publish `contract.cancelled` |
| `user.deactivated`   | `user.events`     | Optional bookkeeping: log deactivation against ACTIVE contracts (no status change unless business rule requires)                                                                                                                     |

Queue declarations: `contract.saga-listener` with DLQ `contract.saga-listener.dlq`.

### S4 Deliverables

- [ ] DB isolation: datasource → `freelancedb-contracts`
- [ ] Implement `GET /api/contracts/user/{userId}/summary`, `active-count`, `completed-count`
- [ ] Implement `GET /api/contracts/job/{jobId}/active-count`
- [ ] Implement `GET /api/contracts/proposal/{proposalId}/active` — returns active contract or 404
- [ ] `feign.user-service.url` and `feign.job-service.url` in `application.yml`
- [ ] `UserServiceClient` Feign interface with `getUser`
- [ ] `JobServiceClient` Feign interface with `getJob`
- [ ] S4-F1 refactored to use Feign → user-service for user existence
- [ ] S4-F3 refactored to use Feign → user-service + job-service for enrichment
- [ ] S4-F8 refactored to use Feign → user-service for freelancer existence
- [ ] S4-F9 refactored to use Feign → user-service + job-service for enrichment
- [ ] RabbitMQ `contract.events` TopicExchange declared
- [ ] Consumer for `proposal.accepted`: create Contract, publish `contract.created`
- [ ] Consumer for `proposal.completed`: mark Contract COMPLETED, publish `contract.status-changed`
- [ ] Consumer for `proposal.cancelled`: revert Contract, publish `contract.cancelled`
- [ ] Consumer for `user.deactivated` (optional bookkeeping)
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 7 — Wallet Service Refactoring (S5)

### New Endpoints S5 Must Expose

| Endpoint                                                             | Called by  | Returns      | Description                                                                             |
| -------------------------------------------------------------------- | ---------- | ------------ | --------------------------------------------------------------------------------------- |
| `GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate` | S1 (S1-F6) | `BigDecimal` | Total COMPLETED payout amount for this freelancer in the date range. 0.0 if no payouts. |

---

#### [S5-F3] Freelancer Payout Summary

**Branch:** `feat/M3/wallet/S5-F3/<studentID>`  
**Endpoint:** `GET /api/payouts/freelancer/{freelancerId}/summary`

**M1 implementation:** `SELECT FROM users WHERE id = ?` on the shared database to verify the freelancer exists before aggregating payouts.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(freelancerId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("Freelancer not found: " + freelancerId);
}
// Then aggregate locally from wallet-postgres (group by method)
```

The aggregation logic (`totalPayouts`, `totalAmount`, `methodBreakdown`) remains local — the `payouts` table is owned by S5.

**Test scenario:**

1. (setup) Freelancer ID=1 in user-postgres. 4 COMPLETED payouts in wallet-postgres for freelancerId=1: 2 BANK_TRANSFER (1500+2000=3500), 1 PAYPAL (800), 1 CRYPTO (500).
2. (action) `GET /api/payouts/freelancer/1/summary`.
3. (expect) 200 — `totalPayouts=4, totalAmount=4800, methodBreakdown={BANK_TRANSFER:3500, PAYPAL:800, CRYPTO:500}`.
4. (action) `GET /api/payouts/freelancer/999/summary` → Feign → user-service throws 404 → 404.

---

#### [S5-F4] Process Payout for Contract

**Branch:** `feat/M3/wallet/S5-F4/<studentID>`  
**Endpoint:** `POST /api/payouts/contract/{contractId}`

**M1 implementation:** `SELECT * FROM contracts WHERE id = ?` on the shared database to verify the contract exists and is COMPLETED.

**M3 change:** Replace contract status validation with Feign → contract-service.

```java
ContractDTO contract = contractServiceClient.getContract(contractId);
// In M3 saga context, the contract should be COMPLETED (saga has marked it after proposal.completed → contract.status-changed)
if (!contract.getStatus().equals("COMPLETED")) {
    throw new BadRequestException("Contract is not completed. Status: " + contract.getStatus());
}
// Authorization: only the client who owns the contract (or an ADMIN) may release the payout.
Long callerId = Long.parseLong(headers.getFirst("X-User-Id"));
String callerRole = headers.getFirst("X-User-Role");
if (!"ADMIN".equals(callerRole) && !contract.getClientId().equals(callerId)) {
    throw new ForbiddenException("Only the contract's client (or an ADMIN) can release this payout");
}
```

> **Race-resilient Feign call (referenced from §5 S3-F2 note).** If `contractClient.getContract(contractId)` throws `FeignException.NotFound`, the contract record may be in-flight from the `proposal.accepted` → `contract.created` event. Retry up to **3 attempts with 200 ms exponential backoff** (200 ms → 400 ms → 800 ms) before giving up and throwing `404`. Implement with `@Retryable(value = FeignException.NotFound.class, maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2))` on a small wrapper method, or with manual `Thread.sleep` retry. Other `FeignException` subclasses (5xx, connection errors) are NOT retried — fall through to the existing try/catch from §2.4.

> **Idempotency on duplicate POST.** `POST /api/payouts/contract/{contractId}` is the only endpoint in this saga that a human client triggers and may retry on flaky networks. To stay idempotent: at the start of S5-F4, look up the PENDING Payout for `contractId`; if its status is already `COMPLETED` or `FAILED`, return `200` with the existing Payout (no second `payment.completed`/`payment.failed` event published). Only mutate + publish when the Payout is still `PENDING`.
>
> _Migration note from M1:_ M1 threw `400` ("already paid") on a duplicate POST. M3 upgrades this to `200` with the existing record so retry-safe clients work correctly under network partitions. M1 clients that explicitly handled the `400 "already paid"` response should now also accept `200` with `status=COMPLETED` (or `FAILED`) as the same outcome — both responses convey "this payout is already done."

After successful payout processing:

- Update the existing PENDING Payout record (created by the saga via `payment.initiated`) in wallet-postgres (status = COMPLETED, set method, populate JSONB transactionDetails).
- Publish `payment.completed` to `payment.events`. **Note: this endpoint does NOT publish `payment.initiated` — that event was already emitted by the `proposal.completed` consumer that pre-created the PENDING payout. S5-F4 only publishes `payment.completed` or `payment.failed`.**

On payout failure (or if `?simulateFailure=true` is passed per M2 affordance):

- Mark Payout status = FAILED.
- Publish `payment.failed` to `payment.events`.

**Test scenario:**

1. (setup) Contract ID=1 in contract-postgres: status=COMPLETED, agreedAmount=3000, clientId=42. PENDING Payout in wallet-postgres for contractId=1 (created by the saga earlier).
2. (action) `POST /api/payouts/contract/1` body `{method: "BANK_TRANSFER", accountLastFour: "9876"}` with `X-User-Id: 42, X-User-Role: CLIENT`.
3. (expect) 201 — Payout status=COMPLETED, method=BANK_TRANSFER, JSONB populated. `payment.completed` event published.
4. (verify) Feign call to contract-service to validate status=COMPLETED. No direct query on contract-postgres.
5. (action) Contract status=ACTIVE (not COMPLETED) → 400.
6. (action) Same POST with `X-User-Id: 99` (a different client) → 403.
7. (action) Same POST with `X-User-Id: 99, X-User-Role: ADMIN` → succeeds (admin override).
8. (action) **Idempotency:** call `POST /api/payouts/contract/1` a second time after step 3 — Payout is already COMPLETED → 200 returning the existing record, no second `payment.completed` published.

---

#### [S5-F10] Get Platform Fee Analytics by Job Category _(M2)_

**Branch:** `feat/M3/wallet/S5-F10/<studentID>`  
**Endpoint:** `GET /api/payouts/analytics/category?startDate={date}&endDate={date}`

**M2 implementation:** Three-table JOIN on the shared database: `payouts JOIN contracts ON contracts.id = payouts.contract_id JOIN jobs ON jobs.id = contracts.job_id` — reads `jobs.category` from the shared database.

**M3 change:** Two rounds of Feign calls replace the JOIN:

1. Fetch all COMPLETED payouts in date range from wallet-postgres (local query, no JOIN).
2. For each payout, Feign → contract-service `GET /api/contracts/{contractId}` → get `jobId`.
3. For each `jobId`, Feign → job-service `GET /api/jobs/{jobId}` → get `category`.
4. Group by `category`, aggregate `platformFeeRevenue` (read from `transactionDetails.platformFee` JSONB key per M2 spec, fallback to `0.10 * amount` if missing), `netPayoutRevenue`, `totalRevenue`, `payoutCount`.

> **Optimization:** Cache `contractId → jobId` and `jobId → category` lookups locally (Map within request lifecycle) to avoid duplicate Feign calls when payouts share contracts or jobs.

**Test scenario:**

1. (setup) Jobs in job-postgres: J1 (WEB_DEV), J2 (MOBILE), J3 (WEB_DEV). Contracts in contract-postgres: C1→J1, C2→J2, C3→J3, C4→J1, C5→J2. Payouts in wallet-postgres (March 2026): 3 COMPLETED on WEB_DEV contracts totaling 6000 (platformFee 600), 2 COMPLETED on MOBILE contracts totaling 4000 (platformFee 400).
2. (action) `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`.
3. (expect) 200 — `[{category: WEB_DEV, netPayoutRevenue: 5400, platformFeeRevenue: 600, totalRevenue: 6000, payoutCount: 3}, {category: MOBILE, netPayoutRevenue: 3600, platformFeeRevenue: 400, totalRevenue: 4000, payoutCount: 2}]`.
4. (verify) No JOIN across wallet-postgres, contract-postgres, and job-postgres. Two rounds of Feign calls.

---

### RabbitMQ: S5 Publishes

| Routing key         | Exchange         | Payload                                            | When                                                               |
| ------------------- | ---------------- | -------------------------------------------------- | ------------------------------------------------------------------ |
| `payment.initiated` | `payment.events` | `{payoutId, proposalId, contractId, amount}`       | After consuming `proposal.completed` and creating a PENDING payout |
| `payment.completed` | `payment.events` | `{payoutId, proposalId, contractId, amount}`       | After S5-F4 successfully processes payout                          |
| `payment.failed`    | `payment.events` | `{payoutId, proposalId, contractId, reason}`       | After S5-F4 fails to process payout                                |
| `payment.refunded`  | `payment.events` | `{payoutId, proposalId, contractId, refundAmount}` | After consuming `proposal.cancelled` and refunding the payout      |

### RabbitMQ: S5 Consumes

| Routing key          | From exchange     | Action                                                                                                                             |
| -------------------- | ----------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `proposal.completed` | `proposal.events` | Feign → user-service for freelancer profile → create PENDING payout in wallet-postgres → publish `payment.initiated`               |
| `proposal.cancelled` | `proposal.events` | If a PENDING/COMPLETED payout exists for this proposal → process refund (apply S5-F12 reversal logic) → publish `payment.refunded` |

Queue declaration: `payment.saga-listener` with DLQ `payment.saga-listener.dlq`.

### S5 Deliverables

- [ ] DB isolation: datasource → `freelancedb-wallet`
- [ ] Expose `GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate`
- [ ] `feign.user-service.url`, `feign.contract-service.url`, `feign.job-service.url` in `application.yml`
- [ ] `UserServiceClient` Feign interface with `getUser`
- [ ] `ContractServiceClient` Feign interface with `getContract`
- [ ] `JobServiceClient` Feign interface with `getJob`
- [ ] S5-F3 refactored to use Feign → user-service for user existence check
- [ ] S5-F4 refactored to use Feign → contract-service for contract status validation; publishes `payment.completed` or `payment.failed`
- [ ] S5-F10 refactored to use Feign → contract-service + job-service for category grouping
- [ ] RabbitMQ `payment.events` TopicExchange declared
- [ ] Consumer for `proposal.completed`: Feign → user-service, create PENDING payout, publish `payment.initiated`
- [ ] Consumer for `proposal.cancelled`: refund if payout exists, publish `payment.refunded`
- [ ] `logback-spring.xml` with Loki4J appender

---

## Section 8 — Proposal Lifecycle Saga & Cancellation Cascade

### 8.1 What Is a Choreography Saga

When a business transaction spans multiple services, there is no distributed rollback. The Choreography Saga achieves eventual consistency through:

1. **Forward path:** each service listens for the previous step's success event and executes its part.
2. **Compensation path:** on failure, the failing service publishes a failure event; every service that already committed reverses its local change on receipt of the compensation event.

> **Why "Proposal Lifecycle"?** Freelance Marketplace does not have a "deliver order → pay" flow like Talabat. The freelance lifecycle is: client posts a job → freelancer submits a proposal → client accepts → freelancer works on the contract → freelancer marks the contract as completed → client releases payout. The natural saga trigger is therefore **S3-F4 Complete Proposal's Contract**, which in M1 already performed the multi-service write (update contract, update job, create payout). M3 promotes this to a Choreography Saga and adds payout-failure compensation.

> **Liveness — manual payout step.** Unlike a fully-automatic Talabat-style saga, Freelance has a human-in-the-loop step: after `proposal.completed` fans out and the proposal reaches `PAYMENT_PENDING`, the client must trigger `POST /api/payouts/contract/{contractId}` (S5-F4) for the saga to progress. To prevent a proposal from deadlocking forever in `PAYMENT_PENDING` if the client never posts, proposal-service runs a scheduled **abandonment reaper** (see §8.7) that auto-publishes `payment.failed` with `reason = "payout_abandoned"` after a configurable timeout. The reaper does not bypass the regular compensation path — it just synthesizes the missing failure signal so the cascade can run.

### 8.2 Saga Overview — All 5 Services

The saga is triggered by `PUT /api/proposals/{id}/complete` (S3-F4).

```
TRIGGER: PUT /api/proposals/{id}/complete

[S3 — Pre-saga Feign checks (all 3 must pass)]
  → Feign → job-service:      GET /api/jobs/{jobId}                       status must NOT be CLOSED
  → Feign → user-service:     GET /api/users/{freelancerId}                status must be ACTIVE
  → Feign → contract-service: GET /api/contracts/proposal/{id}/active       active contract must exist

[If any check fails → 400. No events published. Proposal stays ACCEPTED.]

══════════════════════ FREELANCER MARKS CONTRACT COMPLETE ══════════════════════

[S3] Proposal → COMPLETING
     publishes → proposal.completed {proposalId, jobId, freelancerId, contractId, agreedAmount}
                    │
      ┌─────────────┼──────────────┬─────────────────┐
      ▼             ▼              ▼                  ▼
   [S1]          [S2]           [S4]              [S5]
proposal.       proposal.      proposal.         proposal.
completed       completed      completed         completed
consumer        consumer       consumer          consumer
      │             │              │                  │
Update          Update          Mark Contract     Feign →
freelancer      job status =    COMPLETED         user-svc
stats           CLOSED          (local DB)        (get profile)
(local DB)      (local DB)            │                  │
                      │              publishes         Creates
(no event —      publishes      contract.         PENDING Payout
 local DB only)  job.status-    status-changed    (local DB)
                 changed              │           publishes →
                                      │           payment.initiated
                                      └──────────────────┘
                                              │
                              [S3 consumes contract.status-changed]
                              Links contractId → proposal record
                              [S3 consumes payment.initiated]
                              Proposal → PAYMENT_PENDING

══════════ CLIENT TRIGGERS PAYOUT (separate call) ══════════

[S5] POST /api/payouts/contract/{contractId}
     Feign → S4: GET /api/contracts/{contractId}  (must be COMPLETED)
     processes payout (with method/account details)
     publishes → payment.completed  OR  payment.failed

[S3 consumes payment.completed] → Proposal → PAID  ✅ SAGA DONE

══════════════ COMPENSATION (payment.failed) ══════════════

[S3 consumes payment.failed] → Proposal → PAYMENT_FAILED
     publishes → proposal.cancelled {proposalId, jobId, freelancerId, reason: "payment_failed"}
                    │
      ┌─────────────┼──────────────┬─────────────────┐
      ▼             ▼              ▼                  ▼
   [S1]          [S2]           [S4]              [S5]
proposal.       proposal.      proposal.         proposal.
cancelled       cancelled      cancelled         cancelled
consumer        consumer       consumer          consumer
      │             │              │                  │
Reverse         Revert job      Revert Contract   Refund PENDING/
freelancer      status (CLOSED  (COMPLETED →      COMPLETED payout
stats           → IN_PROGRESS)  TERMINATED)       publishes →
(local DB)      (local DB)      (local DB)        payment.refunded
                                      │
                                publishes →
                                contract.cancelled
                                              │
                              [S3 consumes payment.refunded]
                              Proposal → REFUNDED
```

### 8.3 S3-F4 — Complete Proposal's Contract (Saga Trigger)

**Branch:** `feat/M3/proposal/S3-F4/<studentID>`  
**Endpoint:** `PUT /api/proposals/{id}/complete`

**M1 implementation:** Multi-step transactional operation on the shared database:

- Validate proposal status = ACCEPTED.
- `SELECT * FROM contracts WHERE proposal_id = ? AND status='ACTIVE'` to verify there's an active contract (400 if not).
- `UPDATE contracts SET status='COMPLETED', endDate=now() WHERE id = ?`.
- `UPDATE jobs SET status='CLOSED' WHERE id = ?`.
- `INSERT INTO payouts (...) VALUES (...)` with status=PENDING and amount=contract's agreedAmount.
- Save proposal (keep status ACCEPTED).

**M3 change:** Remove the direct contract/job/payout writes. Run three Feign pre-checks, then publish `proposal.completed`.

Behavior:

1. Find proposal by ID → 404 if not found.
2. Validate status = ACCEPTED → 400 if not.
3. **Authorization:** read `X-User-Id` and `X-User-Role` headers (set by api-gateway after JWT validation). The caller must be **either** the proposal's `freelancerId` **or** have role `ADMIN` — otherwise return **403**. (Only the assigned freelancer can mark their own contract complete.)
4. **Pre-saga Feign checks** (all three must pass before any event is published):
   - Feign → job-service `GET /api/jobs/{jobId}` → status must NOT be CLOSED; if 404 or already CLOSED → 400
   - Feign → user-service `GET /api/users/{freelancerId}` → status must be ACTIVE; if 404 or DEACTIVATED → 400
   - Feign → contract-service `GET /api/contracts/proposal/{proposalId}/active` → active contract must exist; capture the returned `contractId` and `agreedAmount` for the event payload; if 404 → 400
5. Mark proposal status = `COMPLETING`, save.
6. Publish `proposal.completed` to `proposal.events` exchange with payload `{proposalId, jobId, freelancerId, contractId, agreedAmount}`.
7. Return 200 with the updated proposal.

> Proposal transitions from `COMPLETING` → `PAYMENT_PENDING` asynchronously when S3 consumes back the `payment.initiated` event from wallet-service.

**Test scenario:**

1. (setup) Proposal ID=1 in proposal-postgres: status=ACCEPTED, jobId=10, freelancerId=5, bidAmount=2000. Job ID=10 in job-postgres: status=IN_PROGRESS. User ID=5 in user-postgres: status=ACTIVE, role=FREELANCER. Contract in contract-postgres: proposalId=1, status=ACTIVE, agreedAmount=2000.
2. (action) `PUT /api/proposals/1/complete` with `X-User-Id: 5, X-User-Role: FREELANCER` (caller = freelancerId).
3. (expect) 200 — proposal status=COMPLETING. `proposal.completed` published to `proposal.events`.
4. (verify) No direct insert into `payouts` from proposal-service. No direct UPDATE on `contracts` or `jobs` from proposal-service.
5. (action) Same proposal, but no active contract in contract-postgres → Feign → contract-service returns 404 → 400. No event published.
6. (action) Freelancer status=DEACTIVATED → Feign → user-service returns DEACTIVATED → 400. No event published.
7. (action) `PUT /api/proposals/1/complete` with `X-User-Id: 99, X-User-Role: FREELANCER` (caller is a different freelancer, not the proposal owner).
8. (expect) 403. No event published, no DB mutation.
9. (action) `PUT /api/proposals/1/complete` with `X-User-Id: 99, X-User-Role: ADMIN`.
10. (expect) 200 (admins bypass the freelancer-ownership check).

---

### 8.4 S3-F7 — Withdraw Proposal

**Branch:** `feat/M3/proposal/S3-F7/<studentID>`  
**Endpoint:** `PUT /api/proposals/{id}/withdraw`

**M1 implementation:** `UPDATE jobs SET status='OPEN' WHERE id = ?` directly on the shared database when this was the only active proposal and the job is IN_PROGRESS.

**M3 change:** Remove the direct `jobs` write. Publish `proposal.withdrawn` — job-service consumes it and decides whether to revert the job status to OPEN based on its own remaining-active-proposals logic (job-service can query proposal-service via Feign or maintain a counter from `proposal.accepted` / `proposal.withdrawn` events).

Behavior:

1. Find proposal by ID → 404 if not found.
2. Validate status IN (SUBMITTED, SHORTLISTED) → 400 if not. (Cannot withdraw an ACCEPTED/COMPLETING/PAID proposal.)
3. **Authorization:** caller's `X-User-Id` must equal the proposal's `freelancerId`, or `X-User-Role` must be `ADMIN` — otherwise return **403**. (Only the freelancer who submitted the proposal can withdraw it.)
4. Set proposal status = `WITHDRAWN`.
5. Publish `proposal.withdrawn` to `proposal.events` exchange with payload `{proposalId, jobId, freelancerId}`.
6. Return 200.

> Job-service consumes `proposal.withdrawn` and applies its M1 logic: if this was the only active proposal for that job and the job is IN_PROGRESS, set job status back to OPEN.

> **Saga compensation cancel.** S3 also publishes `proposal.cancelled` programmatically when `payment.failed` is consumed (saga compensation path — see §8.2). The same `proposal.cancelled` event is **not** triggered by this S3-F7 endpoint (which uses `proposal.withdrawn` for early-stage withdrawal). The compensation path's `proposal.cancelled` has a different fan-out (all 4 consumers reverse their state), while `proposal.withdrawn` is a job-service-only signal.

**Test scenario:**

1. (setup) Proposal ID=1 in proposal-postgres: status=SUBMITTED, jobId=10, freelancerId=5. Job ID=10 in job-postgres: status=IN_PROGRESS, with no other active proposals.
2. (action) `PUT /api/proposals/1/withdraw` with `X-User-Id: 5, X-User-Role: FREELANCER`.
3. (expect) 200 — proposal status=WITHDRAWN. `proposal.withdrawn` published.
4. (verify) No direct update to `jobs` table from proposal-service. After event processing: job-postgres job ID=10 status=OPEN (job-service's logic applied).
5. (action) Try withdrawing an ACCEPTED proposal → 400.
6. (action) Try withdrawing a COMPLETING proposal → 400.
7. (action) `PUT /api/proposals/1/withdraw` with `X-User-Id: 99` (different freelancer) → 403.
8. (action) Same call with `X-User-Role: ADMIN` → succeeds.
9. (action) `PUT /api/proposals/999/withdraw` → 404 (proposal not found). No event published.

---

### 8.5 Saga Participant Summary

| Service              | Feign calls in saga                                                                   | Publishes                                                                             | Consumes                                                                                                                      |
| -------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| **user-service**     | Target of S3 + S5 pre-checks                                                          | (none — freelancer-stats updates are local DB only, no event emitted)                 | `proposal.completed`, `proposal.cancelled`                                                                                    |
| **job-service**      | Target of S3 pre-check                                                                | `job.status-changed`                                                                  | `proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn`                                         |
| **proposal-service** | → job-service (pre-check), → user-service (pre-check), → contract-service (pre-check) | `proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn` | `contract.created`, `contract.status-changed`, `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded` |
| **contract-service** | Target of S3 pre-check                                                                | `contract.created`, `contract.status-changed`, `contract.cancelled`                   | `proposal.accepted`, `proposal.completed`, `proposal.cancelled`                                                               |
| **wallet-service**   | → user-service (profile), → contract-service (validate COMPLETED on S5-F4)            | `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`        | `proposal.completed`, `proposal.cancelled`                                                                                    |

### 8.6 Saga Test Scenarios

> **Async timing convention.** Every step labelled `(verify after event processing)` requires polling — RabbitMQ delivery + listener invocation + DB write is eventual. Use `Awaitility.await().atMost(5, SECONDS).until(...)` (or equivalent) before each such verify; do NOT assert immediately after the action returns. Without an explicit wait/poll the test is flaky in CI.

**Scenario A — Happy path end-to-end:**

1. (setup) In respective databases: User ID=1 (ACTIVE, FREELANCER), Job ID=10 (IN_PROGRESS), Proposal ID=20 (status=ACCEPTED, jobId=10, freelancerId=1, bidAmount=2000), Contract (proposalId=20, status=ACTIVE, agreedAmount=2000, clientId=42).
2. (action) `PUT /api/proposals/20/complete` with `X-User-Id: 1, X-User-Role: FREELANCER` → all three pre-checks pass.
3. (expect) 200. Proposal status = `COMPLETING`. `proposal.completed` published.
4. (wait) `await().atMost(5, SECONDS).until(...)` for the event fan-out to settle (proposal-service receives `payment.initiated` from wallet-service after wallet creates the PENDING Payout).
5. (verify) Proposal status = `PAYMENT_PENDING`; contract-postgres has Contract with status=COMPLETED + endDate set; wallet-postgres has PENDING Payout for proposalId=20 / contractId=...; job-postgres job ID=10 status=CLOSED.
6. (action) `POST /api/payouts/contract/{contractId}` body `{method: "BANK_TRANSFER", accountLastFour: "9876"}` with `X-User-Id: 42, X-User-Role: CLIENT`.
7. (expect) 201. `payment.completed` published.
8. (wait) poll proposal-postgres for proposal status to become `PAID` (Awaitility, ≤5 seconds).
9. (verify) Proposal status = `PAID`.

**Scenario B — Payout failure and compensation:**

1. (setup) Same as Scenario A — reach Proposal status = `PAYMENT_PENDING`.
2. (action) `POST /api/payouts/contract/{contractId}?simulateFailure=true` (M2's failure simulation affordance) or with deliberately invalid payload, with `X-User-Id: 42, X-User-Role: CLIENT`.
3. (expect) 201 (or 200 — wallet handles failure as a normal Payout outcome). `payment.failed` published.
4. (wait) poll for Proposal status = `PAYMENT_FAILED` (≤5 seconds).
5. (verify) Proposal → `PAYMENT_FAILED`. `proposal.cancelled` published.
6. (wait) poll until all four consumers (S1, S2, S4, S5) have processed `proposal.cancelled` (≤5 seconds).
7. (verify) Freelancer stats reversed in user-postgres; job status reverted in job-postgres; Contract status reverted to TERMINATED in contract-postgres; payout = REFUNDED in wallet-postgres.
8. (wait) poll for Proposal status = `REFUNDED` (after S3 consumes `payment.refunded`, ≤5 seconds).
9. (verify) Proposal = `REFUNDED`.

**Scenario C — Pre-check failure (no active contract):**

1. (setup) User ID=1 (ACTIVE), Job ID=10 (IN_PROGRESS), Proposal ID=20 (ACCEPTED, freelancerId=1). No contract record in contract-postgres for proposalId=20.
2. (action) `PUT /api/proposals/20/complete` with `X-User-Id: 1, X-User-Role: FREELANCER`.
3. (expect) 400 — contract-service `GET /api/contracts/proposal/20/active` returns 404. S3 aborts before publishing any event.
4. (verify, no wait needed — synchronous) No `proposal.completed` event in RabbitMQ. Proposal status still = `ACCEPTED`. (Synchronous failure, so no polling required.)

**Scenario D — Payout abandonment (reaper-driven compensation):**

1. (setup) Same as Scenario A — reach Proposal status = `PAYMENT_PENDING`. The client never POSTs `/api/payouts/contract/{contractId}`.
2. (action) Override the `saga.payout.abandon-after` config to `PT5S` for the test, then sleep ≥6 seconds (or trigger the reaper bean directly via a test hook).
3. (wait) poll for Proposal status = `PAYMENT_FAILED` (the reaper synthesizes `payment.failed`, ≤10 seconds).
4. (expect) `payment.failed` event in RabbitMQ with `reason = "payout_abandoned"`. `proposal.cancelled` then published per the standard compensation path.
5. (wait) poll until Proposal status = `REFUNDED`.
6. (verify) End state matches Scenario B step 7 — every committed side-effect compensated.

### 8.7 Saga Infrastructure Deliverables

- [ ] `ProposalEventConfig` in proposal-service: `proposal.events` TopicExchange
- [ ] `ContractEventConfig` in contract-service: `contract.events` TopicExchange
- [ ] `JobEventConfig` in job-service: `job.events` TopicExchange
- [ ] `UserEventConfig` in user-service: `user.events` TopicExchange
- [ ] `PaymentEventConfig` in wallet-service: `payment.events` TopicExchange
- [ ] All consumer queue declarations with DLQ (one per service per exchange it listens to)
- [ ] All event payload record classes (e.g., `ProposalCompletedEvent`, `PaymentFailedEvent`, `ContractCreatedEvent`)
- [ ] Idempotency guard on `POST /api/payouts/contract/{contractId}` (S5-F4) — non-PENDING payouts return their existing record without publishing duplicate events
- [ ] Saga **abandonment reaper** in proposal-service: a `@Scheduled(fixedDelayString = "PT15M")` job that finds Proposals stuck in `PAYMENT_PENDING` for more than `saga.payout.abandon-after` (default `PT72H` / 72 hours) and publishes `payment.failed` with `reason = "payout_abandoned"` to fire the standard compensation cascade. Configurable via `application.yml`; logged at WARN level with `proposalId` MDC. This prevents a saga from deadlocking when a client never POSTs the manual payout step.
- [ ] Saga test scenarios A, B, C verified end-to-end

---

## Section 9 — Spring Cloud Gateway

### 9.1 New Maven Module

Add `api-gateway` as the 6th module in the root `pom.xml`:

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

The gateway runs on port **8080** internally, exposed as NodePort 30080 externally.

Dependencies:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

> Spring Cloud Gateway is **reactive** (Project Reactor). Do NOT add `spring-boot-starter-web` — it conflicts with webflux. Note: the artifact was renamed from `spring-cloud-starter-gateway` to `spring-cloud-starter-gateway-server-webflux` starting with the 2025.1.x release train (the older name was retired when the servlet variant was dropped).

### 9.2 Routing Configuration

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          predicates:
            - Path=/api/users/**, /api/auth/**, /api/user-skills/**
        - id: job-service
          uri: http://job-service:8080
          predicates:
            - Path=/api/jobs/**
        - id: proposal-service
          uri: http://proposal-service:8080
          predicates:
            - Path=/api/proposals/**
        - id: contract-service
          uri: http://contract-service:8080
          predicates:
            - Path=/api/contracts/**
        - id: wallet-service
          uri: http://wallet-service:8080
          predicates:
            - Path=/api/payouts/**, /api/promo-codes/**
```

### 9.3 JWT Global Filter

Adapt the JWT filter and authentication service from M2 to run inside `api-gateway`. The M2 filter was a servlet `OncePerRequestFilter` using `HttpServletRequest` / `HttpServletResponse` — Spring Cloud Gateway is **reactive (WebFlux)**, so the filter must be rewritten as a `GlobalFilter` that returns `Mono<Void>`. Concretely:

- Replace `OncePerRequestFilter` with `implements GlobalFilter, Ordered`.
- Replace `HttpServletRequest` / `HttpServletResponse` with `ServerWebExchange` (`exchange.getRequest()`, `exchange.getResponse()`).
- Replace `filterChain.doFilter(req, res)` with `return chain.filter(exchange);` and let the rest of the pipeline complete asynchronously.
- Forward parsed claims as headers via `exchange.mutate().request(r -> r.header("X-User-Id", uid))`.
- Reject with 401 by setting `exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)` then `return exchange.getResponse().setComplete();` — do NOT throw exceptions for auth failures (reactive convention).

The `JwtConfigurationManager` Singleton from M2 stays as-is (not Spring-managed, plain Java); the gateway constructs a `GlobalFilter` that calls into it.

### 9.4 Gateway Deliverables

- [ ] `api-gateway` Maven module created and added to root `pom.xml`
- [ ] `spring-cloud-starter-gateway-server-webflux` + `spring-boot-starter-webflux` dependencies
- [ ] All 5 service route entries in `application.yml`
- [ ] `JwtGatewayFilter` implemented and registered as `@Component`
- [ ] `/api/auth/**` bypass (no JWT check on register/login)
- [ ] `X-User-Id`, `X-User-Role`, `X-Correlation-ID` headers forwarded to downstream services
- [ ] `docker-compose.yml` updated: per-service postgres containers + RabbitMQ container + api-gateway service

---

## Section 10 — Kubernetes Deployment

### 10.1 Directory Structure

```
k8s/
├── namespaces/
│   └── namespace.yaml              # namespace: freelance
├── secrets/
│   ├── jwt-secret.yaml
│   ├── user-postgres-secret.yaml
│   ├── job-postgres-secret.yaml
│   ├── proposal-postgres-secret.yaml
│   ├── contract-postgres-secret.yaml
│   └── wallet-postgres-secret.yaml
├── configmaps/
│   ├── user-service-configmap.yaml
│   ├── job-service-configmap.yaml
│   ├── proposal-service-configmap.yaml
│   ├── contract-service-configmap.yaml
│   ├── wallet-service-configmap.yaml
│   └── gateway-configmap.yaml
├── pvcs/
│   ├── user-postgres-pvc.yaml
│   ├── job-postgres-pvc.yaml
│   ├── proposal-postgres-pvc.yaml
│   ├── contract-postgres-pvc.yaml
│   ├── wallet-postgres-pvc.yaml
│   ├── rabbitmq-pvc.yaml
│   ├── mongo-pvc.yaml
│   ├── redis-pvc.yaml
│   ├── elasticsearch-pvc.yaml
│   ├── neo4j-pvc.yaml
│   └── cassandra-pvc.yaml
├── statefulsets/
│   ├── user-postgres-statefulset.yaml
│   ├── job-postgres-statefulset.yaml
│   ├── proposal-postgres-statefulset.yaml
│   ├── contract-postgres-statefulset.yaml
│   ├── wallet-postgres-statefulset.yaml
│   ├── rabbitmq-statefulset.yaml
│   ├── mongo-statefulset.yaml
│   ├── redis-statefulset.yaml
│   ├── elasticsearch-statefulset.yaml
│   ├── neo4j-statefulset.yaml
│   └── cassandra-statefulset.yaml
├── deployments/
│   ├── user-service-deployment.yaml
│   ├── job-service-deployment.yaml
│   ├── proposal-service-deployment.yaml
│   ├── contract-service-deployment.yaml
│   └── wallet-service-deployment.yaml
├── services/
│   ├── user-service-svc.yaml           # ClusterIP
│   ├── user-postgres-svc.yaml          # headless
│   ├── job-service-svc.yaml            # ClusterIP
│   ├── job-postgres-svc.yaml           # headless
│   ├── proposal-service-svc.yaml       # ClusterIP
│   ├── proposal-postgres-svc.yaml      # headless
│   ├── contract-service-svc.yaml       # ClusterIP
│   ├── contract-postgres-svc.yaml      # headless
│   ├── wallet-service-svc.yaml         # ClusterIP
│   ├── wallet-postgres-svc.yaml        # headless
│   ├── rabbitmq-svc.yaml
│   ├── mongo-svc.yaml
│   ├── redis-svc.yaml
│   ├── elasticsearch-svc.yaml
│   ├── neo4j-svc.yaml
│   └── cassandra-svc.yaml
└── api-gateway/
    ├── gateway-deployment.yaml
    └── gateway-service.yaml            # type: NodePort (30080)
```

### 10.2 Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: freelance
```

All `kubectl` commands use `-n freelance`.

### 10.3 ConfigMap Example — Proposal Service

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: proposal-service-configmap
  namespace: freelance
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://proposal-postgres:5432/freelancedb-proposals
  SPRING_DATASOURCE_USERNAME: user
  SPRING_RABBITMQ_HOST: rabbitmq
  FEIGN_USER_SERVICE_URL: http://user-service:8080
  FEIGN_JOB_SERVICE_URL: http://job-service:8080
  FEIGN_CONTRACT_SERVICE_URL: http://contract-service:8080
  FEIGN_WALLET_SERVICE_URL: http://wallet-service:8080
```

### 10.4 StatefulSet — Per-Service PostgreSQL (Example: proposal-postgres)

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: proposal-postgres
  namespace: freelance
spec:
  serviceName: proposal-postgres
  replicas: 1
  selector:
    matchLabels:
      app: proposal-postgres
  template:
    metadata:
      labels:
        app: proposal-postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_PASSWORD
            - name: POSTGRES_DB
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_DB
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

### 10.5 Deployment — Spring Boot Service (Example: proposal-service)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: proposal-service
  namespace: freelance
spec:
  replicas: 1
  selector:
    matchLabels:
      app: proposal-service
  template:
    metadata:
      labels:
        app: proposal-service
    spec:
      containers:
        - name: proposal-service
          image: <your-registry>/proposal-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: proposal-service-configmap
            - secretRef:
                name: proposal-postgres-secret
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: jwt-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
```

### 10.6 API Gateway NodePort Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: freelance
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

Access the platform via: `curl http://$(minikube ip):30080/api/proposals`

All other services use `type: ClusterIP`. No service other than the gateway is reachable from outside the cluster.

### 10.7 Deployment Order

```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/        # all databases first
# Wait for databases ready:
kubectl wait --for=condition=ready pod -l app=proposal-postgres -n freelance --timeout=120s
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/deployments/         # services after databases
kubectl apply -f k8s/services/
kubectl apply -f k8s/api-gateway/
```

### K8s Deliverables

- [ ] `k8s/namespaces/namespace.yaml` — namespace `freelance`
- [ ] `k8s/secrets/jwt-secret.yaml` — shared JWT secret (base64-encoded)
- [ ] 5 PostgreSQL secrets (one per service)
- [ ] 5 PostgreSQL StatefulSets with PVC templates (`postgres:17` image)
- [ ] 5 headless Services for PostgreSQL StatefulSets
- [ ] RabbitMQ StatefulSet + Service
- [ ] MongoDB, Redis, Elasticsearch, Neo4j, Cassandra StatefulSets + headless Services (carry over from M2 Docker Compose)
- [ ] 5 Spring Boot Deployments with readiness/liveness probes on `/actuator/health`
- [ ] 5 ClusterIP Services for Spring Boot services
- [ ] 6 ConfigMaps (one per service + gateway) with all env vars
- [ ] API Gateway Deployment + NodePort Service (port 30080)

---

## Section 11 — Observability

### 11.1 Loki4J Appender (All 5 Services)

Add to each service's `pom.xml`:

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>2.0.0</version>
</dependency>
```

#### Per-Service MDC Fields

Each service populates only the MDC keys relevant to its domain. `correlationId` is shared by all five services (set from the `X-Correlation-ID` header forwarded by api-gateway, or from the RabbitMQ message header in consumers). The remaining entity-specific keys differ:

| Service              | Entity-specific MDC keys                                                |
| -------------------- | ----------------------------------------------------------------------- |
| **user-service**     | `userId`                                                                |
| **job-service**      | `jobId`, `proposalId`, `routingKey`                                     |
| **proposal-service** | `proposalId`, `userId`, `jobId`, `contractId`, `payoutId`, `routingKey` |
| **contract-service** | `contractId`, `proposalId`, `jobId`, `userId`, `routingKey`             |
| **wallet-service**   | `payoutId`, `contractId`, `proposalId`, `userId`, `routingKey`          |

#### `logback-spring.xml`

The example below is the **proposal-service** template (the busiest service — its JSON includes every entity field). Each other service uses the same XML structure but **drops the MDC fields it does not populate** from the `<message><pattern>` block.

```xml
<configuration>
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://loki:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=freelance,service=${spring.application.name},level=%level,env=k8s</pattern>
            </label>
            <message>
                <pattern>
                    {
                      "timestamp": "%d{ISO8601}",
                      "level": "%level",
                      "service": "${spring.application.name}",
                      "thread": "%thread",
                      "logger": "%logger{36}",
                      "correlationId": "%X{correlationId:-}",
                      "userId": "%X{userId:-}",
                      "jobId": "%X{jobId:-}",
                      "proposalId": "%X{proposalId:-}",
                      "contractId": "%X{contractId:-}",
                      "payoutId": "%X{payoutId:-}",
                      "routingKey": "%X{routingKey:-}",
                      "message": "%msg"
                    }
                </pattern>
            </message>
        </format>
    </appender>
    <root level="INFO">
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```

#### MDC Population

- **`correlationId`** — populated by a servlet filter (`OncePerRequestFilter`) that reads the `X-Correlation-ID` header set by api-gateway and calls `MDC.put("correlationId", value)`. The filter must clear MDC in `finally`. RabbitMQ consumers must also read the `correlationId` header from the inbound `Message` and call `MDC.put` at the start of the listener method.
- **Entity IDs** (`userId`, `jobId`, `proposalId`, `contractId`, `payoutId`) — populated manually by service-layer methods using `MDC.put("proposalId", id.toString())` immediately before performing the operation, paired with `MDC.remove(...)` in a `finally` block to prevent leaking IDs into unrelated subsequent requests.
- **`routingKey`** — set by RabbitMQ publishers and consumers to the routing key being processed (e.g., `proposal.completed`, `payment.failed`). This makes the Layer 3 RabbitMQ event audit panel (§11.3) usable.

#### Required Log Points

Each service must emit logs at the following points so the LogQL panels in §11.3 have data to query. Use SLF4J: `private static final Logger log = LoggerFactory.getLogger(<Class>.class);`.

| Log point                            | Level | Suggested message format                                                                                                                                                                                                                                                                                   |
| ------------------------------------ | ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Controller method entry              | INFO  | `"Received {} {}"` (HTTP method, path)                                                                                                                                                                                                                                                                     |
| Controller method exit               | INFO  | `"Returning {} for {} {}"` (status, method, path)                                                                                                                                                                                                                                                          |
| Feign call — before request          | INFO  | `"Calling {}.{} with args={}"` (client, method, args)                                                                                                                                                                                                                                                      |
| Feign call — after success           | INFO  | `"{}.{} returned successfully"` (client, method)                                                                                                                                                                                                                                                           |
| Feign call — exception caught        | WARN  | `"Feign call to {} failed: {}"` (service, exception message)                                                                                                                                                                                                                                               |
| RabbitMQ — event published           | INFO  | `"Published {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                                                    |
| RabbitMQ — event consumed (start)    | INFO  | `"Consuming {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                                                    |
| RabbitMQ — event processed (success) | INFO  | `"Processed {} for {}={}"` (routingKey, entityName, id)                                                                                                                                                                                                                                                    |
| RabbitMQ — consumer error            | ERROR | `"Failed to process {}: {}"` (routingKey, exception message) → DLQ                                                                                                                                                                                                                                         |
| Saga state transition (S3 only)      | INFO  | `"Proposal {} transitioning {} → {}"` (proposalId, oldStatus, newStatus)                                                                                                                                                                                                                                   |
| DB write success                     | INFO  | `"{} {} saved with status={}"` (entityName, id, status)                                                                                                                                                                                                                                                    |
| Slow operation (> threshold)         | WARN  | `"Slow {} took {}ms"` (operationName, elapsedMs) — wrap operations expected to be slow under load (e.g., S5-F10 platform-fee analytics, S4-F3 contract enrichment, S1-F6 top-freelancers report) with a stopwatch and emit when elapsed exceeds a threshold (e.g., 1000ms). Feeds the Layer 6 LogQL panel. |

Required in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,info"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

### 11.2 Dashboard per Service

Each of the 5 services has its own Grafana dashboard. Each dashboard has at minimum **3 LogQL panels** and **3 PromQL panels** chosen from the lists below. Five dashboard JSON files must be submitted (one per service).

---

### 11.3 LogQL Panel Options (choose ≥ 3 per service)

A LogQL query is built up in **three layers**, and every panel below uses all three:

1. **Label** — `{app="freelance", service="proposal-service", level="ERROR"}` — match log streams by the labels emitted by the Loki4J appender (§11.1). This narrows down which streams the rest of the query reads from.
2. **Line** — `|= "search-text"`, `!= "exclude"`, `| json`, `| line_format "{{...}}"` — filter and parse individual log lines within the matched streams. Because messages are JSON (§11.1), `| json` exposes every field (`correlationId`, `proposalId`, `routingKey`, …) for further filtering.
3. **Aggregator** — `count_over_time(...[1m])`, `rate(...[5m])`, `sum by (service) (...)` — turn the matching lines into time-series numbers that Grafana can plot.

#### Available Panels

1. **Error rate panel** — Count of ERROR-level log lines per service per minute.  
   _Example purpose:_ Spike detection — if proposal-service logs 50 ERRORs in one minute, something is wrong.

2. **Correlation ID trace panel** — Filter all log lines by a specific `X-Correlation-ID` value across all services.  
   _Example purpose:_ Trace a single proposal-acceptance request from api-gateway through proposal-service, contract-service, and job-service.

3. **RabbitMQ event audit panel** — Lines emitted by event publishers and consumers, filtered by routing key.  
   _Example purpose:_ Show how many `proposal.completed` events were published vs. how many `payment.initiated` events were consumed in the last hour.

4. **Feign call outcomes panel** — Log lines for successful Feign responses vs. `FeignException` catches.  
   _Example purpose:_ Detect when contract-service is degraded — proposal-service Feign calls to it start throwing exceptions.

5. **Saga state transitions panel** — Log lines at each saga step filtered by proposalId.  
   _Example purpose:_ Visualize the complete saga flow for proposal ID=42: COMPLETING → PAYMENT_PENDING → PAID.

6. **Slow operation warnings panel** — Log lines where elapsed time exceeded a threshold.  
   _Example purpose:_ Alert when S5-F10 platform-fee analytics aggregation takes > 5 seconds.

---

### 11.4 PromQL Panel Options (choose ≥ 3 per service)

A PromQL query is built up in **four layers**, and every panel below uses all four:

1. **Metric** — the metric name itself, e.g., `http_server_requests_seconds_count` or `jvm_memory_used_bytes`. These are exposed by each service's `/actuator/prometheus` endpoint and scraped by Prometheus every 15s.
2. **Label** — narrow the metric down with label matchers, e.g., `{service="proposal-service", uri="/api/proposals", method="GET"}`. Labels come from Spring Boot's Actuator metrics and from the `job_name` set in `prometheus.yml`.
3. **Range** — append a time window in square brackets, e.g., `[5m]` or `[1h]`. This turns the instant counter into a sequence of samples over that window so the function in layer 4 has data to operate on.
4. **Function** — `rate(...)`, `increase(...)`, `histogram_quantile(0.99, ...)`, `sum by (uri) (...)`, `topk(5, ...)` — converts the range vector into the final per-second rate, percentile, top-N, or grouped aggregate that Grafana plots.

#### Available Panels

1. **HTTP request rate panel** — Requests per second per endpoint.  
   _Example purpose:_ Which proposal-service endpoints are under the most load during peak hours?

2. **HTTP latency percentiles panel** — P50/P95/P99 latency per endpoint.  
   _Example purpose:_ P99 latency on `GET /api/contracts/freelancer/{id}/summary` is 4s — Feign enrichment is slow.

3. **JVM health panel** — Heap usage, GC pause duration, thread count.  
   _Example purpose:_ Memory pressure before OOM — wallet-service heap at 90% after processing 10,000 events.

4. **Database connection pool panel** — HikariCP active connections vs. pool size.  
   _Example purpose:_ Pool exhaustion alert — wallet-service using 10/10 connections during saga fan-out.

5. **Cache hit/miss ratio panel** — Redis cache hits vs. misses from `cache_gets_total`.  
   _Example purpose:_ S2-F12 dashboard cache hit rate — verify caching is effective.

6. **RabbitMQ throughput panel** — Messages published vs. consumed per queue.  
   _Example purpose:_ Consumer lag on `payment.saga-listener` — published count > consumed count by > 100.

### 11.5 Observability Stack (K8s — monitoring namespace)

The three observability tools — **Loki**, **Prometheus**, and **Grafana** — run as their own pods inside the cluster, in a dedicated namespace called `monitoring`. Keeping them separate from the `freelance` namespace means observability resources (CPU, memory, restarts) are isolated from the application services, and an issue in the app does not take down the dashboards.

The data flow uses two opposite directions:

- **Logs (push):** Each Spring Boot service runs the **Loki4J appender** (§11.1), which pushes log lines as JSON over HTTP to `http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push`. Loki itself never reaches into the services — they send to it.
- **Metrics (pull):** **Prometheus** scrapes each service's `/actuator/prometheus` endpoint on a 15-second interval (configured below). "Scrape" here just means an HTTP GET — Prometheus pulls the current metric values from each service and stores them as time-series.
- **Dashboards:** **Grafana** is configured with two datasources — Loki (for LogQL panels) and Prometheus (for PromQL panels). The 5 dashboard JSON files (one per service) are committed to the repo and provisioned into Grafana via a ConfigMap mount.

| Component  | Image                     | Role in the stack                                                   |
| ---------- | ------------------------- | ------------------------------------------------------------------- |
| Loki       | `grafana/loki:2.9.4`      | Receives JSON log streams pushed by Loki4J from each service.       |
| Prometheus | `prom/prometheus:v2.51.2` | Pulls metrics from each service's `/actuator/prometheus` every 15s. |
| Grafana    | `grafana/grafana:10.4.2`  | Dashboard UI; runs the LogQL/PromQL queries from §11.3 and §11.4.   |

Cross-namespace DNS resolution makes this work: from `monitoring`, Prometheus reaches a service in `freelance` using the fully qualified name `<service-name>.freelance.svc.cluster.local`. From `freelance`, services push logs to `loki.monitoring.svc.cluster.local`.

#### Two Namespaces Required

The cluster must contain **two namespaces**, each defined as its own YAML file under `k8s/namespaces/`:

| Namespace    | Purpose                                                                                             | YAML file                                  |
| ------------ | --------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `freelance`  | All 5 application services + their PostgreSQL + RabbitMQ + NoSQL stores. Already declared in §10.2. | `k8s/namespaces/namespace.yaml`            |
| `monitoring` | Loki + Prometheus + Grafana only. Nothing application-related deploys here.                         | `k8s/namespaces/monitoring-namespace.yaml` |

```yaml
# k8s/namespaces/monitoring-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: monitoring
```

#### Required Files & Directory Structure

All observability manifests live under `k8s/monitoring/`, separate from the application K8s tree shown in §10.1:

```
k8s/
├── namespaces/
│   ├── namespace.yaml                  # freelance (already in §10.1)
│   └── monitoring-namespace.yaml       # monitoring (new — see above)
└── monitoring/
    ├── loki/
    │   ├── loki-configmap.yaml         # /etc/loki/local-config.yaml content
    │   ├── loki-pvc.yaml               # storage for log chunks (≥ 5Gi)
    │   ├── loki-statefulset.yaml       # image: grafana/loki:2.9.4, port 3100
    │   └── loki-service.yaml           # ClusterIP, port 3100 → name "loki"
    ├── prometheus/
    │   ├── prometheus-configmap.yaml   # contains prometheus.yml (scrape config below)
    │   ├── prometheus-pvc.yaml         # storage for TSDB (≥ 5Gi)
    │   ├── prometheus-deployment.yaml  # image: prom/prometheus:v2.51.2, port 9090
    │   └── prometheus-service.yaml     # ClusterIP, port 9090 → name "prometheus"
    └── grafana/
        ├── grafana-datasources.yaml    # ConfigMap — Loki + Prometheus datasource provisioning
        ├── grafana-dashboards.yaml     # ConfigMap — embeds 5 dashboard JSON files
        ├── grafana-pvc.yaml            # storage for Grafana state (≥ 1Gi)
        ├── grafana-deployment.yaml     # image: grafana/grafana:10.4.2, port 3000
        └── grafana-service.yaml        # NodePort 30030 — browser access to dashboards
```

The 5 dashboard JSON files (`user-dashboard.json`, `job-dashboard.json`, `proposal-dashboard.json`, `contract-dashboard.json`, `wallet-dashboard.json`) are committed to `k8s/monitoring/grafana/dashboards/` and embedded into the `grafana-dashboards.yaml` ConfigMap so Grafana auto-loads them on startup.

#### Required Manifests Per Component

**Loki (StatefulSet):** Mount `loki-configmap` at `/etc/loki/`, attach the PVC at `/loki` for chunk storage. Service named `loki` so the Loki4J appender URL `http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push` resolves.

**Prometheus (Deployment):** Mount `prometheus-configmap` at `/etc/prometheus/prometheus.yml`. The ConfigMap holds the scrape config below. Attach the PVC at `/prometheus` for the TSDB.

**Grafana (Deployment):** Mount `grafana-datasources` at `/etc/grafana/provisioning/datasources/` and `grafana-dashboards` at `/etc/grafana/provisioning/dashboards/`. Service is `type: NodePort` on port 30030 so the dashboards are reachable from the host at `http://$(minikube ip):30030` (default credentials `admin/admin`, change on first login).

#### Example Manifest — Prometheus Deployment

The full file at `k8s/monitoring/prometheus/prometheus-deployment.yaml`. Loki and Grafana follow the same pattern (different image, different mount paths, different ports — see "Required Manifests Per Component" above).

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:v2.51.2
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --storage.tsdb.path=/prometheus
          ports:
            - containerPort: 9090
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: storage
              mountPath: /prometheus
          readinessProbe:
            httpGet:
              path: /-/ready
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: prometheus-config # ConfigMap.metadata.name = "prometheus-config" inside k8s/monitoring/prometheus/prometheus-configmap.yaml
        - name: storage
          persistentVolumeClaim:
            claimName: prometheus-pvc
```

The companion `prometheus-service.yaml` is a `ClusterIP` Service named `prometheus` exposing port 9090 — Grafana's Prometheus datasource uses `http://prometheus.monitoring.svc.cluster.local:9090` to reach it.

#### Prometheus Scrape Config

This is the file that goes inside `prometheus-configmap.yaml` under the key `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: user-service
    static_configs:
      - targets: ["user-service.freelance.svc.cluster.local:8080"]
    metrics_path: /actuator/prometheus
  - job_name: job-service
    static_configs:
      - targets: ["job-service.freelance.svc.cluster.local:8080"]
    metrics_path: /actuator/prometheus
  - job_name: proposal-service
    static_configs:
      - targets: ["proposal-service.freelance.svc.cluster.local:8080"]
    metrics_path: /actuator/prometheus
  - job_name: contract-service
    static_configs:
      - targets: ["contract-service.freelance.svc.cluster.local:8080"]
    metrics_path: /actuator/prometheus
  - job_name: wallet-service
    static_configs:
      - targets: ["wallet-service.freelance.svc.cluster.local:8080"]
    metrics_path: /actuator/prometheus
```

#### Apply Order

```bash
kubectl apply -f k8s/namespaces/monitoring-namespace.yaml
kubectl apply -f k8s/monitoring/loki/
kubectl apply -f k8s/monitoring/prometheus/
kubectl apply -f k8s/monitoring/grafana/
kubectl wait --for=condition=ready pod -l app=loki -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=prometheus -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=grafana -n monitoring --timeout=120s
```

Open Grafana at `http://$(minikube ip):30030` — both datasources should be green and all 5 dashboards visible under the Freelance folder.

### Observability Deliverables

- [ ] `logback-spring.xml` in all 5 services with Loki4J appender (§11.1)
- [ ] `management.endpoints.web.exposure.include: prometheus,health,info` in all 5 services
- [ ] 5 Grafana dashboard JSON files (`user-dashboard.json`, `job-dashboard.json`, `proposal-dashboard.json`, `contract-dashboard.json`, `wallet-dashboard.json`) — ≥3 LogQL + ≥3 PromQL panels each, committed under `k8s/monitoring/grafana/dashboards/`
- [ ] `k8s/namespaces/monitoring-namespace.yaml` declaring the `monitoring` namespace
- [ ] `k8s/monitoring/loki/` — ConfigMap + PVC + StatefulSet + Service
- [ ] `k8s/monitoring/prometheus/` — ConfigMap (with the 5-job scrape config) + PVC + Deployment + Service
- [ ] `k8s/monitoring/grafana/` — datasources ConfigMap + dashboards ConfigMap + PVC + Deployment + NodePort Service (30030)
- [ ] Verified end-to-end: trigger an HTTP request via the gateway → log line appears in Loki within ~5s; metric counter increments in Prometheus within ~15s; both render in the corresponding service dashboard

---

## Section 12 — Project Folder Structure

This is the canonical layout your team's repo must end up in by the end of M3. Every file path referenced elsewhere in this spec maps onto this tree.

```
freelance-m3/                               # git repo root
├── pom.xml                                 # parent POM — 7 modules (contracts + 5 services + api-gateway)
├── README.md
├── docker-compose.yml                      # local dev compose: 5 postgres + RabbitMQ + 5 NoSQL + 5 services + gateway
│
├── contracts/                              # Day-0 kickoff module (see §13.2 Parallelism Strategy) — depended on by all 5 services
│   ├── pom.xml
│   └── src/main/java/com/<teamID>/freelance/contracts/
│       ├── feign/                          # @FeignClient interfaces — agreed Day 0, never edited per slice
│       │   ├── UserServiceClient.java
│       │   ├── JobServiceClient.java
│       │   ├── ProposalServiceClient.java
│       │   ├── ContractServiceClient.java
│       │   └── WalletServiceClient.java
│       ├── dto/                            # request/response DTOs returned by Feign
│       │   ├── UserDTO.java
│       │   ├── JobDTO.java
│       │   ├── ProposalDTO.java
│       │   ├── UserContractSummaryDTO.java
│       │   ├── JobProposalSummaryDTO.java
│       │   ├── ContractDTO.java
│       │   └── PayoutDTO.java
│       └── events/                         # RabbitMQ event payload records
│           ├── ProposalAcceptedEvent.java
│           ├── ProposalCompletedEvent.java
│           ├── ProposalCancelledEvent.java
│           ├── ProposalWithdrawnEvent.java
│           ├── ContractCreatedEvent.java
│           ├── ContractStatusChangedEvent.java
│           ├── ContractCancelledEvent.java
│           ├── PaymentInitiatedEvent.java
│           ├── PaymentCompletedEvent.java
│           ├── PaymentFailedEvent.java
│           ├── PaymentRefundedEvent.java
│           ├── UserRegisteredEvent.java
│           ├── UserDeactivatedEvent.java
│           ├── JobStatusChangedEvent.java
│           ├── JobRatedEvent.java
│           └── JobClosedEvent.java
│
├── user-service/                           # S1
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/<teamID>/freelance/user/
│       │   │   ├── UserServiceApplication.java         # @EnableFeignClients
│       │   │   ├── controller/                         # UserController, AuthController, UserSkillController
│       │   │   ├── service/
│       │   │   ├── repository/
│       │   │   ├── entity/                             # User, UserSkill — cross-service Longs only
│       │   │   ├── config/
│       │   │   │   ├── FeignCorrelationConfig.java     # X-Correlation-ID interceptor
│       │   │   │   ├── CorrelationIdFilter.java        # OncePerRequestFilter — sets MDC
│       │   │   │   ├── UserEventConfig.java            # user.events TopicExchange + queues + DLQ + bindings
│       │   │   │   └── SecurityConfig.java             # M2 JWT filter retained
│       │   │   └── messaging/
│       │   │       ├── publishers/                     # UserEventPublisher (publishes user.registered, user.deactivated)
│       │   │       └── consumers/                      # ProposalEventConsumer (@RabbitListener for proposal.completed, proposal.cancelled)
│       │   └── resources/
│       │       ├── application.yml                     # datasource → freelancedb-users; feign URLs; rabbit config
│       │       └── logback-spring.xml                  # Loki4J appender, JSON pattern, MDC fields
│       └── test/
│
├── job-service/                            # S2
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/freelance/job/
│       ├── JobServiceApplication.java
│       ├── controller/                                 # JobController, JobAttachmentController
│       ├── service/
│       ├── repository/
│       ├── entity/                                     # Job, JobAttachment
│       ├── config/                                     # FeignConfig, JobEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                             # publishes job.status-changed, job.rated, job.closed
│           └── consumers/                              # consumes proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn
│
├── proposal-service/                       # S3 (saga state machine lives here)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/freelance/proposal/
│       ├── ProposalServiceApplication.java
│       ├── controller/                                 # ProposalController, ProposalMilestoneController
│       ├── service/
│       ├── repository/
│       ├── entity/                                     # Proposal (with saga statuses), ProposalMilestone
│       ├── config/                                     # FeignConfig, ProposalEventConfig, SecurityConfig
│       ├── saga/                                       # saga-specific consumers + state transitions
│       │   ├── SagaTriggerService.java                 # S3-F4 complete: pre-checks + publish proposal.completed
│       │   ├── PaymentEventConsumer.java               # consumes payment.initiated, payment.completed, payment.failed (compensation), payment.refunded
│       │   └── ContractEventConsumer.java              # consumes contract.created, contract.status-changed
│       └── messaging/
│           ├── publishers/                             # publishes proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn
│           └── consumers/                              # any non-saga event consumers
│
├── contract-service/                       # S4
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/freelance/contract/
│       ├── ContractServiceApplication.java
│       ├── controller/                                 # ContractController (incl. /api/contracts/proposal/{id}/active)
│       ├── service/
│       ├── repository/
│       ├── entity/                                     # Contract
│       ├── config/                                     # FeignConfig, ContractEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                             # publishes contract.created, contract.status-changed, contract.cancelled
│           └── consumers/                              # consumes proposal.accepted, proposal.completed, proposal.cancelled, user.deactivated
│
├── wallet-service/                         # S5 (payout processing + reversal logic)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<teamID>/freelance/wallet/
│       ├── WalletServiceApplication.java
│       ├── controller/                                 # PayoutController, PromoCodeController
│       ├── service/                                    # PayoutService, ReversalService (S5-F12 strategies)
│       ├── repository/
│       ├── entity/                                     # Payout, PromoCode, PayoutPromo
│       ├── config/                                     # FeignConfig, PaymentEventConfig, SecurityConfig
│       └── messaging/
│           ├── publishers/                             # publishes payment.initiated, payment.completed, payment.failed, payment.refunded
│           └── consumers/                              # consumes proposal.completed (creates PENDING payout), proposal.cancelled (refund)
│
├── api-gateway/                            # 6th Maven module — Spring Cloud Gateway (reactive)
│   ├── pom.xml                             # spring-cloud-starter-gateway-server-webflux + spring-boot-starter-webflux
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/<teamID>/freelance/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   └── filter/
│       │       └── JwtGatewayFilter.java               # GlobalFilter, Ordered = -1
│       └── resources/
│           └── application.yml                         # routing predicates per service (5 routes)
│
├── k8s/                                    # all Kubernetes manifests
│   ├── namespaces/
│   │   ├── namespace.yaml                              # freelance
│   │   └── monitoring-namespace.yaml                   # monitoring
│   ├── secrets/
│   │   ├── jwt-secret.yaml
│   │   ├── user-postgres-secret.yaml
│   │   ├── job-postgres-secret.yaml
│   │   ├── proposal-postgres-secret.yaml
│   │   ├── contract-postgres-secret.yaml
│   │   └── wallet-postgres-secret.yaml
│   ├── configmaps/
│   │   ├── user-service-configmap.yaml
│   │   ├── job-service-configmap.yaml
│   │   ├── proposal-service-configmap.yaml
│   │   ├── contract-service-configmap.yaml
│   │   ├── wallet-service-configmap.yaml
│   │   └── gateway-configmap.yaml
│   ├── pvcs/
│   │   ├── user-postgres-pvc.yaml
│   │   ├── job-postgres-pvc.yaml
│   │   ├── proposal-postgres-pvc.yaml
│   │   ├── contract-postgres-pvc.yaml
│   │   ├── wallet-postgres-pvc.yaml
│   │   ├── rabbitmq-pvc.yaml
│   │   ├── mongo-pvc.yaml
│   │   ├── redis-pvc.yaml
│   │   ├── elasticsearch-pvc.yaml
│   │   ├── neo4j-pvc.yaml
│   │   └── cassandra-pvc.yaml
│   ├── statefulsets/
│   │   ├── user-postgres-statefulset.yaml              # postgres:17 — NOT 18
│   │   ├── job-postgres-statefulset.yaml
│   │   ├── proposal-postgres-statefulset.yaml
│   │   ├── contract-postgres-statefulset.yaml
│   │   ├── wallet-postgres-statefulset.yaml
│   │   ├── rabbitmq-statefulset.yaml
│   │   ├── mongo-statefulset.yaml
│   │   ├── redis-statefulset.yaml
│   │   ├── elasticsearch-statefulset.yaml
│   │   ├── neo4j-statefulset.yaml
│   │   └── cassandra-statefulset.yaml
│   ├── deployments/
│   │   ├── user-service-deployment.yaml                # readinessProbe + livenessProbe on /actuator/health
│   │   ├── job-service-deployment.yaml
│   │   ├── proposal-service-deployment.yaml
│   │   ├── contract-service-deployment.yaml
│   │   └── wallet-service-deployment.yaml
│   ├── services/                           # one ClusterIP + one headless per pair
│   │   ├── user-service-svc.yaml                       # ClusterIP
│   │   ├── user-postgres-svc.yaml                      # headless (clusterIP: None)
│   │   ├── job-service-svc.yaml
│   │   ├── job-postgres-svc.yaml
│   │   ├── proposal-service-svc.yaml
│   │   ├── proposal-postgres-svc.yaml
│   │   ├── contract-service-svc.yaml
│   │   ├── contract-postgres-svc.yaml
│   │   ├── wallet-service-svc.yaml
│   │   ├── wallet-postgres-svc.yaml
│   │   ├── rabbitmq-svc.yaml
│   │   ├── mongo-svc.yaml
│   │   ├── redis-svc.yaml
│   │   ├── elasticsearch-svc.yaml
│   │   ├── neo4j-svc.yaml
│   │   └── cassandra-svc.yaml
│   ├── api-gateway/
│   │   ├── gateway-deployment.yaml
│   │   └── gateway-service.yaml                        # NodePort 30080
│   └── monitoring/                         # everything in `monitoring` namespace
│       ├── loki/
│       │   ├── loki-configmap.yaml                     # Loki server config
│       │   ├── loki-pvc.yaml
│       │   ├── loki-statefulset.yaml                   # grafana/loki:2.9.4
│       │   └── loki-service.yaml                       # ClusterIP, port 3100, name "loki"
│       ├── prometheus/
│       │   ├── prometheus-configmap.yaml               # 5-job scrape config
│       │   ├── prometheus-pvc.yaml
│       │   ├── prometheus-deployment.yaml              # prom/prometheus:v2.51.2
│       │   └── prometheus-service.yaml                 # ClusterIP, port 9090, name "prometheus"
│       └── grafana/
│           ├── grafana-datasources.yaml                # ConfigMap — Loki + Prometheus datasource provisioning
│           ├── grafana-dashboards.yaml                 # ConfigMap — references the 5 JSONs below
│           ├── dashboards/
│           │   ├── user-dashboard.json                 # ≥3 LogQL + ≥3 PromQL panels
│           │   ├── job-dashboard.json
│           │   ├── proposal-dashboard.json
│           │   ├── contract-dashboard.json
│           │   └── wallet-dashboard.json
│           ├── grafana-pvc.yaml
│           ├── grafana-deployment.yaml                 # grafana/grafana:10.4.2
│           └── grafana-service.yaml                    # NodePort 30030
│
└── .github/workflows/                      # bonus — CI/CD
    └── ci.yml
```

### 12.1 How Services Reference Files From Other Modules

The `contracts/` module is the mechanism that lets all 5 services share Feign interfaces, DTOs, and event records **without duplicating any Java code**. It is a plain Maven JAR (no Spring Boot parent, no executable) that the 5 services depend on.

> **Package-name placeholder convention:** every Java package path in this section uses `<teamID>` as a placeholder — e.g., `com.<teamID>.freelance.user`. Replace `<teamID>` with your team's actual package prefix from M1 (the unique value derived from your team's student IDs that the M1 grader auto-detects). Do NOT use a literal `com.<teamID>.freelance.*` prefix — that would (1) break M1/M2 grader package detection, (2) make every team use identical package names (defeating plagiarism detection), and (3) require you to rename every M1/M2 source file. The Maven `groupId` follows the same rule: use your existing team groupId from M1, not `com.freelance`. Code samples in this section show `com.<teamID>.freelance.*` purely as a readable example; substitute your real `<teamID>` everywhere before committing.

#### Parent `pom.xml` — Module Aggregator

The root `pom.xml` lists every module in build order. Maven's reactor builds `contracts` first because the 5 services declare it as a `<dependency>`:

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

#### `contracts/pom.xml` — The Shared Types Module

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

The `spring-cloud-starter-openfeign` dependency is required because the `@FeignClient` annotation lives on interfaces inside this module. Event records and DTOs are plain Java records and need no extra dependencies.

#### Each Service `pom.xml` — Depends on `contracts`

Add this to `user-service/pom.xml`, `job-service/pom.xml`, `proposal-service/pom.xml`, `contract-service/pom.xml`, and `wallet-service/pom.xml`:

```xml
<dependency>
    <groupId>com.<teamID>.freelance</groupId>
    <artifactId>contracts</artifactId>
    <version>1.0.0</version>
</dependency>
```

The api-gateway does **not** depend on `contracts` (it does not call Feign clients itself; it just forwards HTTP requests).

#### How Java Code Imports Across Modules

Once a service depends on `contracts`, every type defined there is importable like any other Java package. For example, `user-service` calling contract-service via Feign:

```java
package com.<teamID>.freelance.user.service;

import com.<teamID>.freelance.contracts.feign.ContractServiceClient;       // from contracts module
import com.<teamID>.freelance.contracts.dto.UserContractSummaryDTO;        // from contracts module
import com.<teamID>.freelance.user.entity.User;                             // local to user-service
import com.<teamID>.freelance.user.repository.UserRepository;               // local to user-service

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

Same pattern for events — wallet-service consuming `proposal.completed`:

```java
package com.<teamID>.freelance.wallet.messaging.consumers;

import com.<teamID>.freelance.contracts.events.ProposalCompletedEvent;     // from contracts
import com.<teamID>.freelance.contracts.feign.UserServiceClient;            // from contracts
import com.<teamID>.freelance.wallet.entity.Payout;                          // local

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

#### Build Order — Maven Reactor Handles It Automatically

Run `mvn clean install` from the repo root. Maven's reactor:

1. Detects that `user-service` (and the other 4 services) depend on `contracts:1.0.0`.
2. Builds `contracts` **first**, installs it into the local Maven repo (`~/.m2/repository/com/<teamID>/freelance/contracts/1.0.0/`).
3. Builds the 5 services in any order (no inter-service dependencies — they all only depend on `contracts`).
4. Builds `api-gateway` last (or in parallel with services — it depends on neither contracts nor any service).

For local Docker dev (`docker-compose up`), each service's Dockerfile copies its own JAR — the `contracts` JAR is already baked into the service JAR via Maven's shade/repackage plugin during step 3.

#### Why This Eliminates Cross-Slice Compile Blockers

When student A starts work on `S1-READ-DB` (which calls `ContractServiceClient.getUserContractSummary(...)`), they need that interface to exist in `contracts/` so their code compiles. Day-0 kickoff (§13.2 Parallelism Strategy) ensures:

- All 5 Feign client interfaces + all DTOs + all event records are committed to `contracts/` on **Day 0**, before any of the 15 slices begin work.
- Student A's user-service compiles immediately because `ContractServiceClient` exists in the imported `contracts` JAR — even though student G (owner of `S4-READ-DB`) hasn't yet implemented the matching `GET /api/contracts/user/{userId}/summary` endpoint inside contract-service.
- Runtime testing: student A uses `@MockBean ContractServiceClient` until student G's branch merges.

---

### 12.2 Module-to-Slice Map

The 15 deliverable slices (§13.2) map onto the folder tree as follows. Use this as a per-slice checklist of which files a member touches:

| Slice        | Touches                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `S1-READ-DB` | `user-service/src/main/java/.../entity/` + `controller/` + `service/` + `application.yml` (datasource block); `contracts/.../feign/ContractServiceClient.java` + `WalletServiceClient.java`; `k8s/secrets/user-postgres-secret.yaml` + `k8s/pvcs/user-postgres-pvc.yaml` + `k8s/statefulsets/user-postgres-statefulset.yaml` + `k8s/services/user-postgres-svc.yaml`; `user-service/src/main/resources/logback-spring.xml`; LogQL panels in `k8s/monitoring/grafana/dashboards/user-dashboard.json`. |
| `S1-EVENTS`  | `user-service/src/main/java/.../config/UserEventConfig.java` + `messaging/publishers/UserEventPublisher.java` + `messaging/consumers/ProposalEventConsumer.java`; `contracts/.../events/UserRegisteredEvent.java` + `UserDeactivatedEvent.java`; `k8s/configmaps/user-service-configmap.yaml` + `k8s/deployments/user-service-deployment.yaml` + `k8s/services/user-service-svc.yaml`; PromQL panels in `user-dashboard.json`.                                                                       |
| `S1-INFRA`   | user-service route block in `api-gateway/src/main/resources/application.yml`; user-service scrape job in `k8s/monitoring/prometheus/prometheus-configmap.yaml`; final assembly of `user-dashboard.json`. **Shared infra:** entire `api-gateway/` Maven module (incl. `JwtGatewayFilter.java`) + `k8s/api-gateway/gateway-deployment.yaml` + `gateway-service.yaml` (NodePort 30080) + `k8s/statefulsets/mongo-statefulset.yaml` + `k8s/services/mongo-svc.yaml` + `k8s/pvcs/mongo-pvc.yaml`.         |
| `S2-READ-DB` | job-service equivalent of S1-READ-DB; `contracts/.../feign/ContractServiceClient.java` (active-count) + `ProposalServiceClient.java` (job summary).                                                                                                                                                                                                                                                                                                                                                  |
| `S2-EVENTS`  | job-service equivalent of S1-EVENTS — JobEventConfig + publishers (status-changed, rated, closed) + consumers (proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn); `contracts/.../events/JobStatusChangedEvent.java` + `JobRatedEvent.java` + `JobClosedEvent.java`.                                                                                                                                                                                                     |
| `S2-INFRA`   | job-service route + scrape entry + dashboard. **Shared infra:** `k8s/namespaces/monitoring-namespace.yaml` + entire `k8s/monitoring/loki/` + `k8s/statefulsets/redis-statefulset.yaml` + redis Service + PVC.                                                                                                                                                                                                                                                                                        |
| `S3-READ-DB` | proposal-service entity (incl. saga statuses on Proposal) + new endpoint `GET /api/proposals/job/{jobId}/summary`; `contracts/.../feign/UserServiceClient.java` + `JobServiceClient.java` + `ContractServiceClient.java`; proposal-postgres K8s; logback + LogQL panels.                                                                                                                                                                                                                             |
| `S3-EVENTS`  | proposal-service `saga/` package (S3-F4 complete, S3-F7 withdraw, payment-event consumers, contract-event consumer) + ProposalEventConfig + publishers (proposal.accepted/completed/cancelled/withdrawn); `contracts/.../events/ProposalCompletedEvent.java` etc.; proposal-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                                                 |
| `S3-INFRA`   | proposal-service route + scrape entry + dashboard. **Shared infra:** entire `k8s/monitoring/prometheus/` (Deployment + ConfigMap holding the full 5-job scrape config + PVC + Service) + `k8s/statefulsets/neo4j-statefulset.yaml` + neo4j Service + PVC.                                                                                                                                                                                                                                            |
| `S4-READ-DB` | contract-service entity + new endpoints `GET /api/contracts/user/{id}/{summary,active-count,completed-count}`, `GET /api/contracts/job/{jobId}/active-count`, `GET /api/contracts/proposal/{proposalId}/active` (saga pre-check); `contracts/.../feign/UserServiceClient.java` (read uses) + `JobServiceClient.java`; contract-postgres K8s; logback + LogQL panels.                                                                                                                                 |
| `S4-EVENTS`  | contract-service ContractEventConfig + publishers (contract.created/status-changed/cancelled) + consumers (proposal.accepted/completed/cancelled, user.deactivated); `contracts/.../events/ContractCreatedEvent.java` + `ContractStatusChangedEvent.java` + `ContractCancelledEvent.java`; contract-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                         |
| `S4-INFRA`   | contract-service route + scrape entry + dashboard. **Shared infra:** entire `k8s/monitoring/grafana/` (Deployment + datasources ConfigMap + dashboards ConfigMap embedding all 5 JSONs + PVC + NodePort 30030) + `k8s/statefulsets/cassandra-statefulset.yaml` + cassandra Service + PVC.                                                                                                                                                                                                            |
| `S5-READ-DB` | wallet-service entity + new endpoint `GET /api/payouts/freelancer/{freelancerId}/total`; `contracts/.../feign/UserServiceClient.java` + `ContractServiceClient.java` + `JobServiceClient.java` (read uses); wallet-postgres K8s; logback + LogQL panels.                                                                                                                                                                                                                                             |
| `S5-EVENTS`  | wallet-service PaymentEventConfig + publishers (payment.initiated/completed/failed/refunded) + consumers (proposal.completed → create PENDING payout, proposal.cancelled → refund); `contracts/.../events/Payment*.java`; wallet-service Deployment + Service + ConfigMap; PromQL panels.                                                                                                                                                                                                            |
| `S5-INFRA`   | wallet-service route + scrape entry + dashboard. **Shared infra:** `k8s/statefulsets/rabbitmq-statefulset.yaml` + rabbitmq Service (5672 + 15672) + PVC + `k8s/statefulsets/elasticsearch-statefulset.yaml` + ES Service + PVC + saga end-to-end test scenarios A/B/C from §8.6 (JUnit integration tests).                                                                                                                                                                                           |

---

## Section 13 — Work Distribution

### 13.1 Branch Format

```
feat/M3/<scope>/<ID>/<studentID>
```

Commit format: `feat(<scope>): <description> (studentID)`

### 13.2 The 15 Deliverables

> **Rule:** Each deliverable is a **vertical slice** that touches **all parts** of M3 — Java code, Kubernetes manifests, and observability artifacts. No deliverable is purely Java, K8s, or YAML. The 15 slices are designed so every team member works in **parallel without blocking anyone else** (see "Parallelism Strategy" below the table).

The 15 deliverables are organized as **5 services × 3 vertical slices per service**:

- **Slice A — Read & DB:** DB isolation, outbound Feign clients, exposed read endpoints, Postgres K8s, ≥3 LogQL panels, Logback config.
- **Slice B — Events & Saga:** RabbitMQ topology, publishers/consumers, saga participation, Spring Boot K8s, ≥3 PromQL panels, actuator config.
- **Slice C — Cross-Cutting Infra:** that service's gateway route entry + scrape job entry + dashboard JSON aggregation, plus **one** assigned shared-infra item.

| #      | Branch ID    | Service  | Work (Java + K8s + Observability)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ------ | ------------ | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1**  | `S1-READ-DB` | user     | DB isolation (datasource → freelancedb-users); `ContractServiceClient` + `WalletServiceClient` Feign interfaces with try-catch error handling + correlation interceptor; user-postgres K8s (StatefulSet + PVC + Secret + headless Service); `logback-spring.xml`; ≥3 LogQL panels for user-service dashboard.                                                                                                                                                                                                       |
| **2**  | `S1-EVENTS`  | user     | `user.events` TopicExchange + publishers (`user.registered`, `user.deactivated`); consumer queue `user.proposal.saga-listener` + DLQ; consumers for `proposal.completed`/`proposal.cancelled` updating freelancer stats; user-service K8s Deployment + ClusterIP Service + ConfigMap; actuator config; ≥3 PromQL panels for user-service dashboard; S1-F3, S1-F4, S1-F6, S1-F9 Java refactor (Feign + event publish).                                                                                               |
| **3**  | `S1-INFRA`   | user     | user-service gateway route entry; user-service scrape job entry in `prometheus.yml`; final user-service dashboard JSON file. **Shared infra owned by this slice:** `api-gateway` Maven module (incl. JwtGatewayFilter) + gateway K8s Deployment + NodePort Service (30080) + `/api/auth/**` bypass + Mongo K8s StatefulSet + Service.                                                                                                                                                                               |
| **4**  | `S2-READ-DB` | job      | DB isolation (datasource → freelancedb-jobs); `ContractServiceClient` (active-count) + `ProposalServiceClient` (job summary) Feign interfaces with error handling; job-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for job-service dashboard.                                                                                                                                                                                                                        |
| **5**  | `S2-EVENTS`  | job      | `job.events` TopicExchange + publishers (`job.status-changed`, `job.rated`, `job.closed`); consumer queue `job.proposal.saga-listener` + DLQ; consumers for `proposal.accepted`/`proposal.completed`/`proposal.cancelled`/`proposal.withdrawn`; job-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S2-F3, S2-F4, S2-F7, S2-F12 Java refactor.                                                                                                                                            |
| **6**  | `S2-INFRA`   | job      | job-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** `monitoring` namespace YAML + Loki K8s (StatefulSet + ConfigMap + PVC + Service named `loki`) + Redis K8s StatefulSet + Service.                                                                                                                                                                                                                                                                         |
| **7**  | `S3-READ-DB` | proposal | DB isolation (datasource → freelancedb-proposals); add saga statuses to Proposal enum; expose `GET /api/proposals/job/{jobId}/summary`; `UserServiceClient` + `JobServiceClient` + `ContractServiceClient` Feign interfaces with error handling; proposal-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for proposal-service dashboard.                                                                                                                                |
| **8**  | `S3-EVENTS`  | proposal | `proposal.events` TopicExchange + publishers (`proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn`); consumer queue `proposal.saga-feedback` + DLQ; consumers for `contract.created`/`contract.status-changed`/`payment.initiated`/`payment.completed`/`payment.failed` (compensation trigger)/`payment.refunded`; proposal-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S3-F2, S3-F4 (saga trigger), S3-F7 (withdraw), S3-F11, S3-F12 Java refactor. |
| **9**  | `S3-INFRA`   | proposal | proposal-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** Prometheus K8s (Deployment + ConfigMap holding the full 5-job `prometheus.yml` + PVC + Service named `prometheus`) + Neo4j K8s StatefulSet + Service.                                                                                                                                                                                                                                               |
| **10** | `S4-READ-DB` | contract | DB isolation (datasource → freelancedb-contracts); expose `GET /api/contracts/user/{id}/{summary,active-count,completed-count}`, `GET /api/contracts/job/{jobId}/active-count`, `GET /api/contracts/proposal/{proposalId}/active` (saga pre-check endpoint); `UserServiceClient` + `JobServiceClient` Feign interfaces with error handling; contract-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for contract-service dashboard.                                     |
| **11** | `S4-EVENTS`  | contract | `contract.events` TopicExchange + publishers (`contract.created`, `contract.status-changed`, `contract.cancelled`); consumer queue `contract.saga-listener` + DLQ; consumers for `proposal.accepted` (create Contract → publish `contract.created`)/`proposal.completed` (mark Contract COMPLETED)/`proposal.cancelled` (revert + publish)/`user.deactivated`; contract-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S4-F1, S4-F3, S4-F8, S4-F9 Java refactor.                         |
| **12** | `S4-INFRA`   | contract | contract-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** Grafana K8s (Deployment + datasources ConfigMap pointing at Loki & Prometheus + dashboards ConfigMap embedding all 5 service dashboards + PVC + NodePort Service on 30030) + Cassandra K8s StatefulSet + Service.                                                                                                                                                                                   |
| **13** | `S5-READ-DB` | wallet   | DB isolation (datasource → freelancedb-wallet); expose `GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate`; `UserServiceClient` + `ContractServiceClient` + `JobServiceClient` Feign interfaces with error handling; wallet-postgres K8s (StatefulSet + PVC + Secret + Service); `logback-spring.xml`; ≥3 LogQL panels for wallet-service dashboard.                                                                                                                                               |
| **14** | `S5-EVENTS`  | wallet   | `payment.events` TopicExchange + publishers (`payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`); consumer queue `payment.saga-listener` + DLQ; consumers for `proposal.completed` (Feign → user → create PENDING payout → publish `payment.initiated`) and `proposal.cancelled` (refund → publish `payment.refunded`); wallet-service K8s Deployment + Service + ConfigMap; actuator; ≥3 PromQL panels; S5-F3, S5-F4, S5-F10 Java refactor.                                            |
| **15** | `S5-INFRA`   | wallet   | wallet-service gateway route + scrape job entry + final dashboard JSON. **Shared infra owned by this slice:** RabbitMQ K8s (StatefulSet + Service exposing 5672 + 15672) + Elasticsearch K8s StatefulSet + Service + saga end-to-end test scenarios A/B/C from §8.6 implemented as JUnit integration tests.                                                                                                                                                                                                         |

#### Parallelism Strategy — How All 15 Members Work Without Blocking Each Other

The 15 slices are designed so nobody waits for anyone else. The key is **contract-first development**: every cross-service interface is agreed in a kickoff meeting on Day 1, written down, and committed before any feature work starts. From that moment, each member writes against the contract — not against another member's implementation — so they can compile, test (with mocks), and deploy their slice independently.

1. **Day-0 kickoff contracts (committed by the team lead, ~2 hours):**
   - **Feign client interfaces** — every `@FeignClient` interface signature (e.g., `ContractServiceClient.getUserContractSummary`) and the DTOs they return (`UserContractSummaryDTO`, `JobProposalSummaryDTO`, etc.). Committed once to a `contracts/` Maven module that all services depend on.
   - **Event payload records** — every `record` class (`ProposalCompletedEvent`, `PaymentFailedEvent`, …) is added to that same `contracts/` module. Routing keys + exchange names are fixed in §2.9 (no team debate).
   - **New endpoint paths + DTO shapes** — exact path, query params, response JSON. Already documented in each service's "New Endpoints" table (§3–§7).
   - **K8s Service names** — `loki`, `prometheus`, `rabbitmq`, `<svc>-postgres` — fixed up-front so DNS resolves correctly across slices.
   - **Shared YAML stub files** — `api-gateway/application.yml` (with route placeholders), `prometheus-configmap.yaml` (with scrape-job placeholders), `grafana-dashboards.yaml` ConfigMap (referencing 5 dashboard JSON paths). Each "INFRA" slice owns the _creation_ of one stub; each service slice fills in its own block.

2. **Compile-time independence** — once the `contracts/` module is in place, slice 1's `ContractServiceClient.getUserContractSummary(...)` call compiles even if slice 10 hasn't implemented `GET /api/contracts/user/{userId}/summary` yet. The interface is the only thing slice 1 needs to compile and unit-test.

3. **Runtime independence (mocking)** — for local dev each slice uses `@MockBean` on Feign clients and Testcontainers RabbitMQ. A slice can run, deploy, and verify in isolation without the other 14 slices being merged.

4. **Disjoint file ownership** — each slice writes to its own packages and YAML blocks. The only shared YAML files are `api-gateway/application.yml`, `prometheus.yml`, and `grafana-dashboards.yaml` — these have a stable structure agreed at kickoff so each slice edits only its assigned block. Merge conflicts are minimized to non-existent.

5. **Deploy-time independence** — when a slice's branch is ready, it merges into `main` whenever; the merge order is **not** prescribed because no slice depends on another slice being merged first. Integration verification (saga end-to-end, gateway routing) happens after all 15 are merged, owned by `S5-INFRA`.

### 13.3 Team Size Mapping

| Team size      | Mapping                                                                                                                                                                                     |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **15 members** | 1 deliverable per member, exactly. Default mapping.                                                                                                                                         |
| **14 members** | One member takes 2 deliverables. Recommended pairing: `S<i>-READ-DB` + `S<i>-INFRA` for any service whose INFRA slice is light (e.g., `S2-INFRA` if Loki is the team's most-familiar tool). |
| **13 members** | Two members each take 2 deliverables. Recommended: pair `S<i>-READ-DB` + `S<i>-INFRA` for two services whose INFRA assignments are smaller (e.g., S2 + S4).                                 |

### 13.4 Merge Order

Because the contract-first design eliminates compile-time dependencies, **merge order is unconstrained** — branches can be merged in any order, as long as the `contracts/` module exists in `main` first.

1. **Day 0:** Team lead merges the `contracts/` Maven module + the 3 stub YAML files (`api-gateway/application.yml`, `prometheus-configmap.yaml`, `grafana-dashboards.yaml`) into `main`.
2. **Day 1 onwards:** All 15 slices proceed in parallel; each merges to `main` when ready. No slice blocks another.
3. **Final integration:** Once all 15 slices are merged, `S5-INFRA` owner runs the saga end-to-end test scenarios A/B/C (§8.6) and signs off.

---

## Section 14 — Evaluation Format

### 14.1 Individual Presentation (~5 minutes per member)

Each member presents the branch they implemented and you will need to answer questions about your part of work

### 14.2 Demo Requirements

The team (like one member at least) must be able to run the full project from the cluster:

```bash
kubectl get pods -n freelance                   # all pods Running
kubectl logs <your-service-pod> -n freelance    # your service logs
curl http://$(minikube ip):30080/api/<endpoint> # your feature end-to-end
```

**For saga branch owners:** demonstrate the Proposal Lifecycle Saga & Cancellation Cascade by triggering `PUT /api/proposals/{id}/complete` and showing the event ripple in contract-service and wallet-service logs, then injecting a `payment.failed` to demonstrate the compensation cascade.

---

## Section 15 — Bonus

| Bonus                         | Description                                                                                                                                                                                                                                                                                                                                                                      |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Full Testing Suite**        | (1) Unit tests for service business logic with `@MockBean` on all Feign clients. (2) RabbitMQ consumer integration tests with Testcontainers — publish an event, assert the consumer processes it and mutates the local DB. (3) Saga E2E test: trigger S3-F4, assert contract.created and payment.initiated are received; then inject payment failure, assert compensation runs. |
| **CI/CD Pipeline**            | GitHub Actions: on push to `feat/*` → Maven build + JUnit + Docker build. On push to `main` → push images to a container registry. Submit as `.github/workflows/ci.yml`.                                                                                                                                                                                                         |
| **Circuit Breaker**           | Add `spring-cloud-starter-circuitbreaker-resilience4j` to services with Feign calls. Configure fallback responses. Demonstrate: circuit opens on repeated failures, fallback activates, circuit recovers.                                                                                                                                                                        |
| **Ingress**                   | Replace NodePort on api-gateway with an Ingress resource. `minikube addons enable ingress`, configure Ingress with path-based routing to the gateway.                                                                                                                                                                                                                            |
| **Horizontal Pod Autoscaler** | HPA on proposal-service (highest traffic — saga state machine). CPU threshold ≥ 50%. Requires `metrics-server` in MiniKube. Demonstrate scale-out under simulated load.                                                                                                                                                                                                          |

---

## Section 16 — Critical Rules

1. **No cross-service JDBC.** After M3, no service opens a JDBC connection to another service's database. Zero tolerance.
2. **Feign for reads. RabbitMQ for side-effects.** Use Feign when you need data to continue processing. Use RabbitMQ when triggering a state change in another service.
3. **Auto ACK with DLQ routing.** Use Spring's default `acknowledge-mode: auto` with `default-requeue-rejected: false`. Spring ACKs the message when the listener method returns normally and rejects when it throws — after retries are exhausted, rejected messages flow to the DLQ via the queue's `x-dead-letter-exchange` argument (no manual `basicAck`/`basicNack` calls).
4. **DLQ for every queue.** Every consumer queue has a dead-letter queue. Failed messages are never silently dropped.
5. **StatefulSet for all databases.** Never use plain `Deployment` for a stateful database.
6. **Explicit constructor injection.** Consistent with M1/M2 — no Lombok.
7. **JWT validation at gateway.** Individual services retain their M2 JWT filter for defense-in-depth, but the gateway is the public-facing validator.
8. **PostgreSQL 17.** Not PostgreSQL 18 — PG18 breaks Hibernate native query implicit cast operator resolution.
9. **No new tests added during grading.** Like M1/M2 the grader-provided test suite is the source of truth.
10. **15 deliverables, contract-first parallel work.** No slice waits for another slice's implementation; the `contracts/` module is the only Day-0 dependency.
