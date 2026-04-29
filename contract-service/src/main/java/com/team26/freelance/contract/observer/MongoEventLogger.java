package com.team26.freelance.contract.observer;

import com.team26.freelance.contract.model.mongo.ContractEvent;
import com.team26.freelance.contract.repository.mongo.ContractEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {
    private static final Logger logger = LoggerFactory.getLogger(MongoEventLogger.class);

    private final ContractEventRepository contractEventRepository;

    public MongoEventLogger(ContractEventRepository contractEventRepository, ContractEventSubject contractEventSubject) {
        this.contractEventRepository = contractEventRepository;
        contractEventSubject.register(this);
    }

    @Override
    public void onEvent(Long contractId, String type, Map<String, Object> details) {
        try {
            contractEventRepository.save(new ContractEvent(contractId, type, type, LocalDateTime.now(), details));
        } catch (RuntimeException e) {
            logger.warn("Unable to write contract event", e);
        }
    }
}
