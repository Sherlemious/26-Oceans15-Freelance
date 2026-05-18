#!/usr/bin/env python3
import argparse
import json
import os
import secrets
import string
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, Optional, Tuple


DEFAULT_BASE = "http://localhost:30080"
DEFAULT_PASSWORD = None


class SagaTestError(Exception):
    pass


@dataclass
class RunContext:
    base: str
    password: str
    run_id: str

    client_email: str = ""
    freelancer_email: str = ""

    client_token: str = ""
    freelancer_token: str = ""

    client_id: Optional[int] = None
    freelancer_id: Optional[int] = None
    job_id: Optional[int] = None
    proposal_id: Optional[int] = None
    contract_id: Optional[int] = None
    payout_id: Optional[int] = None


def log(msg: str) -> None:
    print(msg, flush=True)


def pretty(obj: Any) -> str:
    return json.dumps(obj, indent=2, sort_keys=True)


def random_password() -> str:
    alphabet = string.ascii_letters + string.digits
    tail = "".join(secrets.choice(alphabet) for _ in range(16))
    return f"Password123!{tail}"


def unique_token(prefix: Optional[str] = None) -> str:
    ts_ms = int(time.time() * 1000)
    pid = os.getpid()
    rand = secrets.token_hex(6)
    base = f"{ts_ms}-{pid}-{rand}"
    return f"{prefix}-{base}" if prefix else base


def make_run_id(prefix: Optional[str] = None) -> str:
    return unique_token(prefix)


def suffix_phone(run_id: str, prefix: str) -> str:
    digits = "".join(ch for ch in run_id if ch.isdigit())
    rand_digits = str(secrets.randbelow(10**9)).rjust(9, "0")
    combined = (digits + rand_digits)[-9:]
    return prefix + combined


def request_json(
    method: str,
    url: str,
    token: Optional[str] = None,
    body: Optional[Dict[str, Any]] = None,
    fail: bool = True,
) -> Tuple[int, Any]:
    headers = {"Accept": "application/json"}

    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8")
            if not raw:
                return resp.status, None
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw

    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        if fail:
            raise SagaTestError(
                f"HTTP {e.code} failed for {method} {url}\nResponse:\n{raw}"
            ) from e
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw

    except urllib.error.URLError as e:
        raise SagaTestError(f"Connection failed for {method} {url}: {e}") from e


def request_empty(
    method: str,
    url: str,
    token: Optional[str] = None,
    fail: bool = True,
) -> Tuple[int, str]:
    headers = {"Accept": "application/json"}

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")

    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        if fail:
            raise SagaTestError(
                f"HTTP {e.code} failed for {method} {url}\nResponse:\n{raw}"
            ) from e
        return e.code, raw

    except urllib.error.URLError as e:
        raise SagaTestError(f"Connection failed for {method} {url}: {e}") from e


def assert_eq(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise SagaTestError(f"{label}: expected {expected!r}, got {actual!r}")


def assert_present(value: Any, label: str) -> None:
    if value is None or value == "":
        raise SagaTestError(f"{label}: expected a non-empty value")


def register_with_retry(
    ctx: RunContext,
    role: str,
    name_prefix: str,
    email_attr: str,
    phone_prefix: str,
) -> None:
    for attempt in range(1, 4):
        email = f"{role.lower()}-{ctx.run_id}-{secrets.token_hex(4)}@example.com"
        phone = suffix_phone(f"{ctx.run_id}-{secrets.token_hex(4)}", phone_prefix)

        setattr(ctx, email_attr, email)

        status, body = request_json(
            "POST",
            f"{ctx.base}/api/auth/register",
            body={
                "name": f"{name_prefix} {ctx.run_id}",
                "email": email,
                "password": ctx.password,
                "phone": phone,
                "role": role,
            },
            fail=False,
        )

        if status in (200, 201):
            return

        if status == 409 and attempt < 3:
            log(f"{role} registration collision; retrying with fresh identity...")
            continue

        raise SagaTestError(
            f"{role} registration failed after {attempt} attempt(s): "
            f"HTTP {status}\nResponse:\n{body}"
        )


def setup_users(ctx: RunContext) -> None:
    log("\n== Phase 3: Register users ==")

    register_with_retry(
        ctx=ctx,
        role="CLIENT",
        name_prefix="Client",
        email_attr="client_email",
        phone_prefix="+201",
    )

    register_with_retry(
        ctx=ctx,
        role="FREELANCER",
        name_prefix="Freelancer",
        email_attr="freelancer_email",
        phone_prefix="+202",
    )

    log("Registered client and freelancer.")
    log(f"CLIENT_EMAIL={ctx.client_email}")
    log(f"FREELANCER_EMAIL={ctx.freelancer_email}")

    log("\n== Phase 3: Login users ==")

    _, client_login = request_json(
        "POST",
        f"{ctx.base}/api/auth/login",
        body={"email": ctx.client_email, "password": ctx.password},
    )

    _, freelancer_login = request_json(
        "POST",
        f"{ctx.base}/api/auth/login",
        body={"email": ctx.freelancer_email, "password": ctx.password},
    )

    ctx.client_token = client_login.get("token")
    ctx.freelancer_token = freelancer_login.get("token")
    ctx.client_id = client_login.get("userId")
    ctx.freelancer_id = freelancer_login.get("userId")

    assert_present(ctx.client_token, "CLIENT_TOKEN")
    assert_present(ctx.freelancer_token, "FREELANCER_TOKEN")
    assert_present(ctx.client_id, "CLIENT_ID")
    assert_present(ctx.freelancer_id, "FREELANCER_ID")
    assert_eq(client_login.get("role"), "CLIENT", "client role")
    assert_eq(freelancer_login.get("role"), "FREELANCER", "freelancer role")

    log(f"CLIENT_ID={ctx.client_id} FREELANCER_ID={ctx.freelancer_id}")


def create_job_and_proposal(ctx: RunContext) -> None:
    log("\n== Phase 3: Create job ==")

    _, job = request_json(
        "POST",
        f"{ctx.base}/api/jobs",
        token=ctx.client_token,
        body={
            "title": f"ACL216 Saga Job {ctx.run_id}",
            "description": "Manual saga verification",
            "category": "WEB_DEV",
            "budgetMin": 100.0,
            "budgetMax": 500.0,
        },
    )

    ctx.job_id = job.get("id")
    assert_present(ctx.job_id, "JOB_ID")
    log(f"JOB_ID={ctx.job_id}")

    _, fetched_job = request_json(
        "GET",
        f"{ctx.base}/api/jobs/{ctx.job_id}",
        token=ctx.client_token,
    )

    assert_eq(fetched_job.get("id"), ctx.job_id, "job.id")
    assert_eq(fetched_job.get("clientId"), ctx.client_id, "job.clientId")
    assert_eq(fetched_job.get("status"), "OPEN", "job.status")
    assert_eq(fetched_job.get("title"), f"ACL216 Saga Job {ctx.run_id}", "job.title")

    log("Verified job:")
    log(pretty({k: fetched_job.get(k) for k in ["id", "clientId", "status", "title"]}))

    log("\n== Phase 3: Create proposal ==")

    _, proposal = request_json(
        "POST",
        f"{ctx.base}/api/proposals",
        token=ctx.freelancer_token,
        body={
            "jobId": ctx.job_id,
            "freelancerId": ctx.freelancer_id,
            "coverLetter": "I can do this ACL216 job",
            "bidAmount": 250.0,
            "estimatedDays": 5,
            "metadata": {"runId": ctx.run_id},
        },
    )

    ctx.proposal_id = proposal.get("id")
    assert_present(ctx.proposal_id, "PROPOSAL_ID")
    log(f"PROPOSAL_ID={ctx.proposal_id}")

    _, fetched_proposal = request_json(
        "GET",
        f"{ctx.base}/api/proposals/{ctx.proposal_id}",
        token=ctx.freelancer_token,
    )

    assert_eq(fetched_proposal.get("id"), ctx.proposal_id, "proposal.id")
    assert_eq(fetched_proposal.get("jobId"), ctx.job_id, "proposal.jobId")
    assert_eq(
        fetched_proposal.get("freelancerId"), ctx.freelancer_id, "proposal.freelancerId"
    )
    assert_eq(fetched_proposal.get("status"), "SUBMITTED", "proposal.status")
    assert_eq(float(fetched_proposal.get("bidAmount")), 250.0, "proposal.bidAmount")

    log("Verified proposal:")
    log(
        pretty(
            {
                k: fetched_proposal.get(k)
                for k in ["id", "jobId", "freelancerId", "status", "bidAmount"]
            }
        )
    )


def accept_proposal_and_bootstrap_contract(ctx: RunContext) -> None:
    log("\n== Phase 4 / Scenario A1: Accept proposal ==")

    _, accepted = request_json(
        "PUT",
        f"{ctx.base}/api/proposals/{ctx.proposal_id}/accept",
        token=ctx.client_token,
    )

    assert_eq(accepted.get("status"), "ACCEPTED", "accepted proposal.status")

    log("Accept response:")
    log(
        pretty(
            {
                k: accepted.get(k)
                for k in ["id", "status", "jobId", "freelancerId", "contractId"]
            }
        )
    )

    log("\nPolling for A1 convergence...")

    for i in range(1, 31):
        _, proposal = request_json(
            "GET",
            f"{ctx.base}/api/proposals/{ctx.proposal_id}",
            token=ctx.freelancer_token,
        )
        _, job = request_json(
            "GET",
            f"{ctx.base}/api/jobs/{ctx.job_id}",
            token=ctx.client_token,
        )

        _, contract = request_json(
            "GET",
            f"{ctx.base}/api/contracts/proposal/{ctx.proposal_id}/active",
            token=ctx.client_token,
            fail=False,
        )

        proposal_status = proposal.get("status")
        job_status = job.get("status")
        contract_id = contract.get("id") if isinstance(contract, dict) else None

        log(
            f"poll={i} proposal={proposal_status} "
            f"job={job_status} contract={contract_id or 'none'}"
        )

        if (
            proposal_status == "ACCEPTED"
            and job_status == "IN_PROGRESS"
            and contract_id
        ):
            ctx.contract_id = contract_id
            break

        time.sleep(1)

    assert_present(ctx.contract_id, "CONTRACT_ID after A1 convergence")

    _, contract = request_json(
        "GET",
        f"{ctx.base}/api/contracts/{ctx.contract_id}",
        token=ctx.client_token,
    )

    assert_eq(contract.get("proposalId"), ctx.proposal_id, "contract.proposalId")
    assert_eq(contract.get("jobId"), ctx.job_id, "contract.jobId")
    assert_eq(contract.get("clientId"), ctx.client_id, "contract.clientId")
    assert_eq(contract.get("freelancerId"), ctx.freelancer_id, "contract.freelancerId")
    assert_eq(float(contract.get("agreedAmount")), 250.0, "contract.agreedAmount")
    assert_eq(contract.get("status"), "ACTIVE", "contract.status")

    log("Verified contract:")
    log(
        pretty(
            {
                k: contract.get(k)
                for k in [
                    "id",
                    "proposalId",
                    "jobId",
                    "clientId",
                    "freelancerId",
                    "agreedAmount",
                    "status",
                ]
            }
        )
    )


def find_latest_payout_for_contract(
    payouts: Any,
    contract_id: Optional[int],
) -> Optional[Dict[str, Any]]:
    if not isinstance(payouts, list):
        return None

    matches = [
        payout
        for payout in payouts
        if isinstance(payout, dict) and payout.get("contractId") == contract_id
    ]

    if not matches:
        return None

    return sorted(matches, key=lambda p: p.get("id") or 0)[-1]


def count_payouts_for_contract(payouts: Any, contract_id: Optional[int]) -> int:
    if not isinstance(payouts, list):
        return 0

    return len(
        [
            payout
            for payout in payouts
            if isinstance(payout, dict) and payout.get("contractId") == contract_id
        ]
    )


def complete_work_and_create_pending_payout(ctx: RunContext) -> None:
    log("\n== Phase 5 / Scenario A2: Complete work ==")

    _, completed = request_json(
        "PUT",
        f"{ctx.base}/api/proposals/{ctx.proposal_id}/complete",
        token=ctx.freelancer_token,
    )

    log("Complete response:")
    log(
        pretty({k: completed.get(k) for k in ["id", "status", "jobId", "freelancerId"]})
    )

    log("\nPolling for A2 convergence...")

    for i in range(1, 61):
        _, proposal = request_json(
            "GET",
            f"{ctx.base}/api/proposals/{ctx.proposal_id}",
            token=ctx.freelancer_token,
        )
        _, job = request_json(
            "GET",
            f"{ctx.base}/api/jobs/{ctx.job_id}",
            token=ctx.client_token,
        )
        _, contract = request_json(
            "GET",
            f"{ctx.base}/api/contracts/{ctx.contract_id}",
            token=ctx.client_token,
        )
        _, payouts = request_json(
            "GET",
            f"{ctx.base}/api/payouts",
            token=ctx.freelancer_token,
        )

        proposal_status = proposal.get("status")
        job_status = job.get("status")
        contract_status = contract.get("status")

        payout = find_latest_payout_for_contract(payouts, ctx.contract_id)
        payout_id = payout.get("id") if payout else None
        payout_status = payout.get("status") if payout else None

        log(
            f"poll={i} proposal={proposal_status} contract={contract_status} "
            f"job={job_status} payout={payout_id or 'none'}/{payout_status or 'none'}"
        )

        if (
            proposal_status == "PAYMENT_PENDING"
            and contract_status == "COMPLETED"
            and job_status == "CLOSED"
            and payout_id
            and payout_status == "PENDING"
        ):
            ctx.payout_id = payout_id
            break

        time.sleep(1)

    assert_present(ctx.payout_id, "PAYOUT_ID after A2 convergence")

    _, payout = request_json(
        "GET",
        f"{ctx.base}/api/payouts/{ctx.payout_id}",
        token=ctx.freelancer_token,
    )

    assert_eq(payout.get("contractId"), ctx.contract_id, "payout.contractId")
    assert_eq(payout.get("freelancerId"), ctx.freelancer_id, "payout.freelancerId")
    assert_eq(float(payout.get("amount")), 250.0, "payout.amount")
    assert_eq(payout.get("method"), "BANK_TRANSFER", "payout.method")
    assert_eq(payout.get("status"), "PENDING", "payout.status")

    tx = payout.get("transactionDetails") or {}
    assert_eq(
        tx.get("proposalId"), ctx.proposal_id, "payout.transactionDetails.proposalId"
    )
    assert_eq(tx.get("jobId"), ctx.job_id, "payout.transactionDetails.jobId")

    log("Verified payout:")
    log(
        pretty(
            {
                k: payout.get(k)
                for k in [
                    "id",
                    "contractId",
                    "freelancerId",
                    "amount",
                    "method",
                    "status",
                    "transactionDetails",
                ]
            }
        )
    )


def process_payout_successfully(ctx: RunContext) -> None:
    log("\n== Phase 6 / Scenario A3: Process payout successfully ==")

    _, payout = request_json(
        "POST",
        f"{ctx.base}/api/payouts/contract/{ctx.contract_id}",
        token=ctx.client_token,
        body={
            "method": "BANK_TRANSFER",
            "accountLastFour": "4242",
        },
    )

    returned_payout_id = payout.get("id")
    if returned_payout_id:
        ctx.payout_id = returned_payout_id

    assert_eq(payout.get("contractId"), ctx.contract_id, "success payout.contractId")
    assert_eq(payout.get("status"), "COMPLETED", "success payout.status")

    tx = payout.get("transactionDetails") or {}
    assert_eq(
        tx.get("gatewayResponse"),
        "approved",
        "success transactionDetails.gatewayResponse",
    )
    assert_eq(
        float(tx.get("platformFee")), 25.0, "success transactionDetails.platformFee"
    )

    log("Process payout response:")
    log(
        pretty(
            {
                k: payout.get(k)
                for k in ["id", "contractId", "status", "transactionDetails"]
            }
        )
    )

    log("\nPolling for A3 convergence...")

    converged = False

    for i in range(1, 46):
        _, proposal = request_json(
            "GET",
            f"{ctx.base}/api/proposals/{ctx.proposal_id}",
            token=ctx.freelancer_token,
        )
        _, payout_fetched = request_json(
            "GET",
            f"{ctx.base}/api/payouts/{ctx.payout_id}",
            token=ctx.freelancer_token,
        )
        _, contract = request_json(
            "GET",
            f"{ctx.base}/api/contracts/{ctx.contract_id}",
            token=ctx.client_token,
        )
        _, job = request_json(
            "GET",
            f"{ctx.base}/api/jobs/{ctx.job_id}",
            token=ctx.client_token,
        )

        proposal_status = proposal.get("status")
        payout_status = payout_fetched.get("status")
        contract_status = contract.get("status")
        job_status = job.get("status")

        log(
            f"poll={i} proposal={proposal_status} payout={payout_status} "
            f"contract={contract_status} job={job_status}"
        )

        if (
            proposal_status == "PAID"
            and payout_status == "COMPLETED"
            and contract_status == "COMPLETED"
            and job_status == "CLOSED"
        ):
            converged = True
            break

        time.sleep(1)

    if not converged:
        raise SagaTestError(
            "A3 did not converge to PAID / COMPLETED / COMPLETED / CLOSED"
        )

    log("\nScenario A success path passed.")


def process_payout_failure_and_compensation(ctx: RunContext) -> None:
    log("\n== Phase 7 / Scenario B: Payout failure and compensation ==")

    _, payout_before = request_json(
        "GET",
        f"{ctx.base}/api/payouts/{ctx.payout_id}",
        token=ctx.freelancer_token,
    )

    payout_status = payout_before.get("status")
    log(f"precheck payoutId={ctx.payout_id} payoutStatus={payout_status}")

    assert_eq(payout_status, "PENDING", "failure precheck payout.status")

    _, failed_payout = request_json(
        "POST",
        f"{ctx.base}/api/payouts/contract/{ctx.contract_id}?simulateFailure=true",
        token=ctx.client_token,
        body={
            "method": "BANK_TRANSFER",
            "accountLastFour": "4242",
        },
    )

    returned_payout_id = failed_payout.get("id")
    if returned_payout_id:
        ctx.payout_id = returned_payout_id

    assert_eq(
        failed_payout.get("contractId"), ctx.contract_id, "failed payout.contractId"
    )
    assert_eq(failed_payout.get("status"), "FAILED", "failed payout.status")

    tx = failed_payout.get("transactionDetails") or {}
    assert_eq(
        tx.get("simulateFailure"), True, "failed transactionDetails.simulateFailure"
    )
    assert_eq(
        tx.get("gatewayResponse"),
        "rejected",
        "failed transactionDetails.gatewayResponse",
    )
    assert_eq(
        tx.get("failureReason"),
        "simulated gateway failure",
        "failed transactionDetails.failureReason",
    )

    log("Failure payout response:")
    log(
        pretty(
            {
                k: failed_payout.get(k)
                for k in ["id", "contractId", "status", "transactionDetails"]
            }
        )
    )

    log("\nPolling for compensation convergence...")

    converged = False

    for i in range(1, 61):
        _, proposal = request_json(
            "GET",
            f"{ctx.base}/api/proposals/{ctx.proposal_id}",
            token=ctx.freelancer_token,
        )
        _, payout_fetched = request_json(
            "GET",
            f"{ctx.base}/api/payouts/{ctx.payout_id}",
            token=ctx.freelancer_token,
        )
        _, contract = request_json(
            "GET",
            f"{ctx.base}/api/contracts/{ctx.contract_id}",
            token=ctx.client_token,
        )
        _, job = request_json(
            "GET",
            f"{ctx.base}/api/jobs/{ctx.job_id}",
            token=ctx.client_token,
        )

        proposal_status = proposal.get("status")
        payout_status = payout_fetched.get("status")
        contract_status = contract.get("status")
        job_status = job.get("status")

        log(
            f"poll={i} proposal={proposal_status} payout={payout_status} "
            f"contract={contract_status} job={job_status}"
        )

        if (
            proposal_status == "PAYMENT_FAILED"
            and payout_status == "FAILED"
            and contract_status == "TERMINATED"
            and job_status == "IN_PROGRESS"
        ):
            converged = True
            break

        time.sleep(1)

    if not converged:
        raise SagaTestError(
            "Scenario B did not converge to PAYMENT_FAILED / FAILED / TERMINATED / IN_PROGRESS"
        )

    log("\nScenario B failure compensation path passed.")


def run_precheck_failure_no_side_effects(
    base: str,
    password: Optional[str],
    run_id: Optional[str],
) -> RunContext:
    ctx = RunContext(
        base=base,
        password=password or random_password(),
        run_id=run_id or make_run_id("precheck"),
    )

    setup_users(ctx)
    create_job_and_proposal(ctx)
    accept_proposal_and_bootstrap_contract(ctx)

    log(
        "\n== Phase 8 / Scenario C: Completion pre-check failure with no side effects =="
    )

    log(f"Deleting active contract CONTRACT_ID={ctx.contract_id}")

    delete_status, delete_body = request_empty(
        "DELETE",
        f"{ctx.base}/api/contracts/{ctx.contract_id}",
        token=ctx.client_token,
        fail=False,
    )

    if delete_status != 204:
        raise SagaTestError(
            f"delete contract: expected HTTP 204, got HTTP {delete_status}\nResponse:\n{delete_body}"
        )

    log("Delete contract returned HTTP 204.")

    log("Attempting completion after active contract was deleted...")

    complete_status, complete_body = request_empty(
        "PUT",
        f"{ctx.base}/api/proposals/{ctx.proposal_id}/complete",
        token=ctx.freelancer_token,
        fail=False,
    )

    if complete_status != 400:
        raise SagaTestError(
            f"completion pre-check: expected HTTP 400, got HTTP {complete_status}\n"
            f"Response:\n{complete_body}"
        )

    log("Completion returned HTTP 400 as expected.")

    log("Waiting 3 seconds to verify no async side effects happen...")
    time.sleep(3)

    _, proposal = request_json(
        "GET",
        f"{ctx.base}/api/proposals/{ctx.proposal_id}",
        token=ctx.freelancer_token,
    )

    _, job = request_json(
        "GET",
        f"{ctx.base}/api/jobs/{ctx.job_id}",
        token=ctx.client_token,
    )

    _, payouts = request_json(
        "GET",
        f"{ctx.base}/api/payouts",
        token=ctx.freelancer_token,
    )

    proposal_status = proposal.get("status")
    job_status = job.get("status")
    payout_count = count_payouts_for_contract(payouts, ctx.contract_id)

    log(f"proposal={proposal_status} job={job_status} payoutCount={payout_count}")

    assert_eq(proposal_status, "ACCEPTED", "Scenario C proposal.status")
    assert_eq(job_status, "IN_PROGRESS", "Scenario C job.status")
    assert_eq(payout_count, 0, "Scenario C payout count for deleted contract")

    log("\nScenario C pre-check failure path passed.")
    return ctx


def prepare_fresh_accepted_pending_run(
    base: str,
    password: Optional[str],
    run_id: str,
) -> RunContext:
    ctx = RunContext(
        base=base,
        password=password or random_password(),
        run_id=run_id,
    )

    setup_users(ctx)
    create_job_and_proposal(ctx)
    accept_proposal_and_bootstrap_contract(ctx)
    complete_work_and_create_pending_payout(ctx)

    return ctx


def run_success(
    base: str,
    password: Optional[str],
    run_id: Optional[str],
) -> RunContext:
    ctx = prepare_fresh_accepted_pending_run(
        base=base,
        password=password,
        run_id=run_id or make_run_id("success"),
    )

    process_payout_successfully(ctx)
    return ctx


def run_failure(
    base: str,
    password: Optional[str],
    run_id: Optional[str],
) -> RunContext:
    ctx = prepare_fresh_accepted_pending_run(
        base=base,
        password=password,
        run_id=run_id or make_run_id("fail"),
    )

    process_payout_failure_and_compensation(ctx)
    return ctx


def print_summary(label: str, ctx: RunContext) -> None:
    log(f"\n== {label} SUMMARY ==")
    log(
        pretty(
            {
                "runId": ctx.run_id,
                "clientEmail": ctx.client_email,
                "freelancerEmail": ctx.freelancer_email,
                "clientId": ctx.client_id,
                "freelancerId": ctx.freelancer_id,
                "jobId": ctx.job_id,
                "proposalId": ctx.proposal_id,
                "contractId": ctx.contract_id,
                "payoutId": ctx.payout_id,
            }
        )
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run ACL216 saga verification scenarios against local APIs."
    )

    parser.add_argument(
        "--base",
        default=DEFAULT_BASE,
        help=f"Base API URL. Default: {DEFAULT_BASE}",
    )

    parser.add_argument(
        "--password",
        default=DEFAULT_PASSWORD,
        help=(
            "Password to use for generated users. "
            "If omitted, each scenario gets a random password."
        ),
    )

    parser.add_argument(
        "--scenario",
        choices=["success", "failure", "precheck", "both", "all"],
        default="all",
        help=(
            "Scenario to run. "
            "success=A path, failure=B path, precheck=C path, "
            "both=success+failure, all=success+failure+precheck. Default: all"
        ),
    )

    parser.add_argument(
        "--run-id",
        default=None,
        help=(
            "Optional base run ID. For grouped scenarios, prefixes and random suffixes "
            "are added to keep each run isolated."
        ),
    )

    args = parser.parse_args()

    try:
        if args.scenario == "success":
            ctx = run_success(
                args.base,
                args.password,
                args.run_id or make_run_id("success"),
            )
            print_summary("SUCCESS", ctx)

        elif args.scenario == "failure":
            ctx = run_failure(
                args.base,
                args.password,
                args.run_id or make_run_id("fail"),
            )
            print_summary("FAILURE", ctx)

        elif args.scenario == "precheck":
            ctx = run_precheck_failure_no_side_effects(
                args.base,
                args.password,
                args.run_id or make_run_id("precheck"),
            )
            print_summary("PRECHECK", ctx)

        elif args.scenario == "both":
            base_run_id = args.run_id or make_run_id("suite")

            success_ctx = run_success(
                args.base,
                args.password,
                f"success-{base_run_id}-{secrets.token_hex(4)}",
            )
            print_summary("SUCCESS", success_ctx)

            failure_ctx = run_failure(
                args.base,
                args.password,
                f"fail-{base_run_id}-{secrets.token_hex(4)}",
            )
            print_summary("FAILURE", failure_ctx)

        else:
            base_run_id = args.run_id or make_run_id("suite")

            success_ctx = run_success(
                args.base,
                args.password,
                f"success-{base_run_id}-{secrets.token_hex(4)}",
            )
            print_summary("SUCCESS", success_ctx)

            failure_ctx = run_failure(
                args.base,
                args.password,
                f"fail-{base_run_id}-{secrets.token_hex(4)}",
            )
            print_summary("FAILURE", failure_ctx)

            precheck_ctx = run_precheck_failure_no_side_effects(
                args.base,
                args.password,
                f"precheck-{base_run_id}-{secrets.token_hex(4)}",
            )
            print_summary("PRECHECK", precheck_ctx)

        log("\nALL REQUESTED SCENARIOS PASSED")
        return 0

    except SagaTestError as e:
        log("\nTEST FAILED")
        log(str(e))
        return 1

    except KeyboardInterrupt:
        log("\nInterrupted.")
        return 130


if __name__ == "__main__":
    sys.exit(main())
