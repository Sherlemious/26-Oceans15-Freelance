package com.team26.freelance.user.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.user.config.UserEventConfig;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProposalEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProposalEventConsumer.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";

    private final ObjectMapper objectMapper;

    public ProposalEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = UserEventConfig.USER_PROPOSAL_SAGA_QUEUE)
    public void onProposalEvent(Message message) throws IOException {
        String correlationId = resolveCorrelationId(message);
        MDC.put(CORRELATION_ID_HEADER, correlationId);

        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            log.info("Consumed proposal event routingKey={}", routingKey);

            if (routingKey == null) {
                throw new IllegalArgumentException("Received proposal event without routing key");
            }

            switch (routingKey) {
                case SagaTopics.PROPOSAL_COMPLETED -> handleProposalCompleted(message);
                case SagaTopics.PROPOSAL_CANCELLED -> handleProposalCancelled(message);
                default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
            }

            log.info("Processed proposal event routingKey={}", routingKey);
        } catch (Exception ex) {
            log.error("Failed processing proposal event routingKey={} message=will be dead-lettered", routingKey, ex);
            throw ex;
        } finally {
            MDC.remove(CORRELATION_ID_HEADER);
        }
    }

    private void handleProposalCompleted(Message message) throws IOException {
        ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
        log.info("Handling proposal.completed proposalId={} userId={}", event.proposalId(), event.freelancerId());
    }

    private void handleProposalCancelled(Message message) throws IOException {
        ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
        log.info("Handling proposal.cancelled proposalId={} userId={}", event.proposalId(), event.freelancerId());
    }

    private String resolveCorrelationId(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(CORRELATION_ID_HEADER);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        if (value != null) {
            String normalized = String.valueOf(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }

        return UUID.randomUUID().toString();
    }
}
