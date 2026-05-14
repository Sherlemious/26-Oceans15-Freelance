package com.team26.freelance.contract.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "job-service", url = "${feign.job-service.url}")
public interface JobServiceClient {
    @GetMapping("/api/jobs/{jobId}")
    Map<String, Object> getJob(@PathVariable("jobId") Long jobId);
}

