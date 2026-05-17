package com.team26.freelance.wallet.saga;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.events.PaymentCompletedEvent;
import com.team26.freelance.contracts.events.PaymentFailedEvent;
import com.team26.freelance.contracts.events.PaymentInitiatedEvent;
import com.team26.freelance.contracts.events.PaymentRefundedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.messaging.PaymentEventPublisher;
import com.team26.freelance.wallet.messaging.ProposalEventConsumer;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.observer.PayoutAuditSubject;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.service.PayoutAuditService;
import com.team26.freelance.wallet.service.PayoutService;
import com.team26.freelance.wallet.service.WalletReadClientService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saga end-to-end integration tests — ACL-216 / §8.6.
 *
 * <p>Tests wallet-service participation in the Proposal-Payment Choreography Saga for scenarios
 * A, B, and C from §8.6. Scenario D (reaper — abandonment-driven compensation) is in
 * proposal-service (§8.7) and is out of scope for this ticket.
 *
 * <p><b>Scenario A — Happy path (§8.6):</b>
 * <ol>
 *   <li>Phase 1 (intermediate state): {@code proposal.completed} arrives → wallet creates PENDING
 *       payout → {@code payment.initiated} published → proposal-service sets Proposal=PAYMENT_PENDING
 *       (other service, verified by §8.6 step 5; wallet asserts its own PENDING payout).
 *   <li>Phase 2 (S5-F4): human triggers {@code POST /api/payouts/contract/{id}} →
 *       {@code payment.completed} published → Payout=COMPLETED → proposal-service sets
 *       Proposal=PAID.
 * </ol>
 *
 * <p><b>Scenario B — Payout failure and compensation (§8.6):</b>
 * <ol>
 *   <li>Gateway rejection: S5-F4 with simulateFailure=true → Payout=FAILED → {@code payment.failed}
 *       published.
 *   <li>Compensation: proposal-service receives {@code payment.failed} → Proposal=PAYMENT_FAILED →
 *       publishes {@code proposal.cancelled} → wallet's {@code ProposalEventConsumer} calls
 *       {@code refundPayoutFromProposalCancelled()} → PENDING payout → REFUNDED →
 *       {@code payment.refunded} published.
 *   <li>User-service (§8.5): consumes {@code proposal.cancelled} and reverses freelancer stats
 *       locally — no event emitted. This is user-service scope, not asserted here.
 *   <li>FAILED payout branch: {@code findRefundCandidateByProposalId} only queries
 *       {@code status IN ('PENDING','COMPLETED')} so a FAILED payout is not found → compensation
 *       returns empty → no {@code payment.refunded} published (correct: no money was transferred).
 * </ol>
 *
 * <p><b>Scenario C — Pre-check failure at S5-F4 level (§8.6 / wallet-service scope):</b>
 * <p>§8.6 Scenario C describes S3-F4 (proposal-service's {@code PUT /api/proposals/{id}/complete}
 * finding no active contract via Feign → 400). That is proposal-service's deliverable. From
 * wallet-service's perspective, the analogous pre-check failures are S5-F4 level: contract not
 * found, incomplete contract data, or no PENDING payout existing. These are tested here. All
 * synchronous — no {@code await()} per §8.6.
 *
 * <p><b>Threading note.</b> All async state verifications use {@code Awaitility.await().atMost(5,
 * SECONDS)} per §8.6. Synchronous failure assertions never use {@code await()} — response is
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

    private final ObjectMapper             objectMapper   = new ObjectMapper();
    private final List<String>             auditedActions = new ArrayList<>();
    private final AtomicReference<Payout>  lastSaved      = new AtomicReference<>();
    private final AtomicReference<Object>  publishedEvent = new AtomicReference<>();

    // Configurable stubs — reset in @BeforeEach
    private ContractDTO stubContract      = null;
    private Payout      stubPendingPayout = null;

    private PaymentEventPublisher  capturingPublisher;
    private PayoutService          payoutService;
    private ProposalEventConsumer  consumer;

    // ── @BeforeEach ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        auditedActions.clear();
        lastSaved.set(null);
        publishedEvent.set(null);
        stubContract      = null;
        stubPendingPayout = null;

        // Single capturing publisher shared by both payoutService (for processContractPayout)
        // and consumer (for publishPaymentInitiated / publishPaymentRefunded).
        capturingPublisher = new PaymentEventPublisher(null) {
            @Override public void publishPaymentInitiated(PaymentInitiatedEvent event) {
                publishedEvent.set(event);
            }
            @Override public void publishPaymentCompleted(PaymentCompletedEvent event) {
                publishedEvent.set(event);
            }
            @Override public void publishPaymentFailed(PaymentFailedEvent event) {
                publishedEvent.set(event);
            }
            @Override public void publishPaymentRefunded(PaymentRefundedEvent event) {
                publishedEvent.set(event);
            }
        };

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
                capturingPublisher,
                new FreelancerPayoutSummaryObjectArrayAdapter(),
                new PromoCodeUsageObjectArrayAdapter()
        );

        // Consumer wired with the same payoutService + capturingPublisher
        consumer = new ProposalEventConsumer(objectMapper, payoutService, capturingPublisher);
    }

    // ── Test-double builders ──────────────────────────────────────────────────

    /**
     * JDK dynamic proxy for {@link PayoutRepository} (interface extending JpaRepository).
     *
     * <p>Routes the repository calls made by {@code processContractPayout},
     * {@code createPendingPayoutFromProposalCompleted}, and {@code refundPayoutFromProposalCancelled}.
     * {@code findRefundCandidateByProposalId} mirrors the SQL filter:
     * {@code status IN ('PENDING','COMPLETED')} — FAILED payouts return empty.
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
                    case "findRefundCandidateByProposalId" -> {
                        // Mirrors SQL: status IN ('PENDING','COMPLETED') — FAILED excluded
                        if (stubPendingPayout == null) {
                            yield Optional.empty();
                        }
                        PayoutStatus s = stubPendingPayout.getStatus();
                        if (s == PayoutStatus.PENDING || s == PayoutStatus.COMPLETED) {
                            yield Optional.of(stubPendingPayout);
                        }
                        yield Optional.empty();
                    }
                    case "save" -> {
                        Payout p = (Payout) args[0];
                        if (p.getId() == null) p.setId(100L);
                        lastSaved.set(p);
                        yield p;
                    }
                    case "findById" -> Optional.ofNullable(stubPendingPayout);
                    default -> throw new UnsupportedOperationException(
                            "Unhandled PayoutRepository call in saga test: " + method.getName());
                });
    }

    /**
     * Concrete subclass of {@link WalletReadClientService}.
     * Returns the configured stub contract, or throws 404 when no stub is set.
     * Overrides {@code getUser()} to return a minimal stub — user-service validation
     * succeeds; the content is not used by the saga flow being tested here.
     * Passes {@code null} Feign clients — overrides prevent any delegation to super.
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

            @Override
            public UserDTO getUser(Long userId) {
                // Minimal stub — createPendingPayoutFromProposalCompleted calls getUser() for
                // existence validation only. User-service stats updates are local DB only (§8.5).
                UserDTO dto = new UserDTO();
                dto.setId(userId);
                dto.setStatus("ACTIVE");
                return dto;
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
     * after the {@code proposal.completed} RabbitMQ event is consumed. Includes {@code proposalId}
     * in transactionDetails to match the {@code findRefundCandidateByProposalId} native SQL filter
     * ({@code transaction_details ->> 'proposalId'}).
     */
    private static Payout pendingPayout() {
        Payout p = new Payout();
        p.setId(50L);
        p.setContractId(CONTRACT_ID);
        p.setFreelancerId(FREELANCER_ID);
        p.setAmount(AGREED_AMOUNT);
        p.setMethod(PayoutMethod.BANK_TRANSFER);
        p.setStatus(PayoutStatus.PENDING);
        Map<String, Object> details = new HashMap<>();
        details.put("proposalId", PROPOSAL_ID);
        details.put("jobId", JOB_ID);
        p.setTransactionDetails(details);
        return p;
    }

    /** Builds an AMQP {@link Message} containing the JSON-serialised {@code event}. */
    private Message messageFor(Object event, String routingKey) throws Exception {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        byte[] body = objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
        return new Message(body, props);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Scenario A — Happy path
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario A Phase 1 — Intermediate state (§8.6 steps 3–5, wallet-service participation):
     *
     * <p>{@code proposal.completed} arrives at wallet via {@code ProposalEventConsumer} →
     * {@code createPendingPayoutFromProposalCompleted()} creates a PENDING payout in wallet-postgres
     * → {@code payment.initiated} published → proposal-service (separate service) will consume it
     * and set Proposal=PAYMENT_PENDING.
     *
     * <p>This verifies §8.6 step 5's wallet assertion: "wallet-postgres has PENDING Payout for
     * proposalId={@value #PROPOSAL_ID} / contractId={@value #CONTRACT_ID}."
     */
    @Test
    void scenarioA_intermediateState_proposalCompleted_createsPendingPayout_publishesPaymentInitiated()
            throws Exception {
        // Given: no existing payout; valid contract (getContract + getUser validation passes)
        stubContract = completedContract();
        // stubPendingPayout remains null → findFirstByContractIdAndStatusInOrderByCreatedAtAsc returns empty

        // When: proposal.completed event consumed by ProposalEventConsumer (saga step 3)
        ProposalCompletedEvent event = new ProposalCompletedEvent(
                PROPOSAL_ID, JOB_ID, FREELANCER_ID, CONTRACT_ID, BigDecimal.valueOf(AGREED_AMOUNT));
        consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_COMPLETED));

        // Then: wallet-postgres has PENDING payout (intermediate state — §8.6 step 5)
        await().atMost(5, SECONDS).until(() -> lastSaved.get() != null);
        Payout pendingPayout = lastSaved.get();
        assertThat(pendingPayout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        assertThat(pendingPayout.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(pendingPayout.getFreelancerId()).isEqualTo(FREELANCER_ID);
        assertThat(pendingPayout.getAmount()).isEqualTo(AGREED_AMOUNT);
        assertThat(auditedActions).contains(PayoutAuditService.PAYOUT_CREATED);

        // payment.initiated published (§8.6 step 4) — proposal-service will react by setting
        // Proposal=PAYMENT_PENDING. Proposal state change is proposal-service scope, not asserted here.
        await().atMost(5, SECONDS)
               .until(() -> publishedEvent.get() instanceof PaymentInitiatedEvent);
        PaymentInitiatedEvent initiated = (PaymentInitiatedEvent) publishedEvent.get();
        assertThat(initiated.proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(initiated.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(initiated.amount()).isEqualByComparingTo(BigDecimal.valueOf(AGREED_AMOUNT));
    }

    /**
     * Scenario A Phase 1 (idempotency): {@code proposal.completed} received again for a contract
     * that already has a PENDING payout → {@code createPendingPayoutFromProposalCompleted} returns
     * empty → no second {@code payment.initiated} published. Guards against duplicate event delivery.
     */
    @Test
    void scenarioA_intermediateState_duplicateProposalCompleted_skipsPayoutCreation() throws Exception {
        stubContract      = completedContract();
        stubPendingPayout = pendingPayout(); // existing PENDING payout already in wallet-postgres

        ProposalCompletedEvent event = new ProposalCompletedEvent(
                PROPOSAL_ID, JOB_ID, FREELANCER_ID, CONTRACT_ID, BigDecimal.valueOf(AGREED_AMOUNT));
        consumer.onProposalEvent(messageFor(event, SagaTopics.PROPOSAL_COMPLETED));

        // No new payout saved, no payment.initiated published
        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
    }

    /**
     * Scenario A Phase 2 (S5-F4): PENDING payout already exists (from Phase 1) →
     * human triggers {@code POST /api/payouts/contract/{contractId}} (simulateFailure=false) →
     * {@code payment.completed} published → Payout=COMPLETED.
     *
     * <p>Final saga states for wallet-service: Payout=COMPLETED. Downstream: proposal-service
     * consumes {@code payment.completed} → Proposal=PAID (§8.6 step 8–9, proposal-service scope).
     *
     * <p>Awaitility confirms the COMPLETED state is eventually observable within 5 s — the window
     * in which downstream proposal/contract/job consumers will read the event.
     */
    @Test
    void scenarioA_s5f4_happyPath_payoutReachesCompleted() {
        // Given: COMPLETED contract + PENDING payout (created by Phase 1)
        stubContract      = completedContract();
        stubPendingPayout = pendingPayout();

        // When: human triggers S5-F4 (no gateway failure)
        Payout result = payoutService.processContractPayout(CONTRACT_ID, null, false);

        // Then: Payout=COMPLETED (§8.6 step 7 for wallet)
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

        // payment.completed published — proposal-service will react by setting Proposal=PAID
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
     * Scenario B step 2–3 (§8.6): S5-F4 with simulateFailure=true → Payout=FAILED,
     * {@code payment.failed} published. Compensation choreography begins — proposal-service
     * receives {@code payment.failed} → Proposal=PAYMENT_FAILED → publishes {@code
     * proposal.cancelled} → downstream services roll back their states.
     */
    @Test
    void scenarioB_simulateFailure_payoutReachesFailed() {
        stubContract      = completedContract();
        stubPendingPayout = pendingPayout();

        // When: gateway rejects the payment
        Payout failed = payoutService.processContractPayout(CONTRACT_ID, null, true);

        // Then: Payout=FAILED (§8.6 step 3)
        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.FAILED);

        assertThat(failed.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(failed.getTransactionDetails())
                .containsEntry("gatewayResponse", "rejected")
                .containsEntry("simulateFailure", true);
        assertThat(auditedActions).contains(PayoutAuditService.FAILED);

        // payment.failed published — triggers compensation: proposal-service sets Proposal=PAYMENT_FAILED
        // then publishes proposal.cancelled (§8.6 step 4–5, proposal-service scope)
        await().atMost(5, SECONDS)
               .until(() -> publishedEvent.get() instanceof PaymentFailedEvent);
        PaymentFailedEvent published = (PaymentFailedEvent) publishedEvent.get();
        assertThat(published.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(published.proposalId()).isEqualTo(PROPOSAL_ID);
    }

    /**
     * Scenario B compensation path — PENDING payout (§8.6 steps 6–9, wallet-service side):
     *
     * <p>{@code proposal.cancelled} arrives at wallet (published by proposal-service after it
     * receives {@code payment.failed}) → {@code ProposalEventConsumer.handleProposalCancelled()} →
     * {@code refundPayoutFromProposalCancelled()} → PENDING payout transitions to REFUNDED →
     * {@code payment.refunded} published → proposal-service consumes it → Proposal=REFUNDED.
     *
     * <p>§8.6 step 7 wallet assertion: "payout = REFUNDED in wallet-postgres."
     * §8.6 step 7 user-service assertion: "freelancer stats reversed in user-postgres" —
     * this is user-service scope (local DB only, no event emitted per §8.5); not asserted here.
     *
     * <p>This covers the scenario where the payout was PENDING when {@code proposal.cancelled}
     * arrived (i.e. the client never triggered S5-F4, or payout was abandoned before processing).
     */
    @Test
    void scenarioB_compensationPath_pendingPayoutRefundedOnProposalCancelled_publishesPaymentRefunded()
            throws Exception {
        // Given: PENDING payout in wallet-postgres (created after proposal.completed in Phase 1)
        stubPendingPayout = pendingPayout(); // status=PENDING, proposalId in transactionDetails

        // When: proposal.cancelled arrives — compensation cascade initiated by proposal-service
        ProposalCancelledEvent cancelledEvent = new ProposalCancelledEvent(
                PROPOSAL_ID, JOB_ID, FREELANCER_ID, "simulated gateway failure");
        consumer.onProposalEvent(messageFor(cancelledEvent, SagaTopics.PROPOSAL_CANCELLED));

        // Then: wallet-postgres payout transitions PENDING → REFUNDED (§8.6 step 7)
        await().atMost(5, SECONDS)
               .until(() -> lastSaved.get() != null
                         && lastSaved.get().getStatus() == PayoutStatus.REFUNDED);

        Payout refunded = lastSaved.get();
        assertThat(refunded.getStatus()).isEqualTo(PayoutStatus.REFUNDED);
        assertThat(refunded.getTransactionDetails())
                .containsKey("refundReason")
                .containsKey("refundedAt")
                .containsKey("refundAmount");
        assertThat(auditedActions).contains(PayoutAuditService.REFUNDED);

        // payment.refunded published (§8.6 step 8) — proposal-service consumes it → Proposal=REFUNDED
        await().atMost(5, SECONDS)
               .until(() -> publishedEvent.get() instanceof PaymentRefundedEvent);
        PaymentRefundedEvent refundedEvt = (PaymentRefundedEvent) publishedEvent.get();
        assertThat(refundedEvt.proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(refundedEvt.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(refundedEvt.refundAmount()).isEqualByComparingTo(BigDecimal.valueOf(AGREED_AMOUNT));
    }

    /**
     * Scenario B compensation — FAILED payout branch: gateway rejected → Payout=FAILED →
     * {@code proposal.cancelled} arrives → {@code findRefundCandidateByProposalId} returns empty
     * (SQL: {@code status IN ('PENDING','COMPLETED')} excludes FAILED) → compensation ignored.
     *
     * <p>This is correct behaviour: a FAILED payout means no money was transferred to the
     * freelancer, so there is nothing to refund. No {@code payment.refunded} published.
     * Contract termination and user-stat reversal still happen in their respective services via
     * the {@code proposal.cancelled} event — those are out of wallet-service scope.
     */
    @Test
    void scenarioB_failedPayout_compensationIgnored_noRefundPublished() throws Exception {
        // Given: FAILED payout (gateway rejected S5-F4)
        Payout failedPayout = pendingPayout();
        failedPayout.setStatus(PayoutStatus.FAILED);
        failedPayout.getTransactionDetails().put("gatewayResponse", "rejected");
        stubPendingPayout = failedPayout;

        // When: proposal.cancelled arrives — findRefundCandidateByProposalId excludes FAILED
        ProposalCancelledEvent cancelledEvent = new ProposalCancelledEvent(
                PROPOSAL_ID, JOB_ID, FREELANCER_ID, "simulated gateway failure");
        consumer.onProposalEvent(messageFor(cancelledEvent, SagaTopics.PROPOSAL_CANCELLED));

        // Then: no payout update, no payment.refunded — no money was transferred
        await().atMost(5, SECONDS).until(() -> lastSaved.get() == null);
        assertThat(publishedEvent.get()).isNull();
        assertThat(auditedActions).isEmpty();
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
    // Scenario C — Pre-check failure at S5-F4 level (wallet-service scope)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Scenario C at S5-F4 level: contract-service returns 404 for the given contractId →
     * 404 returned synchronously; no payout created; no saga event published.
     *
     * <p>§8.6 Scenario C describes the analogous failure at S3-F4 level (proposal-service's
     * {@code PUT /api/proposals/{id}/complete} finding no active contract via Feign → 400).
     * That S3-F4 pre-check is proposal-service's deliverable. This test covers wallet-service's
     * S5-F4 pre-check: {@code walletReadClientService.getContract()} returning 404 causes
     * {@code processContractPayout} to abort before creating any payout or publishing any event.
     *
     * <p>Per §8.6: synchronous assertions MUST NOT use {@code await()}.
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
