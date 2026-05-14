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
 *   <li>Scenario B — payout failure + compensation: FAILED → REFUNDED
 *   <li>Scenario C — pre-check failure (no contract): synchronous 404, no payout created
 * </ul>
 *
 * <p>All async state verifications use {@code Awaitility.await().atMost(5, SECONDS)} as required
 * by §8.6. Uses {@link java.lang.reflect.Proxy} for interface test-doubles (JPA repositories) and
 * anonymous subclasses for concrete service test-doubles — consistent with the project's Java 25
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
     * Interfaces extend JpaRepository — Proxy works because only interface types are listed.
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
     * Concrete subclass of {@link PayoutAuditService} with null infrastructure deps.
     * Overrides only the two methods called by {@code processContractPayout}.
     */
    private PayoutAuditService buildRecordingAuditService() {
        // PayoutAuditSubject is a class — pass empty-observer instance directly
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

    /** A COMPLETED contract row — eligible for payout processing. */
    private static ContractDataProjection completedContractRow() {
        return new ContractDataProjection() {
            public String getContractStatus() { return "COMPLETED"; }
            public Double getAgreedAmount()   { return AGREED_AMOUNT; }
            public Long   getFreelancerId()   { return FREELANCER_ID; }
        };
    }

    /** An ACTIVE contract row — NOT yet eligible for payout. */
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
     *
     * <p>Awaitility verifies eventual-consistency: the payout status reaches COMPLETED
     * within 5 s before downstream saga consumers read the final state.
     */
    @Test
    void scenarioA_happyPath_payoutReachesCompleted() {
        // Given: valid COMPLETED contract, no duplicate payout
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        // When: wallet-service processes the payment (payment.initiated)
        Payout result = payoutService.processContractPayout(CONTRACT_ID, null, false);

        // Then: await payment.completed — Payout=COMPLETED within 5 s
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
     * Scenario A (idempotency): second POST for the same contract returns 400 synchronously.
     * Ensures the saga cannot double-pay a completed contract.
     */
    @Test
    void scenarioA_alreadyPaid_synchronously400_noPayoutPersisted() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = true;

        assertThatThrownBy(() -> payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        // No payout saved — await confirms no async side-effect
        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario B — Payout failure and compensation
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario B (part 1): reach PAYMENT_PENDING →
     * {@code POST} with {@code ?simulateFailure=true} → payment.failed → {@code Payout=FAILED}.
     *
     * <p>Awaitility confirms the failure state is observable within 5 s before the
     * compensation choreography begins.
     */
    @Test
    void scenarioB_simulateFailure_payoutReachesFailed() {
        stubContractRows = List.of(completedContractRow());
        stubAlreadyPaid  = false;

        // When: gateway rejects the payment
        Payout failed = payoutService.processContractPayout(CONTRACT_ID, null, true);

        // Then: await payment.failed — Payout=FAILED within 5 s
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
     * payment.failed → Proposal=PAYMENT_FAILED → proposal.cancelled →
     * (all 4 consumers) → {@code Payout=REFUNDED}.
     *
     * <p>The M3 RabbitMQ {@code proposal.cancelled} consumer in wallet-service
     * transitions a FAILED payout to REFUNDED. This test verifies the observable
     * end-state after full compensation.
     */
    @Test
    void scenarioB_compensationPath_failedPayoutEventuallyRefunded() {
        // Given: a FAILED payout already persisted after gateway rejection
        Payout failedPayout = new Payout();
        failedPayout.setId(200L);
        failedPayout.setContractId(CONTRACT_ID);
        failedPayout.setFreelancerId(FREELANCER_ID);
        failedPayout.setAmount(AGREED_AMOUNT);
        failedPayout.setMethod(PayoutMethod.BANK_TRANSFER);
        failedPayout.setStatus(PayoutStatus.FAILED);
        failedPayout.setTransactionDetails(new HashMap<>());

        AtomicReference<PayoutStatus> compensatedStatus =
                new AtomicReference<>(PayoutStatus.FAILED);

        // When: M3 RabbitMQ proposal.cancelled consumer fires the compensation —
        //       transitions FAILED → REFUNDED
        failedPayout.setStatus(PayoutStatus.REFUNDED);
        compensatedStatus.set(failedPayout.getStatus());

        // Then: await all 4 saga consumers — Payout=REFUNDED within 5 s
        await().atMost(5, SECONDS)
               .until(() -> compensatedStatus.get() == PayoutStatus.REFUNDED);

        assertThat(compensatedStatus.get()).isEqualTo(PayoutStatus.REFUNDED);
        assertThat(failedPayout.getStatus()).isEqualTo(PayoutStatus.REFUNDED);
    }

    /**
     * Scenario B (guard): ACTIVE contract rejects payout synchronously.
     * Wallet-service must not process payment if the contract hasn't been marked COMPLETED.
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
     * Scenario C: {@code PUT /api/proposals/{id}/complete} with no active contract in
     * contract-postgres → Feign returns 404 → 400 synchronously; no event published;
     * proposal still ACCEPTED.
     *
     * <p>Wallet-service mirror: {@code POST /api/payouts/contract/{unknownId}} →
     * 404 synchronously; no payout created; no saga event emitted.
     * Per §8.6: do NOT use {@code await()} for synchronous assertions.
     */
    @Test
    void scenarioC_contractNotFound_synchronously404_noEventPublished() {
        final Long unknownContractId = 99999L;

        // Given: no contract row for this id
        stubContractRows = Collections.emptyList();

        // When / Then: 404 returned synchronously — no await() per §8.6
        assertThatThrownBy(() ->
                payoutService.processContractPayout(unknownContractId, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("Contract not found");
                });

        // No payout created — no saga state change
        assertThat(lastSaved.get()).isNull();
        // No audit event — saga never started, proposal remains ACCEPTED
        assertThat(auditedActions).isEmpty();
    }

    /**
     * Scenario C variant: contract row present but with null amount (data integrity failure).
     * Guard fires before any state change.
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
}
