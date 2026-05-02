package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;

public class FullPayoutReversalStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Payout payout, RefundRequest request) {
        return new RefundResult(
                payout.getAmount(),
                "FULL_REVERSAL",
                "FullPayoutReversalStrategy",
                true
        );
    }
}
