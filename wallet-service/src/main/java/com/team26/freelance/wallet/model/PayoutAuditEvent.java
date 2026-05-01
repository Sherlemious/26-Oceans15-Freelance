package com.team26.freelance.wallet.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "payout_audit_trail")
public class PayoutAuditEvent {

    @Id
    private String id;
    private Long payoutId;
    private String eventType;
    private String payoutMethod;
    private Double amount;
    private Double amountReturned;
    private String strategyApplied;
    private String reason;
    private LocalDateTime timestamp;

    public PayoutAuditEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public Double getAmountReturned() { return amountReturned; }
    public void setAmountReturned(Double amountReturned) { this.amountReturned = amountReturned; }
    public String getStrategyApplied() { return strategyApplied; }
    public void setStrategyApplied(String strategyApplied) { this.strategyApplied = strategyApplied; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
