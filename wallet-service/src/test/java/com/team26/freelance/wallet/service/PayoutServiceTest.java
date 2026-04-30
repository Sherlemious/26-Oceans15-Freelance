package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.ProcessContractPayoutRequest;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutAuditEventType;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutPromoRepository;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import com.team26.freelance.wallet.strategy.PayoutReversalContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private PayoutPromoRepository payoutPromoRepository;

    @Mock
    private PayoutReversalContext payoutReversalContext;

    @Mock
    private PayoutAuditService payoutAuditService;

    private PayoutService payoutService;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutService(payoutRepository, promoCodeRepository, payoutPromoRepository,
                payoutReversalContext, payoutAuditService);
    }

    @Test
    void processContractPayoutShouldCompletePendingPayoutAndStoreTransactionDetails() {
        Long contractId = 1L;
        Payout pendingPayout = buildPendingPayout(contractId);

        ProcessContractPayoutRequest request = new ProcessContractPayoutRequest();
        request.setMethod(PayoutMethod.BANK_TRANSFER);
        request.setAccountLastFour("9876");

        when(payoutRepository.findContractDataById(contractId))
                .thenReturn(List.of(new Object[]{"COMPLETED", 3000.0, 11L}));
        when(payoutRepository.existsByContractIdAndStatus(contractId, PayoutStatus.COMPLETED)).thenReturn(false);
        when(payoutRepository.findFirstByContractIdAndStatusOrderByCreatedAtAsc(contractId, PayoutStatus.PENDING))
                .thenReturn(Optional.of(pendingPayout));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payout result = payoutService.processContractPayout(contractId, request);

        assertEquals(PayoutStatus.COMPLETED, result.getStatus());
        assertEquals(PayoutMethod.BANK_TRANSFER, result.getMethod());
        assertEquals("BANK_TRANSFER", result.getTransactionDetails().get("method"));
        assertEquals("9876", result.getTransactionDetails().get("accountLastFour"));
        verify(payoutRepository).save(pendingPayout);
        verify(payoutAuditService).recordLifecycleEvent(
                pendingPayout, PayoutAuditEventType.COMPLETED, "Contract payout completed");
    }

    @Test
    void processContractPayoutShouldPreserveExistingTransactionDetails() {
        Long contractId = 4L;
        Payout pendingPayout = buildPendingPayout(contractId);
        pendingPayout.getTransactionDetails().put("createdBy", "S3-F4");

        ProcessContractPayoutRequest request = new ProcessContractPayoutRequest();
        request.setMethod(PayoutMethod.BANK_TRANSFER);
        request.setAccountLastFour("9876");

        when(payoutRepository.findContractDataById(contractId))
                .thenReturn(List.of(new Object[]{"COMPLETED", 3000.0, 11L}));
        when(payoutRepository.existsByContractIdAndStatus(contractId, PayoutStatus.COMPLETED)).thenReturn(false);
        when(payoutRepository.findFirstByContractIdAndStatusOrderByCreatedAtAsc(contractId, PayoutStatus.PENDING))
                .thenReturn(Optional.of(pendingPayout));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payout result = payoutService.processContractPayout(contractId, request);

        assertEquals("S3-F4", result.getTransactionDetails().get("createdBy"));
        assertEquals("BANK_TRANSFER", result.getTransactionDetails().get("method"));
        assertEquals("9876", result.getTransactionDetails().get("accountLastFour"));
    }

    @Test
    void processContractPayoutShouldThrowBadRequestWhenAlreadyPaid() {
        Long contractId = 2L;
        ProcessContractPayoutRequest request = new ProcessContractPayoutRequest();
        request.setMethod(PayoutMethod.PAYPAL);

        when(payoutRepository.findContractDataById(contractId))
                .thenReturn(List.of(new Object[]{"COMPLETED", 3000.0, 11L}));
        when(payoutRepository.existsByContractIdAndStatus(contractId, PayoutStatus.COMPLETED)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> payoutService.processContractPayout(contractId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("already paid"));
        verify(payoutRepository, never()).findFirstByContractIdAndStatusOrderByCreatedAtAsc(any(Long.class), any(PayoutStatus.class));
    }

    @Test
    void processContractPayoutShouldThrowBadRequestWhenContractNotCompleted() {
        Long contractId = 3L;
        ProcessContractPayoutRequest request = new ProcessContractPayoutRequest();
        request.setMethod(PayoutMethod.CRYPTO);

        when(payoutRepository.findContractDataById(contractId))
                .thenReturn(List.of(new Object[]{"ACTIVE", 3000.0, 11L}));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> payoutService.processContractPayout(contractId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("COMPLETED"));
        verify(payoutRepository, never()).existsByContractIdAndStatus(any(Long.class), any(PayoutStatus.class));
    }

    private Payout buildPendingPayout(Long contractId) {
        Payout payout = new Payout();
        payout.setContractId(contractId);
        payout.setFreelancerId(11L);
        payout.setAmount(3000.0);
        payout.setMethod(PayoutMethod.BANK_TRANSFER);
        payout.setStatus(PayoutStatus.PENDING);
        return payout;
    }
}
