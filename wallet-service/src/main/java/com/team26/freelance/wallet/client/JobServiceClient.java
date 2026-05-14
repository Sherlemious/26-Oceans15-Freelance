package com.team26.freelance.wallet.client;

import com.team26.freelance.wallet.client.dto.JobDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "job-service", url = "${feign.job-service.url}")
public interface JobServiceClient {

    @GetMapping("/api/jobs/{id}")
    JobDTO getJob(@PathVariable Long id);
}
