# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Freelance Marketplace — a Spring Boot **3.4.4** / Java 25 microservices system built by Team 26 for ACL Spring 2026. Group ID: `com.team26.freelance`. **Currently in M3.**

**Milestone context:**
- **M1** delivered 45 features (F1–F9 per service) — layered CRUD + relational queries.
- **M2** added JWT auth, 5 NoSQL databases, Redis caching, 7 design patterns, and 15 new features (F10–F12 per service). Also introduced two shared Maven modules: `security-common` (JWT filter chain, `JwtConfigurationManager`) and `event-common` (MongoDB event logging, Observer pattern infrastructure).
- **M3** (active) adds true microservice isolation: per-service PostgreSQL databases, OpenFeign replacing cross-service SQL, RabbitMQ choreography saga, Spring Cloud Gateway, and Kubernetes deployment with Loki/Prometheus/Grafana observability.

## Build & Run Commands

```bash
# Build all services from root
mvn clean install

# Build a single service
mvn -f user-service/pom.xml clean package

# Run a service locally
mvn -f user-service/pom.xml spring-boot:run

# Run all services + all databases via Docker
docker-compose up --build

# Run a single service in Docker (databases must already be up)
docker-compose up user-service
```

## Testing

JUnit 5 + Mockito. Tests live at `{service}/src/test/java/com/team26/freelance/{service}/service/`.

```bash
# Run all tests in a service
mvn -f wallet-service/pom.xml test

# Run a specific test class
mvn -f wallet-service/pom.xml test -Dtest=PayoutServiceTest

# Run all tests across all modules
mvn clean test
```

## Services, Ports & Packages

| Service          | Local Port | Package                              |
|------------------|-----------|--------------------------------------|
| user-service     | 8081      | `com.team26.freelance.user`          |
| job-service      | 8082      | `com.team26.freelance.job`           |
| proposal-service | 8083      | `com.team26.freelance.proposal`      |
| contract-service | 8084      | `com.team26.freelance.contract`      |
| wallet-service   | 8085      | `com.team26.freelance.wallet`        |

> Inside Docker each service listens on 8080; the compose file maps to the ports above.

## Architecture

Each service is a self-contained Spring Boot app with the same layered structure (the grader verifies each layer exists):

```
controller/   — @RestController, routes under /api/{resource}
service/      — @Service, @Transactional business logic
repository/   — JpaRepository + custom JPQL / native SQL
model/        — @Entity JPA entities
dto/          — API request/response shapes
```

**M2 state (pre-M3):** All services share one Postgres instance (`localhost:5432/freelancedb`, credentials `postgres/postgres`, DDL auto=update). **M3 splits this into 5 isolated instances** (`freelancedb-users`, `freelancedb-jobs`, `freelancedb-proposals`, `freelancedb-contracts`, `freelancedb-wallet`).

**Config format:** Each service uses `application.yml` (not `.properties`) — the auto-grader expects YAML format.

## Database Stack (M2)

Six databases run side-by-side. PostgreSQL is a hard dependency (service won't start without it); the other five are soft dependencies (must degrade gracefully if unavailable — use try-catch, do not prevent startup).

| Database      | Port  | Docker Image              | Role / Used By                                      |
|---------------|-------|---------------------------|-----------------------------------------------------|
| PostgreSQL    | 5432  | `postgres:17`             | Primary relational store — all services (hard dep)  |
| MongoDB       | 27017 | `mongo:latest`            | Event/activity logs (`auth_events`, `job_events`, `proposal_events`, `contract_events`, `payout_audit_trail`) — all services |
| Redis         | 6379  | `redis:latest`            | Read cache (256 MB, allkeys-lru) — all services     |
| Elasticsearch | 9200  | `elasticsearch:8.19.12`   | Full-text job search — job-service only             |
| Neo4j         | 7687  | `neo4j:latest`            | Freelancer-job recommendation graph — proposal-service only |
| Cassandra     | 9042  | `cassandra:latest`        | Contract milestone time-series — contract-service only |

## Key Architectural Patterns

**Inter-service communication (M2 state — being replaced in M3)**
- Cross-service reads use native SQL JOINs on the shared `freelancedb` database (e.g. `ProposalRepository` reads from `users`, `jobs`, `contracts` tables directly).
- Cross-service writes use native SQL inside `ProposalRepository` (inserts into `contracts`, `jobs`, `payouts` on proposal acceptance).
- `job-service` uses `RestTemplate` (configured in `AppConfig`) for any inter-service HTTP calls.
- `contract-service` has Spring Cloud OpenFeign on the classpath.
- **M3 replaces all of the above**: each service gets its own PostgreSQL instance, Feign clients replace SQL JOINs for reads, and RabbitMQ events replace direct SQL writes.

**JSONB fields** — Several entities store flexible data as PostgreSQL JSONB:
- `Contract.metadata` (progress, activity dates)
- `Job.requirements`
- `User.preferences`
- `Proposal.metadata`
- `Payout.transactionDetails` — M2 additively adds a `platformFee` key (10% of amount); missing key defaults to `0.10 * amount` at read time

Queries against these fields use PostgreSQL's `@>` operator and `jsonb_build_object`.

**Enums** are stored as named strings (`@Enumerated(EnumType.STRING)`):
- `UserRole` (FREELANCER, CLIENT, ADMIN)
- `JobStatus` / `JobCategory`
- `ProposalStatus`
- `ContractStatus` (ACTIVE, COMPLETED, TERMINATED)
- `PayoutStatus` / `PayoutMethod`

**Entity relationships with cascade**
- `User` → `UserSkill` (OneToMany, CascadeType.ALL)
- `Job` → `JobAttachment` (OneToMany, CascadeType.ALL)
- `Proposal` → `ProposalMilestone` (OneToMany, CascadeType.ALL)
- `Payout` → `PayoutPromo` (OneToMany, CascadeType.ALL)

## Authentication (M2)

JWT-based auth. User Service (S1) is the auth provider; all other services validate tokens independently using the shared secret — no inter-service auth calls.

- **Algorithm:** HMAC-SHA256
- **Secret:** shared across all 5 services via `jwt.secret` in `application.yml`; must be ≥32 bytes (44-char Base64 string); short strings like `"mySecret123"` throw `WeakKeyException`
- **Token payload:** `sub`=email, `uid`=User.id (Long), `role`=UserRole, `iat`, `exp`
- **Expiry:** 24 hours (`jwt.expiration: 86400000`)
- **Header:** `Authorization: Bearer <token>`

**Public endpoints** (no token required): `POST /api/auth/register`, `POST /api/auth/login`, health checks. Everything else returns 401 without a valid token; wrong role returns 403.

**Role rules:**
- Default on registration: `CLIENT` (never assign `ADMIN` on register, even if requested)
- `ADMIN` assignment: only via `PUT /api/users/{id}/role` (ADMIN-only endpoint)
- `JwtConfigurationManager` is a **classical GoF Singleton** (private constructor + `getInstance()`), NOT a Spring bean — do not annotate it with `@Component`

## Caching (M2 — Redis)

### Key Convention
```
<service>::<entity>::<id>           # entity detail  (e.g., user-service::user::42)
<service>::<featureId>::<param>     # feature result (e.g., user-service::S1-F3::42)
```

### TTLs
| Data type              | TTL     |
|------------------------|---------|
| Search / activity feed | 5 min   |
| DTO / report / combined | 10 min |
| Entity detail / relationship | 15 min |

### What is and isn't cached
- **Cached:** 27 M1 feature GET endpoints + 10 CRUD `GET /api/<entity>/{id}` = 37 total M1 endpoints. Plus all M2 feature GET endpoints.
- **NOT cached:** `GET /api/<entity>` list endpoints (always hit PostgreSQL).

### Invalidation on writes
Use wildcard deletion (`SCAN + DEL` or `KEYS + UNLINK`):
- Delete `<service>::<entity>::{id}` (entity detail).
- Delete all matching `<service>::S{n}-F{m}::*` for features whose output references that entity.
- Over-invalidation is acceptable — correctness beats cache-hit rate.

## Design Patterns (M2)

| # | Pattern | Where applied |
|---|---------|---------------|
| 1 | **Strategy** | S5-F12 payout reversal — `RefundStrategy` interface, 3 strategies (`FullPayoutReversalStrategy`, `MilestoneReversalStrategy`, `NoReversalStrategy`), selected by `RefundStrategySelector`. No `if (reversalScope == FULL)` branching in the service. |
| 2 | **Observer** | All M1 + M2 write endpoints → MongoDB event log. `EntityObserver` interface, `MongoEventLogger` concrete class. Each service maintains its own observer list. Mongo failures must be caught and logged at WARN — never rethrow. No `@EventListener` method may write to MongoDB. |
| 3 | **Chain of Responsibility** | JWT filter chain **inside** `JwtAuthenticationFilter.doFilterInternal()`. Handlers in order: `TokenExtractionHandler` → `SignatureValidationHandler` → `UserLoaderHandler` → `RoleAuthorizationHandler`. Do NOT replace Spring Security's filter chain. |
| 4 | **Builder** | Dashboard DTOs (S2-F12, S3-F10, S4-F10, S5-F10) + M1 DTOs with 5+ fields: S1-F3/F6/F8/F9, S2-F3/F6/F9, S3-F3/F6/F9, S4-F3/F6/F8/F9, S5-F3/F6/F8/F9. Excludes S2-F8 and S3-F8 (return entities, not DTOs). |
| 5 | **Singleton** | `JwtConfigurationManager` — private constructor, static `getInstance()`, not a Spring bean. `JwtService` (a Spring `@Service`) calls `JwtConfigurationManager.getInstance()` to read config. |
| 6 | **Factory** | `EventFactory.createEvent(EventType, Map<String,Object>)` creates the right `MongoEvent` subtype (`AuthEvent`, `JobEvent`, `ProposalEvent`, `ContractEvent`, `PayoutAuditEvent`). No service class may call `new AuthEvent(...)` etc. directly. |
| 7 | **Adapter** | One adapter per NoSQL source per service (e.g., `MongoDocumentAdapter`, `ElasticsearchHitAdapter`, `Neo4jRecordAdapter`, `CassandraRowAdapter`). Also `ObjectArrayDtoAdapter` for any M1 feature that uses `Object[]` native SQL projection (S1-F3 mandates it). |

## M2 Feature Quick Reference

| Service | F10 | F11 | F12 |
|---------|-----|-----|-----|
| S1 user | Register (`POST /api/auth/register`) | Login (`POST /api/auth/login`) | Activity Feed (`GET /api/users/{id}/activity`) |
| S2 job | Full-text Search via ES (`GET /api/jobs/search/full-text`) | Index Job to ES (`POST /api/jobs/{id}/index`) | Job Market Dashboard (`GET /api/jobs/{id}/dashboard`) |
| S3 proposal | Analytics Dashboard (`GET /api/proposals/analytics`) | Record Freelancer-Job Interaction in Neo4j (`POST /api/proposals/{id}/record-interaction`) | Freelancer Recommendations from Neo4j (`GET /api/proposals/recommendations`) |
| S4 contract | Analytics Dashboard (`GET /api/contracts/analytics`) | Track Milestone Event in Cassandra (`POST /api/contracts/{id}/milestones/track`) | Milestone Timeline from Cassandra (`GET /api/contracts/{id}/milestones/timeline`) |
| S5 wallet | Category Revenue Analytics from MongoDB (`GET /api/payouts/analytics/categories`) | Payout Method Breakdown from MongoDB (`GET /api/payouts/analytics/methods`) | Milestone-Based Payout Reversal with Strategy pattern (`POST /api/payouts/{id}/reverse`) |

## Cross-Cutting Requirements (M2)

| ID | Requirement | Notes |
|----|-------------|-------|
| CC-1 | JWT on ALL endpoints | 3 public endpoints: register, login, health. Everything else → 401 without token, 403 for wrong role. |
| CC-2 | `PUT /api/users/{id}/role` | ADMIN-only. Logs `ROLE_CHANGED` to `auth_events`. Invalidates `user-service::user::{id}` and `user-service::S1-F12::*`. |
| CC-3 | Redis caching on M1 endpoints | 27 feature GETs + 10 CRUD GET-by-ID = 37 cached M1 endpoints. |
| CC-4 | 7 design patterns | See table above — grader checks via reflection + integration tests. |
| CC-5 | docker-compose with all 6 DBs | Pinned versions: `postgres:17`, `elasticsearch:8.19.12`. Others: `latest`. Memory caps required. |
| CC-6 | `application.yml` per service | YAML format, not `.properties`. Must include JWT secret + all DB connections. |

## Git Workflow

### Branch naming

**M3 format** (all M3 work uses this):
```
feat/M3/<scope>/<sliceID>/<studentID>
```

| Segment | Values |
|---------|--------|
| `scope` | `user` `job` `proposal` `contract` `wallet` |
| `sliceID` | `S1-READ-DB` `S1-EVENTS` `S1-INFRA` … `S5-READ-DB` `S5-EVENTS` `S5-INFRA` |
| `studentID` | Numeric student ID — always the last segment |

Examples:
```
feat/M3/user/S1-READ-DB/55-2398
feat/M3/proposal/S3-EVENTS/55-4337
feat/M3/wallet/S5-INFRA/58-1752
```

**M1/M2 format** (fixes, hotfixes, and non-M3 work):
```
<type>/<scope>/<descriptor>/<studentID>
```

| Segment | Values |
|---------|--------|
| `type` | `feat` `fix` `hotfix` `refactor` `docs` `test` `chore` `perf` |
| `scope` | service: `user` `job` `proposal` `contract` `wallet` — or cross-cutting: `m1` `cc` `infra` |
| `descriptor` | Stable ID when one exists (`S3-F11`, `MOD-3`, `CC-2`); kebab-case slug otherwise (`payout-rounding-bug`) |
| `studentID` | Numeric student ID — **always the last segment**; the auto-grader matches on it |

Examples:
```
fix/wallet/payout-amount-rounding/55-8080
hotfix/user/token-expiry-leak/55-8080
docs/infra/claude-md-enhancement/55-4626
```

### Commit message (Conventional Commits)
```
<type>(<scope>): <subject> (<studentID>)
```
- `subject`: imperative mood ("add", not "added"); no trailing period; ≤72 chars
- `studentID`: in parentheses at end — every commit must be attributable

Examples:
```
feat(proposal): S3-F11 record freelancer-job ordering pattern (55-8080)
feat(m1): MOD-3 apply JWT filter to all M1 endpoints (55-8080)
feat(cc): CC-2 role management endpoint (55-8080)
fix(wallet): correct payout amount rounding (55-8080)
refactor(user): extract token validation to helper (55-8080)
test(job): add integration test for S2-F11 auto-index (55-8080)
docs(cc): clarify CC-5 healthcheck timings (55-8080)
chore(infra): bump postgres image to 17.4 (55-8080)
```

### PR rules
- One branch → one PR → one work unit (no bundling of unrelated changes)
- At least **1 teammate review and approval** before merge
- Merge using **"Create a merge commit"** on GitHub — **never squash merge** (the grader reads the branch name from the merge commit message)

### Grading-critical rules
> Violations affect the **entire team**, not just the individual.

- **Never push directly to `main`** — always use a feature branch + PR
- **Never delete feature branches** after merging — the grader verifies each branch exists by name + studentID
- **Every commit must end with `(<studentID>)`** — commits without this cannot be attributed and score zero
- **Every team member must have commits** traceable to feature branches — no commits = zero for that member
- **Design pattern placement matters** — grader uses reflection to verify class structure, not just behavior

## Infrastructure Notes

- **Docker images** use `eclipse-temurin:25.0.2_10-jdk`; each service's `Dockerfile` copies `target/*.jar` → `app.jar`.
- **Docker Compose** waits for a Postgres health-check before starting any service. Persistent volume: `pgdata`.
- Root `pom.xml` is a pure aggregator (no `<parent>` block); each service independently inherits from `spring-boot-starter-parent 3.4.4`.
- Root `pom.xml` includes two shared modules: `security-common` (JWT filter chain, `JwtConfigurationManager`, Spring Security config) and `event-common` (MongoDB event logging, Observer pattern base classes). All 5 services depend on both at version `1.0.0`.
- Jackson 2.x (`com.fasterxml.jackson.*`) is used throughout — Spring Boot 3.4.4 ships with Jackson 2.x. No Jackson 3.x dependency exists in this project.
