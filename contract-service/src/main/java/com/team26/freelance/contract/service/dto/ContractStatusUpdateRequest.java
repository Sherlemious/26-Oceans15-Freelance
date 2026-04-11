package com.team26.freelance.contract.service.dto;

import java.io.Serializable;

import com.team26.freelance.contract.model.ContractStatus;

public record ContractStatusUpdateRequest(Long contractId, ContractStatus status) implements Serializable
{

}