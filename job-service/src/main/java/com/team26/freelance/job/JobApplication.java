package com.team26.freelance.job;

import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.ProposalServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.team26.freelance")
@EnableFeignClients(clients = {ContractServiceClient.class, ProposalServiceClient.class})
public class JobApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
