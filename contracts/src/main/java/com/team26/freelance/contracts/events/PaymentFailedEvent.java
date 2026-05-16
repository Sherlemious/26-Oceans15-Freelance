package com.team26.freelance.contracts.events;

public record PaymentFailedEvent(
        Long payoutId,
        Long proposalId,
        Long contractId,
        String reason) {
}
