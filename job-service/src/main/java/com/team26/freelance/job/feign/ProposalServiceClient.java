package com.team26.freelance.job.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "proposal-service", url = "${feign.proposal-service.url}")
public interface ProposalServiceClient {
    @GetMapping("/api/proposals/job/{jobId}/summary")
    ProposalSummaryResponse getJobProposalSummary(
            @PathVariable("jobId") Long jobId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate);

    @PutMapping("/api/proposals/job/{jobId}/reject-submitted")
    void rejectSubmittedProposalsForJob(@PathVariable("jobId") Long jobId);

    @GetMapping("/api/proposals/job/{jobId}/count")
    Long getProposalCountForJob(@PathVariable("jobId") Long jobId);
}