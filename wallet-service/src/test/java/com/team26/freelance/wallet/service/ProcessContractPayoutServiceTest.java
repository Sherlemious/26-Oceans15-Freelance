package com.team26.freelance.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
import com.team26.freelance.wallet.dto.ProcessContractPayoutRequest;
import com.team26.freelance.wallet.messaging.PaymentEventPublisher;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProcessContractPayoutServiceTest {

  private PayoutRepository payoutRepository;
  private WalletReadClientService walletReadClientService;
  private PayoutAuditService payoutAuditService;
  private PaymentEventPublisher paymentEventPublisher;
  private PayoutService payoutService;

  @BeforeEach
  void setUp() {
    payoutRepository = org.mockito.Mockito.mock(PayoutRepository.class);
    walletReadClientService = org.mockito.Mockito.mock(WalletReadClientService.class);
    payoutAuditService = org.mockito.Mockito.mock(PayoutAuditService.class);
    paymentEventPublisher = org.mockito.Mockito.mock(PaymentEventPublisher.class);
    payoutService = new PayoutService(
        payoutRepository,
        null,
        null,
        payoutAuditService,
        null,
        null,
        null,
        null,
        walletReadClientService,
        paymentEventPublisher,
        new FreelancerPayoutSummaryObjectArrayAdapter(),
        new PromoCodeUsageObjectArrayAdapter());
  }

  @Test
  void processContractPayoutCompletesPendingAndPublishesCompletedOnce() {
    Long contractId = 5L;
    Payout pending = pendingPayout(contractId, 44L, 1500.0);
    pending.setId(10L);
    when(walletReadClientService.getContract(contractId)).thenReturn(contract(contractId, 44L, 77L, 1500.0, "COMPLETED"));
    when(payoutRepository.findFirstByContractIdAndStatusInOrderByCreatedAtAsc(
        eq(contractId), any(List.class))).thenReturn(Optional.of(pending));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Payout result = payoutService.processContractPayout(contractId, new ProcessContractPayoutRequest(), false);

    assertThat(result.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
    verify(paymentEventPublisher, times(1)).publishPaymentCompleted(any());
    verify(paymentEventPublisher, never()).publishPaymentFailed(any());
  }

  @Test
  void processContractPayoutFailsPendingAndPublishesFailed() {
    Long contractId = 6L;
    Payout pending = pendingPayout(contractId, 45L, 1800.0);
    pending.setId(11L);
    when(walletReadClientService.getContract(contractId)).thenReturn(contract(contractId, 45L, 88L, 1800.0, "COMPLETED"));
    when(payoutRepository.findFirstByContractIdAndStatusInOrderByCreatedAtAsc(
        eq(contractId), any(List.class))).thenReturn(Optional.of(pending));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Payout result = payoutService.processContractPayout(contractId, new ProcessContractPayoutRequest(), true);

    assertThat(result.getStatus()).isEqualTo(PayoutStatus.FAILED);
    verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
    verify(paymentEventPublisher, times(1)).publishPaymentFailed(any());
  }

  @Test
  void processContractPayoutReturnsExistingCompletedWithoutPublishingDuplicateEvents() {
    Long contractId = 7L;
    Payout completed = pendingPayout(contractId, 46L, 1200.0);
    completed.setId(12L);
    completed.setStatus(PayoutStatus.COMPLETED);
    when(walletReadClientService.getContract(contractId)).thenReturn(contract(contractId, 46L, 99L, 1200.0, "COMPLETED"));
    when(payoutRepository.findFirstByContractIdAndStatusInOrderByCreatedAtAsc(
        eq(contractId), any(List.class))).thenReturn(Optional.of(completed));

    Payout result = payoutService.processContractPayout(contractId, new ProcessContractPayoutRequest(), false);

    assertThat(result).isSameAs(completed);
    verify(payoutRepository, never()).save(any(Payout.class));
    verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
    verify(paymentEventPublisher, never()).publishPaymentFailed(any());
  }

  @Test
  void processContractPayoutRetriesNotFoundFromContractReadBeforeSuccess() {
    Long contractId = 8L;
    Payout pending = pendingPayout(contractId, 47L, 1300.0);
    pending.setId(13L);
    when(walletReadClientService.getContract(contractId))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found: 8"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found: 8"))
        .thenReturn(contract(contractId, 47L, 101L, 1300.0, "COMPLETED"));
    when(payoutRepository.findFirstByContractIdAndStatusInOrderByCreatedAtAsc(
        eq(contractId), any(List.class))).thenReturn(Optional.of(pending));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));

    payoutService.processContractPayout(contractId, new ProcessContractPayoutRequest(), false);

    verify(walletReadClientService, times(3)).getContract(contractId);
  }

  @Test
  void processContractPayoutReturnsNotFoundWhenNoPendingOrFinalPayoutExists() {
    Long contractId = 9L;
    when(walletReadClientService.getContract(contractId)).thenReturn(contract(contractId, 48L, 102L, 500.0, "COMPLETED"));
    when(payoutRepository.findFirstByContractIdAndStatusInOrderByCreatedAtAsc(
        eq(contractId), any(List.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> payoutService.processContractPayout(contractId, new ProcessContractPayoutRequest(), false))
        .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getReason()).isEqualTo("No payout exists for contract");
        });
  }

  private static Payout pendingPayout(Long contractId, Long freelancerId, Double amount) {
    Payout payout = new Payout();
    payout.setContractId(contractId);
    payout.setFreelancerId(freelancerId);
    payout.setAmount(amount);
    payout.setMethod(PayoutMethod.BANK_TRANSFER);
    payout.setStatus(PayoutStatus.PENDING);
    payout.setTransactionDetails(new HashMap<>());
    return payout;
  }

  private static ContractDTO contract(Long contractId,
                                      Long freelancerId,
                                      Long proposalId,
                                      Double agreedAmount,
                                      String status) {
    ContractDTO contract = new ContractDTO();
    contract.setId(contractId);
    contract.setFreelancerId(freelancerId);
    contract.setProposalId(proposalId);
    contract.setAgreedAmount(agreedAmount);
    contract.setStatus(status);
    return contract;
  }
}
