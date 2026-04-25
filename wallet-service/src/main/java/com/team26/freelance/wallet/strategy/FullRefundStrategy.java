package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;

public class FullRefundStrategy implements PayoutReversalStrategy {

    @Override
    public PayoutReversalResult execute(Payout payout) {
        return new PayoutReversalResult(
                payout.getAmount(),
                "FULL_REFUND",
                "Payout is less than 24 hours old — full refund applied",
                true
        );
    }
}
