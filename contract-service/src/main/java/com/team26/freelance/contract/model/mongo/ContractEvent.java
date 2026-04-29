package com.team26.freelance.contract.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "contract_events")
public class ContractEvent {
    @Id
    private String id;

    private Long contractId;
    private String type;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public ContractEvent() {
    }

    public ContractEvent(Long contractId, String type, String action, LocalDateTime timestamp,
            Map<String, Object> details) {
        this.contractId = contractId;
        this.type = type;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public Long getContractId() {
        return contractId;
    }

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
