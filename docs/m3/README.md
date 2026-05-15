# Milestone 3 Documentation Index

> Generated from `docs/m3/m3.txt`. The original source file is intentionally left untouched.

## Files

| File | Purpose |
| --- | --- |
| [`m3.txt`](m3.txt) | Original unmodified source text. |
| [`m3-formatted.md`](m3-formatted.md) | Full formatted Markdown copy of the entire milestone spec. |
| [`sections/`](sections/) | One Markdown file per major section for easier LLM retrieval. |
| [`tools/format_m3.mjs`](tools/format_m3.mjs) | Regenerates the formatted files from `m3.txt`. |

## LLM Entry Points

| Need | Start here |
| --- | --- |
| Overall scope, services, and status enum changes | [`sections/00-overview.md`](sections/00-overview.md) |
| Database isolation rules | [`sections/01-database-isolation.md`](sections/01-database-isolation.md) |
| Feign, RabbitMQ, event payloads, event map | [`sections/02-inter-service-communication-setup.md`](sections/02-inter-service-communication-setup.md) |
| Per-service refactors | Sections 3 through 7 below |
| Saga behavior and tests | [`sections/08-proposal-lifecycle-saga-and-cancellation-cascade.md`](sections/08-proposal-lifecycle-saga-and-cancellation-cascade.md) |
| Gateway, Kubernetes, observability, folder layout | Sections 9 through 12 below |
| Work split, evaluation, bonus, critical rules | Sections 13 through 16 below |

## Section Index

| Section | File | Main Contents |
| --- | --- | --- |
| Overview | [`00-overview.md`](sections/00-overview.md) | Overview and milestone metadata |
| Section 1 | [`01-database-isolation.md`](sections/01-database-isolation.md) | 1.1 What Changes, 1.2 Cross-Service FK Columns Become Plain Longs, 1.3 NoSQL Databases — Shared Instance, Separate Ownership, 1.4 Deliverables for DB Isolation |
| Section 2 | [`02-inter-service-communication-setup.md`](sections/02-inter-service-communication-setup.md) | 2.1 OpenFeign Dependency, 2.2 Feign Client Pattern (Example), 2.3 Correlation ID Propagation, 2.4 Error Handling, 2.5 RabbitMQ Dependency, 2.6 RabbitMQ Connection Configuration, ... |
| Section 3 | [`03-user-service-refactoring-s1.md`](sections/03-user-service-refactoring-s1.md) | New Endpoints S1 Must Expose, Features That Require Feign Calls, [S1-F3] Get User Contract Summary, [S1-F4] Deactivate User Account, [S1-F6] Top Freelancers by Earnings, [S1-F9] Find Users by Language Preference with Minimum Contracts, ... |
| Section 4 | [`04-job-service-refactoring-s2.md`](sections/04-job-service-refactoring-s2.md) | New Endpoints S2 Must Expose, [S2-F3] Get Job Proposal Summary, [S2-F4] Close Job Posting, [S2-F7] Rate Job Client After Contract, [S2-F12] Get Job Market Dashboard (M2), RabbitMQ: S2 Publishes, ... |
| Section 5 | [`05-proposal-service-refactoring-s3.md`](sections/05-proposal-service-refactoring-s3.md) | New Endpoints S3 Must Expose, [S3-F2] Accept Proposal and Create Contract, [S3-F11] Record Freelancer-Job Interaction (M2), [S3-F12] Get Recommended Jobs for Freelancer (M2), RabbitMQ: S3 Publishes, RabbitMQ: S3 Consumes, ... |
| Section 6 | [`06-contract-service-refactoring-s4.md`](sections/06-contract-service-refactoring-s4.md) | New Endpoints S4 Must Expose, [S4-F1] Get Active Contract for User, [S4-F3] Find Contracts by Budget Range with Freelancer Info, [S4-F8] Freelancer Performance Summary, [S4-F9] Find Stalled Contracts, RabbitMQ: S4 Publishes, ... |
| Section 7 | [`07-wallet-service-refactoring-s5.md`](sections/07-wallet-service-refactoring-s5.md) | New Endpoints S5 Must Expose, [S5-F3] Freelancer Payout Summary, [S5-F4] Process Payout for Contract, [S5-F10] Get Platform Fee Analytics by Job Category (M2), RabbitMQ: S5 Publishes, RabbitMQ: S5 Consumes, ... |
| Section 8 | [`08-proposal-lifecycle-saga-and-cancellation-cascade.md`](sections/08-proposal-lifecycle-saga-and-cancellation-cascade.md) | 8.1 What Is a Choreography Saga, 8.2 Saga Overview — All 5 Services, 8.3 S3-F4 — Complete Proposal's Contract (Saga Trigger), 8.4 S3-F7 — Withdraw Proposal, 8.5 Saga Participant Summary, 8.6 Saga Test Scenarios, ... |
| Section 9 | [`09-spring-cloud-gateway.md`](sections/09-spring-cloud-gateway.md) | 9.1 New Maven Module, 9.2 Routing Configuration, 9.3 JWT Global Filter, 9.4 Gateway Deliverables |
| Section 10 | [`10-kubernetes-deployment.md`](sections/10-kubernetes-deployment.md) | 10.1 Directory Structure, 10.2 Namespace, 10.3 ConfigMap Example — Proposal Service, 10.4 StatefulSet — Per-Service PostgreSQL (Example: proposal-postgres), 10.5 Deployment — Spring Boot Service (Example: proposal-service), 10.6 API Gateway NodePort Service, ... |
| Section 11 | [`11-observability.md`](sections/11-observability.md) | 11.1 Loki4J Appender (All 5 Services), 11.2 Dashboard per Service, 11.3 LogQL Panel Options (choose ≥ 3 per service), 11.4 PromQL Panel Options (choose ≥ 3 per service), 11.5 Observability Stack (K8s — monitoring namespace), Observability Deliverables |
| Section 12 | [`12-project-folder-structure.md`](sections/12-project-folder-structure.md) | 12.1 How Services Reference Files From Other Modules, 12.2 Module-to-Slice Map |
| Section 13 | [`13-work-distribution.md`](sections/13-work-distribution.md) | 13.1 Branch Format, 13.2 The 15 Deliverables, 13.3 Team Size Mapping, 13.4 Merge Order |
| Section 14 | [`14-evaluation-format.md`](sections/14-evaluation-format.md) | 14.1 Individual Presentation (~5 minutes per member), 14.2 Demo Requirements |
| Section 15 | [`15-bonus.md`](sections/15-bonus.md) | Bonus options |
| Section 16 | [`16-critical-rules.md`](sections/16-critical-rules.md) | Critical M3 implementation rules |

## Feature Index

| Feature | Title | Section |
| --- | --- | --- |
| `S1-F3` | Get User Contract Summary | [`Section 3 — User Service Refactoring (S1)`](sections/03-user-service-refactoring-s1.md) |
| `S1-F4` | Deactivate User Account | [`Section 3 — User Service Refactoring (S1)`](sections/03-user-service-refactoring-s1.md) |
| `S1-F6` | Top Freelancers by Earnings | [`Section 3 — User Service Refactoring (S1)`](sections/03-user-service-refactoring-s1.md) |
| `S1-F9` | Find Users by Language Preference with Minimum Contracts | [`Section 3 — User Service Refactoring (S1)`](sections/03-user-service-refactoring-s1.md) |
| `S2-F3` | Get Job Proposal Summary | [`Section 4 — Job Service Refactoring (S2)`](sections/04-job-service-refactoring-s2.md) |
| `S2-F4` | Close Job Posting | [`Section 4 — Job Service Refactoring (S2)`](sections/04-job-service-refactoring-s2.md) |
| `S2-F7` | Rate Job Client After Contract | [`Section 4 — Job Service Refactoring (S2)`](sections/04-job-service-refactoring-s2.md) |
| `S2-F12` | Get Job Market Dashboard (M2) | [`Section 4 — Job Service Refactoring (S2)`](sections/04-job-service-refactoring-s2.md) |
| `S3-F2` | Accept Proposal and Create Contract | [`Section 5 — Proposal Service Refactoring (S3)`](sections/05-proposal-service-refactoring-s3.md) |
| `S3-F11` | Record Freelancer-Job Interaction (M2) | [`Section 5 — Proposal Service Refactoring (S3)`](sections/05-proposal-service-refactoring-s3.md) |
| `S3-F12` | Get Recommended Jobs for Freelancer (M2) | [`Section 5 — Proposal Service Refactoring (S3)`](sections/05-proposal-service-refactoring-s3.md) |
| `S4-F1` | Get Active Contract for User | [`Section 6 — Contract Service Refactoring (S4)`](sections/06-contract-service-refactoring-s4.md) |
| `S4-F3` | Find Contracts by Budget Range with Freelancer Info | [`Section 6 — Contract Service Refactoring (S4)`](sections/06-contract-service-refactoring-s4.md) |
| `S4-F8` | Freelancer Performance Summary | [`Section 6 — Contract Service Refactoring (S4)`](sections/06-contract-service-refactoring-s4.md) |
| `S4-F9` | Find Stalled Contracts | [`Section 6 — Contract Service Refactoring (S4)`](sections/06-contract-service-refactoring-s4.md) |
| `S5-F3` | Freelancer Payout Summary | [`Section 7 — Wallet Service Refactoring (S5)`](sections/07-wallet-service-refactoring-s5.md) |
| `S5-F4` | Process Payout for Contract | [`Section 7 — Wallet Service Refactoring (S5)`](sections/07-wallet-service-refactoring-s5.md) |
| `S5-F10` | Get Platform Fee Analytics by Job Category (M2) | [`Section 7 — Wallet Service Refactoring (S5)`](sections/07-wallet-service-refactoring-s5.md) |
| `S3-F4` | Complete Proposal's Contract (Saga Trigger) | [`Section 8 — Proposal Lifecycle Saga & Cancellation Cascade`](sections/08-proposal-lifecycle-saga-and-cancellation-cascade.md) |
| `S3-F7` | Withdraw Proposal | [`Section 8 — Proposal Lifecycle Saga & Cancellation Cascade`](sections/08-proposal-lifecycle-saga-and-cancellation-cascade.md) |

## Search Tags

`database isolation`, `OpenFeign`, `RabbitMQ`, `DLQ`, `saga`, `payment.failed`, `api-gateway`, `JWT`, `Kubernetes`, `StatefulSet`, `Loki`, `Prometheus`, `Grafana`, `15 deliverables`, `contract-first`

## Regenerate

```bash
node docs/m3/tools/format_m3.mjs
```
