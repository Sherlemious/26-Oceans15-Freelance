package com.team26.freelance.job.repository.elastic;

import com.team26.freelance.job.model.elastic.JobSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobSearchRepository extends ElasticsearchRepository<JobSearchDocument, Long> {

}
