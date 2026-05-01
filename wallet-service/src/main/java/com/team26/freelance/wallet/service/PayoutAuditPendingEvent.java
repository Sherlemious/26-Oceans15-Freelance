package com.team26.freelance.wallet.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class PayoutAuditPendingEvent {

    private final Long payoutId;
    private final String action;
    private final String method;
    private final Double amount;
    private final Map<String, Object> details;
    private final LocalDateTime timestamp;

    public PayoutAuditPendingEvent(Long payoutId,
                                   String action,
                                   String method,
                                   Double amount,
                                   Map<String, Object> details,
                                   LocalDateTime timestamp) {
        this.payoutId = payoutId;
        this.action = action;
        this.method = method;
        this.amount = amount;
        this.details = details == null ? new HashMap<>() : details;
        this.timestamp = timestamp;
    }

    public Long getPayoutId() {
        return payoutId;
    }

    public String getAction() {
        return action;
    }

    public String getMethod() {
        return method;
    }

    public Double getAmount() {
        return amount;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}