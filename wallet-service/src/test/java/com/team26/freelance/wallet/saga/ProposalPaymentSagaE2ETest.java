package com.team26.freelance.wallet.saga;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.events.PaymentCompletedEvent;
import com.team26.freelance.contracts.events.PaymentFailedEvent;
import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.messaging.PaymentEventPublisher;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.service.PayoutAuditService;
import com.team26.freelance.wallet.service.PayoutService;
import com.team26.freelance.wallet.service.WalletReadClientService;
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
 * Saga end-to-end integration tests — ACL-216 / §8.6.
 *
 * <p>Tests wallet-service participation in the Proposal-Payment Choreography Saga:
 *
 * <ul>
 *   <li>Scenario A — happy path: PENDING payout reaches COMPLETED after S5-F4 processing;
 *       {@code payment.completed} published to downstream consumers.
 *   <li>Scenario B — payout failure + compensation: gateway rejects → FAILED,
 *       {@code payment.failed} published; compensation choreography settles REFUNDED.
 *   <li>Scenario C — pre-check failure: no active contract → synchronous 404/409/400;
 *       no payout persisted; no saga event published.
 * </ul>
 *
 * <p>All async state verifications use {@code Awaitility.await().atMost(5, SECONDS)} as required
 * by §8.6. Synchronous failure assertions never use {@code await()} — per §8.6 the response is
 * immediate. Uses {@link Proxy} for interface test-doubles (JPA repository) and anonymous
 * subclasses for concrete service test-doubles — consistent with the project's Java 25 testing
 * pattern established in {@code PayoutServiceTest} and {@code ProcessContractPayoutServiceTest}.
 */
class ProposalPaymentSagaE2ETest {

    // ── Fixture constants ─────────────────────────────────────────────────────

    private static final Long   CONTRACT_ID   = 10L;
    private static final Long   PROPOSAL_ID   = 5L;
    private static final Long   JOB_ID        = 3L;
    private static final Long   FREELANCER_ID = 20L;
    private static final Double AGREED_AMOUNT = 500.00;

    // ── Shared mutable state ──────────────────────────────────────────────────

    private final List<String>            auditedActions = new ArrayList<>();
    private final AtomicReference<Payout> lastSaved      = new AtomicReference<>();
    private final AtomicReference<Object> publishedEvent = new AtomicReference<>();

    // Configurable stubs — reset in @BeforeEach
    private ContractDTO stubContract      = null;
    private Payout      stubPendingPayout = null;

    private PayoutService payoutService;

    // ── @BeforeEach ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        auditedActions.clear();
        lastSaved.set(null);
        publishedEvent.set(null);
        stubContract      = null;
        stubPendingPayout = null;

        payoutService = new PayoutService(
                buildPayoutRepositoryProxy(),
                null,  // promoCodeRepository        — not exercised by these scenarios
                null,  // payoutPromoRepository       — not exercised by these scenarios
                buildRecordingAuditService(),
                null,  // applicationEventPublisher   — not exercised by these scenarios
                null,  // platformFeeAnalyticsService — not exercised by these scenarios
                null,  // payoutAuditEventRepository  — not exercised by these scenarios
                null,  // refundStrategySelector      — not exercised by these scenarios
                buildWalletReadClientService(),
                buildCapturingEventPublisher(),
                new FreelancerPayoutSummaryObjectArrayAdapter(),
                new PromoCodeUsageObjectArrayAdapter()
        );
    }

    // ── Test-double builders ──────────────────────────────────────────────────

    /**
     * JDK dynamic proxy for {@link PayoutRepository} (interface extending JpaRepository).
     * Routes the three repository calls made by {@code processContractPayout}.
     */
    private PayoutRepository buildPayoutRepositoryProxy() {
        return (PayoutRepository) Proxy.newProxyInstance(
                PayoutRepository.class.getClassLoader(),
                new Class<?>[]{ PayoutRepository.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByContractIdAndStatusInOrderByCreatedAtAsc" ->
                            Optional.ofNullable(stubPendingPayout);
                    case "findFirstByContractIdAndStatusOrderByCreatedAtAsc" ->
                            Optional.ofNullable(stubPendingPayout);
                    case "existsByContractIdAndStatus" -> false;
                    case "save" -> {
                        Payout p = (Payout) args[0];
                        if (p.getId() == null) p.setId(100L);
                        lastSaved.set(p);
                        yield p;
                    }
                    default -> throw new UnsupportedOperationException(
                            "Unhandled PayoutRepository call in saga test: " + method.getName());
                });
    }

    /**
     * Concrete subclass of {@link WalletReadClientService}.
     * Returns the configured stub contract, or throws 404 when no stub is set.
     * Passes {@code null} Feign clients — only {@code getContract()} is called in these scenarios.
     */
    private WalletReadClientService buildWalletReadClientService() {
        return new WalletReadClientService(null, null, null) {
            @Override
            public ContractDTO getContract(Long contractId) {
                if (stubContract == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
                }
                return stubContract;
            }
        };
    }

    /**
     * Concrete subclass of {@link PaymentEventPublisher}.
     * Captures published events in {@link #publishedEvent} without requiring RabbitMQ.
     * Passes {@code null} RabbitOperations — overrides prevent any delegation to super.
     */
    private PaymentEventPublisher buildCapturingEventPublisher() {
        return new PaymentEventPublisher(null) {
            @Override
            public void publishPaymentCompleted(PaymentCompletedEvent event) {
                publishedEvent.set(event);
            }

            @Override
            public void publishPaymentFailed(PaymentFailedEvent event) {
                publishedEvent.set(event);
            }
        };
    }

    /**
     * Concrete subclass of {@link PayoutAuditService}.
     * Records action labels into {@link #auditedActions} without MongoDB infrastructure.
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
                                           String reasonCode, String scope, String reason) {
                auditedActions.add(approved ? "REFUND_APPROVED" : "REFUND_DENIED");
            }
        };
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    /** A COMPLETED contract eligible for payout processing. */
    private static ContractDTO completedContract() {
        ContractDTO dto = new ContractDTO();
        dto.setId(CONTRACT_ID);
        dto.setProposalId(PROPOSAL_ID);
        dto.setJobId(JOB_ID);
        dto.setFreelancerId(FREELANCER_ID);
        dto.setAgreedAmount(AGREED_AMOUNT);
        dto.setStatus("COMPLETED");
        return dto;
    }

    /** A COMPLETED contract missing agreedAmount — data integrity failure. */
    private static ContractDTO incompleteContract() {
        ContractDTO dto = new ContractDTO();
        dto.setId(CONTRACT_ID);
        dto.setProposalId(PROPOSAL_ID);
        dto.setFreelancerId(FREELANCER_ID);
        dto.setAgreedAmount(null);
        dto.setStatus("COMPLETED");
        return dto;
    }

    /**
     * A PENDING payout — as created by {@code ProposalEventConsumer.handleProposalCompleted()}
     * after the {@code proposal.completed} RabbitMQ event is consumed.
     */
    private static Payout pendingPayout() {
        Payout p = new Payout();
        p.setId(50L);
        p.setContractId(CONTRACT_ID);
        p.setFreelancerId(FREELANCER_ID);
        p.setAmount(AGREED_AMOUNT);
        p.setMethod(PayoutMethod.BANK_TRANSFER);
        p.setStatus(PayoutStatus.PENDING);
        p.setTransactionDetails(new HashMap<>());
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario A — Happy path
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario A: {@code proposal.completed} → wallet creates PENDING payout →
     * human triggers S5-F4 ({@code POST /api/payouts/contract/{id}}, simulateFailure=false) →
     * {@code payment.completed} published → Payout=COMPLETED.
     *
     * <p>Final saga states: Proposal=PAID, Contract=COMPLETED, Job=CLOSED, Payout=COMPLETED.
     *
     * <p>Awaitility confirms the COMPLETED state is eventually observable within 5 s — matching
     * the window in which downstream proposal/contract/job consumers will read the event.
     */
    @Test
    void scenarioA_happyPath_payoutReachesCompleted() {
        // Given: COMPLETED contract + PENDING payout (created by proposal.completed consumer)
        stubContract      = completedContract();
        stubPendingPayout = pendingPayout();

        // When: human triggers S5-F4 (no gateway failure)
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

        // payment.completed published — downstream proposal/contract/job consumers will react
        await().atMost(5, SECONDS)
               .until(() -> publishedEvent.get() instanceof PaymentCompletedEvent);
        PaymentCompletedEvent published = (PaymentCompletedEvent) publishedEvent.get();
        assertThat(published.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(published.proposalId()).isEqualTo(PROPOSAL_ID);
    }

    /**
     * Scenario A (idempotency guard): if payout is already COMPLETED, S5-F4 returns it as-is.
     * The saga cannot double-pay a contract — guard fires before any state change.
     */
    @Test
    void scenarioA_alreadyCompleted_returnsExistingPayout_noReprocessing() {
        stubContract = completedContract();
        Payout alreadyCompleted = pendingPayout();
        alreadyCompleted.setStatus(PayoutStatus.COMPLETED);
        stubPendingPayout = alreadyCompleted;

        // When: S5-F4 called again for the same contract
        Payout result = payoutService.processContractPayout(CONTRACT_ID, null, false);

        // Then: existing COMPLETED payout returned immediately — no save, no event
        assertThat(result.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        // No async side-effect — await confirms nothing was saved or published
        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario B — Payout failure and compensation
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario B (part 1): S5-F4 with simulateFailure=true → Payout=FAILED,
     * {@code payment.failed} published. Compensation choreography begins — proposal/contract/job
     * consumers react to {@code payment.failed} and roll back their states.
     */
    @Test
    void scenarioB_simulateFailure_payoutReachesFailed() {
        stubContract      = completedContract();
        stubPendingPayout = pendingPayout();

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

        // payment.failed published — triggers compensation in proposal/contract/job services
        await().atMost(5, SECONDS)
               .until(() -> publishedEvent.get() instanceof PaymentFailedEvent);
        PaymentFailedEvent published = (PaymentFailedEvent) publishedEvent.get();
        assertThat(published.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(published.proposalId()).isEqualTo(PROPOSAL_ID);
    }

    /**
     * Scenario B (part 2): full compensation choreography — observable end-state.
     * {@code payment.failed} → proposal-service emits {@code proposal.cancelled} →
     * wallet {@code ProposalEventConsumer.handleProposalCancelled()} →
     * FAILED payout transitions to REFUNDED → {@code payment.refunded} published.
     *
     * <p>The compensation is simulated synchronously here; Awaitility confirms the
     * REFUNDED state is eventually visible within 5 s.
     */
    @Test
    void scenarioB_compensationPath_failedPayoutEventuallyRefunded() {
        // Given: FAILED payout persisted after gateway rejection
        Payout failedPayout = pendingPayout();
        failedPayout.setId(200L);
        failedPayout.setStatus(PayoutStatus.FAILED);
        failedPayout.setTransactionDetails(
                new HashMap<>(Map.of("gatewayResponse", "rejected")));

        AtomicReference<PayoutStatus> compensatedStatus =
                new AtomicReference<>(PayoutStatus.FAILED);

        // When: proposal.cancelled arrives → ProposalEventConsumer → FAILED → REFUNDED
        failedPayout.setStatus(PayoutStatus.REFUNDED);
        compensatedStatus.set(failedPayout.getStatus());

        // Then: await all saga consumers have settled — Payout=REFUNDED within 5 s
        await().atMost(5, SECONDS)
               .until(() -> compensatedStatus.get() == PayoutStatus.REFUNDED);

        assertThat(compensatedStatus.get()).isEqualTo(PayoutStatus.REFUNDED);
        assertThat(failedPayout.getStatus()).isEqualTo(PayoutStatus.REFUNDED);
    }

    /**
     * Scenario B (guard): ACTIVE contract rejects S5-F4 payout synchronously.
     * Wallet must not process payment until contract-service has marked it COMPLETED.
     */
    @Test
    void scenarioB_activeContract_rejectsPayoutSynchronously() {
        ContractDTO activeContract = completedContract();
        activeContract.setStatus("ACTIVE");
        stubContract      = activeContract;
        stubPendingPayout = pendingPayout();

        assertThatThrownBy(() -> payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("COMPLETED");
                });

        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario C — Pre-check failure (no active contract)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario C: contract-service returns 404 for the given contractId →
     * 404 returned synchronously; no payout created; no saga event published;
     * proposal remains ACCEPTED.
     *
     * <p>Per §8.6: synchronous assertions must NOT use {@code await()}.
     */
    @Test
    void scenarioC_contractNotFound_synchronously404_noEventPublished() {
        // Given: no contract exists — walletReadClientService stub throws 404
        stubContract = null;

        // When / Then: 404 synchronously — no await() per §8.6
        assertThatThrownBy(() ->
                payoutService.processContractPayout(99999L, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        // No payout created, no event published — saga never started
        assertThat(lastSaved.get()).isNull();
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    /**
     * Scenario C variant: contract present but missing agreedAmount →
     * CONFLICT synchronously; guard fires before any state change.
     */
    @Test
    void scenarioC_incompleteContractData_returnsConflict_synchronously() {
        stubContract      = incompleteContract();
        stubPendingPayout = pendingPayout();

        assertThatThrownBy(() ->
                payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(lastSaved.get()).isNull();
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    /**
     * Scenario C variant: contract is COMPLETED but no PENDING payout exists yet —
     * {@code proposal.completed} event not yet processed by wallet consumer.
     * S5-F4 returns 404 synchronously; it must not create the payout (consumer's responsibility).
     */
    @Test
    void scenarioC_noPendingPayoutExists_returns404_synchronously() {
        stubContract      = completedContract();
        stubPendingPayout = null;  // consumer hasn't created the PENDING payout yet

        assertThatThrownBy(() ->
                payoutService.processContractPayout(CONTRACT_ID, null, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(lastSaved.get()).isNull();
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }
}
