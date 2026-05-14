# Section 7 — Wallet Service Refactoring (S5)

> Split from `../m3.txt`. Original file is untouched.

## New Endpoints S5 Must Expose
| Endpoint | Called by | Returns | Description |
| --- | --- | --- | --- |
| GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate | S1 (S1-F6) | BigDecimal | Total COMPLETED payout amount for this freelancer in the date range. 0.0 if no payouts. |
## [S5-F3] Freelancer Payout Summary
- **Branch:** `feat/M3/wallet/S5-F3/<studentID>`
- **Endpoint:** `GET /api/payouts/freelancer/{freelancerId}/summary`

**M1 implementation:** SELECT FROM users WHERE id = ? on the shared database to verify the freelancer exists before aggregating payouts.

**M3 change:** Replace the user existence check with Feign → user-service.

```java
try {
    userServiceClient.getUser(freelancerId); // throws FeignException.NotFound if not found
} catch (FeignException.NotFound e) {
    throw new NotFoundException("Freelancer not found: " + freelancerId);
}
// Then aggregate locally from wallet-postgres (group by method)
```
The aggregation logic (totalPayouts, totalAmount, methodBreakdown) remains local — the payouts table is owned by S5.

## Test Scenario

- **Setup:** Freelancer ID=1 in user-postgres. 4 COMPLETED payouts in wallet-postgres for freelancerId=1: 2 BANK_TRANSFER (1500+2000=3500), 1 PAYPAL (800), 1 CRYPTO (500).
- **Action:** GET /api/payouts/freelancer/1/summary.
- **Expect:** 200 — totalPayouts=4, totalAmount=4800, methodBreakdown={BANK_TRANSFER:3500, PAYPAL:800, CRYPTO:500}.
- **Action:** GET /api/payouts/freelancer/999/summary → Feign → user-service throws 404 → 404.
## [S5-F4] Process Payout for Contract
- **Branch:** `feat/M3/wallet/S5-F4/<studentID>`
- **Endpoint:** `POST /api/payouts/contract/{contractId}`

**M1 implementation:** SELECT * FROM contracts WHERE id = ? on the shared database to verify the contract exists and is COMPLETED.

**M3 change:** Replace contract status validation with Feign → contract-service.

ContractDTO contract = contractServiceClient.getContract(contractId);
```java
// In M3 saga context, the contract should be COMPLETED (saga has marked it after proposal.completed → contract.status-changed)
```
if (!contract.getStatus().equals("COMPLETED")) {
throw new BadRequestException("Contract is not completed. Status: " + contract.getStatus());
}
```java
// Authorization: only the client who owns the contract (or an ADMIN) may release the payout.
```
Long callerId = Long.parseLong(headers.getFirst("X-User-Id"));
String callerRole = headers.getFirst("X-User-Role");
if (!"ADMIN".equals(callerRole) && !contract.getClientId().equals(callerId)) {
throw new ForbiddenException("Only the contract's client (or an ADMIN) can release this payout");
}
Race-resilient Feign call (referenced from §5 S3-F2 note). If contractClient.getContract(contractId) throws FeignException.NotFound, the contract record may be in-flight from the proposal.accepted → contract.created event. Retry up to 3 attempts with 200 ms exponential backoff (200 ms → 400 ms → 800 ms) before giving up and throwing 404. Implement with @Retryable(value = FeignException.NotFound.class, maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2)) on a small wrapper method, or with manual Thread.sleep retry. Other FeignException subclasses (5xx, connection errors) are NOT retried — fall through to the existing try/catch from §2.4.

Idempotency on duplicate POST. POST /api/payouts/contract/{contractId} is the only endpoint in this saga that a human client triggers and may retry on flaky networks. To stay idempotent: at the start of S5-F4, look up the PENDING Payout for contractId; if its status is already COMPLETED or FAILED, return 200 with the existing Payout (no second payment.completed/payment.failed event published). Only mutate + publish when the Payout is still PENDING.

Migration note from M1: M1 threw 400 ("already paid") on a duplicate POST. M3 upgrades this to 200 with the existing record so retry-safe clients work correctly under network partitions. M1 clients that explicitly handled the 400 "already paid" response should now also accept 200 with status=COMPLETED (or FAILED) as the same outcome — both responses convey "this payout is already done."

After successful payout processing:

Update the existing PENDING Payout record (created by the saga via payment.initiated) in wallet-postgres (status = COMPLETED, set method, populate JSONB transactionDetails).
Publish payment.completed to payment.events. Note: this endpoint does NOT publish payment.initiated — that event was already emitted by the proposal.completed consumer that pre-created the PENDING payout. S5-F4 only publishes payment.completed or payment.failed.
On payout failure (or if ?simulateFailure=true is passed per M2 affordance):

Mark Payout status = FAILED.
Publish payment.failed to payment.events.
## Test Scenario

- **Setup:** Contract ID=1 in contract-postgres: status=COMPLETED, agreedAmount=3000, clientId=42. PENDING Payout in wallet-postgres for contractId=1 (created by the saga earlier).
- **Action:** POST /api/payouts/contract/1 body {method: "BANK_TRANSFER", accountLastFour: "9876"} with X-User-Id: 42, X-User-Role: CLIENT.
- **Expect:** 201 — Payout status=COMPLETED, method=BANK_TRANSFER, JSONB populated. payment.completed event published.
- **Verify:** Feign call to contract-service to validate status=COMPLETED. No direct query on contract-postgres.
- **Action:** Contract status=ACTIVE (not COMPLETED) → 400.
- **Action:** Same POST with X-User-Id: 99 (a different client) → 403.
- **Action:** Same POST with X-User-Id: 99, X-User-Role: ADMIN → succeeds (admin override).
- **Action:** Idempotency: call POST /api/payouts/contract/1 a second time after step 3 — Payout is already COMPLETED → 200 returning the existing record, no second payment.completed published.
## [S5-F10] Get Platform Fee Analytics by Job Category (M2)
- **Branch:** `feat/M3/wallet/S5-F10/<studentID>`
- **Endpoint:** `GET /api/payouts/analytics/category?startDate={date}&endDate={date}`

M2 implementation: Three-table JOIN on the shared database: payouts JOIN contracts ON contracts.id = payouts.contract_id JOIN jobs ON jobs.id = contracts.job_id — reads jobs.category from the shared database.

**M3 change:** Two rounds of Feign calls replace the JOIN:

Fetch all COMPLETED payouts in date range from wallet-postgres (local query, no JOIN).
For each payout, Feign → contract-service GET /api/contracts/{contractId} → get jobId.
For each jobId, Feign → job-service GET /api/jobs/{jobId} → get category.
Group by category, aggregate platformFeeRevenue (read from transactionDetails.platformFee JSONB key per M2 spec, fallback to 0.10 * amount if missing), netPayoutRevenue, totalRevenue, payoutCount.
Optimization: Cache contractId → jobId and jobId → category lookups locally (Map within request lifecycle) to avoid duplicate Feign calls when payouts share contracts or jobs.

## Test Scenario

- **Setup:** Jobs in job-postgres: J1 (WEB_DEV), J2 (MOBILE), J3 (WEB_DEV). Contracts in contract-postgres: C1→J1, C2→J2, C3→J3, C4→J1, C5→J2. Payouts in wallet-postgres (March 2026): 3 COMPLETED on WEB_DEV contracts totaling 6000 (platformFee 600), 2 COMPLETED on MOBILE contracts totaling 4000 (platformFee 400).
- **Action:** GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31.
- **Expect:** 200 — [{category: WEB_DEV, netPayoutRevenue: 5400, platformFeeRevenue: 600, totalRevenue: 6000, payoutCount: 3}, {category: MOBILE, netPayoutRevenue: 3600, platformFeeRevenue: 400, totalRevenue: 4000, payoutCount: 2}].
- **Verify:** No JOIN across wallet-postgres, contract-postgres, and job-postgres. Two rounds of Feign calls.
## RabbitMQ: S5 Publishes
| Routing key | Exchange | Payload | When |
| --- | --- | --- | --- |
| payment.initiated | payment.events | {payoutId, proposalId, contractId, amount} | After consuming proposal.completed and creating a PENDING payout |
| payment.completed | payment.events | {payoutId, proposalId, contractId, amount} | After S5-F4 successfully processes payout |
| payment.failed | payment.events | {payoutId, proposalId, contractId, reason} | After S5-F4 fails to process payout |
| payment.refunded | payment.events | {payoutId, proposalId, contractId, refundAmount} | After consuming proposal.cancelled and refunding the payout |
## RabbitMQ: S5 Consumes
| Routing key | From exchange | Action |
| --- | --- | --- |
| proposal.completed | proposal.events | Feign → user-service for freelancer profile → create PENDING payout in wallet-postgres → publish payment.initiated |
| proposal.cancelled | proposal.events | If a PENDING/COMPLETED payout exists for this proposal → process refund (apply S5-F12 reversal logic) → publish payment.refunded |
Queue declaration: payment.saga-listener with DLQ payment.saga-listener.dlq.

## S5 Deliverables
- [ ] DB isolation: datasource → freelancedb-wallet
- [ ] Expose GET /api/payouts/freelancer/{freelancerId}/total?startDate&endDate
- [ ] feign.user-service.url, feign.contract-service.url, feign.job-service.url in application.yml
- [ ] UserServiceClient Feign interface with getUser
- [ ] ContractServiceClient Feign interface with getContract
- [ ] JobServiceClient Feign interface with getJob
- [ ] S5-F3 refactored to use Feign → user-service for user existence check
- [ ] S5-F4 refactored to use Feign → contract-service for contract status validation; publishes payment.completed or payment.failed
- [ ] S5-F10 refactored to use Feign → contract-service + job-service for category grouping
- [ ] RabbitMQ payment.events TopicExchange declared
- [ ] Consumer for proposal.completed: Feign → user-service, create PENDING payout, publish payment.initiated
- [ ] Consumer for proposal.cancelled: refund if payout exists, publish payment.refunded
- [ ] logback-spring.xml with Loki4J appender
