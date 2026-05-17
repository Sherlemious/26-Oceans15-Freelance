package com.team26.freelance.contracts.events;

import java.math.BigDecimal;

public record PaymentRefundedEvent(
        Long payoutId,
        Long proposalId,
        Long contractId,
        BigDecimal refundAmount) {
}
