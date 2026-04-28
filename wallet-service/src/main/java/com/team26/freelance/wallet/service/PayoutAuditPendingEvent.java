package com.team26.freelance.wallet.service;

import java.time.LocalDateTime;

public class PayoutAuditPendingEvent {

    private final Long payoutId;
    private final String eventType;
    private final Double amountReturned;
    private final String strategyApplied;
    private final String reason;
    private final LocalDateTime timestamp;

    public PayoutAuditPendingEvent(Long payoutId, String eventType, Double amountReturned,
                                   String strategyApplied, String reason, LocalDateTime timestamp) {
        this.payoutId = payoutId;
        this.eventType = eventType;
        this.amountReturned = amountReturned;
        this.strategyApplied = strategyApplied;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public Long getPayoutId() { return payoutId; }
    public String getEventType() { return eventType; }
    public Double getAmountReturned() { return amountReturned; }
    public String getStrategyApplied() { return strategyApplied; }
    public String getReason() { return reason; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
