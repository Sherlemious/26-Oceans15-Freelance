package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.model.Payout;

public interface PayoutReversalStrategy {
    PayoutReversalResult execute(Payout payout);
}
