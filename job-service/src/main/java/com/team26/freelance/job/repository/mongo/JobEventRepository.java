package com.team26.freelance.job.repository.mongo;

import com.team26.freelance.common.event.JobEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobEventRepository extends MongoRepository<JobEvent, String> {
}