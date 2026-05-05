package com.team26.freelance.job.adapter;

import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

import com.team26.freelance.job.dto.JobSearchResultDTO;
import com.team26.freelance.job.model.elastic.JobSearchDocument;

@Component
public class ElasticsearchHitAdapter {

    public JobSearchResultDTO adapt(SearchHit<JobSearchDocument> hit) {
        JobSearchDocument src = hit == null ? null : hit.getContent();
        if (src == null) {
            return JobSearchResultDTO.builder()
                    .score(hit == null ? 0.0f : hit.getScore())
                    .build();
        }

        Double budgetMin = src.getBudgetMin() == null ? 0.0 : src.getBudgetMin();
        Double budgetMax = src.getBudgetMax() == null ? 0.0 : src.getBudgetMax();

        return JobSearchResultDTO.builder()
                .id(src.getId())
                .title(src.getTitle())
                .description(src.getDescription())
                .category(src.getCategory())
                .budgetMin(budgetMin)
                .budgetMax(budgetMax)
                .score(hit.getScore())
                .build();
    }

}
