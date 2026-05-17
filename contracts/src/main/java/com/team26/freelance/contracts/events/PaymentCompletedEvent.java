package com.team26.freelance.contracts.events;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        Long payoutId,
        Long proposalId,
        Long contractId,
        BigDecimal amount) {
}
