package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class PayoutService {

    private final PayoutRepository payoutRepository;

    public PayoutService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public List<Payout> getAllPayouts() {
        return payoutRepository.findAll();
    }

    public Payout getPayoutById(Long id) {
        return payoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
    }

    public Payout createPayout(Payout payout) {
        return payoutRepository.save(payout);
    }

    public Payout updatePayout(Long id, Payout updated) {
        Payout existing = getPayoutById(id);
        existing.setContractId(updated.getContractId());
        existing.setFreelancerId(updated.getFreelancerId());
        existing.setAmount(updated.getAmount());
        existing.setMethod(updated.getMethod());
        existing.setStatus(updated.getStatus());
        existing.setTransactionDetails(updated.getTransactionDetails());
        return payoutRepository.save(existing);
    }

    public void deletePayout(Long id) {
        getPayoutById(id);
        payoutRepository.deleteById(id);
    }

    public List<Payout> searchByStatusAndDateRange(String status, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        return payoutRepository.searchByStatusAndDateRange(status, start, end);
    }

    @Transactional
    public Payout processRefund(Long id, String reason) {
        Payout payout = payoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));
        if (payout.getStatus() != PayoutStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED payouts can be refunded");
        }
        payout.setStatus(PayoutStatus.REFUNDED);
        payout.getTransactionDetails().put("refundReason", reason);
        payout.getTransactionDetails().put("refundedAt", LocalDateTime.now().toString());
        return payoutRepository.save(payout);
    }

    @Transactional
    public Payout retryFailedPayout(Long id) {
        Payout payout = payoutRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));

        if (payout.getStatus() != PayoutStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED payouts can be retried");
        }

        payout.setStatus(PayoutStatus.COMPLETED);

        Map<String, Object> transactionDetails = payout.getTransactionDetails();
        if (transactionDetails == null) {
            transactionDetails = new HashMap<>();
        }

        int retryAttempt = 0;
        Object retryValue = transactionDetails.get("retryAttempt");

        if (retryValue instanceof Number number) {
            retryAttempt = number.intValue();
        } else if (retryValue instanceof String str) {
            try {
                retryAttempt = Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
                retryAttempt = 0;
            }
        }

        transactionDetails.put("retryAttempt", retryAttempt + 1);
        transactionDetails.put("gatewayResponse", "approved");

        payout.setTransactionDetails(transactionDetails);

        return payoutRepository.save(payout);
    }
}
