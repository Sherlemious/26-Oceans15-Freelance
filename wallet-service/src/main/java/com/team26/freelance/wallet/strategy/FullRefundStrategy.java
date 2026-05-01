package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutPromo;

public class FullRefundStrategy implements PayoutReversalStrategy {

    @Override
    public PayoutReversalResult execute(Payout payout) {
        double totalDiscount = payout.getPayoutPromos().stream()
                .mapToDouble(PayoutPromo::getDiscountApplied)
                .sum();
        double netAmount = Math.max(0, payout.getAmount() - totalDiscount);
        return new PayoutReversalResult(
                netAmount,
                "FULL_REFUND",
                "Payout is less than 24 hours old — full refund applied",
                true
        );
    }
}
