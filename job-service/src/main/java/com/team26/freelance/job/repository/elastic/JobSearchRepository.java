package com.team26.freelance.job.repository.elastic;

import com.team26.freelance.job.model.elastic.JobSearchDocument;

import java.util.List;

import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobSearchRepository extends ElasticsearchRepository<JobSearchDocument, Long> {


    /**
     * Full-text match on title + description via multi_match.
     * Filters (category, status, budget) are applied programmatically
     * in the service layer using NativeQuery for full control.
     */
    @Query("""
            {
              "multi_match": {
                "query": "?0",
                "fields": ["title", "description"],
                "type": "best_fields",
                "fuzziness": "AUTO"
              }
            }
            """)
    List<JobSearchDocument> findByTitleOrDescriptionFullText(String query);



}
