# Section 3 — User Service Refactoring (S1)

> Split from `../m3.txt`. Original file is untouched.

## New Endpoints S1 Must Expose
No new external endpoints — user-service already exposes GET /api/users/{id} (M1 CRUD). Downstream services call this existing endpoint.

## Features That Require Feign Calls
## [S1-F3] Get User Contract Summary
- **Branch:** `feat/M3/user/S1-F3/<studentID>`
- **Endpoint:** `GET /api/users/{id}/contract-summary`

**M1 implementation:** Direct native SQL JOIN on the shared contracts table aggregating totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue for the given user.

**M3 change:** Replace the SQL JOIN with a Feign call to contract-service, so that the interface would be:

```java
@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {
    @GetMapping("/api/contracts/user/{userId}/summary")
    UserContractSummaryDTO getUserContractSummary(@PathVariable Long userId);
}
```
UserContractSummaryDTO returned by contract-service: {totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue}

user-service calls this and merges it with the local User data to build the full response DTO with userId, name.

## Test Scenario

- **Setup:** In user-postgres: create User ID=1 (name="Ahmed"). In contract-postgres: create 5 contracts for freelancerId=1 — 3 COMPLETED with agreedAmounts 500, 1000, 1500; 1 TERMINATED; 1 ACTIVE.
- **Action:** GET /api/users/1/contract-summary with valid Bearer token.
- **Expect:** 200 — userId=1, name="Ahmed", totalContracts=5, completedContracts=3, terminatedContracts=1, totalEarnings=3000.00, averageContractValue=1000.00.
- **Verify:** No direct JDBC connection from user-postgres to contract-postgres. The contract counts come from a Feign HTTP call.
## [S1-F4] Deactivate User Account
- **Branch:** `feat/M3/user/S1-F4/<studentID>`
- **Endpoint:** `PUT /api/users/{id}/deactivate`

**M1 implementation:** SELECT COUNT(*) FROM contracts WHERE freelancer_id = ? AND status = 'ACTIVE' directly on the shared database, plus UPDATE proposals SET status='WITHDRAWN' WHERE freelancer_id = ? AND status='SUBMITTED'.

**M3 change:** Replace the cross-service SQL with:

Feign call to contract-service GET /api/contracts/user/{userId}/active-count → returns int. If > 0, throw 400 ("User has active contracts").
Set the user's status to DEACTIVATED in user-postgres and save.
Publish user.deactivated event to user.events. proposal-service consumes this event and withdraws all SUBMITTED proposals for that freelancer in proposal-postgres.
## Test Scenario

- **Setup:** User ID=1 in user-postgres (role=FREELANCER). Contract in contract-postgres: freelancerId=1, status=ACTIVE.
- **Action:** PUT /api/users/1/deactivate → Feign → contract-service returns active-count=1.
- **Expect:** 400 — cannot deactivate user with active contracts.
- **Setup:** Update the contract status to COMPLETED in contract-postgres. Add 2 SUBMITTED proposals for freelancerId=1 in proposal-postgres.
- **Action:** PUT /api/users/1/deactivate → Feign returns active-count=0.
- **Expect:** 200 — user status = DEACTIVATED in user-postgres. RabbitMQ user.deactivated event published. After event processing, the 2 SUBMITTED proposals in proposal-postgres are now WITHDRAWN.
## [S1-F6] Top Freelancers by Earnings
- **Branch:** `feat/M3/user/S1-F6/<studentID>`
- **Endpoint:** `GET /api/users/reports/top-freelancers?startDate={date}&endDate={date}&limit={n}`

**M1 implementation:** Native SQL JOIN of users with payouts (or contracts) GROUP BY freelancer ORDER BY SUM(amount) DESC.

**M3 change:** user-service cannot JOIN the payouts table (it lives in wallet-postgres). Instead:

Fetch all FREELANCER users from user-postgres.
For each freelancer, call Feign → GET /api/payouts/freelancer/{freelancerId}/total?startDate={d}&endDate={d} on wallet-service → returns BigDecimal (total COMPLETED payout amount for this freelancer in the date range).
Sort freelancers by total descending, take first limit.
Build and return List<TopFreelancerDTO> with userId, name, totalEarnings, contractCount (also fetched per freelancer via Feign → contract-service).
**Note on date filtering:** Pass startDate and endDate as query params to the wallet-service endpoint so it filters server-side rather than fetching all payouts.

```java
@FeignClient(name = "wallet-service", url = "${feign.wallet-service.url}")
public interface WalletServiceClient {
    @GetMapping("/api/payouts/freelancer/{freelancerId}/total")
    BigDecimal getFreelancerPayoutTotal(
        @PathVariable Long freelancerId,
        @RequestParam String startDate,
        @RequestParam String endDate
    );
}
```
## Test Scenario

- **Setup:** 3 FREELANCER users in user-postgres (Ahmed, Sara, Omar). In wallet-postgres: COMPLETED payouts within March 2026 — Ahmed = 3000 total, Sara = 8000 total, Omar = 1000 total.
- **Action:** GET /api/users/reports/top-freelancers?startDate=2026-03-01&endDate=2026-03-31&limit=2.
- **Expect:** 200 — [{userId: Sara, totalEarnings: 8000, contractCount: ...}, {userId: Ahmed, totalEarnings: 3000, contractCount: ...}].
- **Verify:** user-service made Feign calls to wallet-service for each freelancer's total. No direct query on payouts table.
## [S1-F9] Find Users by Language Preference with Minimum Contracts
- **Branch:** `feat/M3/user/S1-F9/<studentID>`
- **Endpoint:** `GET /api/users/preferences/language?lang={lang}&minContracts={n}`

**M1 implementation:** JSONB query on users.preferences for language match + subquery counting contracts rows by freelancer_id where status=COMPLETED.

**M3 change:** 

Query user-postgres for users whose preferences->>'language' matches the given value.
For each matching user, call Feign → GET /api/contracts/user/{userId}/completed-count → returns long.
Keep only users whose returned count ≥ minContracts.
## Test Scenario

- **Setup:** 3 users in user-postgres: User A (preferences language=ar), User B (preferences language=ar), User C (preferences language=en). In contract-postgres: User A has 5 COMPLETED contracts, User B has 2 COMPLETED contracts, User C has 10.
- **Action:** GET /api/users/preferences/language?lang=ar&minContracts=3.
- **Expect:** 200 — only User A returned (User B has 2 < 3).
- **Verify:** Feign call made to contract-service for each candidate user.
## RabbitMQ: S1 Publishes
| Routing key | Exchange | Payload | When |
| --- | --- | --- | --- |
| user.registered | user.events | {userId, email, role} | After successful user registration (auth endpoint) |
| user.deactivated | user.events | {userId} | After S1-F4 successfully sets DEACTIVATED |
## RabbitMQ: S1 Consumes
| Routing key | From exchange | Action |
| --- | --- | --- |
| proposal.completed | proposal.events | Update freelancer's stats (increment completed contracts, total earnings) in user-postgres |
| proposal.cancelled | proposal.events | Reverse freelancer's stats (decrement completed contracts, subtract amount) in user-postgres |
Queue declaration: user.proposal.saga-listener with DLQ user.proposal.saga-listener.dlq.

## S1 Deliverables
- [ ] Confirm cross-service @ManyToOne fields are plain Long in User / UserSkill entities (Freelance M1 already used Longs)
- [ ] feign.contract-service.url and feign.wallet-service.url in application.yml
- [ ] ContractServiceClient Feign interface with getUserContractSummary, getActiveContractCount, getCompletedContractCount
- [ ] WalletServiceClient Feign interface with getFreelancerPayoutTotal
- [ ] S1-F3 refactored to use Feign → contract-service
- [ ] S1-F4 refactored to use Feign → contract-service for active-contract check; publishes user.deactivated after success
- [ ] S1-F6 refactored to use Feign → wallet-service per freelancer
- [ ] S1-F9 refactored to use Feign → contract-service per matching user
- [ ] RabbitMQ user.events TopicExchange declared
- [ ] user.registered published on registration
- [ ] user.deactivated published on S1-F4
- [ ] Consumer for proposal.completed and proposal.cancelled with auto ACK + DLQ via x-dead-letter-exchange
- [ ] logback-spring.xml with Loki4J appender (see Section 11)
