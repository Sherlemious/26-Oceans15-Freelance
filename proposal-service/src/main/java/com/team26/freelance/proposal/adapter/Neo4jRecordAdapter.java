package com.team26.freelance.proposal.adapter;

import com.team26.freelance.proposal.dto.JobRecommendationDTO;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public JobRecommendationDTO adapt(Record record) {
        Long jobId = record.get("jobId").asLong();
        long score = record.get("score").asLong();
        // Title/category are enriched from PostgreSQL per spec.
        return new JobRecommendationDTO(jobId, "", "", score);
    }
}
