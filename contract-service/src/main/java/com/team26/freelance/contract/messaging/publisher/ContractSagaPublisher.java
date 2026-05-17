package com.team26.freelance.contract.messaging.publisher;

import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contracts.events.ContractCancelledEvent;
import com.team26.freelance.contracts.events.ContractCreatedEvent;
import com.team26.freelance.contracts.events.ContractStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ContractSagaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContractSagaPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ContractSagaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishContractCreated(Long contractId, Long proposalId, Long jobId, Long freelancerId, Double agreedAmount) {
        ContractCreatedEvent event = new ContractCreatedEvent(
                contractId,
                proposalId,
                jobId,
                freelancerId,
                agreedAmount != null ? BigDecimal.valueOf(agreedAmount) : BigDecimal.ZERO
        );

        log.info("Publishing contract.created event: {}", event);
        rabbitTemplate.convertAndSend(SagaTopics.CONTRACT_EVENTS_EXCHANGE, SagaTopics.CONTRACT_CREATED, event);
    }

    public void publishContractStatusChanged(Long contractId, ContractStatus oldStatus, ContractStatus newStatus) {
        ContractStatusChangedEvent event = new ContractStatusChangedEvent(
                contractId,
                oldStatus != null ? oldStatus.name() : null,
                newStatus != null ? newStatus.name() : null
        );

        log.info("Publishing contract.status-changed event: {}", event);
        rabbitTemplate.convertAndSend(SagaTopics.CONTRACT_EVENTS_EXCHANGE, SagaTopics.CONTRACT_STATUS_CHANGED, event);
    }

    public void publishContractCancelled(Long contractId, Long proposalId) {
        ContractCancelledEvent event = new ContractCancelledEvent(contractId, proposalId);

        log.info("Publishing contract.cancelled event: {}", event);
        rabbitTemplate.convertAndSend(SagaTopics.CONTRACT_EVENTS_EXCHANGE, SagaTopics.CONTRACT_CANCELLED, event);
    }
}
