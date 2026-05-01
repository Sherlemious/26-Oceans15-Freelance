package com.team26.freelance.contract.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "contract_events")
public class ContractEvent implements MongoEvent {

    @Id
    private String id;
    private Long contractId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public ContractEvent() {
    }

    public ContractEvent(Long contractId, String action, Map<String, Object> details) {
        this.contractId = contractId;
        this.action = action;
        this.details = details == null ? new HashMap<>() : new HashMap<>(details);
        this.timestamp = LocalDateTime.now();
    }

    public ContractEvent(Map<String, Object> params) {
        Object rawId = params.get("contractId");
        this.contractId = rawId instanceof Number n ? n.longValue() : null;
        this.action = (String) params.get("action");
        Object ts = params.get("timestamp");
        this.timestamp = ts instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
        Object det = params.get("details");
        this.details = det instanceof Map<?, ?> m ? new HashMap<>((Map<String, Object>) m) : new HashMap<>(params);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
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

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new HashMap<>() : new HashMap<>(details);
    }
}
