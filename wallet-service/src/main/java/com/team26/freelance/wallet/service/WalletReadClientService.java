package com.team26.freelance.wallet.service;

import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.JobServiceClient;
import com.team26.freelance.contracts.feign.UserServiceClient;
import com.team26.freelance.contracts.dto.ContractDTO;
import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletReadClientService {

    private static final Logger log = LoggerFactory.getLogger(WalletReadClientService.class);

    private final UserServiceClient userServiceClient;
    private final ContractServiceClient contractServiceClient;
    private final JobServiceClient jobServiceClient;

    public WalletReadClientService(UserServiceClient userServiceClient,
                                   ContractServiceClient contractServiceClient,
                                   JobServiceClient jobServiceClient) {
        this.userServiceClient = userServiceClient;
        this.contractServiceClient = contractServiceClient;
        this.jobServiceClient = jobServiceClient;
    }

    public UserDTO getUser(Long userId) {
        log.info("Calling UserServiceClient.getUser with args={}", userId);
        try {
            UserDTO user = userServiceClient.getUser(userId);
            log.info("UserServiceClient.getUser returned successfully");
            return user;
        } catch (FeignException.NotFound ex) {
            log.warn("Feign call to user-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (FeignException ex) {
            log.warn("Feign call to user-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable");
        }
    }

    public ContractDTO getContract(Long contractId) {
        log.info("Calling ContractServiceClient.getContract with args={}", contractId);
        try {
            ContractDTO contract = contractServiceClient.getContract(contractId);
            log.info("ContractServiceClient.getContract returned successfully");
            return contract;
        } catch (FeignException.NotFound ex) {
            log.warn("Feign call to contract-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found: " + contractId);
        } catch (FeignException ex) {
            log.warn("Feign call to contract-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Contract service temporarily unavailable");
        }
    }

    public JobDTO getJob(Long jobId) {
        log.info("Calling JobServiceClient.getJob with args={}", jobId);
        try {
            JobDTO job = jobServiceClient.getJob(jobId);
            log.info("JobServiceClient.getJob returned successfully");
            return job;
        } catch (FeignException.NotFound ex) {
            log.warn("Feign call to job-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        } catch (FeignException ex) {
            log.warn("Feign call to job-service failed: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Job service temporarily unavailable");
        }
    }
}
