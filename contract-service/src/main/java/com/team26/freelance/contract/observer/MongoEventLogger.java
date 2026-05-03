package com.team26.freelance.contract.observer;

import com.team26.freelance.contract.model.mongo.ContractEvent;
import com.team26.freelance.contract.repository.mongo.ContractEventRepository;
import com.team26.freelance.contract.service.ContractCacheEvictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final ContractEventRepository contractEventRepository;
    private final ContractCacheEvictionService cacheEvictionService;

    public MongoEventLogger(ContractEventRepository contractEventRepository,
                            ContractCacheEvictionService cacheEvictionService) {
        this.contractEventRepository = contractEventRepository;
        this.cacheEvictionService = cacheEvictionService;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> details = toDetails(payload);
            Long contractId = toLong(details.get("contractId"));
            contractEventRepository.save(new ContractEvent(contractId, eventType, details));
            cacheEvictionService.evictAnalyticsForObserverEvent(eventType);
        } catch (Exception e) {
            log.warn("Failed to log contract event to MongoDB: {}", e.getMessage());
        }
    }

    private Map<String, Object> toDetails(Object payload) {
        if (!(payload instanceof Map<?, ?> source)) {
            return new HashMap<>();
        }
        Map<String, Object> details = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                details.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return details;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(value.toString());
    }
}
