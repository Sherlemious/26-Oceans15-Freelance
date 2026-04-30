package com.team26.freelance.wallet.dto;

public class RefundRequest {

    private String reason;
    private String reversalScope;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReversalScope() { return reversalScope; }
    public void setReversalScope(String reversalScope) { this.reversalScope = reversalScope; }
}
