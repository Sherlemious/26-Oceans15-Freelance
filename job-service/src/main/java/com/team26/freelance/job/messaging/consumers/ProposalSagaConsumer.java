package com.team26.freelance.job.messaging.consumers;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.job.config.JobEventConfig;
import com.team26.freelance.contracts.events.ProposalAcceptedEvent;
import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.contracts.events.ProposalWithdrawnEvent;
import com.team26.freelance.job.service.JobService;

@Component
public class ProposalSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProposalSagaConsumer.class);
    private static final String CORRELATION_ID_HEADER = "correlationId";

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public ProposalSagaConsumer(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = JobEventConfig.JOB_PROPOSAL_SAGA_QUEUE)
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
                case JobEventConfig.PROPOSAL_ACCEPTED_KEY -> {
                    ProposalAcceptedEvent event = objectMapper.readValue(message.getBody(), ProposalAcceptedEvent.class);
                    log.info("Handling proposal.accepted proposalId={} jobId={}", event.proposalId(), event.jobId());
                    jobService.handleProposalAccepted(event);
                }
                case JobEventConfig.PROPOSAL_COMPLETED_KEY -> {
                    ProposalCompletedEvent event = objectMapper.readValue(message.getBody(), ProposalCompletedEvent.class);
                    log.info("Handling proposal.completed proposalId={} jobId={}", event.proposalId(), event.jobId());
                    jobService.handleProposalCompleted(event);
                }
                case JobEventConfig.PROPOSAL_CANCELLED_KEY -> {
                    ProposalCancelledEvent event = objectMapper.readValue(message.getBody(), ProposalCancelledEvent.class);
                    log.info("Handling proposal.cancelled proposalId={} jobId={}", event.proposalId(), event.jobId());
                    jobService.handleProposalCancelled(event);
                }
                case JobEventConfig.PROPOSAL_WITHDRAWN_KEY -> {
                    ProposalWithdrawnEvent event = objectMapper.readValue(message.getBody(), ProposalWithdrawnEvent.class);
                    log.info("Handling proposal.withdrawn proposalId={} jobId={}", event.proposalId(), event.jobId());
                    jobService.handleProposalWithdrawn(event);
                }
                default -> throw new IllegalArgumentException("Unsupported proposal routing key: " + routingKey);
            }

            log.info("Processed proposal event routingKey={}", routingKey);
        } catch (Exception ex) {
            log.error("Failed processing proposal event routingKey={} - will be dead-lettered", routingKey, ex);
            throw ex;
        } finally {
            MDC.remove(CORRELATION_ID_HEADER);
        }
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
