package com.team26.freelance.user.client;

import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.exception.NotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContractClientService {

    private static final Logger log = LoggerFactory.getLogger(ContractClientService.class);

    private final ContractServiceClient contractServiceClient;

    public ContractClientService(ContractServiceClient contractServiceClient) {
        this.contractServiceClient = contractServiceClient;
    }

    public UserContractSummaryDTO getUserContractSummary(Long userId) {
        try {
            log.info("Calling ContractServiceClient.getUserContractSummary with args={}", userId);
            UserContractSummaryDTO result = contractServiceClient.getUserContractSummary(userId);
            log.info("ContractServiceClient.getUserContractSummary returned successfully");
            return result;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Contract summary not found for user " + userId);
        } catch (FeignException e) {
            log.warn("Feign call to contract-service failed: {}", e.getMessage());
            throw e;
        }
    }

    public int getActiveContractCount(Long userId) {
        try {
            log.info("Calling ContractServiceClient.getActiveContractCount with args={}", userId);
            int result = contractServiceClient.getActiveContractCount(userId);
            log.info("ContractServiceClient.getActiveContractCount returned successfully");
            return result;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("User not found in contract-service: " + userId);
        } catch (FeignException e) {
            log.warn("Feign call to contract-service failed: {}", e.getMessage());
            throw e;
        }
    }

    public long getCompletedContractCount(Long userId) {
        try {
            log.info("Calling ContractServiceClient.getCompletedContractCount with args={}", userId);
            long result = contractServiceClient.getCompletedContractCount(userId);
            log.info("ContractServiceClient.getCompletedContractCount returned successfully");
            return result;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("User not found in contract-service: " + userId);
        } catch (FeignException e) {
            log.warn("Feign call to contract-service failed: {}", e.getMessage());
            throw e;
        }
    }
}
