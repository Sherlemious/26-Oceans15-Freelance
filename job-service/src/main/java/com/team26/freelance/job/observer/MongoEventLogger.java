package com.team26.freelance.job.observer;

import com.team26.freelance.job.model.mongo.EventFactory;
import com.team26.freelance.job.model.mongo.EventType;
import com.team26.freelance.job.model.mongo.JobEvent;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
            Object rawId = details.get("jobId");
            Long jobId = rawId instanceof Number n ? n.longValue() : null;
            Map<String, Object> params = new HashMap<>(details);
            params.put("action", eventType);
            params.put("jobId", jobId);
            params.put("details", new HashMap<>(details));
            jobEventRepository.save((JobEvent) EventFactory.createEvent(EventType.JOB, params));
        } catch (Exception e) {
            log.warn("Failed to log job event to MongoDB: {}", e.getMessage());
        }
    }
}