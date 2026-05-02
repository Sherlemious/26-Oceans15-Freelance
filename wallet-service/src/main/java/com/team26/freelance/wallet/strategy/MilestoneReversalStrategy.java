package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;

public class MilestoneReversalStrategy implements RefundStrategy {

    private final double unresolvedMilestoneAmount;

    public MilestoneReversalStrategy(double unresolvedMilestoneAmount) {
        this.unresolvedMilestoneAmount = unresolvedMilestoneAmount;
    }

    @Override
    public RefundResult calculateRefund(Payout payout, RefundRequest request) {
        return new RefundResult(
                unresolvedMilestoneAmount,
                "MILESTONE_REVERSAL",
                "MilestoneReversalStrategy",
                true
        );
    }
}
