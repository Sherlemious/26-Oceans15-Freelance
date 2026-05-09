# Section 1 — Database Isolation

> Split from `../m3.txt`. Original file is untouched.

## 1.1 What Changes
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
## 1.2 Cross-Service FK Columns Become Plain Longs
Every @ManyToOne or @JoinColumn that pointed to another service's entity becomes a plain Long field. The column still exists in the database, but there is no JPA foreign-key relationship across databases. (Most cross-service references in Freelance M1 were already plain Long columns per the M1 spec; this section confirms the rule and makes any remaining JPA cross-service relationships plain.)

| Table | Column | Before (M1/M2) | After (M3) |
| --- | --- | --- | --- |
| jobs | client_id | Long FK reference (already plain) | private Long clientId; (unchanged) |
| proposals | job_id | Long FK reference (already plain) | private Long jobId; (unchanged) |
| proposals | freelancer_id | Long FK reference (already plain) | private Long freelancerId; (unchanged) |
| contracts | job_id | Long FK reference (already plain) | private Long jobId; (unchanged) |
| contracts | freelancer_id | Long FK reference (already plain) | private Long freelancerId; (unchanged) |
| contracts | client_id | Long FK reference (already plain) | private Long clientId; (unchanged) |
| contracts | proposal_id | Long FK reference (already plain) | private Long proposalId; (unchanged) |
| payouts | contract_id | Long FK reference (already plain) | private Long contractId; (unchanged) |
| payouts | freelancer_id | Long FK reference (already plain) | private Long freelancerId; (unchanged) |
## 1.3 NoSQL Databases — Shared Instance, Separate Ownership
MongoDB, Redis, Elasticsearch, Neo4j, and Cassandra remain as single shared instances (one StatefulSet each in Kubernetes). Each service already owns its own collections/indexes/keyspace and never reads another service's data — the logical isolation from M2 is sufficient. Running 5 MongoDB + 5 Redis + 5 Elasticsearch + 5 Neo4j + 5 Cassandra StatefulSets would make MiniKube unrunnable.

The M3 rule: No service connects to another service's PostgreSQL. Each service continues to own its MongoDB collections, Redis key prefix, Elasticsearch index, and Cassandra keyspace.

## 1.4 Deliverables for DB Isolation
- [ ] user-service/application.yml — datasource URL points to user-postgres:5432/freelancedb-users
- [ ] job-service/application.yml — datasource URL points to job-postgres:5432/freelancedb-jobs
- [ ] proposal-service/application.yml — datasource URL points to proposal-postgres:5432/freelancedb-proposals
- [ ] contract-service/application.yml — datasource URL points to contract-postgres:5432/freelancedb-contracts
- [ ] wallet-service/application.yml — datasource URL points to wallet-postgres:5432/freelancedb-wallet
- [ ] All cross-service @ManyToOne fields confirmed as plain Long (Freelance's M1 already used plain Longs — verify no regressions)
- [ ] New saga statuses added to Proposal status enum
