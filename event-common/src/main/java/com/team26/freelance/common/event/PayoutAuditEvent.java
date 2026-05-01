package com.team26.freelance.common.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "payout_audit_trail")
public class PayoutAuditEvent implements MongoEvent {
    @Id
    private String id;

    private Long payoutId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details;

    public PayoutAuditEvent() {}

    public PayoutAuditEvent(Map<String, Object> params) {
        this.id = EventParams.stringValue(params, "id");
        this.payoutId = EventParams.longValue(params, "payoutId");
        this.action = EventParams.stringValue(params, "action");
        this.timestamp = EventParams.timestampValue(params);
        this.method = EventParams.stringValue(params, "method");
        this.amount = EventParams.doubleValue(params, "amount");
        this.details = EventParams.detailsValue(params);
    }

    @Override
    public String getId() {
        return id;
    }

    public Long getPayoutId() {
        return payoutId;
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getMethod() {
        return method;
    }

    public Double getAmount() {
        return amount;
    }

    @Override
    public Map<String, Object> getDetails() {
        return details;
    }
}
