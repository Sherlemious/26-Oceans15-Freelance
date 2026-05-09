# Section 4 — Job Service Refactoring (S2)

> Split from `../m3.txt`. Original file is untouched.

## New Endpoints S2 Must Expose
These endpoints are called by other services via Feign. They must exist before S1, S3, S4, S5 SYNC branches are merged.

| Endpoint | Called by | Returns | Description |
| --- | --- | --- | --- |
| GET /api/jobs/{id} | S3, S4 | JobDTO | Already exists (M1 CRUD). Verify it returns id, clientId, title, description, category, status, budgetMin, budgetMax, rating. |
## [S2-F3] Get Job Proposal Summary
- **Branch:** `feat/M3/job/S2-F3/<studentID>`
- **Endpoint:** `GET /api/jobs/{id}/proposal-summary?startDate={date}&endDate={date}`

**M1 implementation:** Native SQL aggregate on proposals table for this job in date range — totalProposals, averageBidAmount, lowestBid, highestBid.

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
JobProposalSummaryDTO from proposal-service: {totalProposals, averageBidAmount, lowestBid, highestBid}

job-service merges this with the local Job.title to build the response DTO.

## Test Scenario

- **Setup:** Job ID=1 (title="E-Commerce Backend") in job-postgres. 5 proposals in proposal-postgres for jobId=1 with bidAmounts 500, 800, 1200, 600, 900 submitted in March 2026.
- **Action:** GET /api/jobs/1/proposal-summary?startDate=2026-03-01&endDate=2026-03-31.
- **Expect:** 200 — jobId=1, title="E-Commerce Backend", totalProposals=5, averageBidAmount=800, lowestBid=500, highestBid=1200.
- **Verify:** No direct JOIN on proposal-postgres from job-service.
## [S2-F4] Close Job Posting
- **Branch:** `feat/M3/job/S2-F4/<studentID>`
- **Endpoint:** `PUT /api/jobs/{id}/close`

**M1 implementation:** Two cross-service operations on the shared database:

SELECT COUNT(*) FROM contracts WHERE job_id = ? AND status = 'ACTIVE' to check no active contracts exist.
UPDATE proposals SET status='REJECTED' WHERE job_id = ? AND status='SUBMITTED' to reject all submitted proposals.
**M3 change:** 

Feign call to contract-service GET /api/contracts/job/{jobId}/active-count → returns int. If > 0, throw 400 ("Job has active contracts").
Update the job's status to CLOSED in job-postgres and save.
Publish job.closed event to job.events. proposal-service consumes this event and rejects all SUBMITTED proposals for that job in proposal-postgres.
## Test Scenario

- **Setup:** Job ID=1 (status=OPEN) in job-postgres. ACTIVE contract for jobId=1 in contract-postgres.
- **Action:** PUT /api/jobs/1/close → Feign returns active-count=1.
- **Expect:** 400 — cannot close job with active contracts.
- **Setup:** Update the contract status to COMPLETED. Add 3 SUBMITTED proposals for jobId=1 in proposal-postgres.
- **Action:** PUT /api/jobs/1/close → Feign returns active-count=0.
- **Expect:** 200 — job status = CLOSED in job-postgres. RabbitMQ job.closed event published. After event processing, the 3 SUBMITTED proposals in proposal-postgres are now REJECTED.
## [S2-F7] Rate Job Client After Contract
- **Branch:** `feat/M3/job/S2-F7/<studentID>`
- **Endpoint:** `POST /api/jobs/{id}/rate`

**M1 implementation:** SELECT * FROM contracts WHERE id = ? AND job_id = ? AND status = 'COMPLETED' on the shared database to verify the contract belongs to this job and is COMPLETED.

**M3 change:** Replace with Feign → contract-service GET /api/contracts/{contractId} (reuses existing M1 CRUD endpoint). Validate the returned contract:

Exists (Feign returns 404 → throw 404)
jobId matches the job being rated (mismatch → throw 400)
status = COMPLETED (wrong status → throw 400)
Rating is between 1 and 5 (out of range → throw 400)
If all checks pass, recalculate Job's running average rating, update Job.rating and Job.totalRatings, and save. Publish job.rated RabbitMQ event.

## Test Scenario

- **Setup:** Job ID=1 (rating=0.0, totalRatings=0) in job-postgres. Contract ID=10 in contract-postgres: jobId=1, status=COMPLETED.
- **Action:** POST /api/jobs/1/rate body {contractId: 10, rating: 5}.
- **Expect:** 200 — Job rating=5.0, totalRatings=1. job.rated event published.
- **Action:** Same call again with rating=3 referencing a different completed contract → Job rating=4.0, totalRatings=2.
- **Action:** POST /api/jobs/1/rate body {contractId: 99, rating: 4} → Feign returns 404 → throw 404.
- **Action:** POST /api/jobs/1/rate body {contractId: 10, rating: 6} → 400 (rating out of range).
- **Action:** Rate with a contract belonging to a different job → 400.
## [S2-F12] Get Job Market Dashboard (M2)
- **Branch:** `feat/M3/job/S2-F12/<studentID>`
- **Endpoint:** `GET /api/jobs/{id}/dashboard`

M2 implementation: Aggregated totalProposals, acceptedProposals, averageBidAmount from proposals WHERE job_id = ? on the shared database. activeAttachments and rating come from S2's own tables (job_attachments + jobs — no change needed).

**M3 change:** The proposals aggregation becomes a Feign call to proposal-service — reuse the same GET /api/proposals/job/{jobId}/summary endpoint from S2-F3 (extend the DTO to include acceptedProposals).

## Test Scenario

- **Setup:** Job ID=5 in job-postgres with 2 active job_attachments (expiryDate >= today) and rating=4.5. In proposal-postgres: 5 proposals for jobId=5 with bidAmounts 500/800/1000/1200/900 (1 ACCEPTED, others SUBMITTED).
- **Action:** GET /api/jobs/5/dashboard with valid Bearer token.
- **Expect:** 200 — jobId=5, title=..., totalProposals=5, acceptedProposals=1, averageBidAmount=880, activeAttachments=2, rating=4.5.
- **Action:** GET /api/jobs/999/dashboard → 404.
## RabbitMQ: S2 Publishes
| Routing key | Exchange | Payload | When |
| --- | --- | --- | --- |
| job.status-changed | job.events | {jobId, oldStatus, newStatus} | When job transitions between statuses |
| job.rated | job.events | {jobId, contractId, rating, ratedBy} | After S2-F7 rating submission |
| job.closed | job.events | {jobId, clientId} | After S2-F4 closes the job |
## RabbitMQ: S2 Consumes
| Routing key | From exchange | Action |
| --- | --- | --- |
| proposal.accepted | proposal.events | Update job status to IN_PROGRESS for the referenced jobId in job-postgres |
| proposal.completed | proposal.events | Update job status to CLOSED for the referenced jobId in job-postgres |
| proposal.cancelled | proposal.events | If saga compensation, revert job status if needed (stats reverse) |
| proposal.withdrawn | proposal.events | If this was the only active proposal, revert job status to OPEN (M1 S3-F7 behavior) |
Queue declaration: job.proposal.saga-listener with DLQ job.proposal.saga-listener.dlq.

## S2 Deliverables
- [ ] DB isolation: datasource → freelancedb-jobs
- [ ] feign.contract-service.url and feign.proposal-service.url in application.yml
- [ ] ContractServiceClient Feign interface with getActiveContractCountForJob, getContract
- [ ] ProposalServiceClient Feign interface with getJobProposalSummary
- [ ] S2-F3 refactored to use Feign → proposal-service
- [ ] S2-F4 refactored to use Feign → contract-service for active-contract check; publishes job.closed
- [ ] S2-F7 refactored to use Feign → contract-service for contract validation; publishes job.rated
- [ ] S2-F12 refactored to use Feign → proposal-service for proposal aggregation
- [ ] RabbitMQ job.events TopicExchange declared
- [ ] job.status-changed published on status transitions
- [ ] job.rated published on S2-F7
- [ ] job.closed published on S2-F4
- [ ] Consumer for proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn with auto ACK + DLQ via x-dead-letter-exchange
- [ ] logback-spring.xml with Loki4J appender
