# ACL216 Proposal-Payment Saga Testing Guide

This document is the manual proof plan for ACL216. It is designed for a fresh Kubernetes cluster, through the API gateway, with real services, real PostgreSQL state, real RabbitMQ messages, and Awaitility-style polling using shell loops.

The goal is not just to see one happy path pass. The goal is to prove every critical saga transition works, that asynchronous effects actually happen, and that failure paths do not create hidden side effects.

## Scope

This guide verifies the choreography saga that spans:

- `user-service`: registers and authenticates CLIENT/FREELANCER users.
- `job-service`: creates jobs and reacts to proposal saga events.
- `proposal-service`: creates proposals, accepts proposals, completes work, consumes contract/payment feedback, and publishes compensation events.
- `contract-service`: creates ACTIVE contracts on proposal acceptance, completes contracts on proposal completion, and terminates completed contracts on payout-failure compensation.
- `wallet-service`: creates PENDING payouts from proposal completion, processes successful payouts, and publishes payment feedback.
- `contracts`: shared saga event payloads.
- RabbitMQ: routes `proposal.*`, `contract.*`, `job.*`, and `payment.*` messages.
- PostgreSQL: persists final source-of-truth state.
- Redis: may cache reads, so this guide checks for stale-cache regressions.

## What Counts As Passing

The saga is considered working only if all of these pass on a fresh cluster:

- Accept path: `SUBMITTED -> ACCEPTED`, job `OPEN -> IN_PROGRESS`, contract created as `ACTIVE`.
- Completion intermediate path: proposal `ACCEPTED -> COMPLETING -> PAYMENT_PENDING`, contract `ACTIVE -> COMPLETED`, job `IN_PROGRESS -> CLOSED`, payout created as `PENDING`.
- Payout success path: payout `PENDING -> COMPLETED`, proposal `PAYMENT_PENDING -> PAID`, contract remains `COMPLETED`, job remains `CLOSED`.
- Payout failure compensation path: payout `PENDING -> FAILED`, proposal `PAYMENT_PENDING -> PAYMENT_FAILED`, contract `COMPLETED -> TERMINATED`, job `CLOSED -> IN_PROGRESS`.
- Pre-check failure path: completing an accepted proposal without an active contract returns HTTP `400`, creates no payout, publishes no completion saga side effect, and leaves proposal/job unchanged.
- RabbitMQ queues have consumers and no new messages stuck in primary queues or DLQs for the tested run.
- Live E2E JUnit tests pass when explicitly enabled.

## Important Assumptions

- Run all commands from repository root.
- Gateway is reachable at `http://localhost:30080` after deploying Kubernetes manifests.
- If your local setup uses port-forward instead of NodePort, set `BASE=http://localhost:8080` after running `kubectl port-forward svc/gateway-service 8080:8080 -n freelance`.
- Use Docker Maven, not local Maven.
- These tests create disposable users/jobs/proposals/contracts/payouts. Use a fresh cluster when possible.

## Phase 0 - Start From The Correct Code

Before dropping the cluster, verify you are on the branch containing the saga fixes.

```bash
git status --short
git log --oneline -6
```

Expected recent commits include these subjects:

- `test(wallet): replace mocked saga e2e with opt-in live gateway ACL216 cross-service coverage using awaitility (58-1752)`
- `fix(saga): remove jwt-less wallet async contract lookup and return 400 on missing active contract precheck (58-1752)`
- `fix(job): evict saga cache keys after commit to prevent stale status reads during transitions (58-1752)`
- `fix(contract): isolate saga consumers and enable completed-contract compensation termination path (58-1752)`
- `fix(proposal): harden saga messaging and status persistence for completion flow reliability (58-1752)`
- `feat(proposal): persist job snapshot and publish clientId on proposal.accepted for contract bootstrap (58-1752)`

If the working tree is dirty because you are testing local conflict fixes later, that is fine, but you must rebuild images before redeploying.

## Phase 1 - Drop And Recreate The Cluster

Delete namespaces.

```bash
kubectl delete namespace freelance monitoring --ignore-not-found=true
```

Wait until both namespaces disappear.

```bash
kubectl get namespaces
```

Rebuild all Docker images from the latest code.

```bash
docker compose build
```

Recreate namespaces.

```bash
kubectl apply -f k8s/namespaces/namespace.yaml -f k8s/namespaces/monitoring-namespace.yaml
```

Apply manifests in the required non-recursive order.

```bash
kubectl apply \
  -f k8s/secrets \
  -f k8s/configmaps \
  -f k8s/services \
  -f k8s/statefulsets \
  -f k8s/deployments \
  -f k8s/api-gateway \
  -f k8s/monitoring/loki/ \
  -f k8s/monitoring/grafana \
  -f k8s/monitoring/prometheus \
  -f k8s/pvcs
```

Wait for app rollouts.

```bash
kubectl rollout status deployment/user-service -n freelance
kubectl rollout status deployment/job-service -n freelance
kubectl rollout status deployment/proposal-service -n freelance
kubectl rollout status deployment/contract-service -n freelance
kubectl rollout status deployment/wallet-service -n freelance
kubectl rollout status deployment/gateway -n freelance
```

Verify pods.

```bash
kubectl get pods -n freelance
```

Expected: all app pods are `Running`, and no app pod is repeatedly restarting.

## Phase 2 - Baseline Health And Infrastructure Checks

Set the gateway base URL.

```bash
export BASE=http://localhost:30080
```

Check gateway health.

```bash
curl -i "$BASE/actuator/health"
```

Expected: HTTP `200`.

Do not treat `/api/*/health` as a public check. On some builds, these endpoints are protected and return `401` without a bearer token.

Use `GET /actuator/health` as the canonical unauthenticated smoke check.

If you still want to check service health routes, call them with any valid token after login:

```bash
curl -i "$BASE/api/users/health" -H "Authorization: Bearer $CLIENT_TOKEN"
curl -i "$BASE/api/jobs/health" -H "Authorization: Bearer $CLIENT_TOKEN"
curl -i "$BASE/api/proposals/health" -H "Authorization: Bearer $CLIENT_TOKEN"
curl -i "$BASE/api/contracts/health" -H "Authorization: Bearer $CLIENT_TOKEN"
curl -i "$BASE/api/payouts/health" -H "Authorization: Bearer $CLIENT_TOKEN"
```

Expected: HTTP `200` for each route when authenticated.

Check RabbitMQ queues and consumers.

```bash
kubectl exec -n freelance rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

Expected primary queues have consumers:

- `job.proposal.saga-listener` has `1` consumer.
- `payment.saga-listener` has `1` consumer.
- `proposal.saga-feedback` has `1` consumer.
- `contract.saga-listener` has `1` consumer.
- `contract.user.saga-listener` has `1` consumer.

Expected message state before testing:

- Primary queues should ideally have `0` ready and `0` unacknowledged messages.
- DLQs should ideally have `0` ready and `0` unacknowledged messages on a fresh cluster.

If a DLQ has messages on a fresh cluster, stop and inspect logs before continuing.

```bash
kubectl logs deployment/proposal-service -n freelance --since=10m
kubectl logs deployment/contract-service -n freelance --since=10m
kubectl logs deployment/job-service -n freelance --since=10m
kubectl logs deployment/wallet-service -n freelance --since=10m
```

## Phase 3 - Helper Commands

These shell helpers are intentionally verbose. They make each run unique, collect IDs, and fail early if an HTTP call fails.

Create a file for local environment variables during testing.

```bash
export BASE=http://localhost:30080
export PASSWORD='Password123!'
export RUN_ID="$(date +%s)"
```

Register and log in a client and freelancer.

```bash
export CLIENT_EMAIL="client-${RUN_ID}@example.com"
export FREELANCER_EMAIL="freelancer-${RUN_ID}@example.com"

curl -sS -f -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Client ${RUN_ID}\",\"email\":\"${CLIENT_EMAIL}\",\"password\":\"${PASSWORD}\",\"phone\":\"+201${RUN_ID: -9}\",\"role\":\"CLIENT\"}"

curl -sS -f -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Freelancer ${RUN_ID}\",\"email\":\"${FREELANCER_EMAIL}\",\"password\":\"${PASSWORD}\",\"phone\":\"+202${RUN_ID: -9}\",\"role\":\"FREELANCER\"}"

CLIENT_LOGIN=$(curl -sS -f -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${CLIENT_EMAIL}\",\"password\":\"${PASSWORD}\"}")

FREELANCER_LOGIN=$(curl -sS -f -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${FREELANCER_EMAIL}\",\"password\":\"${PASSWORD}\"}")

export CLIENT_TOKEN=$(jq -r '.token' <<<"$CLIENT_LOGIN")
export FREELANCER_TOKEN=$(jq -r '.token' <<<"$FREELANCER_LOGIN")
export CLIENT_ID=$(jq -r '.userId' <<<"$CLIENT_LOGIN")
export FREELANCER_ID=$(jq -r '.userId' <<<"$FREELANCER_LOGIN")

printf 'CLIENT_ID=%s FREELANCER_ID=%s\n' "$CLIENT_ID" "$FREELANCER_ID"
```

Expected:

- Registration returns JSON with a token.
- Login returns `token`, `userId`, and `role`.
- Client role is `CLIENT`.
- Freelancer role is `FREELANCER`.

Create a job as the client.

```bash
JOB_JSON=$(curl -sS -f -X POST "$BASE/api/jobs" \
  -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"ACL216 Saga Job ${RUN_ID}\",\"description\":\"Manual saga verification\",\"category\":\"WEB_DEV\",\"budgetMin\":100.0,\"budgetMax\":500.0}")

export JOB_ID=$(jq -r '.id' <<<"$JOB_JSON")
printf 'JOB_ID=%s\n' "$JOB_ID"
```

Verify the job.

```bash
curl -sS -f "$BASE/api/jobs/$JOB_ID" \
  -H "Authorization: Bearer $CLIENT_TOKEN" | jq '{id, clientId, status, title}'
```

Expected:

```json
{
  "id": <JOB_ID>,
  "clientId": <CLIENT_ID>,
  "status": "OPEN",
  "title": "ACL216 Saga Job <RUN_ID>"
}
```

Create a proposal as the freelancer.

```bash
PROPOSAL_JSON=$(curl -sS -f -X POST "$BASE/api/proposals" \
  -H "Authorization: Bearer $FREELANCER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"jobId\":${JOB_ID},\"freelancerId\":${FREELANCER_ID},\"coverLetter\":\"I can do this ACL216 job\",\"bidAmount\":250.0,\"estimatedDays\":5,\"metadata\":{\"runId\":\"${RUN_ID}\"}}")

export PROPOSAL_ID=$(jq -r '.id' <<<"$PROPOSAL_JSON")
printf 'PROPOSAL_ID=%s\n' "$PROPOSAL_ID"
```

Verify the proposal.

```bash
curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" \
  -H "Authorization: Bearer $FREELANCER_TOKEN" | jq '{id, jobId, freelancerId, status, bidAmount}'
```

Expected:

- `jobId` equals `$JOB_ID`.
- `freelancerId` equals `$FREELANCER_ID`.
- `status` is `SUBMITTED`.
- `bidAmount` is `250.0`.

## Phase 4 - Scenario A1: Accept Proposal And Bootstrap Contract

Accept the proposal as the client.

```bash
curl -sS -f -X PUT "$BASE/api/proposals/$PROPOSAL_ID/accept" \
  -H "Authorization: Bearer $CLIENT_TOKEN" | jq '{id, status, jobId, freelancerId, contractId}'
```

Expected immediate response:

- HTTP `200`.
- Proposal status is `ACCEPTED`.

Poll until the asynchronous effects converge.

```bash
for i in $(seq 1 30); do
  PROPOSAL_STATUS=$(curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
  JOB_STATUS=$(curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')
  CONTRACT_JSON=$(curl -sS "$BASE/api/contracts/proposal/$PROPOSAL_ID/active" -H "Authorization: Bearer $CLIENT_TOKEN" || true)
  CONTRACT_ID=$(jq -r '.id // empty' <<<"$CONTRACT_JSON" 2>/dev/null || true)

  printf 'poll=%s proposal=%s job=%s contract=%s\n' "$i" "$PROPOSAL_STATUS" "$JOB_STATUS" "${CONTRACT_ID:-none}"

  if [ "$PROPOSAL_STATUS" = "ACCEPTED" ] && [ "$JOB_STATUS" = "IN_PROGRESS" ] && [ -n "$CONTRACT_ID" ]; then
    export CONTRACT_ID
    break
  fi
  sleep 1
done

test -n "${CONTRACT_ID:-}"
```

Expected final A1 state:

- Proposal: `ACCEPTED`.
- Job: `IN_PROGRESS`.
- Contract: exists and is `ACTIVE`.

Verify contract details.

```bash
curl -sS -f "$BASE/api/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $CLIENT_TOKEN" | jq '{id, proposalId, jobId, clientId, freelancerId, agreedAmount, status}'
```

Expected:

- `proposalId` equals `$PROPOSAL_ID`.
- `jobId` equals `$JOB_ID`.
- `clientId` equals `$CLIENT_ID`.
- `freelancerId` equals `$FREELANCER_ID`.
- `agreedAmount` equals `250.0`.
- `status` equals `ACTIVE`.

Proof points:

- `proposal-service` published `proposal.accepted` with `clientId`.
- `contract-service` consumed the event and created the contract.
- `job-service` consumed the event and moved the job to `IN_PROGRESS`.
- Redis did not keep the job at stale `OPEN`.

## Phase 5 - Scenario A2: Complete Work And Create Pending Payout

Complete the proposal as the freelancer.

```bash
curl -sS -f -X PUT "$BASE/api/proposals/$PROPOSAL_ID/complete" \
  -H "Authorization: Bearer $FREELANCER_TOKEN" | jq '{id, status, jobId, freelancerId}'
```

Expected immediate response:

- HTTP `200`.
- Proposal may show `COMPLETING` immediately because downstream services are asynchronous.

Poll until intermediate saga state converges.

```bash
for i in $(seq 1 60); do
  PROPOSAL_JSON=$(curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" -H "Authorization: Bearer $FREELANCER_TOKEN")
  JOB_JSON=$(curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN")
  CONTRACT_JSON=$(curl -sS -f "$BASE/api/contracts/$CONTRACT_ID" -H "Authorization: Bearer $CLIENT_TOKEN")
  PAYOUTS_JSON=$(curl -sS -f "$BASE/api/payouts" -H "Authorization: Bearer $FREELANCER_TOKEN")

  PROPOSAL_STATUS=$(jq -r '.status' <<<"$PROPOSAL_JSON")
  JOB_STATUS=$(jq -r '.status' <<<"$JOB_JSON")
  CONTRACT_STATUS=$(jq -r '.status' <<<"$CONTRACT_JSON")
  PAYOUT_ID=$(jq -r --argjson cid "$CONTRACT_ID" '.[] | select(.contractId == $cid) | .id' <<<"$PAYOUTS_JSON" | tail -n 1)
  PAYOUT_STATUS=""
  if [ -n "$PAYOUT_ID" ]; then
    PAYOUT_STATUS=$(jq -r --argjson id "$PAYOUT_ID" '.[] | select(.id == $id) | .status' <<<"$PAYOUTS_JSON")
  fi

  printf 'poll=%s proposal=%s contract=%s job=%s payout=%s/%s\n' "$i" "$PROPOSAL_STATUS" "$CONTRACT_STATUS" "$JOB_STATUS" "${PAYOUT_ID:-none}" "${PAYOUT_STATUS:-none}"

  if [ "$PROPOSAL_STATUS" = "PAYMENT_PENDING" ] && [ "$CONTRACT_STATUS" = "COMPLETED" ] && [ "$JOB_STATUS" = "CLOSED" ] && [ -n "$PAYOUT_ID" ] && [ "$PAYOUT_STATUS" = "PENDING" ]; then
    export PAYOUT_ID
    break
  fi
  sleep 1
done

test -n "${PAYOUT_ID:-}"
```

Expected final A2 state:

- Proposal: `PAYMENT_PENDING`.
- Contract: `COMPLETED`.
- Job: `CLOSED`.
- Payout: `PENDING`.

Verify payout details.

```bash
curl -sS -f "$BASE/api/payouts/$PAYOUT_ID" \
  -H "Authorization: Bearer $FREELANCER_TOKEN" | jq '{id, contractId, freelancerId, amount, method, status, transactionDetails}'
```

Expected:

- `contractId` equals `$CONTRACT_ID`.
- `freelancerId` equals `$FREELANCER_ID`.
- `amount` equals `250.0`.
- `method` defaults to `BANK_TRANSFER`.
- `status` equals `PENDING`.
- `transactionDetails.proposalId` equals `$PROPOSAL_ID`.
- `transactionDetails.jobId` equals `$JOB_ID`.

Proof points:

- `proposal-service` pre-checks passed before publishing `proposal.completed`.
- `contract-service` consumed `proposal.completed` and completed the contract.
- `job-service` consumed `proposal.completed` and closed the job.
- `wallet-service` consumed `proposal.completed` and created a pending payout without making JWT-less Feign calls.
- `proposal-service` consumed `payment.initiated` and moved the proposal to `PAYMENT_PENDING`.
- Redis did not keep stale job status after the async transition.

## Phase 6 - Scenario A3: Successful Payout Processing

Process the payout successfully as the client.

```bash
curl -sS -f -X POST "$BASE/api/payouts/contract/$CONTRACT_ID" \
  -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"method":"BANK_TRANSFER","accountLastFour":"4242"}' | jq '{id, contractId, status, transactionDetails}'
```

Expected immediate response:

- HTTP `201`.
- Payout status is `COMPLETED`.
- `transactionDetails.gatewayResponse` is `approved`.
- `transactionDetails.platformFee` is `25.0` for a `250.0` payout.

Poll until payment feedback reaches proposal-service.

```bash
for i in $(seq 1 45); do
  PROPOSAL_STATUS=$(curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
  PAYOUT_STATUS=$(curl -sS -f "$BASE/api/payouts/$PAYOUT_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
  CONTRACT_STATUS=$(curl -sS -f "$BASE/api/contracts/$CONTRACT_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')
  JOB_STATUS=$(curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')

  printf 'poll=%s proposal=%s payout=%s contract=%s job=%s\n' "$i" "$PROPOSAL_STATUS" "$PAYOUT_STATUS" "$CONTRACT_STATUS" "$JOB_STATUS"

  if [ "$PROPOSAL_STATUS" = "PAID" ] && [ "$PAYOUT_STATUS" = "COMPLETED" ] && [ "$CONTRACT_STATUS" = "COMPLETED" ] && [ "$JOB_STATUS" = "CLOSED" ]; then
    break
  fi
  sleep 1
done
```

Expected final A3 state:

- Proposal: `PAID`.
- Payout: `COMPLETED`.
- Contract remains `COMPLETED`.
- Job remains `CLOSED`.

Proof points:

- `wallet-service` published `payment.completed`.
- `proposal-service` consumed `payment.completed` and moved the proposal to `PAID`.
- No compensation occurred.

## Phase 7 - Scenario B: Payout Failure And Compensation

This scenario must use a new run. Do not reuse a paid proposal.

Reset IDs and create a fresh accepted proposal.

```bash
export RUN_ID="fail-$(date +%s)"
```

Repeat Phase 3 and Phase 4 to create a new `$JOB_ID`, `$PROPOSAL_ID`, `$CONTRACT_ID`, `$CLIENT_TOKEN`, and `$FREELANCER_TOKEN`.

Then run Phase 5 to complete work and create a `$PAYOUT_ID` in `PENDING` status.

Fail-fast precondition check before calling simulated failure:

```bash
PAYOUT_STATUS=$(curl -sS -f "$BASE/api/payouts/$PAYOUT_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
printf 'precheck payoutId=%s payoutStatus=%s\n' "$PAYOUT_ID" "$PAYOUT_STATUS"
test "$PAYOUT_STATUS" = "PENDING"
```

If this precheck is not `PENDING`, stop and start a fresh run. Do not reuse a contract whose payout is already `COMPLETED`, `FAILED`, or `REFUNDED`.

Process payout with simulated gateway failure.

```bash
curl -sS -f -X POST "$BASE/api/payouts/contract/$CONTRACT_ID?simulateFailure=true" \
  -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"method":"BANK_TRANSFER","accountLastFour":"4242"}' | jq '{id, contractId, status, transactionDetails}'
```

Expected immediate response:

- HTTP `201`.
- Payout status is `FAILED`.
- `transactionDetails.simulateFailure` is `true`.
- `transactionDetails.gatewayResponse` is `rejected`.
- `transactionDetails.failureReason` is `simulated gateway failure`.

Poll until compensation converges.

```bash
for i in $(seq 1 60); do
  PROPOSAL_STATUS=$(curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
  PAYOUT_STATUS=$(curl -sS -f "$BASE/api/payouts/$PAYOUT_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
  CONTRACT_STATUS=$(curl -sS -f "$BASE/api/contracts/$CONTRACT_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')
  JOB_STATUS=$(curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')

  printf 'poll=%s proposal=%s payout=%s contract=%s job=%s\n' "$i" "$PROPOSAL_STATUS" "$PAYOUT_STATUS" "$CONTRACT_STATUS" "$JOB_STATUS"

  if [ "$PROPOSAL_STATUS" = "PAYMENT_FAILED" ] && [ "$PAYOUT_STATUS" = "FAILED" ] && [ "$CONTRACT_STATUS" = "TERMINATED" ] && [ "$JOB_STATUS" = "IN_PROGRESS" ]; then
    break
  fi
  sleep 1
done
```

Expected final B state:

- Proposal: `PAYMENT_FAILED`.
- Payout: `FAILED`.
- Contract: `TERMINATED`.
- Job: `IN_PROGRESS`.

Proof points:

- `wallet-service` published `payment.failed`.
- `proposal-service` consumed `payment.failed`, marked `PAYMENT_FAILED`, and published `proposal.cancelled`.
- `contract-service` consumed `proposal.cancelled` and terminated a previously completed contract.
- `job-service` consumed `proposal.cancelled` and reopened job from `CLOSED` to `IN_PROGRESS`.
- Wallet did not emit a refund for a payout that never transferred money.

## Phase 8 - Scenario C: Completion Pre-Check Failure With No Side Effects

This scenario must use a new run. It deliberately deletes the active contract before the freelancer tries to complete work.

Reset IDs and create a fresh accepted proposal.

```bash
export RUN_ID="precheck-$(date +%s)"
```

Repeat Phase 3 and Phase 4 only. Stop once the proposal is `ACCEPTED`, job is `IN_PROGRESS`, and contract is `ACTIVE`.

Delete the active contract.

```bash
curl -i -sS -X DELETE "$BASE/api/contracts/$CONTRACT_ID" \
  -H "Authorization: Bearer $CLIENT_TOKEN"
```

Expected: HTTP `204`.

Attempt completion.

```bash
curl -i -sS -X PUT "$BASE/api/proposals/$PROPOSAL_ID/complete" \
  -H "Authorization: Bearer $FREELANCER_TOKEN"
```

Expected:

- HTTP `400`.
- This is a synchronous failure.
- No `proposal.completed` event should be published.

Wait briefly and verify no async side effects happened.

```bash
sleep 3

PROPOSAL_STATUS=$(curl -sS -f "$BASE/api/proposals/$PROPOSAL_ID" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq -r '.status')
JOB_STATUS=$(curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status')
PAYOUT_COUNT=$(curl -sS -f "$BASE/api/payouts" -H "Authorization: Bearer $FREELANCER_TOKEN" | jq --argjson cid "$CONTRACT_ID" '[.[] | select(.contractId == $cid)] | length')

printf 'proposal=%s job=%s payoutCount=%s\n' "$PROPOSAL_STATUS" "$JOB_STATUS" "$PAYOUT_COUNT"
```

Expected final C state:

- Proposal remains `ACCEPTED`.
- Job remains `IN_PROGRESS`.
- Payout count for the deleted contract is `0`.

Proof points:

- `proposal-service` correctly maps missing active contract to HTTP `400`.
- The saga does not start when pre-checks fail.
- No pending payout is created.
- Job is not incorrectly closed.
- Proposal is not stuck in `COMPLETING`.

## Phase 9 - RabbitMQ And Log Evidence After Manual Scenarios

Check queue health after all scenarios.

```bash
kubectl exec -n freelance rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

Expected:

- Primary saga queues have `0` ready and `0` unacknowledged messages after convergence.
- No new DLQ messages are added by the test runs.

Inspect logs for each service over the test window.

```bash
kubectl logs deployment/proposal-service -n freelance --since=30m | grep -E 'proposal.accepted|proposal.completed|payment.initiated|payment.completed|payment.failed|proposal.cancelled|PAYMENT_PENDING|PAYMENT_FAILED|PAID|COMPLETING'
kubectl logs deployment/contract-service -n freelance --since=30m | grep -E 'proposal.accepted|proposal.completed|proposal.cancelled|Contract|COMPLETED|TERMINATED'
kubectl logs deployment/job-service -n freelance --since=30m | grep -E 'proposal.accepted|proposal.completed|proposal.cancelled|IN_PROGRESS|CLOSED'
kubectl logs deployment/wallet-service -n freelance --since=30m | grep -E 'proposal.completed|payment.completed|payment.failed|PENDING|COMPLETED|FAILED'
```

Expected log evidence:

- Proposal service logs publishing `proposal.accepted` and `proposal.completed`.
- Contract service logs consuming `proposal.accepted`, `proposal.completed`, and for failure scenario `proposal.cancelled`.
- Job service logs consuming proposal events and changing status.
- Wallet service logs consuming `proposal.completed`, creating or handling payout, and publishing payment events.

If `grep` is unavailable or logs are too noisy, read the full logs without grep and search manually.

```bash
kubectl logs deployment/proposal-service -n freelance --since=30m
kubectl logs deployment/contract-service -n freelance --since=30m
kubectl logs deployment/job-service -n freelance --since=30m
kubectl logs deployment/wallet-service -n freelance --since=30m
```

## Phase 10 - Database Source-Of-Truth Verification

This Kubernetes setup has one PostgreSQL StatefulSet per service, not a single `postgres-0` pod.

Use these pod names:

- `proposal-postgres-0` for proposal rows.
- `job-postgres-0` for job rows.
- `contract-postgres-0` for contract rows.
- `wallet-postgres-0` for payout rows.

Quick check that all PostgreSQL pods exist:

```bash
kubectl get pods -n freelance | grep 'postgres-0'
```

List tables in each DB using the pod's own env vars (`POSTGRES_USER`, `POSTGRES_DB`):

```bash
kubectl exec -n freelance proposal-postgres-0 -- sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
kubectl exec -n freelance job-postgres-0 -- sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
kubectl exec -n freelance contract-postgres-0 -- sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
kubectl exec -n freelance wallet-postgres-0 -- sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

For any run, verify proposal/job/contract/payout rows by ID from the correct DB pod:

```bash
kubectl exec -n freelance proposal-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select id, status, job_id, freelancer_id, contract_id from proposals where id = ${PROPOSAL_ID};\""
kubectl exec -n freelance job-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select id, status, client_id, title from jobs where id = ${JOB_ID};\""
kubectl exec -n freelance contract-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select id, status, proposal_id, job_id, client_id, freelancer_id, agreed_amount from contracts where id = ${CONTRACT_ID};\""
kubectl exec -n freelance wallet-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select id, status, contract_id, freelancer_id, amount, transaction_details from payouts where contract_id = ${CONTRACT_ID};\""
```

Expected database state must match API state. If API and DB disagree, suspect stale cache or wrong read path.

## Phase 11 - Redis Stale Cache Regression Check

This is specifically to prove job status cache invalidation works after async saga transitions.

After Phase 5 completion, call the same job endpoint repeatedly.

```bash
for i in $(seq 1 10); do
  curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status'
  sleep 1
done
```

Expected after completion:

- Every response is `CLOSED`.
- There should be no repeated stale `IN_PROGRESS` after the async job-service consumer logs the close.

After Phase 7 failure compensation, call it repeatedly again.

```bash
for i in $(seq 1 10); do
  curl -sS -f "$BASE/api/jobs/$JOB_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | jq -r '.status'
  sleep 1
done
```

Expected after failure compensation:

- Every response is `IN_PROGRESS`.
- There should be no repeated stale `CLOSED` after compensation converges.

## Phase 12 - Run The Opt-In Live JUnit E2E Test

This test is committed as `wallet-service/src/test/java/com/team26/freelance/wallet/saga/ProposalPaymentSagaE2ETest.java`.

It is skipped by default so normal builds do not require a live cluster. Enable it explicitly.

When running Maven from Docker on macOS, use `host.docker.internal` so the container can reach the host gateway port.

```bash
docker run --rm \
  -e ACL216_LIVE_E2E=true \
  -e ACL216_GATEWAY_URL=http://host.docker.internal:30080 \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn test -pl contracts,wallet-service -am \
    -Dtest=ProposalPaymentSagaE2ETest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dnet.bytebuddy.experimental=true \
    --no-transfer-progress
```

Expected:

- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.
- Reactor build success.

The three live E2E tests cover:

- Happy path through pending payout and final paid proposal.
- Payout failure compensation across proposal, contract, job, and wallet.
- Pre-check failure with no payout side effect.

Also confirm the test is skipped by default.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn test -pl contracts,wallet-service -am \
    -Dtest=ProposalPaymentSagaE2ETest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dnet.bytebuddy.experimental=true \
    --no-transfer-progress
```

Expected:

- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 3`.
- Reactor build success.

## Phase 13 - Negative Authorization Checks

These are not the core saga, but they prove the flow is not accidentally public.

Try creating a job without a token.

```bash
curl -i -sS -X POST "$BASE/api/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"title":"No Auth","description":"should fail","category":"WEB_DEV","budgetMin":1.0,"budgetMax":2.0}'
```

Expected: HTTP `401`.

Try creating a proposal as the client for the freelancer.

```bash
curl -i -sS -X POST "$BASE/api/proposals" \
  -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"jobId\":${JOB_ID},\"freelancerId\":${FREELANCER_ID},\"coverLetter\":\"bad caller\",\"bidAmount\":250.0,\"estimatedDays\":5}"
```

Expected: HTTP `403`.

Try accepting as the freelancer.

```bash
curl -i -sS -X PUT "$BASE/api/proposals/$PROPOSAL_ID/accept" \
  -H "Authorization: Bearer $FREELANCER_TOKEN"
```

Expected: HTTP `403` or HTTP `400` if the proposal is already no longer in an accept-eligible state. For a fresh submitted proposal, it should be `403`.

## Phase 14 - Failure Diagnosis Checklist

If a scenario fails, do not guess. Capture these facts.

Queue depths:

```bash
kubectl exec -n freelance rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

Recent logs:

```bash
kubectl logs deployment/proposal-service -n freelance --since=10m
kubectl logs deployment/contract-service -n freelance --since=10m
kubectl logs deployment/job-service -n freelance --since=10m
kubectl logs deployment/wallet-service -n freelance --since=10m
```

Pod health:

```bash
kubectl get pods -n freelance
kubectl describe pod <bad-pod-name> -n freelance
```

Source-of-truth rows:

```bash
kubectl exec -n freelance proposal-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select * from proposals where id = ${PROPOSAL_ID};\""
kubectl exec -n freelance job-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select * from jobs where id = ${JOB_ID};\""
kubectl exec -n freelance contract-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select * from contracts where id = ${CONTRACT_ID};\""
kubectl exec -n freelance wallet-postgres-0 -- sh -lc "psql -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" -c \"select * from payouts where contract_id = ${CONTRACT_ID};\""
```

Common failure signatures and likely causes:

- Proposal stuck `COMPLETING`: wallet did not create payout or proposal did not consume `payment.initiated`.
- Contract remains `ACTIVE` after completion: contract-service did not consume `proposal.completed`.
- Contract remains `COMPLETED` after payout failure: contract compensation did not consume `proposal.cancelled` or completed-contract fallback failed.
- Job remains `IN_PROGRESS` after completion: job-service did not consume `proposal.completed` or Redis returned stale status.
- Job remains `CLOSED` after payout failure: job-service did not consume compensation `proposal.cancelled` or Redis returned stale status.
- Payout not created: wallet-service failed consuming `proposal.completed`; check `payment.saga-listener.dlq`.
- HTTP `503` on completion pre-check: proposal-service is treating active-contract 404 as service outage; expected behavior is HTTP `400`.
- DLQ messages: inspect the matching service log at the same timestamp and fix the root exception before retesting.

## Phase 15 - Final Retest After Merging Main

After you merge `main` into your branch and resolve conflicts, repeat the proof from a clean cluster.

Recommended final sequence:

```bash
git status --short
docker compose build
kubectl delete namespace freelance monitoring --ignore-not-found=true
kubectl get namespaces
kubectl apply -f k8s/namespaces/namespace.yaml -f k8s/namespaces/monitoring-namespace.yaml
kubectl apply \
  -f k8s/secrets \
  -f k8s/configmaps \
  -f k8s/services \
  -f k8s/statefulsets \
  -f k8s/deployments \
  -f k8s/api-gateway \
  -f k8s/monitoring/loki/ \
  -f k8s/monitoring/grafana \
  -f k8s/monitoring/prometheus \
  -f k8s/pvcs
kubectl rollout status deployment/user-service -n freelance
kubectl rollout status deployment/job-service -n freelance
kubectl rollout status deployment/proposal-service -n freelance
kubectl rollout status deployment/contract-service -n freelance
kubectl rollout status deployment/wallet-service -n freelance
kubectl rollout status deployment/gateway -n freelance
```

Then rerun:

- Phase 2 baseline health and RabbitMQ checks.
- Phase 3 user/job/proposal setup.
- Phase 4 accept/bootstrap contract.
- Phase 5 completion to pending payout.
- Phase 6 payout success.
- Phase 7 payout failure compensation on a new run.
- Phase 8 pre-check failure on a new run.
- Phase 9 RabbitMQ/log evidence.
- Phase 10 database verification.
- Phase 12 enabled live JUnit E2E test.

Only merge when the fresh post-main cluster passes all of those checks.

## Evidence To Save Before Merge

Save this evidence in your notes or PR comment:

- Output of `git log --oneline -6` showing the saga commits.
- Output of `kubectl get pods -n freelance` after fresh deploy.
- Output of RabbitMQ queue list before and after tests.
- One successful `LIVE` manual run summary for completion, payout success, payout failure, and pre-check failure.
- Output of enabled live JUnit E2E test showing `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.
- If possible, paste key final states for each scenario:
  - Happy path: `proposal=PAID payout=COMPLETED contract=COMPLETED job=CLOSED`.
  - Failure compensation: `proposal=PAYMENT_FAILED payout=FAILED contract=TERMINATED job=IN_PROGRESS`.
  - Pre-check failure: `completeHttp=400 proposal=ACCEPTED job=IN_PROGRESS payoutCount=0`.

## Known Non-ACL216 Test Caveat

The full `wallet-service` test suite may fail on `PromoCodeRepositoryTest` when run against H2 because `schema.sql` contains PostgreSQL-specific `DO $$` syntax. This is unrelated to ACL216 saga behavior.

For ACL216-specific validation, use:

```bash
docker run --rm \
  -e ACL216_LIVE_E2E=true \
  -e ACL216_GATEWAY_URL=http://host.docker.internal:30080 \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn test -pl contracts,wallet-service -am \
    -Dtest=ProposalPaymentSagaE2ETest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dnet.bytebuddy.experimental=true \
    --no-transfer-progress
```
