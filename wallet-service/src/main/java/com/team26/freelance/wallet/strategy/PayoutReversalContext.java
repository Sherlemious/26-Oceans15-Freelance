package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class PayoutReversalContext {

    public PayoutReversalResult executeStrategy(Payout payout) {
        PayoutReversalStrategy strategy = selectStrategy(payout);
        return strategy.execute(payout);
    }

    private PayoutReversalStrategy selectStrategy(Payout payout) {
        if (payout.getStatus() != PayoutStatus.COMPLETED) {
            return new RefundDeniedStrategy();
        }
        long hoursOld = ChronoUnit.HOURS.between(payout.getCreatedAt(), LocalDateTime.now());
        if (hoursOld < 24) {
            return new FullRefundStrategy();
        } else if (hoursOld <= 7 * 24) {
            return new PartialRefundStrategy();
        } else {
            return new RefundDeniedStrategy();
        }
    }
}
