# Product Requirements Document (PRD)
## Job Service (S2) — M3 Milestone: Refactoring & Event-Driven Architecture

---

## Project Context

| Field | Value |
|---|---|
| Project | Freelance Marketplace — Team 26, ACL Spring 2026 |
| Stack | Spring Boot 4.0.3 / Java 25 / Maven |
| Group ID | `com.team26.freelance` |
| Service | `job-service` (S2) — port `8082`, package `com.team26.freelance.job` |
| Database | PostgreSQL 17 (`freelancedb`, `localhost:5432`, credentials `postgres/postgres`) |
| Config format | `application.yml` only — never `.properties` |
| Test framework | JUnit 5 + Mockito, tests in `job-service/src/test/java/com/team26/freelance/job/service/` |

**Milestone context:** M1 delivered features F1–F9. M2 added JWT auth, 5 NoSQL databases, Redis caching, and design patterns. This M3 work refactors cross-service DB coupling and introduces event-driven communication.

---

## Claude Code Instruction — Applies to Every Task in Both Modules

> **Before implementing any task:**
> 1. Check whether the task is already fully or partially implemented by inspecting existing code, configs, and tests.
> 2. Compare the existing implementation against the acceptance criteria listed for that task.
> 3. Only implement (or partially implement) what is genuinely missing or incorrect.
> 4. Do not re-implement or overwrite code that already satisfies the acceptance criteria.

---

## Architectural Rules (Non-Negotiable)

These rules apply to every task in both modules and must never be violated:

- `job-service` must **never** directly query `proposal-postgres`, `contract-postgres`, or any table owned by another service.
- All cross-service **reads** go through Feign clients from `contracts/feign`.
- All cross-service **state mutations** go through published events on `job.events` (RabbitMQ).
- Payload DTOs must always come from `contracts/dto`.
- Correlation IDs must be propagated in both HTTP (Feign) headers and RabbitMQ message headers.
- All new classes must follow the established layered structure: `controller/` → `service/` → `repository/` → `model/` → `dto/`.
- Config changes go in `application.yml`, never in `.properties` files.
- Enums are stored as `@Enumerated(EnumType.STRING)`. `ContractStatus` values: `ACTIVE`, `COMPLETED`, `TERMINATED`. `JobStatus` includes `CLOSED`.
- The existing M2 design patterns must not be broken: Observer (MongoDB event log), Builder (dashboard DTOs), Factory (`EventFactory`), Adapter, etc.
- The existing JWT filter chain must not be touched. All refactored endpoints remain protected (JWT required); no endpoint becomes public.
- Redis cache invalidation: when job state changes, invalidate `job-service::job::{id}` and relevant `job-service::S2-F*::*` keys.

---

## Service Communication Reference

**Current state (pre-M3):** `job-service` uses `RestTemplate` (from `AppConfig`) for inter-service calls, and some cross-service side-effects happen through native SQL in repositories (e.g. direct inserts into proposal/contract tables).

**Target state (M3):** All cross-service reads via Feign clients from `contracts/feign`. All cross-service mutations via RabbitMQ events. `RestTemplate` calls and direct cross-DB SQL must be removed from `job-service`.

**Other services for reference:**

| Service | Port | Package |
|---|---|---|
| user-service (S1) | 8081 | `com.team26.freelance.user` |
| proposal-service (S3) | 8083 | `com.team26.freelance.proposal` |
| contract-service (S4) | 8084 | `com.team26.freelance.contract` |
| wallet-service (S5) | 8085 | `com.team26.freelance.wallet` |

---

## Build & Test Commands

```bash
# Build job-service
mvn -f job-service/pom.xml clean package

# Run job-service locally
mvn -f job-service/pom.xml spring-boot:run

# Run all tests in job-service
mvn -f job-service/pom.xml test

# Run a specific test class
mvn -f job-service/pom.xml test -Dtest=<TestClassName>

# Run all services + databases via Docker
docker-compose up --build
```

---

## Module 1 — Refactoring I: Feign-Based Service Communication

**Objective:** Replace all direct cross-service database reads in `job-service` with HTTP calls through Feign clients from `contracts/feign`. The four tasks below refactor existing endpoints — they must not change the external API contract (same URL, same response shape), only the internal data-fetching mechanism.

---

### Task 1 — S2-EVENTS-5A: Job Proposal Summary Refactor

**Purpose:** Refactor `GET /api/jobs/{jobId}/proposal-summary` so that `job-service` retrieves proposal aggregation from `proposal-service` via Feign instead of direct DB access.

**Scope:**
- Refactor `GET /api/jobs/{jobId}/proposal-summary?startDate={date}&endDate={date}`
- Use `ProposalServiceClient` from `contracts/feign`
- Call proposal-service `GET /api/proposals/job/{jobId}/summary`
- Merge the returned proposal summary DTO with `Job.title` from `job-postgres` (local read — this is fine)
- Remove all direct SQL/JDBC/JPA reads of proposal data from `job-service`

**Implementation notes:**
- The response DTO should use the Builder pattern (consistent with M2 — dashboard/report DTOs with 5+ fields use Builder)
- The Feign call may fail if `proposal-service` is down — handle gracefully; do not let a Feign exception propagate as a 500 to the caller without meaningful error mapping
- Do not cache the Feign response in Redis (proposal data is owned by proposal-service)

**Dependencies:**
- Shared Feign Contracts DTOs (`contracts/feign`, `contracts/dto`)
- Job Feign Build + Client Wiring
- Proposal Provider Read Endpoint

**Acceptance Criteria:**
- Given Job ID=1 exists in `job-postgres` with title `E-Commerce Backend`
- Given `proposal-postgres` has 5 proposals for `jobId=1` with bid amounts 500, 800, 1200, 600, 900 in March 2026
- `GET /api/jobs/1/proposal-summary?startDate=2026-03-01&endDate=2026-03-31` returns:
  - `totalProposals=5`
  - `averageBidAmount=800`
  - `lowestBid=500`
  - `highestBid=1200`
- `job-service` does not directly query `proposal-postgres` or any proposal table
- Summary values arrive exclusively through a Feign HTTP call to `proposal-service`

---

### Task 2 — S2-EVENTS-5B: Close Job Refactor

**Purpose:** Refactor `PUT /api/jobs/{id}/close` so that `job-service` checks active contracts via Feign and publishes a `job.closed` event instead of directly mutating proposal-owned state.

**Scope:**
- Refactor `PUT /api/jobs/{id}/close`
- Use `ContractServiceClient` from `contracts/feign`
- Call `GET /api/contracts/job/{jobId}/active-count`
- If `active-count > 0`, return `400` and do not close the job
- If `active-count = 0`, set `job.status = CLOSED` in `job-postgres`
- Publish `job.closed` event to `job.events` on successful close (depends on Module 2 Task 2)
- Do not directly update proposal tables from `job-service`

**Implementation notes:**
- `JobStatus.CLOSED` must be stored as the string `"CLOSED"` via `@Enumerated(EnumType.STRING)`
- After setting status to CLOSED, invalidate Redis keys: `job-service::job::{id}` and `job-service::S2-F*::*` where relevant
- The Observer pattern must fire a MongoDB event log entry for this status change (consistent with M2 write-endpoint behavior)
- Proposal rejection after `job.closed` is handled by the proposal-service consumer — `job-service` must not do this directly

**Dependencies:**
- Shared Feign Contracts DTOs (`contracts/feign`, `contracts/dto`)
- Shared Saga payloads
- Job Event Publishers (Module 2 Task 2)
- Job Feign Build + Client Wiring
- Contract Provider Read Endpoints
- Proposal consumer for `job.closed`

**Acceptance Criteria:**
- Closing a job with an `ACTIVE` contract returns `400` and leaves `job.status` unchanged
- Closing a job with zero active contracts sets `job.status = CLOSED` in `job-postgres`
- Successful close publishes `job.closed` using the payload from `contracts/dto` with a correlation header
- After event processing, submitted proposals for the job become `REJECTED` in `proposal-postgres` (via proposal-service consumer)
- `job-service` does not directly query contract tables or update proposal tables

---

### Task 3 — S2-EVENTS-5C: Rate Job Client Refactor

**Purpose:** Refactor `POST /api/jobs/{id}/rate` so that `job-service` validates completed contracts through Feign and publishes `job.rated`.

**Scope:**
- Refactor `POST /api/jobs/{id}/rate`
- Use `ContractServiceClient` from `contracts/feign`
- Call `GET /api/contracts/{contractId}`
- Validate: contract exists, belongs to the job, and has status `COMPLETED`
- Validate: rating is between 1 and 5 (inclusive)
- Recalculate `job.rating` and `job.totalRatings` in `job-postgres`
- Publish `job.rated` to `job.events` (depends on Module 2 Task 2)

**Implementation notes:**
- `ContractStatus.COMPLETED` is stored as the string `"COMPLETED"` — match by enum, not raw string
- Rating recalculation: `newRating = ((oldRating * oldTotalRatings) + submittedRating) / (oldTotalRatings + 1)`
- After recalculating, invalidate Redis keys for this job
- The Observer pattern must fire a MongoDB event log entry for the rating update

**Dependencies:**
- Shared Feign Contracts DTOs (`contracts/feign`, `contracts/dto`)
- Shared Saga payloads
- Job Event Publishers (Module 2 Task 2)
- Job Feign Build + Client Wiring
- Contract Provider Read Endpoints

**Acceptance Criteria:**
- Valid rating for a `COMPLETED` contract updates `job.rating` and `job.totalRatings`
- A second valid rating recalculates the aggregate rating correctly
- Missing contract returns `404`
- Rating outside `1..5` returns `400`
- Contract belonging to a different job returns `400`
- Successful rating publishes `job.rated` using the payload from `contracts/dto`
- `job-service` does not directly query contract tables

---

### Task 4 — S2-EVENTS-5D: Market Dashboard Refactor

**Purpose:** Refactor `GET /api/jobs/{id}/dashboard` (S2-F12) so that job-owned data stays local and proposal aggregation is fetched from `proposal-service` via Feign.

**Scope:**
- Refactor `GET /api/jobs/{id}/dashboard`
- Keep `activeAttachments` and `rating` reads local to `job-postgres` (these are job-owned fields)
- Use `ProposalServiceClient` from `contracts/feign`
- Call `GET /api/proposals/job/{jobId}/summary` and extend/use the DTO to include `acceptedProposals`
- Remove all direct SQL/JDBC/JPA reads of proposal data from `job-service`

**Implementation notes:**
- This endpoint was introduced in M2 as S2-F12. Its response DTO must continue to use the Builder pattern (M2 requirement for dashboard DTOs with 5+ fields)
- `activeAttachments` comes from the `Job → JobAttachment` relationship (OneToMany, CascadeType.ALL) — count locally
- Redis caching applies: cache key `job-service::S2-F12::{id}`, TTL 10 min (combined/DTO data)
- On cache miss: fetch local job data + Feign proposal summary → merge → cache → return

**Dependencies:**
- Shared Feign Contracts DTOs (`contracts/feign`, `contracts/dto`)
- Job Feign Build + Client Wiring
- Proposal Provider Read Endpoint

**Acceptance Criteria:**
- Given Job ID=5 has 2 active attachments and `rating=4.5`
- Given proposal aggregation for `jobId=5` has 5 proposals, 1 accepted, bid amounts 500, 800, 1000, 1200, 900
- `GET /api/jobs/5/dashboard` returns:
  - `totalProposals=5`
  - `acceptedProposals=1`
  - `averageBidAmount=880`
  - `activeAttachments=2`
  - `rating=4.5`
- `GET /api/jobs/999/dashboard` returns `404`
- `job-service` does not directly query `proposal-postgres` or any proposal table
- Proposal aggregation arrives exclusively through Feign to `proposal-service`

---

## Module 2 — Refactoring II: Event-Driven Infrastructure & Observability

**Objective:** Build out the full RabbitMQ event infrastructure for `job-service` — topology, publishers, consumers, MDC correlation — and establish Prometheus/Grafana observability. Tasks 1–4 have hard sequential dependencies. Tasks 5–6 are independent and can run in parallel with Tasks 1–4.

---

### Task 1 — S2-EVENTS-2: Job RabbitMQ Topology

**Purpose:** Declare the RabbitMQ topology required by `job-service` using Spring configuration beans.

**Scope:**
- Create or update `JobEventConfig` (`@Configuration` class in `com.team26.freelance.job`)
- Declare `job.events` as a `TopicExchange`
- Declare `job.proposal.saga-listener` queue
- Declare `job.proposal.saga-listener.dlq`
- Declare/reference `proposal.events` idempotently for consumed proposal events
- Bind routing keys `proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn` to `job.proposal.saga-listener`
- Configure DLQ routing via dead-letter exchange + dead-letter routing key arguments on the main queue

**Implementation notes:**
- Use Spring AMQP `@Bean` declarations for `Queue`, `TopicExchange`, and `Binding` — do not use imperative RabbitMQ admin calls
- The `proposal.events` exchange must be declared with compatible arguments (same type, same `durable` flag) to avoid topology conflicts if `proposal-service` declares it first
- DLQ queue must have `x-dead-letter-exchange` and `x-dead-letter-routing-key` arguments set on the main queue
- RabbitMQ connection config belongs in `application.yml` under `spring.rabbitmq.*`

**Dependencies:**
- Shared Saga payloads (`contracts/dto`)
- RabbitMQ K8s instance
- Job Service K8s Baseline
- Job RabbitMQ Config + K8s Wiring

**Acceptance Criteria:**
- `job-service` starts in K8s with RabbitMQ connection config loaded from `application.yml`
- `job-service` declares RabbitMQ topology through Spring `@Configuration` beans
- `job.events` TopicExchange exists in RabbitMQ K8s
- `job.proposal.saga-listener` queue exists in RabbitMQ K8s
- `job.proposal.saga-listener.dlq` queue exists in RabbitMQ K8s
- Main queue has `x-dead-letter-*` arguments configured
- `proposal.events` is declared/referenced idempotently with no incompatible arguments
- All four routing keys (`proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn`) are bound to `job.proposal.saga-listener`

---

### Task 2 — S2-EVENTS-3: Job Event Publishers

**Purpose:** Implement RabbitMQ publishers for M3 job events in `job-service`.

**Scope:**
- Publish `job.status-changed` to `job.events`
- Publish `job.rated` to `job.events`
- Publish `job.closed` to `job.events`
- Use payload DTOs from `contracts/dto`
- Attach correlation ID to RabbitMQ `MessageProperties` headers where available

**Implementation notes:**
- Use `RabbitTemplate` for publishing — inject via Spring
- Publisher methods belong in a dedicated `@Service` class (e.g. `JobEventPublisher`) in `com.team26.freelance.job`
- Correlation ID should be read from MDC or passed explicitly by the caller — never generate a new one inside the publisher
- This task only implements publishers — do not add any consumer or listener logic here
- These publishers are called by Module 1 tasks (5B → `job.closed`, 5C → `job.rated`) and by any status-change logic for `job.status-changed`

**Dependencies:**
- Shared Saga payloads (`contracts/dto`)
- Job RabbitMQ Topology (Module 2 Task 1)

**Acceptance Criteria:**
- `job.status-changed`, `job.rated`, and `job.closed` can each be published to `job.events`
- Payload classes come from `contracts/dto`
- Published messages include all required fields from the M3 payload specification
- Published messages include correlation ID in RabbitMQ message headers when available
- Messages are observable in RabbitMQ K8s management UI
- No consumer/listener logic is implemented in this task

---

### Task 3 — S2-EVENTS-4: Job Event Consumers

**Purpose:** Implement `job-service` consumers for proposal lifecycle events, mutating only job-owned state.

**Scope:**
- Consume `proposal.accepted`, `proposal.completed`, `proposal.cancelled`, `proposal.withdrawn` from `job.proposal.saga-listener`
- Use payload DTOs from `contracts/dto`
- Mutate only `job-service`-owned state in `freelancedb` (job-related tables only)
- Route failed/unprocessable messages to `job.proposal.saga-listener.dlq`
- Validate using manually published RabbitMQ test messages — proposal-service publishers not required

**Implementation notes:**
- Use `@RabbitListener(queues = "job.proposal.saga-listener")` with routing key dispatch, or separate listeners per key
- Each consumer method must be `@Transactional` where it writes to `job-postgres`
- Never write to proposal tables, contract tables, or any table not owned by `job-service`
- On unrecoverable exception, let the message be dead-lettered — do not silently swallow exceptions
- The Observer pattern must fire MongoDB event log entries for any state changes (consistent with M2 write-endpoint behavior)

**Dependencies:**
- Shared Saga payloads (`contracts/dto`)
- Job RabbitMQ Topology (Module 2 Task 1)
- Job Datasource Isolation

**Acceptance Criteria:**
- Valid test messages for all four routing keys are consumed successfully from `job.proposal.saga-listener` in RabbitMQ K8s
- `job-service` updates only its own local DB tables — no writes to proposal or contract tables
- Payload classes come from `contracts/dto`
- Invalid or unprocessable messages are routed to `job.proposal.saga-listener.dlq`
- Behavior is validated against RabbitMQ K8s and Job Postgres K8s
- Full end-to-end saga validation (proposal-service publishing → job-service consuming) is out of scope for this task

---

### Task 4 — S2-EVENTS-6: Job Consumer MDC Correlation

**Purpose:** Propagate RabbitMQ message correlation IDs into MDC for traceable consumer logs.

**Scope:**
- Read RabbitMQ correlation ID from message headers into MDC as `correlationId` at the start of each consumer invocation
- Clear MDC after listener processing completes (including on exception paths)
- Log consume/process/fail lifecycle events at appropriate levels (INFO for consume/process, WARN/ERROR for fail)
- Apply to all job-service proposal event consumers added in Task 3

**Implementation notes:**
- MDC population should happen in a shared listener interceptor or at the top of each consumer method — be consistent
- Use `try/finally` to guarantee MDC is cleared even when an exception is thrown
- The `correlationId` MDC key must match exactly — Loki queries will target this field name
- Loki4J appender must already be wired for this task to be verifiable (existing infrastructure)

**Dependencies:**
- Job RabbitMQ Topology (Module 2 Task 1)
- Job Event Consumers (Module 2 Task 3)
- Job Loki4J Logging (existing)

**Acceptance Criteria:**
- A RabbitMQ message carrying a correlation ID causes all consumer log lines for that message to include the matching `correlationId` in MDC
- MDC is cleared after each message handling completes (success or failure)
- Failed-message log entries include the correlation ID
- Logs arrive in Loki K8s and are queryable by `correlationId`
- Verified using RabbitMQ K8s and Loki K8s

---

### Task 5 — S2-EVENTS-7: Job Actuator + Prometheus Config

**Purpose:** Expose Prometheus-compatible metrics for `job-service` via Spring Boot Actuator.

**Scope:**
- Add/verify `spring-boot-starter-actuator` and `micrometer-registry-prometheus` in `job-service/pom.xml`
- Expose `/actuator/prometheus` endpoint
- Configure `management.endpoints.web.exposure.include` in `application.yml`
- Ensure metrics include HTTP server request metrics and JVM/process metrics

**Implementation notes:**
- `/actuator/prometheus` must not be blocked by the JWT filter — add it to the public endpoint whitelist in `SecurityConfig` (alongside `/api/auth/**` and health checks)
- `application.yml` additions:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: prometheus,health,info
    endpoint:
      prometheus:
        enabled: true
  ```
- This task has no RabbitMQ dependency — it can proceed independently of Tasks 1–4

**Dependencies:**
- Prometheus K8s
- Job Service K8s Baseline

**Acceptance Criteria:**
- `job-service` builds successfully with actuator and Micrometer Prometheus dependencies
- `/actuator/prometheus` is exposed and reachable in K8s
- Prometheus K8s can scrape `job-service` metrics once a scrape entry is configured
- Scraped metrics include HTTP request metrics (e.g. `http_server_requests_seconds_*`)
- Scraped metrics include JVM/process metrics (e.g. `jvm_memory_used_bytes`, `process_cpu_usage`)
- No RabbitMQ wiring is required for this task to pass

---

### Task 6 — S2-EVENTS-8: Job PromQL Panels

**Purpose:** Add `job-service` Grafana panels backed by real Prometheus-scraped metrics.

**Scope:**
- Add at least 3 `job-service` PromQL panels to the Grafana dashboard JSON
- Query metrics exposed by `/actuator/prometheus` and scraped by Prometheus K8s
- Cover at minimum: request volume/latency, error rate/HTTP status, and JVM/service health

**Implementation notes:**
- Add panels to the existing shared Grafana dashboard JSON — do not create a new dashboard file unless instructed
- Use the `application` or `job` label to filter metrics to `job-service` — confirm the exact label from scraped metrics before writing queries
- Example panel queries:
  - Request rate: `rate(http_server_requests_seconds_count{application="job-service"}[1m])`
  - Error rate: `rate(http_server_requests_seconds_count{application="job-service",status=~"5.."}[1m])`
  - JVM heap: `jvm_memory_used_bytes{application="job-service",area="heap"}`

**Dependencies:**
- Prometheus K8s
- Grafana K8s
- Job Actuator + Prometheus Config (Module 2 Task 5)
- Job Prometheus Scrape Entry

**Acceptance Criteria:**
- At least 3 `job-service` PromQL panels exist in the Grafana dashboard
- All panels query Prometheus for `job-service`-specific metrics
- All panels render successfully in Grafana (no "No data" on a running service)
- Queries return real K8s-scraped metrics
- At least one panel covers request volume or latency
- At least one panel covers error rate or HTTP status distribution
- At least one panel covers JVM/process/service health

---

## Task Execution Order (Recommended)

### Module 1 — can proceed in parallel once Feign client wiring is available

| Order | Task | Blocked by |
|---|---|---|
| 1 | S2-EVENTS-5A — Proposal Summary Refactor | Feign client wiring |
| 2 | S2-EVENTS-5D — Market Dashboard Refactor | Feign client wiring |
| 3 | S2-EVENTS-5B — Close Job Refactor | Feign wiring + Module 2 Task 2 (publishers) |
| 4 | S2-EVENTS-5C — Rate Job Refactor | Feign wiring + Module 2 Task 2 (publishers) |

### Module 2 — strictly sequential for Tasks 1–4; Tasks 5–6 are independent

| Order | Task | Blocked by |
|---|---|---|
| 1 | S2-EVENTS-2 — RabbitMQ Topology | Nothing — start here |
| 2 | S2-EVENTS-3 — Event Publishers | Task 1 (topology) |
| 3 | S2-EVENTS-4 — Event Consumers | Task 1 (topology) |
| 4 | S2-EVENTS-6 — MDC Correlation | Tasks 1 + 3 |
| 5 | S2-EVENTS-7 — Actuator + Prometheus | Independent — can run in parallel with any task |
| 6 | S2-EVENTS-8 — PromQL Panels | Task 5 (Prometheus scraping must be working) |

---

## Preserved M2 Requirements (Must Not Be Broken)

These M2 behaviors must remain intact after all M3 refactoring:

| Requirement | Rule |
|---|---|
| **Observer pattern** | Every write endpoint (including refactored ones) must still fire a MongoDB event log entry via `MongoEventLogger` using `EventFactory.createEvent(...)`. Mongo failures must be caught and logged at WARN — never rethrown. |
| **Builder pattern** | Dashboard DTO (S2-F12) and any response DTO with 5+ fields must continue to use the Builder pattern. |
| **Factory pattern** | `EventFactory.createEvent(EventType, Map<String,Object>)` must still be used to create `MongoEvent` subtypes — no direct `new JobEvent(...)` calls anywhere. |
| **Redis caching** | Refactored GET endpoints that were previously cached must remain cached with the same key convention (`job-service::S2-F{n}::{param}`) and TTLs (10 min for DTOs). Write operations must still invalidate relevant cache keys via SCAN+DEL or KEYS+UNLINK. |
| **JWT protection** | All refactored endpoints remain JWT-protected. Only `/actuator/prometheus`, `/api/auth/register`, `/api/auth/login`, and health checks are public. |
| **Enum storage** | `JobStatus`, `ContractStatus`, `ProposalStatus` stored as strings via `@Enumerated(EnumType.STRING)`. Never compare raw strings to enum values. |
| **JSONB fields** | `Job.requirements` is stored as JSONB — do not change this field's type or storage format during refactoring. |
| **Entity relationships** | `Job → JobAttachment` (OneToMany, CascadeType.ALL) must remain intact — used by S2-F12 dashboard for `activeAttachments`. |
