package com.team26.freelance.contract.messaging;

import com.team26.freelance.contract.config.ContractEventConfig;
import com.team26.freelance.contract.model.ContractStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ContractSagaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContractSagaPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ContractSagaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishContractCreated(Long contractId, Long proposalId, Long jobId, Long freelancerId, Double agreedAmount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractId", contractId);
        payload.put("proposalId", proposalId);
        payload.put("jobId", jobId);
        payload.put("freelancerId", freelancerId);
        payload.put("agreedAmount", agreedAmount);

        log.info("Publishing contract.created event: {}", payload);
        rabbitTemplate.convertAndSend(ContractEventConfig.EXCHANGE_NAME, "contract.created", payload);
    }

    public void publishContractStatusChanged(Long contractId, ContractStatus oldStatus, ContractStatus newStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractId", contractId);
        payload.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
        payload.put("newStatus", newStatus != null ? newStatus.name() : null);

        log.info("Publishing contract.status-changed event: {}", payload);
        rabbitTemplate.convertAndSend(ContractEventConfig.EXCHANGE_NAME, "contract.status-changed", payload);
    }

    public void publishContractCancelled(Long contractId, Long proposalId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractId", contractId);
        payload.put("proposalId", proposalId);

        log.info("Publishing contract.cancelled event: {}", payload);
        rabbitTemplate.convertAndSend(ContractEventConfig.EXCHANGE_NAME, "contract.cancelled", payload);
    }
}
