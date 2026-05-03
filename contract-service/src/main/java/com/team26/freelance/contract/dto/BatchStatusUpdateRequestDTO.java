package com.team26.freelance.contract.dto;

import com.team26.freelance.contract.model.ContractStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class BatchStatusUpdateRequestDTO {

    @NotNull
    @Min(1)
    private Long contractId;

    @NotNull
    private ContractStatus status;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }
}