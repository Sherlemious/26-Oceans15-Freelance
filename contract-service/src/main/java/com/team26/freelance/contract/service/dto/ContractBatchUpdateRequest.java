package com.team26.freelance.contract.service.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.team26.freelance.contract.model.ContractStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractBatchUpdateRequest implements Serializable {
    private List<Long> ids;
    private List<Long> contractIds;
    private String status;

    public ContractBatchUpdateRequest() {
    }

    public ContractBatchUpdateRequest(List<Long> ids, List<Long> contractIds, String status) {
        this.ids = ids;
        this.contractIds = contractIds;
        this.status = status;
    }

    public List<Long> getIds() {
        return ids != null ? ids : contractIds;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public List<Long> getContractIds() {
        return getIds();
    }

    public void setContractIds(List<Long> contractIds) {
        this.contractIds = contractIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

