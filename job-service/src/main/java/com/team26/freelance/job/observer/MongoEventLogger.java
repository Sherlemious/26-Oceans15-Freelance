package com.team26.freelance.job.observer;

import com.team26.freelance.job.model.mongo.JobEvent;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
    private final JobEventRepository jobEventRepository;

    public MongoEventLogger(JobEventRepository jobEventRepository) {
        this.jobEventRepository = jobEventRepository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> details = (Map<String, Object>) payload;
            Long jobId = (Long) details.get("jobId");
            jobEventRepository.save(new JobEvent(jobId, eventType, details));
        } catch (Exception e) {
            log.warn("Failed to log job event to MongoDB: {}", e.getMessage());
        }
    }
}