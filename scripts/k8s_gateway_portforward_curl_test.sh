#!/usr/bin/env bash
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd jq
need_cmd kubectl
need_cmd rg

health_code="$(curl -sS -o /tmp/gateway_health_body.$$ -w '%{http_code}' "$GATEWAY/actuator/health" || true)"
if [[ "$health_code" != "200" ]]; then
  echo "Gateway health check failed at $GATEWAY/actuator/health (HTTP $health_code)." >&2
  echo "Ensure port-forward is running: kubectl port-forward svc/api-gateway 8080:8080 -n freelance" >&2
  [[ -f /tmp/gateway_health_body.$$ ]] && cat /tmp/gateway_health_body.$$ >&2 || true
  rm -f /tmp/gateway_health_body.$$ || true
  exit 1
fi
rm -f /tmp/gateway_health_body.$$ || true

RUN_ID="$(date +%s)"
OUT_DIR="/tmp/k8s-pf-curl-run-${RUN_ID}"
mkdir -p "$OUT_DIR"

CLIENT_EMAIL="client_${RUN_ID}@test.com"
FREELANCER_EMAIL="freelancer_${RUN_ID}@test.com"
PASSWORD="Pass123!"

request_json() {
  local method="$1" url="$2" payload="$3" out_file="$4"
  local tmp_body tmp_code
  tmp_body="$(mktemp)"
  tmp_code="$(mktemp)"

  if [[ -n "$payload" ]]; then
    curl -sS -X "$method" -H "Content-Type: application/json" -d "$payload" -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code"
  else
    curl -sS -X "$method" -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code"
  fi

  cat "$tmp_body" > "$out_file"
  cat "$tmp_code"
  rm -f "$tmp_body" "$tmp_code"
}

request_auth_json() {
  local method="$1" token="$2" url="$3" payload="$4" out_file="$5"
  local tmp_body tmp_code
  tmp_body="$(mktemp)"
  tmp_code="$(mktemp)"

  if [[ -n "$payload" ]]; then
    curl -sS -X "$method" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d "$payload" -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code"
  else
    curl -sS -X "$method" -H "Authorization: Bearer ${token}" -o "$tmp_body" -w '%{http_code}' "$url" > "$tmp_code"
  fi

  cat "$tmp_body" > "$out_file"
  cat "$tmp_code"
  rm -f "$tmp_body" "$tmp_code"
}

assert_success() {
  local step="$1" code="$2" body_file="$3"
  if [[ "$code" != "200" && "$code" != "201" ]]; then
    echo "Step failed: ${step} (HTTP ${code})" >&2
    echo "Response body:" >&2
    cat "$body_file" >&2
    exit 1
  fi
}

echo "Using gateway: $GATEWAY"
echo "Artifacts dir: $OUT_DIR"

echo "== Register users =="
CLIENT_REGISTER_PAYLOAD="$(printf '{"name":"Client %s","email":"%s","password":"%s","phone":"0101%s","role":"CLIENT"}' "$RUN_ID" "$CLIENT_EMAIL" "$PASSWORD" "${RUN_ID: -6}")"
code="$(request_json POST "$GATEWAY/api/auth/register" "$CLIENT_REGISTER_PAYLOAD" "$OUT_DIR/client_register.json")"
assert_success "client register" "$code" "$OUT_DIR/client_register.json"

FREELANCER_REGISTER_PAYLOAD="$(printf '{"name":"Freelancer %s","email":"%s","password":"%s","phone":"0111%s","role":"FREELANCER"}' "$RUN_ID" "$FREELANCER_EMAIL" "$PASSWORD" "${RUN_ID: -6}")"
code="$(request_json POST "$GATEWAY/api/auth/register" "$FREELANCER_REGISTER_PAYLOAD" "$OUT_DIR/freelancer_register.json")"
assert_success "freelancer register" "$code" "$OUT_DIR/freelancer_register.json"

echo "== Login =="
CLIENT_LOGIN_PAYLOAD="$(printf '{"email":"%s","password":"%s"}' "$CLIENT_EMAIL" "$PASSWORD")"
code="$(request_json POST "$GATEWAY/api/auth/login" "$CLIENT_LOGIN_PAYLOAD" "$OUT_DIR/client_login.json")"
assert_success "client login" "$code" "$OUT_DIR/client_login.json"
CLIENT_TOKEN="$(jq -r '.token // empty' "$OUT_DIR/client_login.json")"
CLIENT_ID="$(jq -r '.userId // empty' "$OUT_DIR/client_login.json")"

FREELANCER_LOGIN_PAYLOAD="$(printf '{"email":"%s","password":"%s"}' "$FREELANCER_EMAIL" "$PASSWORD")"
code="$(request_json POST "$GATEWAY/api/auth/login" "$FREELANCER_LOGIN_PAYLOAD" "$OUT_DIR/freelancer_login.json")"
assert_success "freelancer login" "$code" "$OUT_DIR/freelancer_login.json"
FREELANCER_TOKEN="$(jq -r '.token // empty' "$OUT_DIR/freelancer_login.json")"
FREELANCER_ID="$(jq -r '.userId // empty' "$OUT_DIR/freelancer_login.json")"

if [[ -z "$CLIENT_TOKEN" || -z "$FREELANCER_TOKEN" || -z "$CLIENT_ID" || -z "$FREELANCER_ID" ]]; then
  echo "Token or user ID extraction failed." >&2
  exit 1
fi

echo "== Seed entities =="
JOB_PAYLOAD="$(printf '{"title":"K8s test job %s","description":"Used for Feign tests","category":"WEB_DEV","budgetMin":1000,"budgetMax":2000}' "$RUN_ID")"
code="$(request_auth_json POST "$CLIENT_TOKEN" "$GATEWAY/api/jobs" "$JOB_PAYLOAD" "$OUT_DIR/job_create.json")"
assert_success "job create" "$code" "$OUT_DIR/job_create.json"
JOB_ID="$(jq -r '.id // empty' "$OUT_DIR/job_create.json")"

PROPOSAL_PAYLOAD="$(printf '{"jobId":%s,"freelancerId":%s,"coverLetter":"I can do this","bidAmount":1500,"estimatedDays":7,"metadata":{"source":"k8-pf-curl-test"}}' "$JOB_ID" "$FREELANCER_ID")"
code="$(request_auth_json POST "$FREELANCER_TOKEN" "$GATEWAY/api/proposals" "$PROPOSAL_PAYLOAD" "$OUT_DIR/proposal_create.json")"
assert_success "proposal create" "$code" "$OUT_DIR/proposal_create.json"
PROPOSAL_ID="$(jq -r '.id // empty' "$OUT_DIR/proposal_create.json")"

PROPOSAL_UPDATE_PAYLOAD='{"coverLetter":"I can do this","bidAmount":1500,"estimatedDays":7,"metadata":{"source":"k8-pf-curl-test"},"status":"ACCEPTED"}'
code="$(request_auth_json PUT "$FREELANCER_TOKEN" "$GATEWAY/api/proposals/${PROPOSAL_ID}" "$PROPOSAL_UPDATE_PAYLOAD" "$OUT_DIR/proposal_update.json")"
assert_success "proposal update accepted" "$code" "$OUT_DIR/proposal_update.json"

CONTRACT_PAYLOAD="$(printf '{"jobId":%s,"freelancerId":%s,"clientId":%s,"proposalId":%s,"agreedAmount":1500,"status":"ACTIVE","startDate":"2026-05-19T10:00:00","metadata":{"source":"k8-pf-curl-test"}}' "$JOB_ID" "$FREELANCER_ID" "$CLIENT_ID" "$PROPOSAL_ID")"
code="$(request_auth_json POST "$CLIENT_TOKEN" "$GATEWAY/api/contracts" "$CONTRACT_PAYLOAD" "$OUT_DIR/contract_create.json")"
assert_success "contract create" "$code" "$OUT_DIR/contract_create.json"
CONTRACT_ID="$(jq -r '.id // empty' "$OUT_DIR/contract_create.json")"

PAYOUT_PAYLOAD="$(printf '{"contractId":%s,"freelancerId":%s,"amount":1500,"method":"BANK_TRANSFER","status":"COMPLETED","transactionDetails":{"platformFee":150}}' "$CONTRACT_ID" "$FREELANCER_ID")"
code="$(request_auth_json POST "$CLIENT_TOKEN" "$GATEWAY/api/payouts" "$PAYOUT_PAYLOAD" "$OUT_DIR/payout_create.json")"
assert_success "payout create" "$code" "$OUT_DIR/payout_create.json"
PAYOUT_ID="$(jq -r '.id // empty' "$OUT_DIR/payout_create.json")"

echo "== Non-Feign checks =="
code="$(request_auth_json GET "$FREELANCER_TOKEN" "$GATEWAY/api/users/${FREELANCER_ID}/profile" "" "$OUT_DIR/nonfeign_user_profile.json")"
assert_success "user non-feign profile" "$code" "$OUT_DIR/nonfeign_user_profile.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/jobs/${JOB_ID}" "" "$OUT_DIR/nonfeign_job_get.json")"
assert_success "job non-feign get" "$code" "$OUT_DIR/nonfeign_job_get.json"

ESTIMATE_PAYLOAD='{"bidAmount":1200,"estimatedDays":10}'
code="$(request_auth_json POST "$FREELANCER_TOKEN" "$GATEWAY/api/proposals/estimate" "$ESTIMATE_PAYLOAD" "$OUT_DIR/nonfeign_proposal_estimate.json")"
assert_success "proposal non-feign estimate" "$code" "$OUT_DIR/nonfeign_proposal_estimate.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/contracts/${CONTRACT_ID}" "" "$OUT_DIR/nonfeign_contract_get.json")"
assert_success "contract non-feign get" "$code" "$OUT_DIR/nonfeign_contract_get.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/payouts/${PAYOUT_ID}" "" "$OUT_DIR/nonfeign_wallet_get.json")"
assert_success "wallet non-feign get" "$code" "$OUT_DIR/nonfeign_wallet_get.json"

echo "== Feign checks =="
code="$(request_auth_json GET "$FREELANCER_TOKEN" "$GATEWAY/api/users/${FREELANCER_ID}/contract-summary" "" "$OUT_DIR/feign_user_contract_summary.json")"
assert_success "user->contract feign" "$code" "$OUT_DIR/feign_user_contract_summary.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/users/reports/top-freelancers?startDate=2026-01-01&endDate=2026-12-31&limit=5" "" "$OUT_DIR/feign_user_wallet_report.json")"
assert_success "user->wallet feign" "$code" "$OUT_DIR/feign_user_wallet_report.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/jobs/${JOB_ID}/proposal-summary?startDate=2026-01-01&endDate=2026-12-31" "" "$OUT_DIR/feign_job_proposal_summary.json")"
assert_success "job->proposal feign" "$code" "$OUT_DIR/feign_job_proposal_summary.json"

code="$(request_auth_json PUT "$FREELANCER_TOKEN" "$GATEWAY/api/proposals/${PROPOSAL_ID}/complete" '{}' "$OUT_DIR/feign_proposal_complete.json")"
assert_success "proposal->job/user/contract feign" "$code" "$OUT_DIR/feign_proposal_complete.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/contracts/search?minAmount=1&maxAmount=10000&status=ACTIVE" "" "$OUT_DIR/feign_contract_search.json")"
assert_success "contract->user/job feign" "$code" "$OUT_DIR/feign_contract_search.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/payouts/freelancer/${FREELANCER_ID}/summary" "" "$OUT_DIR/feign_wallet_freelancer_summary.json")"
assert_success "wallet->user feign" "$code" "$OUT_DIR/feign_wallet_freelancer_summary.json"

code="$(request_auth_json GET "$CLIENT_TOKEN" "$GATEWAY/api/payouts/analytics/category?startDate=2026-01-01&endDate=2026-12-31" "" "$OUT_DIR/feign_wallet_category_analytics.json")"
assert_success "wallet->contract/job feign" "$code" "$OUT_DIR/feign_wallet_category_analytics.json"

code="$(request_auth_json PUT "$CLIENT_TOKEN" "$GATEWAY/api/jobs/${JOB_ID}/close" '{"status":"CLOSED"}' "$OUT_DIR/feign_job_close.json")"
assert_success "job->contract feign close" "$code" "$OUT_DIR/feign_job_close.json"

echo "== PASS =="
echo "RUN_ID=$RUN_ID"
echo "CLIENT_EMAIL=$CLIENT_EMAIL"
echo "FREELANCER_EMAIL=$FREELANCER_EMAIL"
echo "CLIENT_ID=$CLIENT_ID"
echo "FREELANCER_ID=$FREELANCER_ID"
echo "JOB_ID=$JOB_ID"
echo "PROPOSAL_ID=$PROPOSAL_ID"
echo "CONTRACT_ID=$CONTRACT_ID"
echo "PAYOUT_ID=$PAYOUT_ID"
echo "ARTIFACTS=$OUT_DIR"
