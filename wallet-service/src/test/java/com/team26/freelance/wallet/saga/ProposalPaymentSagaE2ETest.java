package com.team26.freelance.wallet.saga;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * ACL216 live saga E2E coverage.
 *
 * <p>These tests intentionally go through the gateway and verify async RabbitMQ effects with
 * Awaitility. They are opt-in because they require the Kubernetes stack from {@code k8s/AGENT.md}.
 */
@EnabledIfEnvironmentVariable(named = "ACL216_LIVE_E2E", matches = "true")
class ProposalPaymentSagaE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("ACL216_GATEWAY_URL", "http://localhost:30080");
    private static final String PASSWORD = "Password123!";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void happyPathCompletesProposalAndPayoutAcrossServices() throws Exception {
        SagaRun run = createAcceptedProposal("happy");

        put("/api/proposals/" + run.proposalId + "/complete", run.freelancerToken, null)
                .assertStatus(200);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            JsonNode proposal = getJson("/api/proposals/" + run.proposalId, run.freelancerToken);
            JsonNode contract = getJson("/api/contracts/" + run.contractId, run.clientToken);
            JsonNode job = getJson("/api/jobs/" + run.jobId, run.clientToken);
            JsonNode payout = findPayoutByContract(run.contractId, run.freelancerToken);

            assertThat(proposal.path("status").asText()).isEqualTo("PAYMENT_PENDING");
            assertThat(contract.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(job.path("status").asText()).isEqualTo("CLOSED");
            assertThat(payout.path("status").asText()).isEqualTo("PENDING");
        });

        post("/api/payouts/contract/" + run.contractId, run.clientToken,
                "{\"method\":\"BANK_TRANSFER\",\"accountLastFour\":\"4242\"}")
                .assertStatus(201);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            JsonNode proposal = getJson("/api/proposals/" + run.proposalId, run.freelancerToken);
            JsonNode payout = findPayoutByContract(run.contractId, run.freelancerToken);

            assertThat(proposal.path("status").asText()).isEqualTo("PAID");
            assertThat(payout.path("status").asText()).isEqualTo("COMPLETED");
        });
    }

    @Test
    void payoutFailureCompensatesContractAndJobAcrossServices() throws Exception {
        SagaRun run = createAcceptedProposal("failure");
        put("/api/proposals/" + run.proposalId + "/complete", run.freelancerToken, null)
                .assertStatus(200);

        await().atMost(30, SECONDS).untilAsserted(() ->
                assertThat(findPayoutByContract(run.contractId, run.freelancerToken).path("status").asText())
                        .isEqualTo("PENDING"));

        post("/api/payouts/contract/" + run.contractId + "?simulateFailure=true", run.clientToken,
                "{\"method\":\"BANK_TRANSFER\",\"accountLastFour\":\"4242\"}")
                .assertStatus(201);

        await().atMost(45, SECONDS).untilAsserted(() -> {
            JsonNode proposal = getJson("/api/proposals/" + run.proposalId, run.freelancerToken);
            JsonNode contract = getJson("/api/contracts/" + run.contractId, run.clientToken);
            JsonNode job = getJson("/api/jobs/" + run.jobId, run.clientToken);
            JsonNode payout = findPayoutByContract(run.contractId, run.freelancerToken);

            assertThat(proposal.path("status").asText()).isEqualTo("PAYMENT_FAILED");
            assertThat(payout.path("status").asText()).isEqualTo("FAILED");
            assertThat(contract.path("status").asText()).isEqualTo("TERMINATED");
            assertThat(job.path("status").asText()).isEqualTo("IN_PROGRESS");
        });
    }

    @Test
    void completionPrecheckFailsWithoutActiveContractAndPublishesNoSagaEvent() throws Exception {
        SagaRun run = createAcceptedProposal("precheck");

        delete("/api/contracts/" + run.contractId, run.clientToken).assertStatus(204);
        put("/api/proposals/" + run.proposalId + "/complete", run.freelancerToken, null)
                .assertStatus(400);

        Thread.sleep(3_000L);

        JsonNode proposal = getJson("/api/proposals/" + run.proposalId, run.freelancerToken);
        JsonNode job = getJson("/api/jobs/" + run.jobId, run.clientToken);

        assertThat(proposal.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(job.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(findPayoutByContractOrNull(run.contractId, run.freelancerToken)).isNull();
    }

    private SagaRun createAcceptedProposal(String scenario) throws Exception {
        String runId = scenario + "-" + System.currentTimeMillis();
        Login client = registerAndLogin("client-" + runId, "CLIENT");
        Login freelancer = registerAndLogin("freelancer-" + runId, "FREELANCER");

        JsonNode job = post("/api/jobs", client.token,
                "{\"title\":\"ACL216 " + scenario + "\",\"description\":\"live e2e\","
                        + "\"category\":\"WEB_DEV\",\"budgetMin\":100.0,\"budgetMax\":500.0}")
                .assertStatus(201)
                .json();

        long jobId = job.path("id").asLong();
        JsonNode proposal = post("/api/proposals", freelancer.token,
                "{\"jobId\":" + jobId + ",\"freelancerId\":" + freelancer.userId
                        + ",\"coverLetter\":\"live e2e\",\"bidAmount\":250.0,\"estimatedDays\":5,"
                        + "\"metadata\":{\"runId\":\"" + runId + "\"}}")
                .assertStatus(201)
                .json();

        long proposalId = proposal.path("id").asLong();
        put("/api/proposals/" + proposalId + "/accept", client.token, null).assertStatus(200);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            JsonNode acceptedProposal = getJson("/api/proposals/" + proposalId, freelancer.token);
            JsonNode acceptedJob = getJson("/api/jobs/" + jobId, client.token);
            JsonNode activeContract = getJson("/api/contracts/proposal/" + proposalId + "/active", client.token);

            assertThat(acceptedProposal.path("status").asText()).isEqualTo("ACCEPTED");
            assertThat(acceptedJob.path("status").asText()).isEqualTo("IN_PROGRESS");
            assertThat(activeContract.path("id").asLong()).isPositive();
        });

        long contractId = getJson("/api/contracts/proposal/" + proposalId + "/active", client.token)
                .path("id")
                .asLong();
        return new SagaRun(client.token, freelancer.token, jobId, proposalId, contractId);
    }

    private Login registerAndLogin(String prefix, String role) throws Exception {
        String email = prefix + "@example.com";
        String phone = "+20" + Math.abs(email.hashCode());
        post("/api/auth/register", null,
                "{\"name\":\"" + prefix + "\",\"email\":\"" + email + "\","
                        + "\"password\":\"" + PASSWORD + "\",\"phone\":\"" + phone + "\","
                        + "\"role\":\"" + role + "\"}")
                .assertStatus(201);

        JsonNode login = post("/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .assertStatus(200)
                .json();
        return new Login(login.path("token").asText(), login.path("userId").asLong());
    }

    private JsonNode findPayoutByContract(long contractId, String token) throws Exception {
        JsonNode payout = findPayoutByContractOrNull(contractId, token);
        assertThat(payout).as("payout for contract " + contractId).isNotNull();
        return payout;
    }

    private JsonNode findPayoutByContractOrNull(long contractId, String token) throws Exception {
        JsonNode payouts = getJson("/api/payouts", token);
        for (JsonNode payout : payouts) {
            if (payout.path("contractId").asLong() == contractId) {
                return payout;
            }
        }
        return null;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return get(path, token).assertStatus(200).json();
    }

    private ApiResponse get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private ApiResponse post(String path, String token, String body) throws Exception {
        return send("POST", path, token, body);
    }

    private ApiResponse put(String path, String token, String body) throws Exception {
        return send("PUT", path, token, body);
    }

    private ApiResponse delete(String path, String token) throws Exception {
        return send("DELETE", path, token, null);
    }

    private ApiResponse send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    private record Login(String token, long userId) {}

    private record SagaRun(String clientToken, String freelancerToken, long jobId, long proposalId, long contractId) {}

    private record ApiResponse(int status, String body) {
        ApiResponse assertStatus(int expected) {
            assertThat(status).as(body).isEqualTo(expected);
            return this;
        }

        JsonNode json() throws IOException {
            return new ObjectMapper().readTree(body);
        }
    }
}
