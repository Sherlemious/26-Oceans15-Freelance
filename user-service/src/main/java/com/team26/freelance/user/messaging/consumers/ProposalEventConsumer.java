package com.team26.freelance.user.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.user.config.UserEventConfig;
import com.team26.freelance.user.service.UserProposalEventService;
import java.io.IOException;
import java.util.Objects;
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
    private static final String ROUTING_KEY_MDC = "routingKey";
    private static final String PROPOSAL_ID_MDC = "proposalId";
    private static final String CONTRACT_ID_MDC = "contractId";
    private static final String USER_ID_MDC = "userId";

    private final ObjectMapper objectMapper;
    private final UserProposalEventService userProposalEventService;

    public ProposalEventConsumer(
            ObjectMapper objectMapper, UserProposalEventService userProposalEventService) {
        this.objectMapper = objectMapper;
        this.userProposalEventService = userProposalEventService;
    }

    @RabbitListener(queues = UserEventConfig.USER_PROPOSAL_SAGA_QUEUE)
    public void onProposalEvent(Message message) throws IOException {
        String correlationId = resolveCorrelationId(message);
        MDC.put(CORRELATION_ID_HEADER, correlationId);

        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (routingKey != null) {
            MDC.put(ROUTING_KEY_MDC, routingKey);
        }

        log.info(
                "Consuming proposal event routingKey={} correlationId={}",
                routingKey,
                MDC.get(CORRELATION_ID_HEADER));

        if (routingKey == null) {
            log.error(
                    "Failed proposal event consumption: missing routing key correlationId={}",
                    MDC.get(CORRELATION_ID_HEADER));
            clearMessageMdc();
            throw new IllegalArgumentException("Received proposal event without routing key");
        }

        try {
            switch (routingKey) {
                case SagaTopics.PROPOSAL_COMPLETED -> handleProposalCompleted(message);
                case SagaTopics.PROPOSAL_CANCELLED -> handleProposalCancelled(message);
                default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
            }
            log.info(
                    "Processed proposal event routingKey={} proposalId={} userId={}",
                    routingKey,
                    MDC.get(PROPOSAL_ID_MDC),
                    MDC.get(USER_ID_MDC));
        } catch (Exception ex) {
            log.error(
                    "Failed to process proposal event routingKey={} proposalId={} userId={} error={}",
                    routingKey,
                    MDC.get(PROPOSAL_ID_MDC),
                    MDC.get(USER_ID_MDC),
                    ex.getMessage(),
                    ex);
            throw ex;
        } finally {
            clearMessageMdc();
        }
    }

    private void handleProposalCompleted(Message message) throws IOException {
        ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
        MDC.put(PROPOSAL_ID_MDC, String.valueOf(event.proposalId()));
        MDC.put(CONTRACT_ID_MDC, String.valueOf(event.contractId()));
        MDC.put(USER_ID_MDC, String.valueOf(event.freelancerId()));
        userProposalEventService.handleProposalCompleted(event);
    }

    private void handleProposalCancelled(Message message) throws IOException {
        ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
        MDC.put(PROPOSAL_ID_MDC, String.valueOf(event.proposalId()));
        MDC.put(USER_ID_MDC, String.valueOf(event.freelancerId()));
        userProposalEventService.handleProposalCancelled(event);
    }

    private void clearMessageMdc() {
        MDC.remove(CORRELATION_ID_HEADER);
        MDC.remove(ROUTING_KEY_MDC);
        MDC.remove(PROPOSAL_ID_MDC);
        MDC.remove(CONTRACT_ID_MDC);
        MDC.remove(USER_ID_MDC);
    }

    private String resolveCorrelationId(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(CORRELATION_ID_HEADER);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        if (Objects.nonNull(value)) {
            String normalized = String.valueOf(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }

        return UUID.randomUUID().toString();
    }
}
