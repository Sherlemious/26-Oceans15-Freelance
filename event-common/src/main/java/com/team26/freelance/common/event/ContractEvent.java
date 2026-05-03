package com.team26.freelance.common.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "contract_events")
public class ContractEvent implements MongoEvent {
    @Id
    private String id;

    private Long contractId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public ContractEvent() {}

    public ContractEvent(Map<String, Object> params) {
        this.id = EventParams.stringValue(params, "id");
        this.contractId = EventParams.longValue(params, "contractId");
        this.action = EventParams.stringValue(params, "action");
        this.timestamp = EventParams.timestampValue(params);
        this.details = EventParams.detailsValue(params);
    }

    @Override
    public String getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public Map<String, Object> getDetails() {
        return details;
    }
}
