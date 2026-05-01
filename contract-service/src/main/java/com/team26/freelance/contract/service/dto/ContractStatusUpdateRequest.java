package com.team26.freelance.contract.service.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractStatusUpdateRequest implements Serializable {
    private Long id;
    private Long contractId;
    private String status;

    public ContractStatusUpdateRequest() {
    }

    public ContractStatusUpdateRequest(Long id, Long contractId, String status) {
        this.id = id;
        this.contractId = contractId;
        this.status = status;
    }

    public Long getId() {
        return id != null ? id : contractId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}