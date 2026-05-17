package com.team26.freelance.job.dto;

import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.job.model.Job;

public class JobDTOMapper {

    private JobDTOMapper() {}

    public static JobDTO toJobDTO(Job job) {
        JobDTO dto = new JobDTO();
        dto.setId(job.getId());
        dto.setClientId(job.getClientId());
        dto.setTitle(job.getTitle());
        dto.setDescription(job.getDescription());
        dto.setCategory(job.getCategory() != null ? job.getCategory().name() : null);
        dto.setStatus(job.getStatus() != null ? job.getStatus().name() : null);
        dto.setBudgetMin(job.getBudgetMin());
        dto.setBudgetMax(job.getBudgetMax());
        dto.setRating(job.getRating());
        dto.setTotalRatings(job.getTotalRatings());
        dto.setRequirements(job.getRequirements());
        dto.setCreatedAt(job.getCreatedAt());
        return dto;
    }
}
