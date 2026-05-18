package com.team26.freelance.user.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.SagaTopics;
import com.team26.freelance.user.config.UserEventConfig;
import com.team26.freelance.user.service.UserProposalEventService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProposalEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProposalEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final UserProposalEventService userProposalEventService;

    public ProposalEventConsumer(ObjectMapper objectMapper,
                                 UserProposalEventService userProposalEventService) {
        this.objectMapper = objectMapper;
        this.userProposalEventService = userProposalEventService;
    }

    @RabbitListener(queues = UserEventConfig.USER_PROPOSAL_SAGA_QUEUE)
    public void onProposalEvent(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        bindMessageMdc(message, routingKey);

        log.info("Consuming proposal event routingKey={} correlationId={}",
                routingKey, MDC.get("correlationId"));

        if (routingKey == null) {
            log.error("Failed proposal event consumption: missing routing key correlationId={}",
                    MDC.get("correlationId"));
            clearMessageMdc();
            throw new IllegalArgumentException("Received proposal event without routing key");
        }

        try {
            switch (routingKey) {
                case SagaTopics.PROPOSAL_COMPLETED -> handleProposalCompleted(message);
                case SagaTopics.PROPOSAL_CANCELLED -> handleProposalCancelled(message);
                default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
            }
            log.info("Processed proposal event routingKey={} proposalId={} userId={}",
                    routingKey, MDC.get("proposalId"), MDC.get("userId"));
        } catch (Exception ex) {
            log.error("Failed to process proposal event routingKey={} proposalId={} userId={} error={}",
                    routingKey,
                    MDC.get("proposalId"),
                    MDC.get("userId"),
                    ex.getMessage(),
                    ex);
            throw ex;
        } finally {
            clearMessageMdc();
        }
    }

    private void handleProposalCompleted(Message message) throws IOException {
        ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
        MDC.put("proposalId", String.valueOf(event.proposalId()));
        MDC.put("contractId", String.valueOf(event.contractId()));
        MDC.put("userId", String.valueOf(event.freelancerId()));
        userProposalEventService.handleProposalCompleted(event);
    }

    private void handleProposalCancelled(Message message) throws IOException {
        ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
        MDC.put("proposalId", String.valueOf(event.proposalId()));
        MDC.put("userId", String.valueOf(event.freelancerId()));
        userProposalEventService.handleProposalCancelled(event);
    }

    private void bindMessageMdc(Message message, String routingKey) {
        MessageProperties properties = message.getMessageProperties();
        Object correlationIdHeader = properties.getHeaders().get("correlationId");
        if (correlationIdHeader != null) {
            MDC.put("correlationId", correlationIdHeader.toString());
        }
        if (routingKey != null) {
            MDC.put("routingKey", routingKey);
        }
    }

    private void clearMessageMdc() {
        MDC.remove("correlationId");
        MDC.remove("routingKey");
        MDC.remove("proposalId");
        MDC.remove("contractId");
        MDC.remove("userId");
    }
}
