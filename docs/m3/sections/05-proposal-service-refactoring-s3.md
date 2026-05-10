# Section 5 — Proposal Service Refactoring (S3)

> Split from `../m3.txt`. Original file is untouched.

## New Endpoints S3 Must Expose
These are called by S1, S2, S4, S5 via Feign. All must be implemented before the ASYNC branches merge.

| Endpoint | Called by | Returns | Description |
| --- | --- | --- | --- |
| GET /api/proposals/job/{jobId}/summary | S2 (S2-F3, S2-F12) | JobProposalSummaryDTO | {totalProposals, acceptedProposals, averageBidAmount, lowestBid, highestBid} filtered by date range |
| GET /api/proposals/{proposalId} | S4 (saga), S5 (saga), S2 | ProposalDTO | Already exists (M1 CRUD). Verify it returns id, jobId, freelancerId, status, bidAmount, acceptedAt. |
## [S3-F2] Accept Proposal and Create Contract
- **Branch:** `feat/M3/proposal/S3-F2/<studentID>`
- **Endpoint:** `PUT /api/proposals/{proposalId}/accept`

**M1 implementation:** Multi-step transactional operation on the shared database:

SELECT * FROM users WHERE id = ? AND role='FREELANCER' to verify the freelancer.
UPDATE jobs SET status='IN_PROGRESS' WHERE id = ?.
INSERT INTO contracts (...) VALUES (...) with status=ACTIVE, agreedAmount=bidAmount, startDate=now.
**M3 change:** Replace each cross-service step:

Find proposal (404 if not found). Validate proposal status is SUBMITTED or SHORTLISTED (400).
Feign → user-service GET /api/users/{freelancerId} → verify role is FREELANCER (404 if not found, 400 if not a freelancer).
Set the proposal's status to ACCEPTED and acceptedAt = now() in proposal-postgres. Save.
Publish proposal.accepted event to proposal.events. job-service consumes this event and updates the job status to IN_PROGRESS. contract-service consumes this event and creates a new ACTIVE contract record with the agreed amount.
Return the updated proposal.
```java
@FeignClient(name = "user-service", url = "${feign.user-service.url}")
public interface UserServiceClient {
    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable Long id);
}
```
Note: Contract creation moves from inline INSERT to event-driven. The proposal's acceptedAt is set immediately for client-side responsiveness, but the contract record exists only after proposal.accepted is consumed by contract-service. To avoid a race condition where S5-F4 (Process Payout) is called before the contract record exists, S5-F4 retries Feign → contract-service with a brief backoff if the contract is not yet found — see §7.

## Test Scenario

- **Setup:** Proposal ID=1 in proposal-postgres: status=SUBMITTED, jobId=10, freelancerId=5, bidAmount=2000. Job ID=10 in job-postgres: status=OPEN. User ID=5 in user-postgres: role=FREELANCER.
- **Action:** PUT /api/proposals/1/accept → Feign returns user with role=FREELANCER.
- **Expect:** 200 — proposal status=ACCEPTED, acceptedAt set. proposal.accepted event published.
(verify after event processing) job-postgres job ID=10 status=IN_PROGRESS; contract-postgres has a new ACTIVE contract referencing proposalId=1, jobId=10, freelancerId=5, agreedAmount=2000.
- **Action:** Try accepting again → 400 (proposal not SUBMITTED/SHORTLISTED).
- **Action:** Try with a user whose role is CLIENT → 400. Try with freelancerId=999 → Feign 404 → 404.
## [S3-F11] Record Freelancer-Job Interaction (M2)
- **Branch:** `feat/M3/proposal/S3-F11/<studentID>`
- **Endpoint:** `POST /api/proposals/{proposalId}/record-interaction`

M2 implementation: Cross-service native SQL pattern on shared database — query users WHERE id = ? (freelancer name) and jobs WHERE id = ? (job title) to fetch enrichment data for the Neo4j graph node creation.

**M3 change:** Replace both SQL queries with Feign calls.

```java
// Get user details from user-service
```
UserDTO freelancer = userServiceClient.getUser(proposal.getFreelancerId());

```java
// Get job details from job-service
```
JobDTO job = jobServiceClient.getJob(proposal.getJobId());

```java
// Then proceed with Neo4j graph write as in M2
// (FreelancerNode, JobNode, PROPOSED_TO relationship with idempotency marker)
```
The rest of the feature (Neo4j idempotency check via recorded_proposal_ids collection on the relationship, PROPOSED_TO relationship increment, MongoDB INTERACTION_RECORDED event logging) is unchanged from M2.

## Test Scenario

- **Setup:** User ID=5 in user-postgres (role=FREELANCER, name="Sara"). Job ID=10 in job-postgres (title="E-Commerce Backend"). Submitted proposal ID=20 in proposal-postgres: freelancerId=5, jobId=10.
- **Action:** POST /api/proposals/20/record-interaction.
- **Expect:** 200. Neo4j has (Freelancer:5)-[:PROPOSED_TO {proposalCount:1, recorded_proposal_ids:[20]}]->(Job:10). MongoDB has INTERACTION_RECORDED event.
- **Verify:** Feign calls made to user-service and job-service. No direct SQL on user-postgres or job-postgres from proposal-service.
## [S3-F12] Get Recommended Jobs for Freelancer (M2)
- **Branch:** `feat/M3/proposal/S3-F12/<studentID>`
- **Endpoint:** `GET /api/proposals/recommendations?freelancerId={id}&limit={n}`

M2 implementation: After traversing the Neo4j graph to find candidate jobs, the M2 spec enriches each result with the job's title and category from the shared jobs table via native SQL.

**M3 change:** Replace SQL enrichment with Feign calls.

```java
// Verify the requesting freelancer exists (still needs PG existence check)
```
UserDTO freelancer = userServiceClient.getUser(freelancerId);

```java
// For each candidate jobId from Neo4j graph traversal:
```
JobDTO job = jobServiceClient.getJob(candidateJobId);
```java
// Build JobRecommendationDTO from job + score
```
The Neo4j graph traversal logic, score calculation, and ownership check (caller's uid must match freelancerId or be ADMIN) are unchanged from M2.

## Test Scenario

- **Setup:** 3 freelancers (A=ID 1, B=ID 2, C=ID 3) in user-postgres. 4 jobs (J1 WEB_DEV, J2 MOBILE, J3 WEB_DEV, J4 DESIGN) in job-postgres. Neo4j edges: A→J1, A→J2, B→J1, B→J3, C→J2, C→J4.
- **Action:** GET /api/proposals/recommendations?freelancerId=1&limit=5 with A's token.
- **Expect:** 200 — should include J3 (B also proposed to J1 that A proposed to) and J4 (C also proposed to J2 that A proposed to). Should NOT include J1 or J2.
- **Verify:** Feign calls to user-service for A's existence and to job-service for J3 and J4 enrichment. No SQL on user-postgres or job-postgres from proposal-service.
S3-F4 and S3-F7 are RabbitMQ-based saga participants, not Feign refactors. They are fully described in Section 8 (Proposal Lifecycle Saga & Cancellation Cascade).

## RabbitMQ: S3 Publishes
| Routing key | Exchange | Payload | When |
| --- | --- | --- | --- |
| proposal.accepted | proposal.events | {proposalId, jobId, freelancerId, bidAmount} | After S3-F2 accepts the proposal |
| proposal.completed | proposal.events | {proposalId, jobId, freelancerId, contractId, agreedAmount} | After S3-F4 completes the proposal (saga trigger) |
| proposal.cancelled | proposal.events | {proposalId, jobId, freelancerId, reason} | After S3-F7 cancel/withdraw, or after compensation |
| proposal.withdrawn | proposal.events | {proposalId, jobId, freelancerId} | After S3-F7 withdraws a SUBMITTED/SHORTLISTED proposal |
## RabbitMQ: S3 Consumes
| Routing key | From exchange | Action |
| --- | --- | --- |
| contract.created | contract.events | Link the contractId into the proposal record (store contractId on the Proposal) |
| payment.initiated | payment.events | Mark proposal status = PAYMENT_PENDING |
| payment.completed | payment.events | Mark proposal status = PAID |
| payment.failed | payment.events | Mark proposal status = PAYMENT_FAILED → publish proposal.cancelled (compensation trigger) |
| payment.refunded | payment.events | Mark proposal status = REFUNDED |
Queue declaration: proposal.saga-feedback with DLQ proposal.saga-feedback.dlq.

## S3 Deliverables
- [ ] DB isolation: datasource → freelancedb-proposals
- [ ] Add saga statuses to Proposal status enum: COMPLETING, PAYMENT_PENDING, PAID, PAYMENT_FAILED, REFUNDED
- [ ] Expose GET /api/proposals/job/{jobId}/summary
- [ ] feign.user-service.url and feign.job-service.url and feign.contract-service.url in application.yml
- [ ] UserServiceClient Feign interface with getUser
- [ ] JobServiceClient Feign interface with getJob
- [ ] ContractServiceClient Feign interface with getContract
- [ ] S3-F2 refactored to use Feign → user-service for freelancer validation; publishes proposal.accepted
- [ ] S3-F4 refactored: pre-saga Feign checks + publish proposal.completed (no direct payout insert) — see §8
- [ ] S3-F7 refactored: publish proposal.withdrawn / proposal.cancelled (no direct job/contract write) — see §8
- [ ] S3-F11 refactored to use Feign → user-service + job-service
- [ ] S3-F12 refactored to use Feign → user-service + job-service
- [ ] RabbitMQ proposal.events TopicExchange declared
- [ ] Consumers for contract.created, payment.initiated, payment.completed, payment.failed, payment.refunded
- [ ] logback-spring.xml with Loki4J appender
