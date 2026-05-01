package com.team26.freelance.wallet.model;

public enum PayoutAuditEventType {
    CREATED,
    COMPLETED,
    FAILED,
    REFUNDED,
    REFUND_DENIED,
    ANALYTICS_VIEWED;

    public boolean invalidatesMethodBreakdownCache() {
        return this == CREATED || this == COMPLETED || this == FAILED || this == REFUNDED;
    }
}
