package com.team26.freelance.contract.observer;

import com.team26.freelance.common.event.ContractEvent;
import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;

import com.team26.freelance.contract.repository.mongo.ContractEventRepository;
import com.team26.freelance.contract.service.ContractCacheEvictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final ContractEventRepository contractEventRepository;
    private final ContractCacheEvictionService cacheEvictionService;
    private final EventType eventType = EventType.CONTRACT;

    public MongoEventLogger(ContractEventRepository contractEventRepository,
                            ContractCacheEvictionService cacheEvictionService) {
        this.contractEventRepository = contractEventRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("action", eventType);

            if (payload instanceof Map<?, ?> payloadMap) {
                payloadMap.forEach((key, value) -> {
                    if (key != null) {
                        details.put(key.toString(), value);
                    }
                });
            } else {
                details.put("details", Map.of("payload", payload));
            }


            MongoEvent event = EventFactory.createEvent(this.eventType, details);
            contractEventRepository.save((ContractEvent) event);
            cacheEvictionService.evictAnalyticsForObserverEvent(eventType);
        } catch (Exception e) {
            log.warn("Failed to log contract event to MongoDB: {}", e.getMessage());
        }
    }
}
