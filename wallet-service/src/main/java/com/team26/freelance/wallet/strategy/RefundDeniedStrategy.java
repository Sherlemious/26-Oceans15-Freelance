package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class RefundDeniedStrategy implements PayoutReversalStrategy {

    @Override
    public PayoutReversalResult execute(Payout payout) {
        String reason = buildDenialReason(payout);
        return new PayoutReversalResult(0.0, "REFUND_DENIED", reason, false);
    }

    private String buildDenialReason(Payout payout) {
        if (payout.getStatus() != PayoutStatus.COMPLETED) {
            return "Reversal denied: payout status is " + payout.getStatus();
        }
        long daysOld = ChronoUnit.DAYS.between(payout.getCreatedAt(), LocalDateTime.now());
        return "Reversal denied: payout is " + daysOld + " day(s) old — exceeds 7-day reversal window";
    }
}
