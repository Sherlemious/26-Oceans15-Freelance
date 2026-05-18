package com.team26.freelance.proposal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.team26.freelance")
@EnableFeignClients(basePackages = "com.team26.freelance.contracts.feign")
public class ProposalApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProposalApplication.class, args);
    }
}
