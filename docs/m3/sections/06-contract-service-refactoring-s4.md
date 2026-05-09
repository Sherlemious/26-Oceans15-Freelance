# Section 6 — Contract Service Refactoring (S4)

> Split from `../m3.txt`. Original file is untouched.

## New Endpoints S4 Must Expose
| Endpoint | Called by | Returns | Description |
| --- | --- | --- | --- |
| GET /api/contracts/user/{userId}/summary | S1 (S1-F3) | UserContractSummaryDTO | {totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue} |
| GET /api/contracts/user/{userId}/active-count | S1 (S1-F4) | int | Count of contracts with status=ACTIVE for this freelancer |
| GET /api/contracts/user/{userId}/completed-count | S1 (S1-F9) | long | Count of contracts with status=COMPLETED for this freelancer |
| GET /api/contracts/job/{jobId}/active-count | S2 (S2-F4) | int | Count of contracts with status=ACTIVE for this job |
| GET /api/contracts/proposal/{proposalId}/active | S3 (saga pre-check) | ContractDTO | Returns the ACTIVE contract for this proposalId. 404 if none. This is a new endpoint not in M1/M2. |
| GET /api/contracts/{contractId} | S2 (S2-F7), S5 (S5-F4) | ContractDTO | Already exists (M1 CRUD). Verify it returns id, jobId, freelancerId, clientId, proposalId, agreedAmount, status, startDate, endDate. |
## [S4-F1] Get Active Contract for User
- **Branch:** `feat/M3/contract/S4-F1/<studentID>`
- **Endpoint:** `GET /api/contracts/user/{userId}/active`

**M1 implementation:** Verify user exists via SELECT FROM users WHERE id = ? on shared database before querying contracts.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(userId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("User not found: " + userId);
}
// Then query local contracts table for the most recent ACTIVE contract
```
## Test Scenario

- **Setup:** User ID=1 in user-postgres. 3 contracts in contract-postgres for freelancerId=1, all ACTIVE, with different createdAt timestamps.
- **Action:** GET /api/contracts/user/1/active.
- **Expect:** 200 — the most recent ACTIVE contract.
- **Action:** GET /api/contracts/user/999/active → Feign → user-service throws 404 → 404.
- **Action:** User ID=2 with no active contracts → 404.
## [S4-F3] Find Contracts by Budget Range with Freelancer Info
- **Branch:** `feat/M3/contract/S4-F3/<studentID>`
- **Endpoint:** `GET /api/contracts/search?minAmount={a}&maxAmount={a}&status={s}`

**M1 implementation:** Native SQL JOIN of contracts with users (for freelancer name) and jobs (for job title) — DTO contains freelancerName and jobTitle from those external services' tables.

**M3 change:** Two Feign calls replace the JOIN:

Query contract-postgres for contracts in the budget range and status filter (local query, no JOIN).
For each contract, Feign → user-service GET /api/users/{freelancerId} → get name.
For each contract, Feign → job-service GET /api/jobs/{jobId} → get title.
Compute durationDays from local startDate/endDate. Build ContractSummaryDTO.
Optimization: Cache the freelancerId → name and jobId → title lookups locally (in a Map) within the request lifecycle to avoid duplicate Feign calls when contracts share users or jobs.

## Test Scenario

- **Setup:** 3 contracts in contract-postgres: agreedAmount 1000 (ACTIVE), 3000 (COMPLETED), 5000 (ACTIVE). Corresponding users in user-postgres (Ahmed, Sara, Omar) and jobs in job-postgres ("E-Commerce Backend", "Mobile App UI", "Data Pipeline").
- **Action:** GET /api/contracts/search?minAmount=2000&maxAmount=6000&status=ACTIVE.
- **Expect:** 200 — single result for the 5000 ACTIVE contract with freelancerName and jobTitle populated from Feign.
- **Verify:** Feign calls to user-service and job-service. No direct SQL on user-postgres or job-postgres from contract-service.
## [S4-F8] Freelancer Performance Summary
- **Branch:** `feat/M3/contract/S4-F8/<studentID>`
- **Endpoint:** `GET /api/contracts/freelancer/{freelancerId}/summary?startDate={d}&endDate={d}`

**M1 implementation:** Verify the freelancer exists via SELECT FROM users WHERE id = ? on the shared database before aggregating.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(freelancerId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("Freelancer not found: " + freelancerId);
}
// Then aggregate locally from contract-postgres
```
The aggregation logic (totalContracts, totalEarnings, averageContractValue, completionRate, averageDurationDays) remains local — the contracts table is owned by S4.

## Test Scenario

- **Setup:** Freelancer ID=1 in user-postgres. 5 contracts in contract-postgres for freelancerId=1 (March 2026): 4 COMPLETED (amounts 1000/1500/2000/2500; durations 10/15/20/25 days), 1 TERMINATED.
- **Action:** GET /api/contracts/freelancer/1/summary?startDate=2026-03-01&endDate=2026-03-31.
- **Expect:** 200 — totalContracts=5, totalEarnings=7000, averageContractValue=1400, completionRate=80.0, averageDurationDays=17.5.
- **Action:** Freelancer ID=999 → Feign returns 404 → 404.
## [S4-F9] Find Stalled Contracts
- **Branch:** `feat/M3/contract/S4-F9/<studentID>`
- **Endpoint:** `GET /api/contracts/stalled?maxProgress={p}&stalledDays={d}`

**M1 implementation:** Native SQL JOIN of contracts with users (freelancer name) and jobs (job title) — DTO contains freelancerName and jobTitle.

**M3 change:** Same Feign-enrichment pattern as S4-F3:

Query contract-postgres for ACTIVE contracts whose JSONB metadata.progressPercentage ≤ maxProgress and whose metadata.lastActivityDate is more than stalledDays days ago.
For each candidate contract, Feign → user-service for freelancerName and Feign → job-service for jobTitle.
Compute daysSinceLastActivity locally. Build StalledContractDTO.
## Test Scenario

- **Setup:** 3 ACTIVE contracts in contract-postgres with users + jobs in their respective DBs:
Contract A (progressPercentage=10, lastActivityDate=30 days ago)
Contract B (progressPercentage=5, lastActivityDate=2 days ago)
Contract C (progressPercentage=80, lastActivityDate=30 days ago)
- **Action:** GET /api/contracts/stalled?maxProgress=50&stalledDays=7.
- **Expect:** 200 — only Contract A returned (progress ≤ 50 AND stalled > 7 days), with freelancerName and jobTitle populated via Feign.
- **Verify:** Feign calls only for the surviving candidate (A); B and C are filtered out before enrichment.
Features Verified as NOT Cross-Service (Freelance-Specific)
S4-F4 (Batch Contract Status Update) — operates only on the local contracts table; the request body carries the IDs and target statuses. Validates each contract exists in contract-postgres only. No M3 change required.

S4-F6 (Get Contracts in Date Range) — pure local query on contracts filtered by createdAt and optional status. Response is the contract list. No M3 change required.

S4-F7 (Purge Old Contract Data) — local DELETE on contracts filtered by status (COMPLETED/TERMINATED) and age. No M3 change required.

S4-F2 (Update Contract Progress with Metadata) — local JSONB update on contracts.metadata. No M3 change required.

S4-F5 (Filter Contracts by Metadata) — local JSONB query on contracts.metadata. No M3 change required.

S4-F10 (Contract Analytics Dashboard) (M2) — aggregates only over contracts per the M2 spec. The DTO does not require freelancer or job enrichment. No M3 change required.

## RabbitMQ: S4 Publishes
| Routing key | Exchange | Payload | When |
| --- | --- | --- | --- |
| contract.created | contract.events | {contractId, proposalId, jobId, freelancerId, agreedAmount} | After consuming proposal.accepted and creating the Contract |
| contract.status-changed | contract.events | {contractId, oldStatus, newStatus} | When contract status is updated (e.g., COMPLETED on saga completion) |
| contract.cancelled | contract.events | {contractId, proposalId} | After consuming proposal.cancelled (compensation) and reverting the contract |
## RabbitMQ: S4 Consumes
| Routing key | From exchange | Action |
| --- | --- | --- |
| proposal.accepted | proposal.events | Create new Contract entity (status=ACTIVE) in contract-postgres → publish contract.created |
| proposal.completed | proposal.events | Update Contract status to COMPLETED + set endDate → publish contract.status-changed |
| proposal.cancelled | proposal.events | If a Contract exists for this proposal, set its status to TERMINATED (covers both ACTIVE → TERMINATED early cancellation and COMPLETED → TERMINATED post-completion compensation when payout fails) → publish contract.cancelled |
| user.deactivated | user.events | Optional bookkeeping: log deactivation against ACTIVE contracts (no status change unless business rule requires) |
Queue declarations: contract.saga-listener with DLQ contract.saga-listener.dlq.

## S4 Deliverables
- [ ] DB isolation: datasource → freelancedb-contracts
- [ ] Implement GET /api/contracts/user/{userId}/summary, active-count, completed-count
- [ ] Implement GET /api/contracts/job/{jobId}/active-count
- [ ] Implement GET /api/contracts/proposal/{proposalId}/active — returns active contract or 404
- [ ] feign.user-service.url and feign.job-service.url in application.yml
- [ ] UserServiceClient Feign interface with getUser
- [ ] JobServiceClient Feign interface with getJob
- [ ] S4-F1 refactored to use Feign → user-service for user existence
- [ ] S4-F3 refactored to use Feign → user-service + job-service for enrichment
- [ ] S4-F8 refactored to use Feign → user-service for freelancer existence
- [ ] S4-F9 refactored to use Feign → user-service + job-service for enrichment
- [ ] RabbitMQ contract.events TopicExchange declared
- [ ] Consumer for proposal.accepted: create Contract, publish contract.created
- [ ] Consumer for proposal.completed: mark Contract COMPLETED, publish contract.status-changed
- [ ] Consumer for proposal.cancelled: revert Contract, publish contract.cancelled
- [ ] Consumer for user.deactivated (optional bookkeeping)
- [ ] logback-spring.xml with Loki4J appender
