package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import com.team26.freelance.wallet.repository.PromoCodeRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PromoCodeRepository promoCodeRepository;

    public PayoutService(PayoutRepository payoutRepository, PromoCodeRepository promoCodeRepository) {
        this.payoutRepository = payoutRepository;
        this.promoCodeRepository = promoCodeRepository;
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

    public List<PromoCodeUsageDTO> getTopUsedPromoCodes(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be positive");
        }

        List<Object[]> rows = promoCodeRepository.findTopUsedPromoCodes(limit);
        List<PromoCodeUsageDTO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : rows) {
            PromoCodeUsageDTO dto = new PromoCodeUsageDTO();

            dto.setPromoCodeId(((Number) row[0]).longValue());
            dto.setCode((String) row[1]);
            dto.setDiscountType((String) row[2]);
            dto.setDiscountValue(((Number) row[3]).doubleValue());
            dto.setTimesUsed(((Number) row[4]).intValue());
            dto.setTotalDiscountGiven(row[5] == null ? 0.0 : ((Number) row[5]).doubleValue());
            dto.setActive((Boolean) row[6]);

            LocalDateTime expiryDate;
            if (row[7] instanceof LocalDateTime localDateTime) {
                expiryDate = localDateTime;
            } else if (row[7] instanceof Timestamp timestamp) {
                expiryDate = timestamp.toLocalDateTime();
            } else {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected expiry date type returned from database"
                );
            }

            dto.setExpired(expiryDate.isBefore(now));

            result.add(dto);
        }

        return result;
    }
}
