package com.team26.freelance.wallet.saga;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.dto.ContractDataProjection;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.service.PayoutAuditService;
import com.team26.freelance.wallet.service.PayoutService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saga end-to-end integration tests — §8.6.
 *
 * <p>Tests wallet-service participation in the Proposal-Payment Choreography Saga:
 * <ul>
 *   <li>Scenario A — happy path: payout created and reaches COMPLETED
 *   <li>Scenario B — payout failure + compensation: FAILED → REFUNDED via
 *       {@link PayoutService#compensateFailedPayout(Long)} (the proposal.cancelled consumer
 *       entry point) — no direct test-side {@code setStatus()} mutation
 *   <li>Scenario C — pre-check failure (no contract): synchronous 404, no payout created
 *   <li>Edge cases: PENDING reuse, compensation idempotency, state-order guarantee
 * </ul>
 *
 * <p>Every async state assertion uses {@code Awaitility.await().atMost(5, SECONDS)} per §8.6.
 * Uses {@link java.lang.reflect.Proxy} for interface test-doubles (JPA repositories) and
 * anonymous subclasses for concrete service test-doubles — consistent with the Java 25
 * testing pattern established in {@code PayoutServiceTest}.
 */
class ProposalPaymentSagaE2ETest {

    // ── Fixture constants ─────────────────────────────────────────────────────

    private static final Long   CONTRACT_ID   = 10L;
    private static final Long   FREELANCER_ID = 20L;
    private static final Double AGREED_AMOUNT = 500.00;

    // ── Shared mutable state for proxy handlers ───────────────────────────────

    private final List<String>            auditedActions = new ArrayList<>();
    private final AtomicReference<Payout> lastSaved      = new AtomicReference<>();

    // Configurable stubs reset in @BeforeEach
    private List<ContractDataProjection> stubContractRows  = Collections.emptyList();
    private boolean                      stubAlreadyPaid   = false;
    private Payout                       stubPendingPayout = null;
    private Payout                       stubFoundById     = null;

    private PayoutService payoutService;

    // ── @BeforeEach ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        auditedActions.clear();
        lastSaved.set(null);
        stubContractRows  = Collections.emptyList();
        stubAlreadyPaid   = false;
        stubPendingPayout = null;
        stubFoundById     = null;

        payoutService = new PayoutService(
                buildPayoutRepositoryProxy(),
                null,  // promoCodeRepository  — not exercised
                null,  // payoutPromoRepository — not exercised
                buildRecordingAuditService(),
                null,  // eventPublisher        — not exercised
                null,  // platformFeeAnalytics  — not exercised
                null,  // auditEventRepository  — not exercised
                null,  // refundStrategySelector — not exercised
                new FreelancerPayoutSummaryObjectArrayAdapter(),
                new PromoCodeUsageObjectArrayAdapter()
        );
    }

    // ── Test-double builders ──────────────────────────────────────────────────

    /**
     * JDK dynamic proxy for {@link PayoutRepository}.
     * All repository calls route to the configurable stub fields on this test class.
     * No Mockito — consistent with the project's Java 25 testing pattern.
     */
    private PayoutRepository buildPayoutRepositoryProxy() {
        return (PayoutRepository) Proxy.newProxyInstance(
                PayoutRepository.class.getClassLoader(),
                new Class<?>[]{ PayoutRepository.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "findContractDataById" -> stubContractRows;
                    case "existsByContractIdAndStatus" -> stubAlreadyPaid;
                    case "findFirstByContractIdAndStatusOrderByCreatedAtAsc" ->
                            Optional.ofNullable(stubPendingPayout);
                    case "findByIdWithPromos", "findById" ->
                            Optional.ofNullable(stubFoundById);
                    case "save" -> {
                        Payout p = (Payout) args[0];
                        if (p.getId() == null) p.setId(100L);
                        lastSaved.set(p);
                        yield p;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Anonymous subclass of {@link PayoutAuditService} — concrete class, not interface,
     * so Proxy cannot be used. Overrides only the two methods exercised by the saga paths.
     */
    private PayoutAuditService buildRecordingAuditService() {
        PayoutAuditSubject noopSubject = new PayoutAuditSubject(Collections.emptyList());
        return new PayoutAuditService(noopSubject, null) {
            @Override
            public void recordPayoutEvent(Payout payout, String action,
                                          Map<String, Object> details) {
                auditedActions.add(action);
            }

            @Override
            public void recordRefundResult(Payout payout, boolean approved,
                                           Double amount, String strategy,
                                           String reasonCode, String scope,
                                           String reason) {
                auditedActions.add(approved ? "REFUND_APPROVED" : "REFUND_DENIED");
            }
        };
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private static ContractDataProjection completedContractRow() {
        return new ContractDataProjection() {
            public String getContractStatus() { return "COMPLETED"; }
            public Double getAgreedAmount()   { return AGREED_AMOUNT; }
            public Long   getFreelancerId()   { return FREELANCER_ID; }
        };
    }

    private static ContractDataProjection activeContractRow() {
        return new ContractDataProjection() {
            public String getContractStatus() { return "ACTIVE"; }
            public Double getAgreedAmount()   { return AGREED_AMOUNT; }
            public Long   getFreelancerId()   { return FREELANCER_ID; }
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario A — Happy path
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario A: proposal.completed → PAYMENT_PENDING →
     * {@code POST /api/payouts/contract/{id}} (simulateFailure=false) →
     * payment.completed → {@code Payout=COMPLETED}.
     */
    @Test
    void scenarioA_happyPath_payoutReachesCompleted() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        Payout result = payoutService.processContractPayout(CONTRACT_ID, null, false);

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(result.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(result.getFreelancerId()).isEqualTo(FREELANCER_ID);
        assertThat(result.getAmount()).isEqualTo(AGREED_AMOUNT);
        assertThat(result.getTransactionDetails())
                .containsKey("platformFee")
                .containsEntry("gatewayResponse", "approved");
        assertThat(auditedActions).contains(PayoutAuditService.COMPLETED);
    }

    /**
     * Scenario A (idempotency): second POST for the same contract → 400 synchronously.
     * Duplicate POST must not persist a payout and must not publish any audit event.
     */
    @Test
    void scenarioA_alreadyPaid_synchronously400_noAuditPublished() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = true;

        assertThatThrownBy(() -> payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        // No payout saved and no audit event — duplicate POST cannot publish twice
        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario B — Payout failure and compensation
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario B (part 1): simulateFailure=true → payment.failed → {@code Payout=FAILED}.
     * FAILED state must be observable by Awaitility before compensation starts.
     */
    @Test
    void scenarioB_simulateFailure_payoutReachesFailed() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        Payout failed = payoutService.processContractPayout(CONTRACT_ID, null, true);

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.FAILED);

        assertThat(failed.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(failed.getTransactionDetails())
                .containsEntry("gatewayResponse", "rejected")
                .containsEntry("simulateFailure", true);
        assertThat(auditedActions).contains(PayoutAuditService.FAILED);
    }

    /**
     * Scenario B (part 2): saga compensation choreography.
     * payment.failed → proposal.cancelled → {@code compensateFailedPayout()} →
     * {@code Payout=REFUNDED}.
     *
     * <p>Compensation is driven entirely through
     * {@link PayoutService#compensateFailedPayout(Long)} — the method the
     * proposal.cancelled consumer calls. No direct {@code setStatus()} mutation.
     * Awaitility guards both the FAILED gate and the final REFUNDED state.
     * State order is asserted: FAILED must be recorded before REFUNDED.
     */
    @Test
    void scenarioB_compensationPath_failedPayoutEventuallyRefunded() {
        // Part 1: reach Payout=FAILED
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        Payout failed = payoutService.processContractPayout(CONTRACT_ID, null, true);

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.FAILED);
        assertThat(auditedActions).contains(PayoutAuditService.FAILED);

        // Part 2: proposal.cancelled consumer — compensate via service, not setStatus()
        stubFoundById = lastSaved.get();   // proxy returns the FAILED payout on findById

        Payout compensated = payoutService.compensateFailedPayout(failed.getId());

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.REFUNDED);

        assertThat(compensated.getStatus()).isEqualTo(PayoutStatus.REFUNDED);
        // FAILED must appear before REFUNDED — correct saga state ordering
        assertThat(auditedActions).containsSubsequence(
                PayoutAuditService.FAILED, PayoutAuditService.REFUNDED);
    }

    /**
     * Scenario B (guard): ACTIVE contract rejects payout synchronously.
     * Wallet-service must not process payment before contract is COMPLETED.
     */
    @Test
    void scenarioB_activeContract_rejectsPayoutRequest_synchronously() {
        stubContractRows = List.of(activeContractRow());

        assertThatThrownBy(() -> payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("COMPLETED");
                });

        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario C — Pre-check failure (no active contract)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario C: unknown contractId → 404 synchronously; no payout created;
     * no saga event emitted; proposal remains ACCEPTED.
     * Per §8.6: do NOT use {@code await()} for synchronous assertions.
     */
    @Test
    void scenarioC_contractNotFound_synchronously404_noEventPublished() {
        stubContractRows = Collections.emptyList();

        assertThatThrownBy(() ->
                payoutService.processContractPayout(99999L, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("Contract not found");
                });

        assertThat(lastSaved.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    /**
     * Scenario C variant: contract row present but with null amount (data integrity failure).
     * Guard fires before any state change — 409 synchronously.
     */
    @Test
    void scenarioC_incompleteContractData_returnsConflict_synchronously() {
        stubContractRows = List.of(new ContractDataProjection() {
            public String getContractStatus() { return "COMPLETED"; }
            public Double getAgreedAmount()   { return null; }
            public Long   getFreelancerId()   { return FREELANCER_ID; }
        });

        assertThatThrownBy(() ->
                payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(lastSaved.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * A PENDING payout already exists (PAYMENT_PENDING — e.g. a previous interrupted
     * attempt). {@code processContractPayout} must reuse it rather than creating a new row.
     * Verifies idempotent re-entry into the PAYMENT_PENDING state and correct completion.
     */
    @Test
    void edge_existingPendingPayout_isReused_completedNotDuplicated() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        Payout preExisting = new Payout();
        preExisting.setId(77L);
        preExisting.setContractId(CONTRACT_ID);
        preExisting.setFreelancerId(FREELANCER_ID);
        preExisting.setAmount(AGREED_AMOUNT);
        preExisting.setMethod(PayoutMethod.BANK_TRANSFER);
        preExisting.setStatus(PayoutStatus.PENDING);
        preExisting.setTransactionDetails(new HashMap<>());
        stubPendingPayout = preExisting;

        Payout result = payoutService.processContractPayout(CONTRACT_ID, null, false);

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.COMPLETED);

        // Same id confirms pre-existing PENDING payout was reused, not a new one
        assertThat(result.getId()).isEqualTo(77L);
        assertThat(result.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(auditedActions).contains(PayoutAuditService.COMPLETED);
        // CREATED must not appear — payout was pre-existing, not newly inserted
        assertThat(auditedActions).doesNotContain(PayoutAuditService.CREATED);
    }

    /**
     * Compensation is idempotent: a second {@code compensateFailedPayout} call on an
     * already-REFUNDED payout is rejected with 400. Exactly one REFUNDED audit event
     * is recorded — the compensation choreography ran only once.
     */
    @Test
    void edge_compensationIdempotent_secondCallRejected() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        Payout failed = payoutService.processContractPayout(CONTRACT_ID, null, true);

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.FAILED);

        // First compensation succeeds
        stubFoundById = lastSaved.get();
        payoutService.compensateFailedPayout(failed.getId());

        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get().getStatus() == PayoutStatus.REFUNDED);

        // Second compensation — payout is now REFUNDED, not FAILED → 400
        stubFoundById = lastSaved.get();
        assertThatThrownBy(() -> payoutService.compensateFailedPayout(failed.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        // Exactly one REFUNDED audit event — compensation ran only once
        long refundedCount = auditedActions.stream()
                .filter(PayoutAuditService.REFUNDED::equals)
                .count();
        assertThat(refundedCount).isEqualTo(1);
    }

    /**
     * FAILED state must be observable by Awaitility before compensation fires.
     * Verifies the ordering guarantee required by the choreography: downstream
     * consumers can safely read FAILED before the compensation cascade begins.
     */
    @Test
    void edge_failedStateObservableBeforeCompensationFires() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        payoutService.processContractPayout(CONTRACT_ID, null, true);

        // FAILED must be Awaitility-observable BEFORE compensation is triggered
        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.FAILED);

        assertThat(lastSaved.get().getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(lastSaved.get().getTransactionDetails())
                .containsEntry("simulateFailure", true)
                .containsEntry("gatewayResponse", "rejected");
        // Only FAILED recorded — compensation has not yet been triggered
        assertThat(auditedActions).containsExactly(PayoutAuditService.FAILED);
    }
}
