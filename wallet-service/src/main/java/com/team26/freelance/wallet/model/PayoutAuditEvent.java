package com.team26.freelance.wallet.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "payout_audit_trail")
public class PayoutAuditEvent implements MongoEvent {

    @Id
    private String id;
    private Long payoutId;
    private String action;
    private String eventType;
    private String payoutMethod;
    private Double amount;
    private Double amountReturned;
    private String strategyApplied;
    private String reason;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public PayoutAuditEvent() {}

    public PayoutAuditEvent(Map<String, Object> params) {
        Object rawId = params.get("payoutId");
        this.payoutId = rawId instanceof Number n ? n.longValue() : null;
        this.action = (String) params.get("action");
        this.eventType = params.containsKey("eventType") ? (String) params.get("eventType") : this.action;
        this.payoutMethod = (String) params.get("method");
        Object rawAmount = params.get("amount");
        this.amount = rawAmount instanceof Number n ? n.doubleValue() : null;
        this.reason = (String) params.get("reason");
        Object ts = params.get("timestamp");
        this.timestamp = ts instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
        Object det = params.get("details");
        this.details = det instanceof Map<?, ?> m ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
    }

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }

    public String getMethod() { return payoutMethod; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getAmountReturned() { return amountReturned; }
    public void setAmountReturned(Double amountReturned) { this.amountReturned = amountReturned; }

    public String getStrategyApplied() { return strategyApplied; }
    public void setStrategyApplied(String strategyApplied) { this.strategyApplied = strategyApplied; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
