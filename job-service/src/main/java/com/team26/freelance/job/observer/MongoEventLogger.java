package com.team26.freelance.job.observer;

import com.team26.freelance.common.event.JobEvent;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

            Map<String, Object> params = new HashMap<>();
            params.put("id", UUID.randomUUID().toString());
            params.put("jobId", jobId);
            params.put("action", eventType);
            params.put("timestamp", java.time.LocalDateTime.now().toString());
            params.put("details", details);

            jobEventRepository.save(new JobEvent(params));
        } catch (Exception e) {
            log.warn("Failed to log job event to MongoDB: {}", e.getMessage());
        }
    }
}