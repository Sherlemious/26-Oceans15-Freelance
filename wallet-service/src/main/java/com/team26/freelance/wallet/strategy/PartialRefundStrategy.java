package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;

public class PartialRefundStrategy implements PayoutReversalStrategy {

    @Override
    public PayoutReversalResult execute(Payout payout) {
        double platformFee = resolvePlatformFee(payout);
        double amountReturned = Math.max(0, payout.getAmount() - platformFee);
        return new PayoutReversalResult(
                amountReturned,
                "PARTIAL_REFUND",
                String.format("Payout is 24h–7 days old — platform fee of %.2f retained", platformFee),
                true
        );
    }

    private double resolvePlatformFee(Payout payout) {
        var details = payout.getTransactionDetails();
        if (details != null) {
            Object stored = details.get("platformFee");
            if (stored instanceof Number number) {
                return number.doubleValue();
            }
        }
        return payout.getAmount() * 0.10;
    }
}
