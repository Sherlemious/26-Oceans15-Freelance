# Milestone 3 — Freelance Marketplace Platform

> Split from `../m3.txt` lines before Section 1. Original file is untouched.

True Microservices: Service Isolation, Inter-Service Communication & Kubernetes
**Weight:** 40% of final grade
**Theme:** Freelance Marketplace

**Deadline:** Saturday 17/05/2026 at 11:59 PM

## Services in This Theme
| Service | Module name | Internal port | Database (M3) |
| --- | --- | --- | --- |
| User Service | user-service | 8080 | freelancedb-users |
| Job Service | job-service | 8080 | freelancedb-jobs |
| Proposal Service | proposal-service | 8080 | freelancedb-proposals |
| Contract Service | contract-service | 8080 | freelancedb-contracts |
| Wallet Service | wallet-service | 8080 | freelancedb-wallet |
| API Gateway | api-gateway | 8080 | — |
## What M3 Adds to Your Codebase
M1 built 5 services sharing one PostgreSQL database.
M2 added 6 databases (polyglot persistence), authentication, caching, and design patterns — still one PostgreSQL, still cross-service SQL JOINs inside that PostgreSQL.
M3 finishes the transformation:

Database isolation — each service gets its own PostgreSQL instance. No service can open a JDBC connection to another service's database.
OpenFeign — synchronous HTTP calls replace cross-service SQL JOINs for read dependencies.
RabbitMQ — asynchronous events replace cross-service write side-effects.
Spring Cloud Gateway — a 6th Maven module acts as the single entry point. JWT validation moves here.
Kubernetes — all services and databases deploy to a local MiniKube cluster.
## What Does NOT Change
All 45 M1 features — except the cross-service SQL inside ~16 of them (see sections below)
All 7 M2 design patterns
All 6 M2 databases (PostgreSQL + MongoDB + Redis + Elasticsearch + Neo4j + Cassandra)
JWT authentication (shared secret, stays the same)
Redis caching (all cached endpoints remain cached)
MongoDB event logging (Observer pattern stays in place)
## New Proposal Status Values
M3 adds saga-related statuses to the Proposal entity's status enum. Existing M1 values (SUBMITTED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN) are preserved; new values are appended for saga lifecycle tracking:

| New status | When it is set |
| --- | --- |
| COMPLETING | S3 sets this immediately before publishing proposal.completed |
| PAYMENT_PENDING | S3 sets this when payment.initiated event is consumed |
| PAID | S3 sets this when payment.completed event is consumed |
| PAYMENT_FAILED | S3 sets this when payment.failed event is consumed |
| REFUNDED | S3 sets this when payment.refunded event is consumed |
**Note on saga entity:** The Proposal acts as the saga trigger entity even though M1's S3-F4 transitions Contract.status (not Proposal.status). M3 lifts the saga state to Proposal so all subscribers correlate by proposalId. Contract.status continues to follow its M1 lifecycle (ACTIVE → COMPLETED/TERMINATED/DISPUTED) under the saga's direction.
