package com.team26.freelance.contract.service;

import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.feign.JobServiceClient;
import com.team26.freelance.contracts.feign.UserServiceClient;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContractReadClientService {

    private static final Logger log = LoggerFactory.getLogger(ContractReadClientService.class);

    private final UserServiceClient userServiceClient;
    private final JobServiceClient jobServiceClient;

    public ContractReadClientService(UserServiceClient userServiceClient, JobServiceClient jobServiceClient) {
        this.userServiceClient = userServiceClient;
        this.jobServiceClient = jobServiceClient;
    }

    public UserDTO getUser(Long userId) {
        log.info("Calling UserServiceClient.getUser with args={}", userId);
        try {
            UserDTO user = userServiceClient.getUser(userId);
            log.info("UserServiceClient.getUser returned successfully");
            return user;
        } catch (FeignException.NotFound e) {
            log.warn("Feign call to user-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (FeignException e) {
            log.warn("Feign call to user-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable");
        } catch (RuntimeException e) {
            log.warn("Feign call to user-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service temporarily unavailable");
        }
    }

    public JobDTO getJob(Long jobId) {
        log.info("Calling JobServiceClient.getJob with args={}", jobId);
        try {
            JobDTO job = jobServiceClient.getJob(jobId);
            log.info("JobServiceClient.getJob returned successfully");
            return job;
        } catch (FeignException.NotFound e) {
            log.warn("Feign call to job-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        } catch (FeignException e) {
            log.warn("Feign call to job-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Job service temporarily unavailable");
        } catch (RuntimeException e) {
            log.warn("Feign call to job-service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Job service temporarily unavailable");
        }
    }
}
