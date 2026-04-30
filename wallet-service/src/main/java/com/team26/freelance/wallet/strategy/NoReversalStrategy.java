package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class NoReversalStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payout payout, RefundRequest request) {
        long daysOld = ChronoUnit.DAYS.between(payout.getCreatedAt(), LocalDateTime.now());
        return new RefundResult(
                0.0,
                "reversal window expired",
                "NoReversalStrategy",
                false
        );
    }
}
