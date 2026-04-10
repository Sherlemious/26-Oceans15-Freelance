package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.FreelancerPayoutSummaryDTO;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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

    public FreelancerPayoutSummaryDTO getFreelancerPayoutSummary(Long freelancerId) {
        if (payoutRepository.countUsersById(freelancerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        List<Object[]> rows = payoutRepository.getPayoutSummaryByFreelancer(freelancerId);
        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
        long totalPayouts = 0;
        double totalAmount = 0.0;
        for (Object[] row : rows) {
            String method = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double sum = ((Number) row[2]).doubleValue();
            methodBreakdown.put(method, sum);
            totalPayouts += count;
            totalAmount += sum;
        }
        return new FreelancerPayoutSummaryDTO(freelancerId, totalPayouts, totalAmount, methodBreakdown);
    }
}
