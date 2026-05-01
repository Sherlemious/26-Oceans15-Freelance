package com.team26.freelance.wallet.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "payout_audit_trail")
public class PayoutAuditEvent {

    @Id
    private String id;
    private Long payoutId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details = new HashMap<>();

    public PayoutAuditEvent() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getPayoutId() {
        return payoutId;
    }

    public void setPayoutId(Long payoutId) {
        this.payoutId = payoutId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getEventType() {
        return this.action;
    }

    public void setEventType(String eventType) {
        this.action = eventType;
    }

    public String getPayoutMethod() {
        return this.method;
    }

    public void setPayoutMethod(String payoutMethod) {
        this.method = payoutMethod;
    }

    public Double getAmountReturned() {
        return this.amount;
    }

    public void setAmountReturned(Double amountReturned) {
        this.amount = amountReturned;
    }

    public String getReason() {
        Object value = this.details == null ? null : this.details.get("reason");
        return value == null ? null : value.toString();
    }

    public void setReason(String reason) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put("reason", reason);
    }

    public String getStrategyApplied() {
        Object value = this.details == null ? null : this.details.get("strategyApplied");
        return value == null ? null : value.toString();
    }

    public void setStrategyApplied(String strategyApplied) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put("strategyApplied", strategyApplied);
    }
}