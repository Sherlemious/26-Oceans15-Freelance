package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;

public interface RefundStrategy {
    RefundResult calculateRefund(Payout payout, RefundRequest request);
}
