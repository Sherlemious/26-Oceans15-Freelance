package com.team26.freelance.proposal;

import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.JobServiceClient;
import com.team26.freelance.contracts.feign.UserServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.team26.freelance")
@EnableFeignClients(clients = {ContractServiceClient.class, JobServiceClient.class, UserServiceClient.class})
public class ProposalApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProposalApplication.class, args);
    }
}
