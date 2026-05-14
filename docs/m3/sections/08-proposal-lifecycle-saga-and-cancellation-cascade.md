# Section 8 — Proposal Lifecycle Saga & Cancellation Cascade

> Split from `../m3.txt`. Original file is untouched.

## 8.1 What Is a Choreography Saga
When a business transaction spans multiple services, there is no distributed rollback. The Choreography Saga achieves eventual consistency through:

Forward path: each service listens for the previous step's success event and executes its part.
Compensation path: on failure, the failing service publishes a failure event; every service that already committed reverses its local change on receipt of the compensation event.
Why "Proposal Lifecycle"? Freelance Marketplace does not have a "deliver order → pay" flow like Talabat. The freelance lifecycle is: client posts a job → freelancer submits a proposal → client accepts → freelancer works on the contract → freelancer marks the contract as completed → client releases payout. The natural saga trigger is therefore S3-F4 Complete Proposal's Contract, which in M1 already performed the multi-service write (update contract, update job, create payout). M3 promotes this to a Choreography Saga and adds payout-failure compensation.

Liveness — manual payout step. Unlike a fully-automatic Talabat-style saga, Freelance has a human-in-the-loop step: after proposal.completed fans out and the proposal reaches PAYMENT_PENDING, the client must trigger POST /api/payouts/contract/{contractId} (S5-F4) for the saga to progress. To prevent a proposal from deadlocking forever in PAYMENT_PENDING if the client never posts, proposal-service runs a scheduled abandonment reaper (see §8.7) that auto-publishes payment.failed with reason = "payout_abandoned" after a configurable timeout. The reaper does not bypass the regular compensation path — it just synthesizes the missing failure signal so the cascade can run.

## 8.2 Saga Overview — All 5 Services
The saga is triggered by PUT /api/proposals/{id}/complete (S3-F4).

SAGA · S3-F4 · PROPOSAL LIFECYCLE
⏮
◀
▶
▶
⏭
1 / 24
01
SAGA TRIGGER
Freelancer initiates proposal completion.
HTTP TRIGGER
FEIGN (sync)
KAFKA EVENT
STATE TRANSITION
COMPENSATION
01
SAGA TRIGGER
02
PROPOSAL COMPLETING
03
FORWARD FAN-OUT
04
AWAITING PAYOUT
05
CUSTOMER PAYOUT
06a
OUTCOME · SUCCESS
06b
OUTCOME · FAILURE
07
COMPENSATION CASCADE
08
REFUNDED
CLIENT
S1
FREELANCER
S2
JOB
S3
PROPOSAL
S4
CONTRACT
S5
WALLET
ACCEPTED → COMPLETING
COMPLETING → PAYMENT_PENDING
PAYMENT_PENDING → PAID
PAYMENT_PENDING → PAYMENT_FAILED
PAYMENT_FAILED → REFUNDED
## 8.3 S3-F4 — Complete Proposal's Contract (Saga Trigger)
- **Branch:** `feat/M3/proposal/S3-F4/<studentID>`
- **Endpoint:** `PUT /api/proposals/{id}/complete`

**M1 implementation:** Multi-step transactional operation on the shared database:

Validate proposal status = ACCEPTED.
SELECT * FROM contracts WHERE proposal_id = ? AND status='ACTIVE' to verify there's an active contract (400 if not).
UPDATE contracts SET status='COMPLETED', endDate=now() WHERE id = ?.
UPDATE jobs SET status='CLOSED' WHERE id = ?.
INSERT INTO payouts (...) VALUES (...) with status=PENDING and amount=contract's agreedAmount.
Save proposal (keep status ACCEPTED).
**M3 change:** Remove the direct contract/job/payout writes. Run three Feign pre-checks, then publish proposal.completed.

## Behavior

Find proposal by ID → 404 if not found.
Validate status = ACCEPTED → 400 if not.
Authorization: read X-User-Id and X-User-Role headers (set by api-gateway after JWT validation). The caller must be either the proposal's freelancerId or have role ADMIN — otherwise return 403. (Only the assigned freelancer can mark their own contract complete.)
Pre-saga Feign checks (all three must pass before any event is published):
Feign → job-service GET /api/jobs/{jobId} → status must NOT be CLOSED; if 404 or already CLOSED → 400
Feign → user-service GET /api/users/{freelancerId} → status must be ACTIVE; if 404 or DEACTIVATED → 400
Feign → contract-service GET /api/contracts/proposal/{proposalId}/active → active contract must exist; capture the returned contractId and agreedAmount for the event payload; if 404 → 400
Mark proposal status = COMPLETING, save.
Publish proposal.completed to proposal.events exchange with payload {proposalId, jobId, freelancerId, contractId, agreedAmount}.
Return 200 with the updated proposal.
Proposal transitions from COMPLETING → PAYMENT_PENDING asynchronously when S3 consumes back the payment.initiated event from wallet-service.

## Test Scenario

- **Setup:** Proposal ID=1 in proposal-postgres: status=ACCEPTED, jobId=10, freelancerId=5, bidAmount=2000. Job ID=10 in job-postgres: status=IN_PROGRESS. User ID=5 in user-postgres: status=ACTIVE, role=FREELANCER. Contract in contract-postgres: proposalId=1, status=ACTIVE, agreedAmount=2000.
- **Action:** PUT /api/proposals/1/complete with X-User-Id: 5, X-User-Role: FREELANCER (caller = freelancerId).
- **Expect:** 200 — proposal status=COMPLETING. proposal.completed published to proposal.events.
- **Verify:** No direct insert into payouts from proposal-service. No direct UPDATE on contracts or jobs from proposal-service.
- **Action:** Same proposal, but no active contract in contract-postgres → Feign → contract-service returns 404 → 400. No event published.
- **Action:** Freelancer status=DEACTIVATED → Feign → user-service returns DEACTIVATED → 400. No event published.
- **Action:** PUT /api/proposals/1/complete with X-User-Id: 99, X-User-Role: FREELANCER (caller is a different freelancer, not the proposal owner).
- **Expect:** 403. No event published, no DB mutation.
- **Action:** PUT /api/proposals/1/complete with X-User-Id: 99, X-User-Role: ADMIN.
- **Expect:** 200 (admins bypass the freelancer-ownership check).
## 8.4 S3-F7 — Withdraw Proposal
- **Branch:** `feat/M3/proposal/S3-F7/<studentID>`
- **Endpoint:** `PUT /api/proposals/{id}/withdraw`

**M1 implementation:** UPDATE jobs SET status='OPEN' WHERE id = ? directly on the shared database when this was the only active proposal and the job is IN_PROGRESS.

**M3 change:** Remove the direct jobs write. Publish proposal.withdrawn — job-service consumes it and decides whether to revert the job status to OPEN based on its own remaining-active-proposals logic (job-service can query proposal-service via Feign or maintain a counter from proposal.accepted / proposal.withdrawn events).

## Behavior

Find proposal by ID → 404 if not found.
Validate status IN (SUBMITTED, SHORTLISTED) → 400 if not. (Cannot withdraw an ACCEPTED/COMPLETING/PAID proposal.)
Authorization: caller's X-User-Id must equal the proposal's freelancerId, or X-User-Role must be ADMIN — otherwise return 403. (Only the freelancer who submitted the proposal can withdraw it.)
Set proposal status = WITHDRAWN.
Publish proposal.withdrawn to proposal.events exchange with payload {proposalId, jobId, freelancerId}.
Return 200.
Job-service consumes proposal.withdrawn and applies its M1 logic: if this was the only active proposal for that job and the job is IN_PROGRESS, set job status back to OPEN.

Saga compensation cancel. S3 also publishes proposal.cancelled programmatically when payment.failed is consumed (saga compensation path — see §8.2). The same proposal.cancelled event is not triggered by this S3-F7 endpoint (which uses proposal.withdrawn for early-stage withdrawal). The compensation path's proposal.cancelled has a different fan-out (all 4 consumers reverse their state), while proposal.withdrawn is a job-service-only signal.

## Test Scenario

- **Setup:** Proposal ID=1 in proposal-postgres: status=SUBMITTED, jobId=10, freelancerId=5. Job ID=10 in job-postgres: status=IN_PROGRESS, with no other active proposals.
- **Action:** PUT /api/proposals/1/withdraw with X-User-Id: 5, X-User-Role: FREELANCER.
- **Expect:** 200 — proposal status=WITHDRAWN. proposal.withdrawn published.
- **Verify:** No direct update to jobs table from proposal-service. After event processing: job-postgres job ID=10 status=OPEN (job-service's logic applied).
- **Action:** Try withdrawing an ACCEPTED proposal → 400.
- **Action:** Try withdrawing a COMPLETING proposal → 400.
- **Action:** PUT /api/proposals/1/withdraw with X-User-Id: 99 (different freelancer) → 403.
- **Action:** Same call with X-User-Role: ADMIN → succeeds.
- **Action:** PUT /api/proposals/999/withdraw → 404 (proposal not found). No event published.
## 8.5 Saga Participant Summary
| Service | Feign calls in saga | Publishes | Consumes |
| --- | --- | --- | --- |
| user-service | Target of S3 + S5 pre-checks | (none — freelancer-stats updates are local DB only, no event emitted) | proposal.completed, proposal.cancelled |
| job-service | Target of S3 pre-check | job.status-changed | proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn |
| proposal-service | → job-service (pre-check), → user-service (pre-check), → contract-service (pre-check) | proposal.accepted, proposal.completed, proposal.cancelled, proposal.withdrawn | contract.created, contract.status-changed, payment.initiated, payment.completed, payment.failed, payment.refunded |
| contract-service | Target of S3 pre-check | contract.created, contract.status-changed, contract.cancelled | proposal.accepted, proposal.completed, proposal.cancelled |
| wallet-service | → user-service (profile), → contract-service (validate COMPLETED on S5-F4) | payment.initiated, payment.completed, payment.failed, payment.refunded | proposal.completed, proposal.cancelled |
## 8.6 Saga Test Scenarios
Async timing convention. Every step labelled (verify after event processing) requires polling — RabbitMQ delivery + listener invocation + DB write is eventual. Use Awaitility.await().atMost(5, SECONDS).until(...) (or equivalent) before each such verify; do NOT assert immediately after the action returns. Without an explicit wait/poll the test is flaky in CI.

## Scenario A — Happy path end-to-end

- **Setup:** In respective databases: User ID=1 (ACTIVE, FREELANCER), Job ID=10 (IN_PROGRESS), Proposal ID=20 (status=ACCEPTED, jobId=10, freelancerId=1, bidAmount=2000), Contract (proposalId=20, status=ACTIVE, agreedAmount=2000, clientId=42).
- **Action:** PUT /api/proposals/20/complete with X-User-Id: 1, X-User-Role: FREELANCER → all three pre-checks pass.
- **Expect:** 200. Proposal status = COMPLETING. proposal.completed published.
- **Wait:** await().atMost(5, SECONDS).until(...) for the event fan-out to settle (proposal-service receives payment.initiated from wallet-service after wallet creates the PENDING Payout).
- **Verify:** Proposal status = PAYMENT_PENDING; contract-postgres has Contract with status=COMPLETED + endDate set; wallet-postgres has PENDING Payout for proposalId=20 / contractId=...; job-postgres job ID=10 status=CLOSED.
- **Action:** POST /api/payouts/contract/{contractId} body {method: "BANK_TRANSFER", accountLastFour: "9876"} with X-User-Id: 42, X-User-Role: CLIENT.
- **Expect:** 201. payment.completed published.
- **Wait:** poll proposal-postgres for proposal status to become PAID (Awaitility, ≤5 seconds).
- **Verify:** Proposal status = PAID.
## Scenario B — Payout failure and compensation

- **Setup:** Same as Scenario A — reach Proposal status = PAYMENT_PENDING.
- **Action:** POST /api/payouts/contract/{contractId}?simulateFailure=true (M2's failure simulation affordance) or with deliberately invalid payload, with X-User-Id: 42, X-User-Role: CLIENT.
- **Expect:** 201 (or 200 — wallet handles failure as a normal Payout outcome). payment.failed published.
- **Wait:** poll for Proposal status = PAYMENT_FAILED (≤5 seconds).
- **Verify:** Proposal → PAYMENT_FAILED. proposal.cancelled published.
- **Wait:** poll until all four consumers (S1, S2, S4, S5) have processed proposal.cancelled (≤5 seconds).
- **Verify:** Freelancer stats reversed in user-postgres; job status reverted in job-postgres; Contract status reverted to TERMINATED in contract-postgres; payout = REFUNDED in wallet-postgres.
- **Wait:** poll for Proposal status = REFUNDED (after S3 consumes payment.refunded, ≤5 seconds).
- **Verify:** Proposal = REFUNDED.
## Scenario C — Pre-check failure (no active contract)

- **Setup:** User ID=1 (ACTIVE), Job ID=10 (IN_PROGRESS), Proposal ID=20 (ACCEPTED, freelancerId=1). No contract record in contract-postgres for proposalId=20.
- **Action:** PUT /api/proposals/20/complete with X-User-Id: 1, X-User-Role: FREELANCER.
- **Expect:** 400 — contract-service GET /api/contracts/proposal/20/active returns 404. S3 aborts before publishing any event.
(verify, no wait needed — synchronous) No proposal.completed event in RabbitMQ. Proposal status still = ACCEPTED. (Synchronous failure, so no polling required.)
## Scenario D — Payout abandonment (reaper-driven compensation)

- **Setup:** Same as Scenario A — reach Proposal status = PAYMENT_PENDING. The client never POSTs /api/payouts/contract/{contractId}.
- **Action:** Override the saga.payout.abandon-after config to PT5S for the test, then sleep ≥6 seconds (or trigger the reaper bean directly via a test hook).
- **Wait:** poll for Proposal status = PAYMENT_FAILED (the reaper synthesizes payment.failed, ≤10 seconds).
- **Expect:** payment.failed event in RabbitMQ with reason = "payout_abandoned". proposal.cancelled then published per the standard compensation path.
- **Wait:** poll until Proposal status = REFUNDED.
- **Verify:** End state matches Scenario B step 7 — every committed side-effect compensated.
## 8.7 Saga Infrastructure Deliverables
- [ ] ProposalEventConfig in proposal-service: proposal.events TopicExchange
- [ ] ContractEventConfig in contract-service: contract.events TopicExchange
- [ ] JobEventConfig in job-service: job.events TopicExchange
- [ ] UserEventConfig in user-service: user.events TopicExchange
- [ ] PaymentEventConfig in wallet-service: payment.events TopicExchange
- [ ] All consumer queue declarations with DLQ (one per service per exchange it listens to)
- [ ] All event payload record classes (e.g., ProposalCompletedEvent, PaymentFailedEvent, ContractCreatedEvent)
- [ ] Idempotency guard on POST /api/payouts/contract/{contractId} (S5-F4) — non-PENDING payouts return their existing record without publishing duplicate events
- [ ] Saga abandonment reaper in proposal-service: a @Scheduled(fixedDelayString = "PT15M") job that finds Proposals stuck in PAYMENT_PENDING for more than saga.payout.abandon-after (default PT72H / 72 hours) and publishes payment.failed with reason = "payout_abandoned" to fire the standard compensation cascade. Configurable via application.yml; logged at WARN level with proposalId MDC. This prevents a saga from deadlocking when a client never POSTs the manual payout step.
- [ ] Saga test scenarios A, B, C verified end-to-end
