# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Freelance Marketplace — a Spring Boot microservices system built by Team 26 for ACL Spring 2026. Five independent services share a single PostgreSQL database (`freelancedb`). Group ID: `com.team26.freelance`.

## Build & Run Commands

```bash
# Build all services from root
mvn clean install

# Build a single service
mvn -f user-service/pom.xml clean package

# Run a service locally
mvn -f user-service/pom.xml spring-boot:run

# Run all services + Postgres via Docker
docker-compose up --build

# Run a single service in Docker (Postgres must already be up)
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

Each service is a self-contained Spring Boot 3.4.4 / Java 25 app with the same layered structure:

```
controller/   — @RestController, routes under /api/{resource}
service/      — @Service, @Transactional business logic
repository/   — JpaRepository + custom JPQL / native SQL
model/        — @Entity JPA entities
dto/          — API request/response shapes
```

All services share one Postgres instance (`localhost:5432/freelancedb`, credentials `postgres/postgres`, DDL auto=update).

## Key Architectural Patterns

**Inter-service communication**
- `job-service` calls other services via `RestTemplate` (configured in `AppConfig`).
- `contract-service` has Spring Cloud OpenFeign on the classpath for Feign clients.
- Some cross-service side-effects happen through native SQL inside `ProposalRepository` (e.g. it inserts rows directly into the `contracts`, `jobs`, and `payouts` tables on proposal acceptance).

**JSONB fields** — Several entities store flexible data as PostgreSQL JSONB:
- `Contract.metadata` (progress, activity dates)
- `Job.requirements`
- `User.preferences`
- `Proposal.metadata`

Queries against these fields use PostgreSQL's `@>` operator and `jsonb_build_object`.

**Enums** are stored as named strings (`@Enumerated(EnumType.STRING)`):
- `UserRole` (FREELANCER, CLIENT)
- `JobStatus` / `JobCategory`
- `ProposalStatus`
- `ContractStatus` (ACTIVE, COMPLETED, TERMINATED)
- `PayoutStatus` / `PayoutMethod`

**Entity relationships with cascade**
- `User` → `UserSkill` (OneToMany, CascadeType.ALL)
- `Job` → `JobAttachment` (OneToMany, CascadeType.ALL)
- `Proposal` → `ProposalMilestone` (OneToMany, CascadeType.ALL)
- `Payout` → `PayoutPromo` (OneToMany, CascadeType.ALL)

## Infrastructure Notes

- **Docker images** use `eclipse-temurin:25.0.2_10-jdk`; each service's `Dockerfile` copies `target/*.jar` → `app.jar`.
- **Docker Compose** waits for a Postgres health-check before starting any service. Persistent volume: `pgdata`.
- Root `pom.xml` is a pure aggregator (no `<parent>` block); each service independently inherits from `spring-boot-starter-parent`.

## Git Workflow

- Never add `Co-Authored-By: Claude` lines to commits.
- Branch naming follows feature/chore conventions visible in recent history.
